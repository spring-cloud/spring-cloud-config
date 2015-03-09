package org.springframework.cloud.bootstrap.encrypt;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.crypto.encrypt.TextEncryptor;

public class EncryptionBootstrapConfigurationTests {

	@Test
	public void rsaKeyStore() {
		ConfigurableApplicationContext context = new SpringApplicationBuilder(
				EncryptionBootstrapConfiguration.class).web(false).properties(
				"encrypt.keyStore.location:classpath:/server.jks",
				"encrypt.keyStore.password:letmein",
				"encrypt.keyStore.alias:mytestkey", "encrypt.keyStore.secret:changeme")
				.run();
		TextEncryptor encryptor = context.getBean(TextEncryptor.class);
		assertEquals("foo", encryptor.decrypt(encryptor.encrypt("foo")));
	}

}
