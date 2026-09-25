/*
 * Copyright © 2012 Broadcom Inc. and/or its subsidiaries. All Rights Reserved.
 * Copyright 2012-present the original author or authors.
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
