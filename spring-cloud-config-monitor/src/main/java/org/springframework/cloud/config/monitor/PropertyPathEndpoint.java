/*
 * Copyright 2015-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.config.monitor;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP endpoint for webhooks coming from repository providers.
 *
 * @author Dave Syer
 *
 */
@RestController
@RequestMapping(
		path = "${spring.cloud.config.server.monitor.endpoint.path:${spring.cloud.config.monitor.endpoint.path:}}/monitor")
public class PropertyPathEndpoint {

	private static Log log = LogFactory.getLog(PropertyPathEndpoint.class);

	private final PropertyPathNotificationExtractor extractor;

	private final List<PropertyPathNotifier> notifiers;

	/**
	 * Upper bound on the number of dash-separated segments processed when guessing a
	 * service name. Prevents a long, heavily dashed path from producing an unbounded
	 * number of candidate service names (and refresh events). Configurable via
	 * {@code spring.cloud.config.server.monitor.max-dashes}.
	 */
	private final int maxDashes;

	public PropertyPathEndpoint(PropertyPathNotificationExtractor extractor, PropertyPathNotifier notifier) {
		this(extractor, List.of(notifier), MonitorConfigurationProperties.DEFAULT_MAX_DASHES);
	}

	public PropertyPathEndpoint(PropertyPathNotificationExtractor extractor, PropertyPathNotifier notifier,
			int maxDashes) {
		this(extractor, List.of(notifier), maxDashes);
	}

	public PropertyPathEndpoint(PropertyPathNotificationExtractor extractor, List<PropertyPathNotifier> notifiers,
			int maxDashes) {
		this.extractor = extractor;
		this.notifiers = notifiers;
		this.maxDashes = maxDashes;
	}

	@PostMapping
	public Set<String> notifyByPath(@RequestHeader HttpHeaders headers, @RequestBody Map<String, Object> request) {
		PropertyPathNotification notification = this.extractor.extract(headers, request);
		if (notification != null) {

			Set<String> services = new LinkedHashSet<>();

			for (String path : notification.getPaths()) {
				services.addAll(guessServiceName(path));
			}
			if (!services.isEmpty()) {
				for (String service : services) {
					log.info("Refresh for: " + service);
				}
				for (PropertyPathNotifier notifier : this.notifiers) {
					notifier.notifyApplications(services);
				}
				return services;
			}

		}
		return Collections.emptySet();
	}

	@PostMapping(consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
	public Set<String> notifyByForm(@RequestHeader HttpHeaders headers, @RequestParam("path") List<String> request) {
		Map<String, Object> map = new HashMap<>();
		String key = "path";
		map.put(key, request);
		return notifyByPath(headers, map);
	}

	private Set<String> guessServiceName(String path) {
		Set<String> services = new LinkedHashSet<>();
		if (path != null) {
			String stem = StringUtils.stripFilenameExtension(StringUtils.getFilename(StringUtils.cleanPath(path)));
			// TODO: correlate with service registry
			String name = stem + "-";
			int index;
			// support application name with dashes, but bound the number of segments
			// processed so a long, heavily dashed path can't produce an
			// unbounded number of candidate service names
			for (int count = 0; count < this.maxDashes && (index = name.lastIndexOf("-")) >= 0; count++) {
				name = name.substring(0, index);
				if ("application".equals(name)) {
					services.add("*");
				}
				else {
					services.add(name);
				}
			}
			// if dashes remain we stopped early because the limit was reached
			if (name.lastIndexOf("-") >= 0) {
				log.warn("Number of dashes in path '" + path + "' exceeds the configured maximum of " + this.maxDashes
						+ " (spring.cloud.config.server.monitor.max-dashes); stopping service name resolution early");
			}
		}
		return services;
	}

}
