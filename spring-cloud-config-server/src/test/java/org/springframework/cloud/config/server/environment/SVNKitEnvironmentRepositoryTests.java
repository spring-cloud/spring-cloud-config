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

import java.io.File;
import java.io.IOException;

import io.micrometer.observation.ObservationRegistry;
import org.assertj.core.api.Assertions;
import org.eclipse.jgit.util.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.EnableConfigServer;
import org.springframework.cloud.config.server.test.ConfigServerTestUtils;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Michael Prankl
 * @author Roy Clarkson
 */
public class SVNKitEnvironmentRepositoryTests {

	private static final String REPOSITORY_NAME = "svn-config-repo";

	private StandardEnvironment environment = new StandardEnvironment();

	private SvnKitEnvironmentRepository repository = new SvnKitEnvironmentRepository(this.environment,
			new SvnKitEnvironmentProperties(), ObservationRegistry.NOOP);

	private File basedir = new File("target/config");

	@BeforeEach
	public void init() throws Exception {
		String uri = ConfigServerTestUtils.prepareLocalSvnRepo("src/test/resources/" + REPOSITORY_NAME,
				"target/repos/" + REPOSITORY_NAME);
		this.repository.setUri(uri);
		if (this.basedir.exists()) {
			FileUtils.delete(this.basedir, FileUtils.RECURSIVE | FileUtils.RETRY);
		}
	}

	@Test
	public void vanilla() {
		Environment environment = this.findOne();
		assertThat(environment.getPropertySources()).hasSize(2);
		assertThat(environment.getPropertySources().get(0).getName()).contains("bar.properties");
		assertThat(environment.getPropertySources().get(1).getName()).contains("application.yml");
	}

	@Test
	public void basedir() {
		this.repository.setBasedir(this.basedir);
		Environment environment = this.findOne();
		assertThat(environment.getPropertySources()).hasSize(2);
		assertThat(environment.getPropertySources().get(0).getName()).contains("bar.properties");
		assertThat(environment.getPropertySources().get(1).getName()).contains("application.yml");
	}

	@Test
	public void basedirWithSpace() throws Exception {
		File basedirWithSpace = new File("target/config with space");
		if (basedirWithSpace.exists()) {
			FileUtils.delete(basedirWithSpace, FileUtils.RECURSIVE | FileUtils.RETRY);
		}

		this.repository.setBasedir(basedirWithSpace);

		Environment environment = this.findOne();
		assertThat(environment.getPropertySources()).hasSize(2);
		assertThat(environment.getPropertySources().get(0).getName()).contains("bar.properties");
		assertThat(environment.getPropertySources().get(1).getName()).contains("application.yml");
	}

	@Test
	public void branch() {
		Environment environment = this.repository.findOne("bar", "staging", "branches/demobranch");
		assertThat(environment.getPropertySources()).hasSize(1);
		assertThat(environment.getPropertySources().get(0).getName()).contains("bar.properties");
	}

	@Test
	public void branch_no_folder() {
		Environment environment = this.repository.findOne("bar", "staging", "demobranch", false);
		assertThat(environment.getPropertySources()).hasSize(1);
		assertThat(environment.getPropertySources().get(0).getName()).contains("bar.properties");
	}

	@Test
	public void vanilla_with_update() {
		this.findOne();
		Environment environment = this.findOne();
		assertThat(environment.getPropertySources()).hasSize(2);
		assertThat(environment.getPropertySources().get(0).getName()).contains("bar.properties");
		assertThat(environment.getPropertySources().get(1).getName()).contains("application.yml");
	}

	@Test
	public void testMultipleLabels() {
		Environment environment = this.repository.findOne("bar", "staging", "branches/demobranch,trunk");
		assertThat(environment.getPropertySources()).hasSize(3);
		assertThat(environment.getPropertySources().get(0).getName()).contains("bar.properties");
		assertThat(environment.getPropertySources().get(1).getName()).contains("application.yml");
		assertThat(environment.getPropertySources().get(2).getName()).contains("branches/demobranch/bar.properties");
	}

	@Test
	public void invalidLabel() {
		Assertions.assertThatThrownBy(() -> {
			Environment environment = this.repository.findOne("bar", "staging", "unknownlabel");
			assertThat(environment.getPropertySources()).isEmpty();
		}).isInstanceOf(NoSuchLabelException.class);
	}

	@Test
	public void vanilla_with_update_after_repo_delete() throws IOException {
		this.vanilla_with_update();
		ConfigServerTestUtils.deleteLocalRepo(REPOSITORY_NAME);
		assertThat(new File(this.basedir, REPOSITORY_NAME)).doesNotExist();
		this.vanilla();
	}

	private Environment findOne() {
		return this.repository.findOne("bar", "staging", "trunk");
	}

	@EnableAutoConfiguration
	@Configuration(proxyBeanMethods = false)
	@EnableConfigServer
	protected static class TestApplication {

		public static void main(String[] args) throws Exception {
			File basedir = new File("target/config");
			String uri = ConfigServerTestUtils.prepareLocalSvnRepo("src/test/resources/" + REPOSITORY_NAME,
					"target/repos/" + REPOSITORY_NAME);
			if (basedir.exists()) {
				FileUtils.delete(basedir, FileUtils.RECURSIVE | FileUtils.RETRY);
			}
			new SpringApplicationBuilder(TestApplication.class).profiles("subversion")
				.properties("server.port=8888", "spring.cloud.config.server.svn.uri:" + uri)
				.run(args);
		}

	}

}
