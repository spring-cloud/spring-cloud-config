/*
 * Copyright 2016-2019 the original author or authors.
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

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cloud.config.server.ConfigServerApplication;
import org.springframework.cloud.config.server.test.ConfigServerTestUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;

public class EncryptionIntegrationTests {

	@RunWith(SpringRunner.class)
	@SpringBootTest(classes = { ConfigServerApplication.class },
			webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
			properties = "encrypt.key=foobar")
	@ActiveProfiles({ "test", "native" })
	@DirtiesContext
	public static class ConfigSymmetricEncryptionIntegrationTests {

		@Autowired
		private TestRestTemplate testRestTemplate;

		@Test
		public void symmetricEncryptionEnabled() throws Exception {
			ResponseEntity<String> entity = this.testRestTemplate
					.getForEntity("/encrypt/status", String.class);
			assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
		}

	}

	@RunWith(SpringRunner.class)
	@SpringBootTest(classes = { ConfigServerApplication.class },
			properties = "spring.cloud.bootstrap.name:symmetric-key-bootstrap",
			webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
	@ActiveProfiles({ "test", "native" })
	@DirtiesContext
	public static class BootstrapConfigSymmetricEncryptionIntegrationTests {

		@Autowired
		private TestRestTemplate testRestTemplate;

		@Test
		public void symmetricEncryptionBootstrapConfig() throws Exception {
			ResponseEntity<String> entity = this.testRestTemplate
					.getForEntity("/encrypt/status", String.class);
			assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
		}

	}

	@RunWith(SpringRunner.class)
	@SpringBootTest(classes = { ConfigServerApplication.class },
			properties = "spring.cloud.bootstrap.name:keystore-bootstrap",
			webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
	@ActiveProfiles({ "test", "native" })
	@DirtiesContext
	public static class KeystoreConfigurationIntegrationTests {

		@Autowired
		private TestRestTemplate testRestTemplate;

		@Test
		public void keystoreBootstrapConfig() throws Exception {
			ResponseEntity<String> entity = this.testRestTemplate
					.getForEntity("/encrypt/status", String.class);
			assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
		}

	}

	@RunWith(SpringRunner.class)
	@SpringBootTest(classes = { ConfigServerApplication.class },
			properties = { "spring.cloud.bootstrap.name:keystore-bootstrap",
					"spring.cloud.config.server.encrypt.enabled=false",
					"encrypt.keyStore.alias=myencryptionkey" },
			webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
	@ActiveProfiles({ "test", "git" })
	@DirtiesContext
	public static class KeystoreConfigurationEncryptionOnlyIntegrationTests {

		@Autowired
		private TestRestTemplate testRestTemplate;

		@BeforeClass
		public static void setupTest() throws Exception {
			ConfigServerTestUtils.prepareLocalRepo("./", "target/repos", "encrypt-repo",
					"target/config");
		}

		@Test
		public void shouldOnlySupportEncryption() {
			ResponseEntity<String> entity = this.testRestTemplate
					.getForEntity("/keystore-bootstrap/encrypt", String.class);
			assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
			assertThat(entity.getBody()).contains(
					"{cipher}{key:mytestkey}AQCohs2V6P8/UiG6a4TF/CZTCBdt5Q7wvNvcyf6vs2ByK2ZYSM77Nu0sOAduxUpMbVwJ/syecmkIXR+hU3EfT2uqPieA7/v5n33ppqIQ9JAt5JggdYIGe+wX25zU3DTXOOJdAAMzNX+zjOVyCh0QtmJf/kFslg6NqQq0E+kSg3zBi3AnkKj5BLnLIxkjxzKA4mnDXpSm7ekLZZP2iQSYSW/82AC7UOLLzTqwInMI3tJLW1e9Ne+LDsjmSxA+nkK9zhidtXPwb/SPaNF74cJCEf9mgzzKYwJlwqChLzJt8UQ1jHwRc8B6FufmizUHSp27nxdtVB4HMqh3nNsMCy137Ces58T09ZS/y/cYNRxcFbp78MHFHUqAgbC0B/p5t6h4XbQ=");

			HttpEntity<String> encryptionRequest = new HttpEntity<>("valueToBeEncrypted");
			entity = this.testRestTemplate.postForEntity("/encrypt", encryptionRequest,
					String.class);
			assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);

			HttpHeaders decryptionRequestHeaders = new HttpHeaders();
			decryptionRequestHeaders.setContentType(MediaType.TEXT_PLAIN);
			HttpEntity<String> decryptionRequest = new HttpEntity<>(entity.getBody(),
					decryptionRequestHeaders);
			entity = this.testRestTemplate.postForEntity("/decrypt", decryptionRequest,
					String.class);
			assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		}

	}

}
