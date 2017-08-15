/*
 * Copyright 2016-2017 the original author or authors.
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

package org.springframework.cloud.config.server.encryption;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cloud.config.server.ConfigServerApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

public class EncryptionIntegrationTests {

	@RunWith(SpringRunner.class)
	@SpringBootTest(classes = {
			ConfigServerApplication.class }, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
					properties = "encrypt.key=foobar")
	@ActiveProfiles({ "test", "native" })
	@DirtiesContext
	public static class ConfigSymmetricEncryptionIntegrationTests {

		@Autowired
		private TestRestTemplate testRestTemplate;

		@Test
		public void symmetricEncryptionEnabled() throws Exception {
			ResponseEntity<String> entity = testRestTemplate
					.getForEntity("/encrypt/status", String.class);
			assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
		}
	}

	@RunWith(SpringRunner.class)
	@SpringBootTest(classes = { ConfigServerApplication.class}, properties = "spring.cloud.bootstrap.name:symmetric-key-bootstrap", webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
	@ActiveProfiles({ "test", "native" })
	@DirtiesContext
	public static class BootstrapConfigSymmetricEncryptionIntegrationTests {

		@Autowired
		private TestRestTemplate testRestTemplate;

		@Test
		public void symmetricEncryptionBootstrapConfig() throws Exception {
			ResponseEntity<String> entity = testRestTemplate
					.getForEntity("/encrypt/status", String.class);
			assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
		}
	}

	@RunWith(SpringRunner.class)
	@SpringBootTest(classes = { ConfigServerApplication.class},
			properties = "spring.cloud.bootstrap.name:keystore-bootstrap",
			webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
	@ActiveProfiles({ "test", "native" })
	@DirtiesContext
	public static class KeystoreConfigurationIntegrationTests {

		@Autowired
		private TestRestTemplate testRestTemplate;

		@Test
		public void keystoreBootstrapConfig() throws Exception {
			ResponseEntity<String> entity = testRestTemplate
					.getForEntity("/encrypt/status", String.class);
			assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
		}
	}

}