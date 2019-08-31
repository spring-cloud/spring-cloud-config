/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.config.server.environment;

import org.eclipse.jgit.junit.MockSystemReader;
import org.eclipse.jgit.util.SystemReader;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.test.ConfigServerTestUtils;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Spencer Gibb
 * @author Dave Syer
 *
 */
public class MultipleJGitEnvironmentLabelPlaceholderRepositoryTests {

	private StandardEnvironment environment = new StandardEnvironment();

	private MultipleJGitEnvironmentRepository repository = new MultipleJGitEnvironmentRepository(
			this.environment, new MultipleJGitEnvironmentProperties());

	private String defaultUri;

	@BeforeClass
	public static void initClass() {
		// mock Git configuration to make tests independent of local Git configuration
		SystemReader.setInstance(new MockSystemReader());
	}

	@Before
	public void init() throws Exception {
		this.defaultUri = ConfigServerTestUtils
				.prepareLocalRepo("master-labeltest-config-repo");
		this.repository.setUri(this.defaultUri.replace("master-", "{label}-"));
	}

	@Test
	public void defaultRepo() {
		Environment environment = this.repository.findOne("bar", "staging", "master");
		assertThat(environment.getPropertySources().size()).isEqualTo(1);
		assertThat(environment.getPropertySources().get(0).getName())
				.isEqualTo(this.defaultUri + "application.yml");
		assertVersion(environment);
	}

	@Test
	public void missingRepo() {
		Environment environment = this.repository.findOne("missing-config-repo",
				"staging", "master");
		assertThat(environment.getPropertySources().size())
				.as("Wrong property sources: " + environment).isEqualTo(1);
		assertThat(environment.getPropertySources().get(0).getName())
				.isEqualTo(this.defaultUri + "application.yml");
		assertVersion(environment);
	}

	@Test
	public void defaultLabelRepo() {
		Environment environment = this.repository.findOne("bar", "staging", null);
		assertThat(environment.getPropertySources().size()).isEqualTo(1);
		assertThat(environment.getPropertySources().get(0).getName())
				.isEqualTo(this.defaultUri + "application.yml");
		assertVersion(environment);
	}

	private void assertVersion(Environment environment) {
		String version = environment.getVersion();
		assertThat(version).as("version was null").isNotNull();
		assertThat(version.length() >= 40 && version.length() <= 64)
				.as("version length was wrong").isTrue();
	}

}
