package org.springframework.cloud.config.server;

import static org.junit.Assert.assertFalse;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.IntegrationTest;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.boot.test.TestRestTemplate;
import org.springframework.cloud.config.Environment;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = Application.class)
@IntegrationTest("server.port:0")
@WebAppConfiguration
public class ApplicationTests {
	
	@Value("${local.server.port}")
	private int port;

	@Test
	public void contextLoads() {
		Environment environment = new TestRestTemplate().getForObject("http://localhost:" + port + "/foo/development/", Environment.class);
		assertFalse(environment.getPropertySources().isEmpty());
	}

}
