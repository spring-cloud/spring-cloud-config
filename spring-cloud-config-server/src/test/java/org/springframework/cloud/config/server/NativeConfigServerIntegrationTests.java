package org.springframework.cloud.config.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.IOException;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.IntegrationTest;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.boot.test.TestRestTemplate;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.ConfigServerApplication;
import org.springframework.cloud.config.server.ConfigServerTestUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = ConfigServerApplication.class)
@IntegrationTest({ "server.port:0", "spring.config.name:configserver" })
@WebAppConfiguration
@ActiveProfiles({ "test", "native" })
public class NativeConfigServerIntegrationTests {
	
	@Value("${local.server.port}")
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

	public static void main(String[] args) {
		new SpringApplicationBuilder(ConfigServerApplication.class).profiles("native").properties(
				"spring.config.name=configserver").run(args);
	}

}
