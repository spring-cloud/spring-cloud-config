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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;

import org.springframework.boot.context.config.ConfigData;
import org.springframework.boot.context.config.ConfigDataLoader;
import org.springframework.boot.context.config.ConfigDataLoaderContext;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.OriginTrackedMapPropertySource;
import org.springframework.boot.origin.Origin;
import org.springframework.boot.origin.OriginTrackedValue;
import org.springframework.cloud.config.client.ConfigServerBootstrapper.LoadContext;
import org.springframework.cloud.config.client.ConfigServerBootstrapper.LoaderInterceptor;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.core.Ordered;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Base64Utils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.springframework.cloud.config.client.ConfigClientProperties.AUTHORIZATION;
import static org.springframework.cloud.config.client.ConfigClientProperties.STATE_HEADER;
import static org.springframework.cloud.config.client.ConfigClientProperties.TOKEN_HEADER;
import static org.springframework.cloud.config.environment.EnvironmentMediaType.V2_JSON;

public class ConfigServerConfigDataLoader implements ConfigDataLoader<ConfigServerConfigDataLocation>, Ordered {

	protected final Log logger;

	public ConfigServerConfigDataLoader(Log logger) {
		this.logger = logger;
	}

	@Override
	public int getOrder() {
		return -1;
	}

	@Override
	// TODO: implement retry LoaderInterceptor
	public ConfigData load(ConfigDataLoaderContext context, ConfigServerConfigDataLocation location) {
		if (context.getBootstrapContext().isRegistered(LoaderInterceptor.class)) {
			LoaderInterceptor interceptor = context.getBootstrapContext().get(LoaderInterceptor.class);
			Binder binder = context.getBootstrapContext().get(Binder.class);
			return interceptor.apply(new LoadContext(context, location, binder, this::doLoad));
		}
		return doLoad(context, location);
	}

	public ConfigData doLoad(ConfigDataLoaderContext context, ConfigServerConfigDataLocation location) {
		ConfigClientProperties properties = location.getProperties();
		List<PropertySource<?>> composite = new ArrayList<>();
		Exception error = null;
		String errorBody = null;
		try {
			String[] labels = new String[] { "" };
			if (StringUtils.hasText(properties.getLabel())) {
				labels = StringUtils.commaDelimitedListToStringArray(properties.getLabel());
			}
			String state = ConfigClientStateHolder.getState();
			// Try all the labels until one works
			for (String label : labels) {
				Environment result = getRemoteEnvironment(context, location, label.trim(), state);
				if (result != null) {
					log(result);

					// result.getPropertySources() can be null if using xml
					if (result.getPropertySources() != null) {
						for (org.springframework.cloud.config.environment.PropertySource source : result
								.getPropertySources()) {
							@SuppressWarnings("unchecked")
							Map<String, Object> map = translateOrigins(source.getName(),
									(Map<String, Object>) source.getSource());
							composite.add(0,
									new OriginTrackedMapPropertySource("configserver:" + source.getName(), map));
						}
					}

					HashMap<String, Object> map = new HashMap<>();
					if (StringUtils.hasText(result.getState())) {
						putValue(map, "config.client.state", result.getState());
					}
					if (StringUtils.hasText(result.getVersion())) {
						putValue(map, "config.client.version", result.getVersion());
					}
					// the existence of this property source confirms a successful
					// response from config server
					composite.add(0, new MapPropertySource("configClient", map));
					return new ConfigData(composite);
				}
			}
			errorBody = String.format("None of labels %s found", Arrays.toString(labels));
		}
		catch (HttpServerErrorException e) {
			error = e;
			if (MediaType.APPLICATION_JSON.includes(e.getResponseHeaders().getContentType())) {
				errorBody = e.getResponseBodyAsString();
			}
		}
		catch (Exception e) {
			error = e;
		}
		if (properties.isFailFast() || !location.isOptional()) {
			String reason;
			if (properties.isFailFast()) {
				reason = "the fail fast property is set";
			}
			else {
				reason = "the location is not optional";
			}
			throw new IllegalStateException("Could not locate PropertySource and " + reason + ", failing"
					+ (errorBody == null ? "" : ": " + errorBody), error);
		}
		logger.warn("Could not locate PropertySource: " + (error != null ? error.getMessage() : errorBody));
		return null;
	}

