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

package org.springframework.cloud.config.client.diagnostics.analyzer;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.cloud.config.client.validation.InvalidApplicationNameException;

/**
 * An {@link AbstractFailureAnalyzer} that analyzes {@link InvalidApplicationNameException
 * InvalidApplicationNameException}.
 *
 * @author Anshul Mehra
 */
public class InvalidApplicationNameExceptionFailureAnalyzer
		extends AbstractFailureAnalyzer<InvalidApplicationNameException> {

	@Override
	protected FailureAnalysis analyze(Throwable rootFailure, InvalidApplicationNameException cause) {
		StringBuilder description = new StringBuilder(String.format("%s:%n", cause.getMessage()));
		description.append(String.format("%n    Property: %s", cause.getProperty()));
		description.append(String.format("%n    Value: %s", cause.getValue()));
		String action = "Change ${spring.application.name} or the ${spring.cloud.config.name} "
				+ "override so that it does not begin with 'application-'.";

		return new FailureAnalysis(description.toString(), action, cause);
	}

}
