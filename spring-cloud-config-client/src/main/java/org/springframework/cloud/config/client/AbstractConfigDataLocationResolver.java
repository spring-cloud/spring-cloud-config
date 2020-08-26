/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.config.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;

import org.springframework.boot.context.config.ConfigDataLocationResolver;
import org.springframework.boot.context.config.ConfigDataLocationResolverContext;
import org.springframework.boot.context.config.Profiles;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.Ordered;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import static org.springframework.cloud.config.client.ConfigClientProperties.AUTHORIZATION;

public abstract class AbstractConfigDataLocationResolver<L extends AbstractConfigDataLocation>
		implements ConfigDataLocationResolver<L>, Ordered {

	/**
	 * Prefix for Config Server imports.
	 */
	public static final String PREFIX = "configserver:";

	protected final Log log;

	public AbstractConfigDataLocationResolver(Log log) {
		this.log = log;
	}

	@Override
	public int getOrder() {
		return -1;
	}

	protected ConfigClientProperties loadProperties(Binder binder) {
		ConfigClientProperties configClientProperties = binder
				.bind(ConfigClientProperties.PREFIX,
						Bindable.of(ConfigClientProperties.class))
				.orElse(new ConfigClientProperties());
		String applicationName = binder.bind("spring.application.name", String.class)
				.orElse("application");
		configClientProperties.setName(applicationName);
		return configClientProperties;
	}

	protected RestTemplate createRestTemplate(ConfigClientProperties properties) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		if (properties.getRequestReadTimeout() < 0) {
			throw new IllegalStateException("Invalid Value for Read Timeout set.");
		}
		if (properties.getRequestConnectTimeout() < 0) {
			throw new IllegalStateException("Invalid Value for Connect Timeout set.");
		}
		requestFactory.setReadTimeout(properties.getRequestReadTimeout());
		requestFactory.setConnectTimeout(properties.getRequestConnectTimeout());
		RestTemplate template = new RestTemplate(requestFactory);
		Map<String, String> headers = new HashMap<>(properties.getHeaders());
		if (headers.containsKey(AUTHORIZATION)) {
			headers.remove(AUTHORIZATION); // To avoid redundant addition of header
		}
		if (!headers.isEmpty()) {
			template.setInterceptors(Collections.singletonList(
					new ConfigServicePropertySourceLocator.GenericRequestHeaderInterceptor(
							headers)));
		}

		return template;
	}

	protected Log getLog() {
		return this.log;
	}

	public boolean isResolvable(ConfigDataLocationResolverContext context,
			String location) {
		return location.startsWith(getPrefix());
	}

	protected String getPrefix() {
		return PREFIX;
	}

	public List<L> resolve(ConfigDataLocationResolverContext context, String location,
			boolean optional) {
		return Collections.emptyList();
	}

	public List<L> resolveProfileSpecific(ConfigDataLocationResolverContext context,
			String location, boolean optional, Profiles profiles) {

		ConfigClientProperties properties = loadProperties(context.getBinder());

		String uris = (location.startsWith(getPrefix()))
				? location.substring(getPrefix().length()) : location;

		if (StringUtils.hasText(uris)) {
			String[] uri = StringUtils.commaDelimitedListToStringArray(uris);
			properties.setUri(uri);
		}

		RestTemplate restTemplate = createRestTemplate(properties);

		List<L> locations = new ArrayList<>();
		locations.add(
				createConfigDataLocation(optional, profiles, properties, restTemplate));

		return locations;
	}

	protected abstract L createConfigDataLocation(boolean optional, Profiles profiles,
			ConfigClientProperties properties, RestTemplate restTemplate);

}
