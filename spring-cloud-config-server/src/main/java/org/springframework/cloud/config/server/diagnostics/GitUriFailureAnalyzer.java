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

package org.springframework.cloud.config.server.diagnostics;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.cloud.config.server.environment.JGitEnvironmentRepository;

/**
 * @author Ryan Baxter
 */
public class GitUriFailureAnalyzer
		extends AbstractFailureAnalyzer<IllegalStateException> {

	/**
	 * Description of the failure.
	 */
	public static final String DESCRIPTION = "Invalid config server configuration.";

	/**
	 * Action to take for git failure.
	 */
	public static final String ACTION = "If you are using the git profile, you need to set a Git URI in your "
			+ "configuration.  If you are using a native profile and have spring.cloud.config.server.bootstrap=true, "
			+ "you need to use a composite configuration.";

	@Override
	protected FailureAnalysis analyze(Throwable rootFailure,
			IllegalStateException cause) {
		if (JGitEnvironmentRepository.MESSAGE.equalsIgnoreCase(cause.getMessage())) {
			return new FailureAnalysis(DESCRIPTION, ACTION, cause);
		}
		return null;
	}

}
