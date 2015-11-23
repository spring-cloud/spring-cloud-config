/*
 * Copyright 2013-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.config.client;

import java.util.Arrays;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.bootstrap.config.PropertySourceLocator;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.MediaType;
import org.springframework.retry.annotation.Retryable;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpServerErrorException;

/**
 * 
 * @author Jeffrey Nelson
 *
 * @param <C> type of Context class used
 */
public abstract class BaseConfigServicePropertySourceLocator<C extends BaseConfigServicePropertySourceLocator.Context>
		implements PropertySourceLocator {

	private static Log logger = LogFactory.getLog(BaseConfigServicePropertySourceLocator.class);

	private final ConfigClientProperties defaults;
	private final Class<C> contextClass;

	public BaseConfigServicePropertySourceLocator(ConfigClientProperties defaults, Class<C> contextClass) {
		this.defaults = defaults;
		this.contextClass = contextClass;
	}

	@Override
	@Retryable(interceptor = "configServerRetryInterceptor")
	public org.springframework.core.env.PropertySource<?> locate(org.springframework.core.env.Environment environment) {
		C context = newContext(environment);
		fillContext(context, environment);

		CompositePropertySource composite = new CompositePropertySource("configService");
		Exception error = null;
		String errorBody = null;
		logger.info("Fetching config from server");
		try {
			String[] labels = new String[] { "" };
			if (StringUtils.hasText(context.getConfigClientProperties().getLabel())) {
				labels = StringUtils.commaDelimitedListToStringArray(context.getConfigClientProperties().getLabel());
			}
			// Try all the labels until one works
			for (String label : labels) {
				context.setLabel(label.trim());
				Environment result = getRemoteEnvironment(context);
				if (result != null) {
					logger.info(String.format("Located environment: name=%s, profiles=%s, label=%s, version=%s",
							result.getName(), result.getProfiles() == null ? "" : Arrays.asList(result.getProfiles()),
							result.getLabel(), result.getVersion()));

					for (PropertySource source : result.getPropertySources()) {
						@SuppressWarnings("unchecked")
						Map<String, Object> map = (Map<String, Object>) source.getSource();
						composite.addPropertySource(new MapPropertySource(source.getName(), map));
					}
					return composite;
				}
			}
		} catch (HttpServerErrorException e) {
			error = e;
			if (MediaType.APPLICATION_JSON.includes(e.getResponseHeaders().getContentType())) {
				errorBody = e.getResponseBodyAsString();
			}
		} catch (Exception e) {
			error = e;
		}
		if (context.getConfigClientProperties() != null && context.getConfigClientProperties().isFailFast()) {
			throw new IllegalStateException(
					"Could not locate PropertySource and the fail fast property is set, failing", error);
		}
		logger.warn("Could not locate PropertySource: "
				+ (errorBody == null ? error == null ? "label not found" : error.getMessage() : errorBody));
		return null;

	}
	
	protected C newContext(org.springframework.core.env.Environment environment) {
		try {
			C context = contextClass.newInstance();
			return context;
		} catch (Exception e) {
			throw new IllegalArgumentException("Error creating new context instance. Must have default ctor", e);
		}
	}

	protected void fillContext(C context, org.springframework.core.env.Environment environment) {
		ConfigClientProperties client = defaults.override(environment);
		context.setConfigClientProperties(client);
	}
	
	protected abstract Environment getRemoteEnvironment(C context);

	public static class Context {

		private ConfigClientProperties configClientProperties;
		private String label;

		public ConfigClientProperties getConfigClientProperties() {
			return configClientProperties;
		}

		public void setConfigClientProperties(ConfigClientProperties configClientProperties) {
			this.configClientProperties = configClientProperties;
		}

		public String getLabel() {
			return label;
		}

		public void setLabel(String label) {
			this.label = label;
		}
	}
}
