package org.springframework.cloud.config.server;

import java.io.IOException;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.test.ConfigServerTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ConfigServerApplication.class, properties = {"spring.cloud.bootstrap.name:enable-bootstrap",
		"encrypt.rsa.algorithm=DEFAULT", "encrypt.rsa.strong=false"},
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"test", "encrypt"})
public class BootstrapConfigServerIntegrationTests {

	@LocalServerPort
	private int port;

	@Value("${info.foo}")
	private String foo;

	@Value("${config.foo}")
	private String config;

	@BeforeClass
	public static void init() throws IOException {
		ConfigServerTestUtils.prepareLocalRepo("encrypt-repo");
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
		assertEquals("foo", config);
	}

}
