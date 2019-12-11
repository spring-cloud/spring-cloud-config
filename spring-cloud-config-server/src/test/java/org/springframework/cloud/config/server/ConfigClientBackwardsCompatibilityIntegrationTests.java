/*
 * Copyright 2018-2019 the original author or authors.
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
import java.util.List;
import java.util.Map;

import org.eclipse.jgit.junit.MockSystemReader;
import org.eclipse.jgit.util.SystemReader;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.cloud.config.server.test.ConfigServerTestUtils;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.cloud.config.server.test.ConfigServerTestUtils.getV2AcceptEntity;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ConfigServerApplication.class,
		properties = { "spring.config.name:configserver" }, webEnvironment = RANDOM_PORT)
@ActiveProfiles({ "test", "native" })
public class ConfigClientBackwardsCompatibilityIntegrationTests {

	@LocalServerPort
	private int port;

	@Autowired
	private ApplicationContext context;

	@BeforeClass
	public static void init() throws IOException {
		// mock Git configuration to make tests independent of local Git configuration
		SystemReader.setInstance(new MockSystemReader());

		ConfigServerTestUtils.prepareLocalRepo();
	}

	@Test
	public void testBackwardsCompatibleFormat() {
		Map environment = new TestRestTemplate().getForObject(
				"http://localhost:" + this.port + "/foo/development/", Map.class);
		Object value = getPropertySourceValue(environment);
		assertThat(value).isInstanceOf(String.class).isEqualTo("true");
	}

	@Test
	public void testBackwardsCompatibleFormatWithLabel() {
		Map environment = new TestRestTemplate().getForObject(
				"http://localhost:" + this.port + "/foo/development/master", Map.class);
		Object value = getPropertySourceValue(environment);
		assertThat(value).isInstanceOf(String.class).isEqualTo("true");
	}

	@Test
	public void testNewFormat() {
		ResponseEntity<Map> response = new TestRestTemplate().exchange(
				"http://localhost:" + this.port + "/foo/development/", HttpMethod.GET,
				getV2AcceptEntity(), Map.class);
		Object value = getPropertySourceValue(response.getBody());
		assertThat(value).isInstanceOf(Map.class);
		Map valueMap = Map.class.cast(value);
		assertThat(valueMap).containsEntry("value", "true");
	}

	@Test
	public void testNewFormatWithLabel() {
		ResponseEntity<Map> response = new TestRestTemplate().exchange(
				"http://localhost:" + this.port + "/foo/development/master",
				HttpMethod.GET, getV2AcceptEntity(), Map.class);
		Object value = getPropertySourceValue(response.getBody());
		assertThat(value).isInstanceOf(Map.class);
		Map valueMap = Map.class.cast(value);
		assertThat(valueMap).containsEntry("value", "true");
	}

	private Object getPropertySourceValue(Map environment) {
		assertThat(environment).isNotNull();
		assertThat(environment.containsKey("propertySources"));
		List propertySources = (List) environment.get("propertySources");
		assertThat(propertySources).hasSizeGreaterThan(1);
		Map source = (Map) propertySources.get(0);
		assertThat(source).containsKeys("source");
		Map sourceValue = (Map) source.get("source");
		assertThat(sourceValue).containsKeys("spring.cloud.config.enabled");
		return sourceValue.get("spring.cloud.config.enabled");
	}

}
