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

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
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
 * {@link MultipleJGitEnvironmentProperties} bean for the constraints: - If host key algo
 * is supported
 *
 * Beans annotated with {@link HostKeyAlgoSupported} and {@link Validated} will have the
 * constraints applied.
 *
 * @author Ollie Hughes
 */
public class HostKeyAlgoSupportedValidator implements
		ConstraintValidator<HostKeyAlgoSupported, MultipleJGitEnvironmentProperties> {

	private static final String GIT_PROPERTY_PREFIX = "spring.cloud.config.server.git.";

	private static final Set<String> VALID_HOST_KEY_ALGORITHMS = new LinkedHashSet<>(
			Arrays.asList("ssh-dss", "ssh-rsa", "ecdsa-sha2-nistp256",
					"ecdsa-sha2-nistp384", "ecdsa-sha2-nistp521"));

	private final SshPropertyValidator sshPropertyValidator = new SshPropertyValidator();

	@Override
	public void initialize(HostKeyAlgoSupported constrainAnnotation) {
		// No special initialization of validator required
	}

	@Override
	public boolean isValid(MultipleJGitEnvironmentProperties sshUriProperties,
			ConstraintValidatorContext context) {
		context.disableDefaultConstraintViolation();
		Set<Boolean> validationResults = new HashSet<>();
		List<JGitEnvironmentProperties> extractedProperties = this.sshPropertyValidator
				.extractRepoProperties(sshUriProperties);

		for (JGitEnvironmentProperties extractedProperty : extractedProperties) {
			if (sshUriProperties.isIgnoreLocalSshSettings()
					&& isSshUri(extractedProperty.getUri())) {
				validationResults.add(
						isHostKeySpecifiedWhenAlgorithmSet(extractedProperty, context));
			}
		}
		return !validationResults.contains(false);
	}

	private boolean isHostKeySpecifiedWhenAlgorithmSet(
			JGitEnvironmentProperties sshUriProperties,
			ConstraintValidatorContext context) {
		if (hasText(sshUriProperties.getHostKeyAlgorithm()) && !VALID_HOST_KEY_ALGORITHMS
				.contains(sshUriProperties.getHostKeyAlgorithm())) {

			context.buildConstraintViolationWithTemplate(
					format("Property '%shostKeyAlgorithm' must be one of %s",
							GIT_PROPERTY_PREFIX, VALID_HOST_KEY_ALGORITHMS))
					.addConstraintViolation();
			return false;
		}
		return true;
	}

}
