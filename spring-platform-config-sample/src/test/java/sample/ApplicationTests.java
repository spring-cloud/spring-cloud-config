
package sample;

import static org.junit.Assert.assertEquals;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.embedded.EmbeddedWebApplicationContext;
import org.springframework.boot.test.IntegrationTest;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.boot.test.TestRestTemplate;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = Application.class)
@IntegrationTest("server.port:0")
@WebAppConfiguration
public class ApplicationTests {

	private static int configPort = 0;

	@Value("${local.server.port}")
	private int port;

	@BeforeClass
	public static void startConfigServer() {
		ConfigurableApplicationContext context = SpringApplication.run(
				org.springframework.platform.config.server.Application.class,
				"--server.port=" + configPort, "--spring.config.name=server");
		configPort = ((EmbeddedWebApplicationContext) context).getEmbeddedServletContainer().getPort();
		System.setProperty("config.port", "" + configPort);
	}

	@Test
	public void contextLoads() {
		String foo = new TestRestTemplate().getForObject("http://localhost:" + port
				+ "/env/foo", String.class);
		assertEquals("bar", foo);
	}

	public static void main(String[] args) {
		configPort = 8888;
		startConfigServer();
		SpringApplication.run(Application.class, args);
	}

}
