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

package org.springframework.cloud.config.server.ssh;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import org.springframework.cloud.config.server.environment.JGitEnvironmentProperties;
import org.springframework.cloud.config.server.environment.MultipleJGitEnvironmentProperties;
import org.springframework.validation.annotation.Validated;

import static java.lang.String.format;
import static org.springframework.cloud.config.server.ssh.SshPropertyValidator.isSshUri;
import static org.springframework.util.StringUtils.hasText;

/**
 * JSR-303 Cross Field validator that ensures that a
 * {@link MultipleJGitEnvironmentProperties} bean for the constraints: - When strict host
 * key checking is enabled, either a hostKey or a knownHostsFile is provided so that the
 * server key can actually be verified.
 *
 * Beans annotated with {@link HostKeyOrKnownHostsFileRequiredValidator} and
 * {@link Validated} will have the constraints applied.
 *
 * @author Ryan Baxter
 */
public class HostKeyOrKnownHostsFileRequiredValidator
		implements ConstraintValidator<HostKeyOrKnownHostsFileRequired, MultipleJGitEnvironmentProperties> {

	private static final String GIT_PROPERTY_PREFIX = "spring.cloud.config.server.git.";

	private final SshPropertyValidator sshPropertyValidator = new SshPropertyValidator();

	@Override
	public void initialize(HostKeyOrKnownHostsFileRequired constrainAnnotation) {
		// No special initialization of validator required
	}

	@Override
	public boolean isValid(MultipleJGitEnvironmentProperties sshUriProperties, ConstraintValidatorContext context) {
		context.disableDefaultConstraintViolation();
		Set<Boolean> validationResults = new HashSet<>();
		List<JGitEnvironmentProperties> extractedProperties = this.sshPropertyValidator
			.extractRepoProperties(sshUriProperties);

		for (JGitEnvironmentProperties extractedProperty : extractedProperties) {
			if (extractedProperty.isIgnoreLocalSshSettings() && extractedProperty.isStrictHostKeyChecking()
					&& isSshUri(extractedProperty.getUri())) {
				validationResults.add(isHostKeyOrKnownHostsFilePresent(extractedProperty, context));
			}
		}
		return !validationResults.contains(false);
	}

	private boolean isHostKeyOrKnownHostsFilePresent(JGitEnvironmentProperties sshUriProperties,
			ConstraintValidatorContext context) {
		if (!hasText(sshUriProperties.getHostKey()) && !hasText(sshUriProperties.getKnownHostsFile())) {
			context
				.buildConstraintViolationWithTemplate(format(
						"Property '%shostKey' or '%sknownHostsFile' must be set when '%sstrictHostKeyChecking' "
								+ "is enabled and '%signoreLocalSshSettings' is specified",
						GIT_PROPERTY_PREFIX, GIT_PROPERTY_PREFIX, GIT_PROPERTY_PREFIX, GIT_PROPERTY_PREFIX))
				.addConstraintViolation();
			return false;
		}
		return true;
	}

}
