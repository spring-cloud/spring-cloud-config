/*
 * Copyright 2015-2019 the original author or authors.
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

import org.springframework.cloud.bus.event.RefreshRemoteApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP endpoint for webhooks coming from repository providers.
 *
 * @author Dave Syer
 *
 */
@RestController
@RequestMapping(path = "${spring.cloud.config.monitor.endpoint.path:}/monitor")
public class PropertyPathEndpoint implements ApplicationEventPublisherAware {

	private static Log log = LogFactory.getLog(PropertyPathEndpoint.class);

	private final PropertyPathNotificationExtractor extractor;

	private ApplicationEventPublisher applicationEventPublisher;

	private String busId;

	public PropertyPathEndpoint(PropertyPathNotificationExtractor extractor,
			String busId) {
		this.extractor = extractor;
		this.busId = busId;
	}

	/* for testing */ String getBusId() {
		return this.busId;
	}

	@Override
	public void setApplicationEventPublisher(
			ApplicationEventPublisher applicationEventPublisher) {
		this.applicationEventPublisher = applicationEventPublisher;
	}

	@RequestMapping(method = RequestMethod.POST)
	public Set<String> notifyByPath(@RequestHeader HttpHeaders headers,
			@RequestBody Map<String, Object> request) {
		PropertyPathNotification notification = this.extractor.extract(headers, request);
		if (notification != null) {

			Set<String> services = new LinkedHashSet<>();

			for (String path : notification.getPaths()) {
				services.addAll(guessServiceName(path));
			}
			if (this.applicationEventPublisher != null) {
				for (String service : services) {
					log.info("Refresh for: " + service);
					this.applicationEventPublisher.publishEvent(
							new RefreshRemoteApplicationEvent(this, this.busId, service));
				}
				return services;
			}

		}
		return Collections.emptySet();
	}

	@RequestMapping(method = RequestMethod.POST,
			consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
	public Set<String> notifyByForm(@RequestHeader HttpHeaders headers,
			@RequestParam("path") List<String> request) {
		Map<String, Object> map = new HashMap<>();
		String key = "path";
		map.put(key, request);
		return notifyByPath(headers, map);
	}

	private Set<String> guessServiceName(String path) {
		Set<String> services = new LinkedHashSet<>();
		if (path != null) {
			String stem = StringUtils.stripFilenameExtension(
					StringUtils.getFilename(StringUtils.cleanPath(path)));
			// TODO: correlate with service registry
			int index = stem.indexOf("-");
			while (index >= 0) {
				String name = stem.substring(0, index);
				String profile = stem.substring(index + 1);
				if ("application".equals(name)) {
					services.add("*:" + profile);
				}
				else if (!name.startsWith("application")) {
					services.add(name + ":" + profile);
				}
				index = stem.indexOf("-", index + 1);
			}
			String name = stem;
			if ("application".equals(name)) {
				services.add("*");
			}
			else if (!name.startsWith("application")) {
				services.add(name);
			}
		}
		return services;
	}

}
