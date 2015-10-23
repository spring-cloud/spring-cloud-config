package sample;

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.embedded.EmbeddedWebApplicationContext;
import org.springframework.boot.test.IntegrationTest;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.boot.test.TestRestTemplate;
import org.springframework.cloud.config.server.test.ConfigServerTestUtils;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = Application.class)
// Normally spring.cloud.config.enabled:true is the default but since we have the config
// server on the classpath we need to set it explicitly
@IntegrationTest({ "server.port:0", "spring.cloud.config.enabled:true" })
@WebAppConfiguration
public class ApplicationTests {

	private static int configPort = 0;

	@Value("${local.server.port}")
	private int port;

	private static ConfigurableApplicationContext server;

	@BeforeClass
	public static void startConfigServer() throws IOException {
		String baseDir = ConfigServerTestUtils
				.getBaseDirectory("spring-cloud-config-sample");
		String repo = ConfigServerTestUtils.prepareLocalRepo(baseDir, "target/repos",
				"config-repo", "target/config");
		server = SpringApplication.run(
				org.springframework.cloud.config.server.ConfigServerApplication.class,
				"--server.port=" + configPort, "--spring.config.name=server",
				"--spring.cloud.config.server.git.uri=" + repo);
		configPort = ((EmbeddedWebApplicationContext) server)
				.getEmbeddedServletContainer().getPort();
		System.setProperty("config.port", "" + configPort);
	}

	@AfterClass
	public static void close() {
		System.clearProperty("config.port");
		if (server != null) {
			server.close();
		}
	}

	@Test
	public void contextLoads() {
		String foo = new TestRestTemplate()
				.getForObject("http://localhost:" + port + "/env/info.foo", String.class);
		assertEquals("{\"info.foo\":\"bar\"}", foo);
	}

	public static void main(String[] args) throws IOException {
		configPort = 8888;
		startConfigServer();
		SpringApplication.run(Application.class, args);
	}

}
