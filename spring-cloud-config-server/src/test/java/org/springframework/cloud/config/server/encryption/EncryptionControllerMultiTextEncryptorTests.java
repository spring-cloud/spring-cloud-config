/*
 * Copyright 2018-2019 the original author or authors.
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

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import org.springframework.security.crypto.encrypt.Encryptors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.TEXT_PLAIN;

/**
 * @author Bartosz Wojtkiewicz
 *
 */

public class EncryptionControllerMultiTextEncryptorTests {

	EncryptionController controller = new EncryptionController(new SingleTextEncryptorLocator(Encryptors.noOpText()));

	String application = "application";

	String profiles = "profile1,profile2";

	String data = "foo";

	@Test
	public void shouldEncryptUsingApplicationAndProfiles() {

		this.controller = new EncryptionController(
				new SingleTextEncryptorLocator(Encryptors.text("application", "11")));

		// when
		String encrypted = this.controller.encrypt(this.application, this.profiles, this.data, TEXT_PLAIN);

		// then
		assertThat(this.controller.decrypt(this.application, this.profiles, encrypted, TEXT_PLAIN))
				.isEqualTo(this.data);
	}

	@Test
	public void shouldNotEncryptUsingNoOp() {
		// given
		Assertions.assertThatThrownBy(() -> {
			String application = "unknown";
			this.controller.encrypt(application, this.profiles, this.data, TEXT_PLAIN);
		}).isInstanceOf(EncryptionTooWeakException.class);
	}

	@Test
	public void shouldNotDecryptUsingNoOp() {

		Assertions.assertThatThrownBy(() -> {
			String application = "unknown";
			this.controller.decrypt(application, this.profiles, this.data, TEXT_PLAIN);
		}).isInstanceOf(EncryptionTooWeakException.class);
	}

}
