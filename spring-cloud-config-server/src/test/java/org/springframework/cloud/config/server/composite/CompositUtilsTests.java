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

package org.springframework.cloud.config.server.composite;

import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.cloud.config.server.ConfigServerApplication;

import static org.assertj.core.api.Assertions.assertThat;

public class CompositUtilsTests {

	@Rule
	public ExpectedException thrown = ExpectedException.none();

	@Test
	public void getCompositeTypeListWorks() {
		new WebApplicationContextRunner()
				.withUserConfiguration(ConfigServerApplication.class)
				.withPropertyValues("spring.profiles.active:test,composite",
						"spring.config.name:compositeconfigserver",
						"spring.jmx.enabled=false",
						"spring.cloud.config.server.composite[0].uri:file:./target/repos/config-repo",
						"spring.cloud.config.server.composite[0].type:git",
						"spring.cloud.config.server.composite[1].uri:file:///./target/repos/svn-config-repo",
						"spring.cloud.config.server.composite[1].type:svn")
				.run(context -> {
					List<String> types = CompositeUtils
							.getCompositeTypeList(context.getEnvironment());
					assertThat(types).containsExactly("git", "svn");
				});
	}

	@Test
	public void getCompositeTypeListFails() {
		this.thrown.expect(IllegalStateException.class);

		new WebApplicationContextRunner()
				.withUserConfiguration(ConfigServerApplication.class)
				.withPropertyValues("spring.profiles.active:test,composite",
						"spring.config.name:compositeconfigserver",
						"spring.jmx.enabled=false",
						"spring.cloud.config.server.composite[0].uri:file:./target/repos/config-repo",
						"spring.cloud.config.server.composite[0].type:git",
						"spring.cloud.config.server.composite[2].uri:file:///./target/repos/svn-config-repo",
						"spring.cloud.config.server.composite[2].type:svn")
				.run(context -> {
					CompositeUtils.getCompositeTypeList(context.getEnvironment());
				});
	}

}
