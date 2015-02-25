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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.bootstrap.encrypt.KeyProperties.KeyStore;
import org.springframework.cloud.configure.encrypt.EncryptorFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.security.rsa.crypto.KeyStoreKeyFactory;
import org.springframework.security.rsa.crypto.RsaSecretEncryptor;
import org.springframework.util.StringUtils;

/**
 * @author Dave Syer
 *
 */
@Configuration
@ConditionalOnClass({TextEncryptor.class, RsaSecretEncryptor.class})
@EnableConfigurationProperties(KeyProperties.class)
public class EncryptionBootstrapConfiguration {

	@Autowired(required = false)
	private TextEncryptor encryptor;

	@Autowired
	private KeyProperties key;

	@Configuration
	@Conditional(KeyCondition.class)
	@ConditionalOnClass(RsaSecretEncryptor.class)
	protected static class RsaEncryptionConfiguration {

		@Autowired
		private KeyProperties key;

		@Bean
		@ConditionalOnMissingBean(TextEncryptor.class)
		public TextEncryptor textEncryptor() {
			KeyStore keyStore = key.getKeyStore();
			if (keyStore.getLocation() != null && keyStore.getLocation().exists()) {
				return new RsaSecretEncryptor(
						new KeyStoreKeyFactory(keyStore.getLocation(), keyStore
								.getPassword().toCharArray()).getKeyPair(keyStore
								.getAlias()));
			}
			return new EncryptorFactory().create(key.getKey());
		}

	}

	@Configuration
	@Conditional(KeyCondition.class)
	@ConditionalOnMissingClass(name = "org.springframework.security.rsa.crypto.RsaSecretEncryptor")
	protected static class VanillaEncryptionConfiguration {

		@Autowired
		private KeyProperties key;

		@Bean
		@ConditionalOnMissingBean(TextEncryptor.class)
		public TextEncryptor textEncryptor() {
			return new EncryptorFactory().create(key.getKey());
		}

	}

	@Bean
	public EnvironmentDecryptApplicationInitializer environmentDecryptApplicationListener() {
		if (encryptor == null) {
			encryptor = new FailsafeTextEncryptor();
		}
		EnvironmentDecryptApplicationInitializer listener = new EnvironmentDecryptApplicationInitializer(encryptor);
		listener.setFailOnError(key.isFailOnError());
		return listener;
	}

	public static class KeyCondition extends SpringBootCondition {

		@Override
		public ConditionOutcome getMatchOutcome(ConditionContext context,
				AnnotatedTypeMetadata metadata) {
			Environment environment = context.getEnvironment();
			if (hasProperty(environment, "encrypt.keyStore.location")) {
				if (hasProperty(environment, "encrypt.keyStore.password")) {
					return ConditionOutcome.match("Keystore found in Environment");
				}
				return ConditionOutcome
						.noMatch("Keystore found but no password in Environment");
			}
			else if (hasProperty(environment, "encrypt.key")) {
				return ConditionOutcome.match("Key found in Environment");
			}
			return ConditionOutcome.noMatch("Keystore nor key found in Environment");
		}

		private boolean hasProperty(Environment environment, String key) {
			String value = environment.getProperty(key);
			if (value == null) {
				return false;
			}
			return StringUtils.hasText(environment.resolvePlaceholders(value));
		}

	}

	/**
	 * TextEncryptor that just fails, so that users don't get a false sense of security
	 * adding ciphers to config files and not getting them decrypted.
	 * 
	 * @author Dave Syer
	 *
	 */
	protected static class FailsafeTextEncryptor implements TextEncryptor {

		@Override
		public String encrypt(String text) {
			throw new UnsupportedOperationException(
					"No encryption for FailsafeTextEncryptor. Did you configure the keystore correctly?");
		}

		@Override
		public String decrypt(String encryptedText) {
			throw new UnsupportedOperationException(
					"No decryption for FailsafeTextEncryptor. Did you configure the keystore correctly?");
		}

	}

}
