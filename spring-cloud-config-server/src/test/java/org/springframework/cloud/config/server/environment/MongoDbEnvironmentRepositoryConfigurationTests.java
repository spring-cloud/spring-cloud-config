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

package org.springframework.cloud.config.server.environment;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import org.springframework.boot.test.context.assertj.AssertableWebApplicationContext;
import org.springframework.boot.test.context.runner.ContextConsumer;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.cloud.config.server.test.ConfigServerTestUtils;
import org.springframework.cloud.config.server.test.TestConfigServerApplication;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests to verify MongoDbEnvironmentRepository configuration.
 *
 * @author Alexandros Pappas
 */
public class MongoDbEnvironmentRepositoryConfigurationTests {

	@Test
	public void mongoDbEnvironmentRepositoryBeansConfiguredWhenDefault() {
		new WebApplicationContextRunner().withUserConfiguration(TestConfigServerApplication.class)
				.withPropertyValues("spring.profiles.active=test,mongodb", "spring.main.web-application-type=none")
				.run(context -> {
					assertThat(context).hasSingleBean(MongoDbEnvironmentRepositoryFactory.class);
					assertThat(context).hasSingleBean(MongoDbEnvironmentRepository.class);
				});
	}

	@Test
	public void mongoDbEnvironmentRepositoryBeansConfiguredWhenEnabled() throws IOException {
		getApplicationContextWithMongoDbEnabled(true, context -> {
			assertThat(context).hasSingleBean(MongoDbEnvironmentRepositoryFactory.class);
			assertThat(context).hasSingleBean(MongoDbEnvironmentRepository.class);
		});
	}

	@Test
	public void mongoDbEnvironmentRepositoryFactoryNotConfiguredWhenDisabled() throws IOException {
		getApplicationContextWithMongoDbEnabled(false,
				context -> assertThat(context).doesNotHaveBean(MongoDbEnvironmentRepositoryFactory.class));
	}

	@Test
	public void mongoDbEnvironmentRepositoryNotConfiguredWhenDisabled() throws IOException {
		getApplicationContextWithMongoDbEnabled(false,
				context -> assertThat(context).doesNotHaveBean(MongoDbEnvironmentRepository.class));
	}

	private void getApplicationContextWithMongoDbEnabled(boolean mongoDbEnabled,
			ContextConsumer<? super AssertableWebApplicationContext> consumer) throws IOException {
		String uri = ConfigServerTestUtils.prepareLocalRepo();
		new WebApplicationContextRunner().withUserConfiguration(TestConfigServerApplication.class)
				.withPropertyValues("spring.profiles.active=test,mongodb", "spring.main.web-application-type=none",
						"spring.cloud.config.server.git.uri:" + uri,
						"spring.cloud.config.server.mongodb.enabled:" + mongoDbEnabled)
				.run(consumer);
	}

}