	protected void log(Environment result) {
		if (logger.isInfoEnabled()) {
			logger.info(String.format("Located environment: name=%s, profiles=%s, label=%s, version=%s, state=%s",
					result.getName(), result.getProfiles() == null ? "" : Arrays.asList(result.getProfiles()),
					result.getLabel(), result.getVersion(), result.getState()));
		}
		if (logger.isDebugEnabled()) {
			List<org.springframework.cloud.config.environment.PropertySource> propertySourceList = result
					.getPropertySources();
			if (propertySourceList != null) {
				int propertyCount = 0;
				for (org.springframework.cloud.config.environment.PropertySource propertySource : propertySourceList) {
					propertyCount += propertySource.getSource().size();
				}
				logger.debug(String.format("Environment %s has %d property sources with %d properties.",
						result.getName(), result.getPropertySources().size(), propertyCount));
			}

		}
	}

	protected Map<String, Object> translateOrigins(String name, Map<String, Object> source) {
		Map<String, Object> withOrigins = new LinkedHashMap<>();
		for (Map.Entry<String, Object> entry : source.entrySet()) {
			boolean hasOrigin = false;

			if (entry.getValue() instanceof Map) {
				@SuppressWarnings("unchecked")
				Map<String, Object> value = (Map<String, Object>) entry.getValue();
				if (value.size() == 2 && value.containsKey("origin") && value.containsKey("value")) {
					Origin origin = new ConfigServicePropertySourceLocator.ConfigServiceOrigin(name,
							value.get("origin"));
					OriginTrackedValue trackedValue = OriginTrackedValue.of(value.get("value"), origin);
					withOrigins.put(entry.getKey(), trackedValue);
					hasOrigin = true;
				}
			}

			if (!hasOrigin) {
				withOrigins.put(entry.getKey(), entry.getValue());
			}
		}
		return withOrigins;
	}

	protected void putValue(HashMap<String, Object> map, String key, String value) {
		if (StringUtils.hasText(value)) {
			map.put(key, value);
		}
	}

	protected Environment getRemoteEnvironment(ConfigDataLoaderContext context, ConfigServerConfigDataLocation location,
			String label, String state) {
		ConfigClientProperties properties = location.getProperties();
		RestTemplate restTemplate = context.getBootstrapContext().get(RestTemplate.class);

		String path = "/{name}/{profile}";
		String name = properties.getName();
		String profile = StringUtils.collectionToCommaDelimitedString(location.getProfiles().getAccepted());
		String token = properties.getToken();
		int noOfUrls = properties.getUri().length;
		if (noOfUrls > 1) {
			logger.info("Multiple Config Server Urls found listed.");
		}

		Object[] args = new String[] { name, profile };
		if (StringUtils.hasText(label)) {
			// workaround for Spring MVC matching / in paths
			label = Environment.denormalize(label);
			args = new String[] { name, profile, label };
			path = path + "/{label}";
		}
		ResponseEntity<Environment> response = null;

		for (int i = 0; i < noOfUrls; i++) {
			ConfigClientProperties.Credentials credentials = properties.getCredentials(i);
			String uri = credentials.getUri();
			String username = credentials.getUsername();
			String password = credentials.getPassword();

			logger.info("Fetching config from server at : " + uri);

			try {
				HttpHeaders headers = new HttpHeaders();
				headers.setAccept(Collections.singletonList(MediaType.parseMediaType(V2_JSON)));
				addAuthorizationToken(properties, headers, username, password);
				if (StringUtils.hasText(token)) {
					headers.add(TOKEN_HEADER, token);
				}
				if (StringUtils.hasText(state) && properties.isSendState()) {
					headers.add(STATE_HEADER, state);
				}

				final HttpEntity<Void> entity = new HttpEntity<>((Void) null, headers);
				response = restTemplate.exchange(uri + path, HttpMethod.GET, entity, Environment.class, args);
			}
			catch (HttpClientErrorException e) {
				if (e.getStatusCode() != HttpStatus.NOT_FOUND) {
					throw e;
				}
			}
			catch (ResourceAccessException e) {
				logger.info("Connect Timeout Exception on Url - " + uri + ". Will be trying the next url if available");
				if (i == noOfUrls - 1) {
					throw e;
				}
				else {
					continue;
				}
			}

			if (response == null || response.getStatusCode() != HttpStatus.OK) {
				return null;
			}

			Environment result = response.getBody();
			return result;
		}

		return null;
	}

	protected void addAuthorizationToken(ConfigClientProperties configClientProperties, HttpHeaders httpHeaders,
			String username, String password) {
		String authorization = configClientProperties.getHeaders().get(AUTHORIZATION);

		if (password != null && authorization != null) {
			throw new IllegalStateException("You must set either 'password' or 'authorization'");
		}

		if (password != null) {
			byte[] token = Base64Utils.encode((username + ":" + password).getBytes());
			httpHeaders.add("Authorization", "Basic " + new String(token));
		}
		else if (authorization != null) {
			httpHeaders.add("Authorization", authorization);
		}

	}

}
