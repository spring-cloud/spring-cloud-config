/*
 * Copyright 2013-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.config.server;

import java.io.IOException;
import java.util.Arrays;

import org.eclipse.jgit.junit.MockSystemReader;
import org.eclipse.jgit.util.SystemReader;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.test.ConfigServerTestUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ConfigServerApplication.class, properties = {
		"spring.config.name:configserver",
		"spring.cloud.config.server.git.uri:file:./target/repos/config-repo" }, webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
public class VanillaConfigServerIntegrationTests {

	@LocalServerPort
	private int port;

	@BeforeClass
	public static void init() throws IOException {
		// mock Git configuration to make tests independent of local Git configuration
		SystemReader.setInstance(new MockSystemReader());

		ConfigServerTestUtils.prepareLocalRepo();
	}

	@Test
	public void contextLoads() {
		Environment environment = new TestRestTemplate().getForObject(
				"http://localhost:" + this.port + "/foo/development/", Environment.class);
		assertThat(environment.getPropertySources().isEmpty()).isFalse();
		assertThat(environment.getPropertySources().get(0).getName())
				.isEqualTo("overrides");
		assertThat(environment.getPropertySources().get(0).getSource().toString())
				.isEqualTo("{spring.cloud.config.enabled=true}");
	}

	@Test
	public void resourseEndpointsWork() {
		String text = new TestRestTemplate().getForObject("http://localhost:" + this.port
				+ "/foo/development/master/bar.properties", String.class);

		String expected = "foo: bar";
		assertThat(text).as("invalid content").isEqualTo(expected);

		HttpHeaders headers = new HttpHeaders();
		headers.setAccept(Arrays.asList(MediaType.APPLICATION_OCTET_STREAM));
		ResponseEntity<byte[]> response = new TestRestTemplate().exchange(
				"http://localhost:" + this.port + "/foo/development/raw/bar.properties",
				HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
		// FIXME: this is calling the text endpoint, not the binary one
		// assertTrue("invalid content type",
		// response.getHeaders().getContentType().isCompatibleWith(MediaType.APPLICATION_OCTET_STREAM));
		assertThat(response.getBody().length).isEqualTo(expected.length());
	}

}
