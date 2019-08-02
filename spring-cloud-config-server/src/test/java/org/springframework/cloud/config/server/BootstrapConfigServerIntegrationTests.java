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
import java.util.Map;

import org.eclipse.jgit.junit.MockSystemReader;
import org.eclipse.jgit.util.SystemReader;
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

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ConfigServerApplication.class,
		properties = { "spring.cloud.bootstrap.name:enable-bootstrap",
				"encrypt.rsa.algorithm=DEFAULT", "encrypt.rsa.strong=false" },
		webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({ "test", "encrypt" })
public class BootstrapConfigServerIntegrationTests {

	@LocalServerPort
	private int port;

	@Value("${info.foo}")
	private String foo;

	@Value("${config.foo}")
	private String config;

	@BeforeClass
	public static void init() throws IOException {
		// mock Git configuration to make tests independent of local Git configuration
		SystemReader.setInstance(new MockSystemReader());

		ConfigServerTestUtils.prepareLocalRepo("encrypt-repo");
	}

	@Test
	public void contextLoads() {
		Environment environment = new TestRestTemplate().getForObject(
				"http://localhost:" + this.port + "/foo/development/", Environment.class);
		assertThat(environment.getPropertySources()).hasSize(2);
		Object value = environment.getPropertySources().get(0).getSource().get("bar");
		assertThat(value).isNotNull().isInstanceOf(Map.class);
		Map map = (Map) value;
		assertThat(map).containsEntry("value", "foo");
		value = environment.getPropertySources().get(1).getSource().get("info.foo");
		assertThat(value).isNotNull().isInstanceOf(Map.class);
		map = (Map) value;
		assertThat(map).containsEntry("value", "bar");
	}

	@Test
	public void environmentBootstraps() throws Exception {
		assertThat(this.foo).isEqualTo("bar");
		assertThat(this.config).isEqualTo("foo");
	}

}
