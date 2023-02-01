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

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.cloud.config.server.composite.CompositeUtils;
import org.springframework.cloud.config.server.test.TestConfigServerApplication;
import org.springframework.cloud.test.ClassPathExclusions;

import static org.assertj.core.api.Assertions.assertThat;

public class CompositeClasspathTests {

	@ClassPathExclusions({ "spring-jdbc-*.jar", "spring-data-redis-*.jar" })
	public static class JdbcTests {

		@Test
		public void contextLoads() {
			new WebApplicationContextRunner().withUserConfiguration(TestConfigServerApplication.class)
					.withPropertyValues("spring.profiles.active:test,composite", "spring.jmx.enabled=false",
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

	@ClassPathExclusions({ "spring-jdbc-*.jar", "spring-data-redis-*.jar", "spring-boot-actuator-*.jar" })
	public static class NoActuatorTests {

		@Test
		public void contextLoads() {
			new WebApplicationContextRunner().withUserConfiguration(TestConfigServerApplication.class)
					.withPropertyValues("spring.profiles.active:test,composite", "spring.jmx.enabled=false",
							"spring.config.name:compositeconfigserver",
							"spring.cloud.config.server.composite[0].uri:file:./target/repos/config-repo",
							"spring.cloud.config.server.composite[0].type:git")
					.run(context -> {
						CompositeUtils.getCompositeTypeList(context.getEnvironment());
						assertThat(context).doesNotHaveBean("configServerHealthIndicator");
					});
		}

	}

	@ClassPathExclusions("httpclient-*.jar")
	public static class HttpClientTests {

		@Test
		public void contextLoads() {
			new WebApplicationContextRunner().withUserConfiguration(TestConfigServerApplication.class)
					.withPropertyValues("spring.profiles.active:test,composite", "spring.jmx.enabled=false",
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

	@ClassPathExclusions("svnkit-*.jar")
	public static class SvnTests {

		@Test
		public void contextLoads() {
			new WebApplicationContextRunner().withUserConfiguration(TestConfigServerApplication.class)
					.withPropertyValues("spring.profiles.active:test,composite", "spring.jmx.enabled=false",
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

	@ClassPathExclusions("org.eclipse.jgit-*.jar")
	public static class JGitTests {

		@Test
		public void contextLoads() {
			new WebApplicationContextRunner().withUserConfiguration(TestConfigServerApplication.class)
					.withPropertyValues("spring.profiles.active:test,composite", "spring.jmx.enabled=false",
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

	@ClassPathExclusions("google-auth-library-oauth2-http-*.jar")
	public static class GoogleAuthTests {

		@Test
		public void contextLoads() {
			new WebApplicationContextRunner().withUserConfiguration(TestConfigServerApplication.class)
					.withPropertyValues("spring.profiles.active:test,composite", "spring.jmx.enabled=false",
							"spring.config.name:configserver",
							"spring.cloud.config.server.composite[0].uri:https://source.developers.google.com",
							"spring.cloud.config.server.composite[0].type:git")
					.run(context -> {
						CompositeUtils.getCompositeTypeList(context.getEnvironment());
					});
		}

	}

}
