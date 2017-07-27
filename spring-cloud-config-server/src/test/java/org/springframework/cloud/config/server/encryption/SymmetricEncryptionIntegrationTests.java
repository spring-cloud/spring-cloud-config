package org.springframework.cloud.config.server.encryption;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cloud.config.server.ConfigServerApplication;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.assertEquals;

public class SymmetricEncryptionIntegrationTests {

	@RunWith(SpringRunner.class)
	@SpringBootTest(classes = {ConfigServerApplication.class,SymmetricKeyEncryptor.class},
			webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
	@ActiveProfiles({"test", "native"})
	public static class SpringAppJsonConfigSymmetricEncryptionIntegrationTests {

		@BeforeClass
		public static void setupEnvironmentProperties() {
			System.setProperty("SPRING_APPLICATION_JSON", "{\"encrypt\": {\"key\": \"foobar\"}}");
		}

		@Autowired
		private TestRestTemplate testRestTemplate;

		@Test
		public void symmetricEncryptionSpringAppJson() throws Exception {
			ResponseEntity<String> entity = testRestTemplate.getForEntity("/encrypt/status", String.class);
			assertEquals(entity.getStatusCode().value(), 200);
		}
	}

	@RunWith(SpringRunner.class)
	@SpringBootTest(classes = {ConfigServerApplication.class,SymmetricKeyEncryptor.class},
		properties = "spring.cloud.bootstrap.name:symmetric-key-bootstrap",
			webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
	@ActiveProfiles({"test", "native"})
	public static class BoostrapConfigSymmetricEncryptionIntegrationTests {

		@Autowired
		private TestRestTemplate testRestTemplate;

		@Test
		public void symmetricEncryptionBootstrapConfig() throws Exception {
			ResponseEntity<String> entity = testRestTemplate.getForEntity("/encrypt/status", String.class);
			assertEquals(entity.getStatusCode().value(), 200);
		}
	}

	@RunWith(SpringRunner.class)
	@SpringBootTest(classes = {ConfigServerApplication.class,SymmetricKeyEncryptor.class},
		properties = "spring.config.name:symmetric-key-bootstrap",
			webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
	@ActiveProfiles({"test", "native"})
	public static class ApplicationConfigSymmetricEncryptionIntegrationTests {

		@Autowired
		private TestRestTemplate testRestTemplate;

		@Test
		public void symmetricEncryptionCannotBeConfiguredInApplicationContext() throws Exception {
			ResponseEntity<String> entity = testRestTemplate.getForEntity("/encrypt/status", String.class);
			assertEquals(entity.getStatusCode().value(), 404);
		}
	}
}
