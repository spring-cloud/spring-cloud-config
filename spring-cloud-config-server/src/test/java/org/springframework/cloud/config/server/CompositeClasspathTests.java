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

package org.springframework.cloud.config.server;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.cloud.config.server.composite.CompositeUtils;
import org.springframework.cloud.test.ClassPathExclusions;
import org.springframework.cloud.test.ModifiedClassPathRunner;

public class CompositeClasspathTests {

	@RunWith(ModifiedClassPathRunner.class)
	@ClassPathExclusions("spring-jdbc-*.jar")
	public static class JdbcTests {

		@Test
		public void contextLoads() {
			new WebApplicationContextRunner()
					.withUserConfiguration(ConfigServerApplication.class)
					.withPropertyValues("spring.profiles.active:test,composite",
							"spring.jmx.enabled=false",
							"spring.config.name:compositeconfigserver",
							"spring.cloud.config.server.composite[0].uri:file:./target/repos/config-repo",
							"spring.cloud.config.server.composite[0].type:git",
							"spring.cloud.config.server.composite[1].uri:file:///./target/repos/svn-config-repo",
							"spring.cloud.config.server.composite[1].type:svn")
					.run(context -> {
						CompositeUtils.getCompositeTypeList(context.getEnvironment());
					});
		}

	}

	@RunWith(ModifiedClassPathRunner.class)
	@ClassPathExclusions("httpclient-*.jar")
	public static class HttpClientTests {

		@Test
		public void contextLoads() {
			new WebApplicationContextRunner()
					.withUserConfiguration(ConfigServerApplication.class)
					.withPropertyValues("spring.profiles.active:test,composite",
							"spring.jmx.enabled=false",
							"spring.config.name:compositeconfigserver",
							"spring.cloud.config.server.composite[0].uri:file:./target/repos/config-repo",
							"spring.cloud.config.server.composite[0].type:git",
							"spring.cloud.config.server.composite[1].uri:file:///./target/repos/svn-config-repo",
							"spring.cloud.config.server.composite[1].type:svn")
					.run(context -> {
						CompositeUtils.getCompositeTypeList(context.getEnvironment());
					});
		}

	}

	@RunWith(ModifiedClassPathRunner.class)
	@ClassPathExclusions("svnkit-*.jar")
	public static class SvnTests {

		@Test
		public void contextLoads() {
			new WebApplicationContextRunner()
					.withUserConfiguration(ConfigServerApplication.class)
					.withPropertyValues("spring.profiles.active:test,composite",
							"spring.jmx.enabled=false",
							"spring.config.name:compositeconfigserver",
							"spring.cloud.config.server.composite[0].uri:file:./target/repos/config-repo",
							"spring.cloud.config.server.composite[0].type:git",
							"spring.cloud.config.server.composite[1].uri:file:./target/repos/config-repo",
							"spring.cloud.config.server.composite[1].type:native")
					.run(context -> {
						CompositeUtils.getCompositeTypeList(context.getEnvironment());
					});
		}

	}

	@RunWith(ModifiedClassPathRunner.class)
	@ClassPathExclusions("org.eclipse.jgit-*.jar")
	public static class JGitTests {

		@Test
		public void contextLoads() {
			new WebApplicationContextRunner()
					.withUserConfiguration(ConfigServerApplication.class)
					.withPropertyValues("spring.profiles.active:test,composite",
							"spring.jmx.enabled=false",
							"spring.config.name:compositeconfigserver",
							"spring.cloud.config.server.composite[0].uri:file:///./target/repos/svn-config-repo",
							"spring.cloud.config.server.composite[0].type:svn",
							"spring.cloud.config.server.composite[1].uri:file:./target/repos/config-repo",
							"spring.cloud.config.server.composite[1].type:native")
					.run(context -> {
						CompositeUtils.getCompositeTypeList(context.getEnvironment());
					});
		}

	}

}
