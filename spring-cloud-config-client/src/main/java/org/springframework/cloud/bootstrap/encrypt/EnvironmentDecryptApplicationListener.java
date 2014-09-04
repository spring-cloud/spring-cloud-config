/*
 * Copyright 2013-2014 the original author or authors.
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
package org.springframework.cloud.bootstrap.encrypt;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.boot.context.config.ConfigFileApplicationListener;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.util.ReflectionUtils;

/**
 * @author Dave Syer
 *
 */
public class EnvironmentDecryptApplicationListener implements
		ApplicationContextInitializer<ConfigurableApplicationContext>, Ordered {

	private static Log logger = LogFactory
			.getLog(EnvironmentDecryptApplicationListener.class);

	private int order = ConfigFileApplicationListener.DEFAULT_ORDER + 1;

	private TextEncryptor encryptor;

	private Field propertySourcesField;

	{
		initField();
	}

	private void initField() {
		propertySourcesField = ReflectionUtils.findField(CompositePropertySource.class,
				"propertySources");
		propertySourcesField.setAccessible(true);
	}

	public EnvironmentDecryptApplicationListener(TextEncryptor encryptor) {
		this.encryptor = encryptor;
	}

	@Override
	public int getOrder() {
		return order;
	}

	@Override
	public void initialize(ConfigurableApplicationContext applicationContext) {
		
		ConfigurableEnvironment environment = applicationContext.getEnvironment();
		Map<String, Object> overrides = new LinkedHashMap<String, Object>();
		for (PropertySource<?> source : environment.getPropertySources()) {
			decrypt(source, overrides);
		}
		if (!overrides.isEmpty()) {
			environment.getPropertySources().addFirst(
					new MapPropertySource("decrypted", overrides));
		}
	}

	private void decrypt(PropertySource<?> source, Map<String, Object> overrides) {

		if (source instanceof EnumerablePropertySource) {

			EnumerablePropertySource<?> enumerable = (EnumerablePropertySource<?>) source;
			for (String key : enumerable.getPropertyNames()) {
				String value = source.getProperty(key).toString();
				if (value.startsWith("{cipher}")) {
					value = value.substring("{cipher}".length());
					try {
						value = encryptor.decrypt(value);
						overrides.put(key, value);
					}
					catch (Exception e) {
						logger.warn("Cannot decrypt: key=" + key);
					}
				}
			}

		}
		else if (source instanceof CompositePropertySource) {

			try {
				@SuppressWarnings("unchecked")
				Set<PropertySource<?>> sources = (Set<PropertySource<?>>) propertySourcesField
						.get(source);
				for (PropertySource<?> nested : sources) {
					decrypt(nested, overrides);
				}
			}
			catch (IllegalAccessException e) {
				return;
			}
		}

	}

}
