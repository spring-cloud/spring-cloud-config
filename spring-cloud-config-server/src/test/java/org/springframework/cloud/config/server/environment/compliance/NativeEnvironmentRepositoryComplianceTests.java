/*
 * Copyright 2013-present the original author or authors.
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

package org.springframework.cloud.config.server.environment.compliance;

import java.nio.file.Path;

import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.cloud.config.server.environment.NativeEnvironmentProperties;
import org.springframework.cloud.config.server.environment.NativeEnvironmentRepository;
import org.springframework.core.env.StandardEnvironment;

/**
 * Compliance tests for {@link NativeEnvironmentRepository}. The native repository
 * delegates to Spring Boot, so it is the reference the other backends are compared to.
 *
 * @author Ryan Baxter
 */
class NativeEnvironmentRepositoryComplianceTests extends EnvironmentRepositoryComplianceTests {

	@TempDir
	Path dir;

	private EnvironmentRepositoryFixture fixture;

	@Override
	protected EnvironmentRepositoryFixture fixture() {
		if (this.fixture == null) {
			this.fixture = new FileSystemEnvironmentRepositoryFixture(this.dir.resolve("config")) {
				@Override
				public EnvironmentRepository repository() {
					NativeEnvironmentProperties properties = new NativeEnvironmentProperties();
					properties.setSearchLocations(new String[] { root().toUri().toString() });
					return new NativeEnvironmentRepository(new StandardEnvironment(), properties,
							ObservationRegistry.NOOP);
				}
			};
		}
		return this.fixture;
	}

	@Override
	@Test
	@Disabled("TODO-issue: Label directories are added as search locations, so labels are interleaved by profile and the first label wins")
	public void lastLabelTakesPrecedence() throws Exception {
		super.lastLabelTakesPrecedence();
	}

}
