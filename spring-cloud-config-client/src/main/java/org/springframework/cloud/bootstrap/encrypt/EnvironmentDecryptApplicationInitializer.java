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

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.security.crypto.encrypt.TextEncryptor;

/**
 * @author Dave Syer
 *
 */
public class EnvironmentDecryptApplicationInitializer implements
		ApplicationContextInitializer<ConfigurableApplicationContext>, Ordered {

	private static Log logger = LogFactory
			.getLog(EnvironmentDecryptApplicationInitializer.class);

	private int order = Ordered.HIGHEST_PRECEDENCE + 15;

	private TextEncryptor encryptor;

	private boolean failOnError = true;

	public EnvironmentDecryptApplicationInitializer(TextEncryptor encryptor) {
		this.encryptor = encryptor;
	}

	/**
	 * Strategy to determine how to handle exceptions during decryption.
	 * 
	 * @param failOnError the flag value (default true)
	 */
	public void setFailOnError(boolean failOnError) {
		this.failOnError = failOnError;
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
						if (logger.isDebugEnabled()) {
							logger.debug("Decrypted: key=" + key);
						}
					}
					catch (Exception e) {
						String message = "Cannot decrypt: key=" + key;
						if (failOnError) {
							throw new IllegalStateException(message, e);
						}
						if (logger.isDebugEnabled()) {
							logger.warn(message, e);
						}
						else {
							logger.warn(message);
						}
						// Set value to empty to avoid making a password out of the
						// cipher text
						value = "";
					}
					overrides.put(key, value);
				}
			}

		}
		else if (source instanceof CompositePropertySource) {

			for (PropertySource<?> nested : ((CompositePropertySource) source)
					.getPropertySources()) {
				decrypt(nested, overrides);
			}

		}

	}

}
