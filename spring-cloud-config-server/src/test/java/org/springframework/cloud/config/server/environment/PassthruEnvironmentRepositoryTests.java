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

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import org.springframework.boot.env.OriginTrackedMapPropertySource;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

public class PassthruEnvironmentRepositoryTests {

	@Test
	public void originTrackedPropertySourceWithoutOriginWorks() {
		MockEnvironment mockEnvironment = new MockEnvironment();
		mockEnvironment.setProperty("normalKey", "normalValue");
		mockEnvironment.getPropertySources()
				.addFirst(new OriginTrackedMapPropertySource("myorigintrackedsource",
						Collections.singletonMap("keyNoOrigin", "valueNoOrigin")));
		PassthruEnvironmentRepository repository = new PassthruEnvironmentRepository(
				mockEnvironment);
		Environment environment = repository.findOne("testapp", "default", "master",
				true);
		assertThat(environment).isNotNull();
		List<PropertySource> propertySources = environment.getPropertySources();
		assertThat(propertySources).hasSize(2);
		for (PropertySource propertySource : propertySources) {
			Map source = propertySource.getSource();
			if (propertySource.getName().equals("myorigintrackedsource")) {
				assertThat(source).containsEntry("keyNoOrigin", "valueNoOrigin");
			}
			else if (propertySource.getName().equals("mockProperties")) {
				assertThat(source).containsEntry("normalKey", "normalValue");
			}
		}
	}

}
