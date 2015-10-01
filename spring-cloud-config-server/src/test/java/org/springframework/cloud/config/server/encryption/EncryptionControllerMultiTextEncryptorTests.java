package org.springframework.cloud.config.server.encryption;

import static org.junit.Assert.assertEquals;
import static org.springframework.http.MediaType.TEXT_PLAIN;

import org.junit.Test;
import org.springframework.security.crypto.encrypt.Encryptors;

/**
 * @author Bartosz Wojtkiewicz
 *
 */

public class EncryptionControllerMultiTextEncryptorTests {

	EncryptionController controller = new EncryptionController(
			new SingleTextEncryptorLocator(Encryptors.noOpText()));

	String application = "application";
	String profiles = "profile1,profile2";
	String data = "foo";

	@Test
	public void shouldEncryptUsingApplicationAndProfiles() {

		this.controller = new EncryptionController(
				new SingleTextEncryptorLocator(Encryptors.text("application", "11")));

		// when
		String encrypted = this.controller.encrypt(this.application, this.profiles,
				this.data, TEXT_PLAIN);

		// then
		assertEquals(this.data, this.controller.decrypt(this.application, this.profiles,
				encrypted, TEXT_PLAIN));
	}

	@Test(expected = KeyNotInstalledException.class)
	public void shouldNotEncryptUsingNoOp() {
		// given
		String application = "unknown";

		// when
		this.controller.encrypt(application, this.profiles, this.data, TEXT_PLAIN);

		// then exception is thrown
	}

	@Test(expected = KeyNotInstalledException.class)
	public void shouldNotDecryptUsingNoOp() {
		// given
		String application = "unknown";

		// when
		this.controller.decrypt(application, this.profiles, this.data, TEXT_PLAIN);

		// then exception is thrown
	}

}
