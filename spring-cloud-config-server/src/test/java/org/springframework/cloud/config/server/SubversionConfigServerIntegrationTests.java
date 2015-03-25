/*
 * Copyright 2013-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.cloud.config.server;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.IntegrationTest;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.boot.test.TestRestTemplate;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * @author Michael Prankl
 * @author Dave Syer
 * @author Roy Clarkson
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = ConfigServerApplication.class)
@IntegrationTest({ "server.port:0", "spring.config.name:configserver",
		"spring.cloud.config.server.svn.uri:file:///./target/repos/svn-config-repo" })
@WebAppConfiguration
@ActiveProfiles("subversion")
public class SubversionConfigServerIntegrationTests {

	@Value("${local.server.port}")
	private int port;

	@Autowired
	private ApplicationContext context;

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

	@Test
	public void defaultLabel() throws Exception {
		EnvironmentRepository repository = context.getBean(EnvironmentRepository.class);
		assertEquals("trunk", repository.getDefaultLabel());
	}

}
