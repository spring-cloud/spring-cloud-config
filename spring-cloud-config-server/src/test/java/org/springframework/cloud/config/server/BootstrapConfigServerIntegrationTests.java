package org.springframework.cloud.config.server;

import java.io.IOException;
import java.util.Map;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.test.ConfigServerTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ConfigServerApplication.class, properties = "spring.cloud.bootstrap.name:enable-bootstrap",
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
		assertThat(environment.getPropertySources().isEmpty()).isFalse();
		Object value = environment.getPropertySources().get(0).getSource().get("info.foo");
		assertThat(value).isNotNull().isInstanceOf(Map.class);
		Map map = (Map) value;
		assertThat(map).containsEntry("value", "bar");
	}

	@Test
	public void environmentBootstraps() throws Exception {
		assertEquals("bar", foo);
		assertEquals("foo", config);
	}

}
