package org.springframework.cloud.config.server;

import java.io.IOException;
import java.util.Arrays;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.context.embedded.LocalServerPort;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.test.ConfigServerTestUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ConfigServerApplication.class, properties = {
		"spring.config.name:configserver", "spring.cloud.config.server.git.uri:file:./target/repos/config-repo"},
		webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
public class VanillaConfigServerIntegrationTests {

	@LocalServerPort
	private int port;

	@BeforeClass
	public static void init() throws IOException {
		ConfigServerTestUtils.prepareLocalRepo();
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

	@Test
	public void resourseEndpointsWork() {
		String text = new TestRestTemplate().getForObject("http://localhost:"
				+ port + "/foo/development/master/bar.properties", String.class);

		String expected = "foo: bar";
		assertEquals("invalid content", expected, text);

		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(Arrays.asList(MediaType.APPLICATION_OCTET_STREAM));
		ResponseEntity<byte[]> response = new TestRestTemplate().exchange("http://localhost:"
				+ port + "/foo/development/raw/bar.properties", HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
		//FIXME: this is calling the text endpoint, not the binary one
		// assertTrue("invalid content type", response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_OCTET_STREAM));
		assertEquals(expected.length(), response.getBody().length);
	}

}
