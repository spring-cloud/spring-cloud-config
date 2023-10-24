/*
 * Copyright 2002-2023 the original author or authors.
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

package org.springframework.cloud.config.server.encryption;

import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.bootstrap.encrypt.TextEncryptorUtils;
import org.springframework.cloud.config.server.config.DefaultTextEncryptionAutoConfiguration;
import org.springframework.cloud.config.server.config.RsaEncryptionAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.security.rsa.crypto.RsaSecretEncryptor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Ryan Baxter
 */
public class EncryptionAutoConfigurationTests {

	@Test
	public void defaultNoKeyAutoConfigurationTest() {
		ConfigurableApplicationContext context = new SpringApplicationBuilder(
				DefaultTextEncryptionAutoConfiguration.class, RsaEncryptionAutoConfiguration.class,
				ServletWebServerFactoryAutoConfiguration.class, ServerProperties.class,
				PropertyPlaceholderAutoConfiguration.class).properties("server.port=0").run();
		TextEncryptor textEncryptor = context.getBean(TextEncryptor.class);
		String[] textEncryptorLocatorNames = context.getBeanNamesForType(TextEncryptorLocator.class);
		assertThat(textEncryptor).isInstanceOf(TextEncryptorUtils.FailsafeTextEncryptor.class);
		assertThat(textEncryptorLocatorNames).isEmpty();
		context.close();
	}

	@Test
	public void defaultKeyStoreAutoConfigurationTest() {
		ConfigurableApplicationContext context = new SpringApplicationBuilder(
				DefaultTextEncryptionAutoConfiguration.class, RsaEncryptionAutoConfiguration.class,
				ServletWebServerFactoryAutoConfiguration.class, ServerProperties.class,
				PropertyPlaceholderAutoConfiguration.class)
						.properties("server.port=0", "encrypt.key-store.location=classpath:server.jks",
								"encrypt.key-store.password=letmein", "encrypt.key-store.alias=myKey")
						.run();
		TextEncryptor textEncryptor = context.getBean(TextEncryptor.class);
		TextEncryptorLocator textEncryptorLocator = context.getBean(TextEncryptorLocator.class);
		assertThat(textEncryptor).isInstanceOf(RsaSecretEncryptor.class);
		assertThat(textEncryptorLocator).isInstanceOf(KeyStoreTextEncryptorLocator.class);
		context.close();
	}

	@Test
	public void defaultKeyAutoConfigurationTest() {
		ConfigurableApplicationContext context = new SpringApplicationBuilder(
				DefaultTextEncryptionAutoConfiguration.class, RsaEncryptionAutoConfiguration.class,
				ServletWebServerFactoryAutoConfiguration.class, ServerProperties.class,
				PropertyPlaceholderAutoConfiguration.class).properties("server.port=0", "encrypt.key=mykey").run();
		TextEncryptor textEncryptor = context.getBean(TextEncryptor.class);
		String[] textEncryptorLocatorNames = context.getBeanNamesForType(TextEncryptorLocator.class);
		assertThat(textEncryptor.getClass().getName())
				.isEqualTo("org.springframework.security.crypto.encrypt.HexEncodingTextEncryptor");
		assertThat(textEncryptorLocatorNames).isEmpty();
		context.close();
	}

	@Test
	public void bootstrapNoKeyAutoConfigurationTest() {
		ConfigurableApplicationContext context = new SpringApplicationBuilder(
				DefaultTextEncryptionAutoConfiguration.class, RsaEncryptionAutoConfiguration.class,
				ServletWebServerFactoryAutoConfiguration.class, ServerProperties.class,
				PropertyPlaceholderAutoConfiguration.class)
						.properties("server.port=0", "spring.cloud.bootstrap.enabled=true").run();
		TextEncryptor textEncryptor = context.getBean(TextEncryptor.class);
		String[] textEncryptorLocatorNames = context.getBeanNamesForType(TextEncryptorLocator.class);
		assertThat(textEncryptor.getClass().isInstance(Encryptors.noOpText().getClass()));
		assertThat(textEncryptorLocatorNames).isEmpty();
		context.close();
	}

	@Test
	public void bootstrapKeyAutoConfigurationTests() {
		ConfigurableApplicationContext context = new SpringApplicationBuilder(
				DefaultTextEncryptionAutoConfiguration.class, RsaEncryptionAutoConfiguration.class,
				ServletWebServerFactoryAutoConfiguration.class, ServerProperties.class,
				PropertyPlaceholderAutoConfiguration.class)
						.properties("server.port=0", "spring.cloud.bootstrap.enabled=true", "encrypt.key=mykey").run();
		TextEncryptor textEncryptor = context.getBean(TextEncryptor.class);
		assertThat(textEncryptor.getClass().getName())
				.isEqualTo("org.springframework.security.crypto.encrypt.HexEncodingTextEncryptor");
		context.close();
	}

	@Test
	public void bootstrapKeyStoreAutoConfigurationTest() {
		ConfigurableApplicationContext context = new SpringApplicationBuilder(
				DefaultTextEncryptionAutoConfiguration.class, RsaEncryptionAutoConfiguration.class,
				ServletWebServerFactoryAutoConfiguration.class, ServerProperties.class,
				PropertyPlaceholderAutoConfiguration.class)
						.properties("server.port=0", "spring.cloud.bootstrap.enabled=true",
								"encrypt.key-store.location=classpath:server.jks", "encrypt.key-store.password=letmein",
								"encrypt.key-store.alias=myKey")
						.run();
		TextEncryptor textEncryptor = context.getBean(TextEncryptor.class);
		TextEncryptorLocator textEncryptorLocator = context.getBean(TextEncryptorLocator.class);
		assertThat(textEncryptor).isInstanceOf(LocatorTextEncryptor.class);
		assertThat(textEncryptorLocator).isInstanceOf(KeyStoreTextEncryptorLocator.class);
		context.close();
	}

}
