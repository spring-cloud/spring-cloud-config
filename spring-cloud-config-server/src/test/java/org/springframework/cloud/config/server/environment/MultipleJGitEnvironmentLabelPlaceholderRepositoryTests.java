/*
 * Copyright 2013-2016 the original author or authors.
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
 *
 */
package org.springframework.cloud.config.server.environment;

import org.junit.Before;
import org.junit.Test;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.test.ConfigServerTestUtils;
import org.springframework.core.env.StandardEnvironment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * @author Spencer Gibb
 * @author Dave Syer
 *
 */
public class MultipleJGitEnvironmentLabelPlaceholderRepositoryTests {

	private StandardEnvironment environment = new StandardEnvironment();
	private MultipleJGitEnvironmentRepository repository = new MultipleJGitEnvironmentRepository(
			this.environment);
	private String defaultUri;

	@Before
	public void init() throws Exception {
		this.defaultUri = ConfigServerTestUtils.prepareLocalRepo("master-labeltest-config-repo");
		this.repository.setUri(defaultUri.replace("master-", "{label}-"));
	}
	@Test
	public void defaultRepo() {
		Environment environment = this.repository.findOne("bar", "staging", "master");
		assertEquals(1, environment.getPropertySources().size());
		assertEquals(this.defaultUri + "application.yml",
				environment.getPropertySources().get(0).getName());
		assertVersion(environment);
	}

	@Test
	public void missingRepo() {
		Environment environment = this.repository.findOne("missing-config-repo",
				"staging", "master");
		assertEquals("Wrong property sources: " + environment, 1,
				environment.getPropertySources().size());
		assertEquals(this.defaultUri + "application.yml",
				environment.getPropertySources().get(0).getName());
		assertVersion(environment);
	}

	@Test
	public void defaultLabelRepo() {
		Environment environment = this.repository.findOne("bar", "staging", null);
		assertEquals(1, environment.getPropertySources().size());
		assertEquals(this.defaultUri + "application.yml",
				environment.getPropertySources().get(0).getName());
		assertVersion(environment);
	}

	private void assertVersion(Environment environment) {
		String version = environment.getVersion();
		assertNotNull("version was null", version);
		assertTrue("version length was wrong",
				version.length() >= 40 && version.length() <= 64);
	}

}
