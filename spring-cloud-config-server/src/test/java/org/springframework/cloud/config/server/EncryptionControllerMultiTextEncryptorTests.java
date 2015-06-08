package org.springframework.cloud.config.server;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import org.springframework.cloud.config.server.encryption.TextEncryptorLocator;
import org.springframework.security.crypto.encrypt.TextEncryptor;

import static org.junit.Assert.assertEquals;
import static org.springframework.http.MediaType.TEXT_PLAIN;
import static org.springframework.security.crypto.encrypt.Encryptors.noOpText;
import static org.springframework.security.crypto.encrypt.Encryptors.text;

/**
 * @author Bartosz Wojtkiewicz
 *
 */

public class EncryptionControllerMultiTextEncryptorTests {
	NaiveMultiTextEncryptorLocator encryptorLocator = new NaiveMultiTextEncryptorLocator();
	ConfigServerProperties properties = new ConfigServerProperties();
	EncryptionController controller = new EncryptionController(encryptorLocator, properties);

	String application = "application";
	String profiles = "profile1,profile2";
	String data = "foo";

	@Test
	public void shouldEncryptUsingApplicationAndProfiles() {
		// given
		encryptorLocator.addSupportFor(application, profiles);

		// when
		String encrypted = controller.encrypt(application, profiles, data, TEXT_PLAIN);

		// then
		assertEquals(data, controller.decrypt(application, profiles, encrypted, TEXT_PLAIN));
	}

	@Test(expected = KeyNotInstalledException.class)
	public void shouldNotEncryptUsingUnknownApplicationName() {
		// given
		String application = "unknown";

		// when
		controller.encrypt(application, profiles, data, TEXT_PLAIN);

		// then exception is thrown
	}

	@Test(expected = KeyNotInstalledException.class)
	public void shouldNotDecryptUsingUnknownApplicationName() {
		// given
		String application = "unknown";

		// when
		controller.decrypt(application, profiles, data, TEXT_PLAIN);

		// then exception is thrown
	}

	class NaiveMultiTextEncryptorLocator implements TextEncryptorLocator {
		private Map<String, TextEncryptor> encryptors = new HashMap<>();

		@Override
		public TextEncryptor locate(String applicationName, String profiles) {
			String key = applicationName + profiles;
			return encryptors.containsKey(key) ? encryptors.get(key) : noOpText();
		}

		public void addSupportFor(String applicationName, String profiles) {
			encryptors.put(applicationName + profiles, text(applicationName, "11"));
		}
	}
}
