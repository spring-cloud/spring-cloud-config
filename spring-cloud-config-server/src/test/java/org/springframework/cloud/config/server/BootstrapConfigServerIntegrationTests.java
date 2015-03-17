package org.springframework.cloud.config.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.io.IOException;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.IntegrationTest;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.boot.test.TestRestTemplate;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.ConfigServerApplication;
import org.springframework.cloud.config.server.ConfigServerTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = ConfigServerApplication.class)
@IntegrationTest({ "server.port:0", "spring.cloud.bootstrap.name:enable-bootstrap" })
@WebAppConfiguration
@ActiveProfiles("test")
public class BootstrapConfigServerIntegrationTests {

	@Value("${local.server.port}")
	private int port;

	@Value("${info.foo}")
	private String foo;

	@BeforeClass
	public static void init() throws IOException {
		ConfigServerTestUtils.prepareLocalRepo();
	}

	@Test
	public void contextLoads() {
		Environment environment = new TestRestTemplate().getForObject("http://localhost:"
				+ port + "/foo/development/", Environment.class);
		assertFalse(environment.getPropertySources().isEmpty());
		assertEquals("bar",
				environment.getPropertySources().get(0).getSource().get("info.foo"));
	}

	@Test
	public void environmentBootstraps() throws Exception {
		assertEquals("bar", foo);
	}

}
