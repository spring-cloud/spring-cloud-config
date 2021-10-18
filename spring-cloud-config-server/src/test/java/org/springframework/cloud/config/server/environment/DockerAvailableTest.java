/*
 * Copyright 2013-2021 the original author or authors.
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

import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;

import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

/**
 * @author Robert McNees
 *
 * In test classes where Docker is required, the annotation @Testcontainers(disabledWithoutDocker = true) can be used
 * to disable test cases if a Docker environment is not present.  In circumstances such as local builds, this may be
 * an acceptable solution.  However, in some circumstances (i.e. when building in Jenkins) it is expected that Docker
 * is available and all subsequent Docker tests will run.  This class tests if a Docker environment is expected
 * and is not found.
 *
 */
@SpringBootTest
public class DockerAvailableTest {

	@Test
	public void dockerAvailableWhenRunningInJenkins() {
		if (isJenkinsEnvironment()) {
			assertThat(isDockerAvailable()).isTrue();
		}
	}

	private boolean isDockerAvailable() {
		try {
			DockerClientFactory.instance().client();
			return true;
		}
		catch (Throwable var) {
			return false;
		}
	}

	private boolean isJenkinsEnvironment() {
		return System.getenv("JENKINS_HOME") != null;
	}

}
