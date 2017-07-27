package org.springframework.cloud.config.server.encryption;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.encrypt.TextEncryptor;

/**
 * Provide a default {@link TextEncryptorLocator} when a symmetric key is configured
 *
 * @author Ollie Hughes
 *
*/
@Configuration
@ConditionalOnProperty(value = "encrypt.key", matchIfMissing = false)
public class SymmetricKeyEncryptor {

	@Autowired
	private TextEncryptor encryptor;

	@Bean
	@ConditionalOnMissingBean
	public TextEncryptorLocator defaultTextEncryptorLocator() {
		return new SingleTextEncryptorLocator(this.encryptor);
	}

}
