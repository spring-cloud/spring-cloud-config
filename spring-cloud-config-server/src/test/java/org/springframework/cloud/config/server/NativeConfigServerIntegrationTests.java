package org.springframework.cloud.config.server;

import java.io.IOException;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.embedded.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.test.ConfigServerTestUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ConfigServerApplication.class,
		properties = { "spring.config.name:configserver" },
		webEnvironment = RANDOM_PORT)
@ActiveProfiles({ "test", "native" })
public class NativeConfigServerIntegrationTests {
	
	@LocalServerPort
	private int port;
	
	@BeforeClass
	public static void init() throws IOException{
		ConfigServerTestUtils.prepareLocalRepo();
	}

	@Test
	public void contextLoads() {
		Environment environment = new TestRestTemplate().getForObject("http://localhost:" + port + "/foo/development/", Environment.class);
		assertFalse(environment.getPropertySources().isEmpty());
		assertEquals("overrides", environment.getPropertySources().get(0).getName());
		assertEquals("{spring.cloud.config.enabled=true}", environment.getPropertySources().get(0).getSource().toString());
	}

	@Test
	public void badYaml() {
		ResponseEntity<String> response = new TestRestTemplate().getForEntity("http://localhost:"
				+ port + "/bad/default/", String.class);
		assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
	}
}
