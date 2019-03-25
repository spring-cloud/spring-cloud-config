/*
 * Copyright 2015-2019 the original author or authors.
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

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

import org.springframework.cloud.config.server.environment.JGitEnvironmentProperties;
import org.springframework.cloud.config.server.environment.MultipleJGitEnvironmentProperties;
import org.springframework.validation.annotation.Validated;

import static java.lang.String.format;
import static org.springframework.cloud.config.server.ssh.SshPropertyValidator.isSshUri;
import static org.springframework.util.StringUtils.hasText;

/**
 * JSR-303 Cross Field validator that ensures that a
 * {@link MultipleJGitEnvironmentProperties} bean for the constraints: - If host key is
 * set then host key algo must also be set - If host key algo is set then host key must
 * also be set
 *
 * Beans annotated with {@link HostKeyAndAlgoBothExist} and {@link Validated} will have
 * the constraints applied.
 *
 * @author Ollie Hughes
 */
public class HostKeyAndAlgoBothExistValidator implements
		ConstraintValidator<HostKeyAndAlgoBothExist, MultipleJGitEnvironmentProperties> {

	private static final String GIT_PROPERTY_PREFIX = "spring.cloud.config.server.git.";

	private final SshPropertyValidator sshPropertyValidator = new SshPropertyValidator();

	@Override
	public void initialize(HostKeyAndAlgoBothExist constrainAnnotation) {
		// No special initialization of validator required
	}

	@Override
	public boolean isValid(MultipleJGitEnvironmentProperties sshUriProperties,
			ConstraintValidatorContext context) {
		Set<Boolean> validationResults = new HashSet<>();
		List<JGitEnvironmentProperties> extractedProperties = this.sshPropertyValidator
				.extractRepoProperties(sshUriProperties);

		for (JGitEnvironmentProperties extractedProperty : extractedProperties) {
			if (sshUriProperties.isIgnoreLocalSshSettings()
					&& isSshUri(extractedProperty.getUri())) {
				validationResults.add(
						isAlgorithmSpecifiedWhenHostKeySet(extractedProperty, context)
								&& isHostKeySpecifiedWhenAlgorithmSet(extractedProperty,
										context));
			}
		}
		return !validationResults.contains(false);
	}

	private boolean isHostKeySpecifiedWhenAlgorithmSet(
			JGitEnvironmentProperties sshUriProperties,
			ConstraintValidatorContext context) {
		if (hasText(sshUriProperties.getHostKeyAlgorithm())
				&& !hasText(sshUriProperties.getHostKey())) {
			context.disableDefaultConstraintViolation();
			context.buildConstraintViolationWithTemplate(format(
					"Property '%shostKey' must be set when '%shostKeyAlgorithm' is specified",
					GIT_PROPERTY_PREFIX, GIT_PROPERTY_PREFIX)).addConstraintViolation();
			return false;
		}
		return true;
	}

	private boolean isAlgorithmSpecifiedWhenHostKeySet(
			JGitEnvironmentProperties sshUriProperties,
			ConstraintValidatorContext context) {
		if (hasText(sshUriProperties.getHostKey())
				&& !hasText(sshUriProperties.getHostKeyAlgorithm())) {
			context.disableDefaultConstraintViolation();
			context.buildConstraintViolationWithTemplate(format(
					"Property '%shostKeyAlgorithm' must be set when '%shostKey' is specified",
					GIT_PROPERTY_PREFIX, GIT_PROPERTY_PREFIX)).addConstraintViolation();
			return false;
		}
		return true;
	}

}
