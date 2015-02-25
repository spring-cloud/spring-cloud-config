package org.springframework.cloud.configure.server;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.IntegrationTest;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.boot.test.TestRestTemplate;
import org.springframework.cloud.configure.Environment;
import org.springframework.cloud.configure.server.ConfigServerApplication;
import org.springframework.cloud.configure.server.ConfigServerTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = ConfigServerApplication.class)
@IntegrationTest({ "server.port:0", "spring.config.name:configserver",
		"spring.cloud.config.server.svn.uri:file:///./target/repos/svn-config-repo" })
@WebAppConfiguration
@ActiveProfiles("subversion")
public class SubversionConfigServerIntegrationTests {

	@Value("${local.server.port}")
	private int port;

	@BeforeClass
	public static void init() throws Exception {
		ConfigServerTestUtils.prepareLocalSvnRepo("src/test/resources/svn-config-repo",
				"target/repos/svn-config-repo");
	}

	@Test
	public void contextLoads() {
		Environment environment = new TestRestTemplate().getForObject("http://localhost:"
				+ port + "/foo/development/", Environment.class);
		assertFalse(environment.getPropertySources().isEmpty());
		assertEquals("overrides", environment.getPropertySources().get(0).getName());
		assertEquals("{spring.cloud.config.enabled=true}", environment
				.getPropertySources().get(0).getSource().toString());
	}

}
