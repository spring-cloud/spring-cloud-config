/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
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

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.KeyPair;

import org.springframework.validation.annotation.Validated;

import static java.lang.String.format;
import static org.springframework.cloud.config.server.ssh.SshPropertyValidator.isSshUri;
import static org.springframework.util.StringUtils.hasText;

/**
 * JSR-303 Cross Field validator that ensures that an {@link SshUriProperties} bean for the constraints:
 * - Private key is present and can be correctly parsed using {@link com.jcraft.jsch.KeyPair}
 *
 * Beans annotated with {@link PrivateKeyValidator} and {@link Validated} will have the constraints applied.
 *
 * @author Ollie Hughes
 */
public class PrivateKeyValidator implements ConstraintValidator<PrivateKeyIsValid, SshUriProperties> {
	private static final String GIT_PROPERTY_PREFIX = "spring.cloud.config.server.git.";
	private final SshPropertyValidator sshPropertyValidator = new SshPropertyValidator();

	@Override
	public void initialize(PrivateKeyIsValid constrainAnnotation) {
		//No special initialization of validator required
	}

	@Override
	public boolean isValid(SshUriProperties sshUriProperties, ConstraintValidatorContext context) {
		context.disableDefaultConstraintViolation();
		Set<Boolean> validationResults = new HashSet<>();
		List<SshUri> extractedProperties = sshPropertyValidator.extractRepoProperties(sshUriProperties);

		for (SshUri extractedProperty : extractedProperties) {
			if (sshUriProperties.isIgnoreLocalSshSettings() && isSshUri(extractedProperty.getUri())) {
				validationResults.add(
						 isPrivateKeyPresent(extractedProperty, context)
						&& isPrivateKeyFormatCorrect(extractedProperty, context));
			}
		}
		return !validationResults.contains(false);

	}

	private boolean isPrivateKeyPresent(SshUri sshUriProperties, ConstraintValidatorContext context) {
		if (!hasText(sshUriProperties.getPrivateKey())) {
			context.buildConstraintViolationWithTemplate(
					format("Property '%sprivateKey' must be set when '%signoreLocalSshSettings' is specified", GIT_PROPERTY_PREFIX, GIT_PROPERTY_PREFIX))
					.addConstraintViolation();
			return false;
		}
		return true;
	}

	private boolean isPrivateKeyFormatCorrect(SshUri sshUriProperties, ConstraintValidatorContext context) {
		try {
			KeyPair.load(new JSch(), sshUriProperties.getPrivateKey().getBytes(), null);
			return true;
		} catch (JSchException e) {
			context.buildConstraintViolationWithTemplate(
					format("Property '%sprivateKey' is not a valid private key", GIT_PROPERTY_PREFIX))
					.addConstraintViolation();
			return false;
		}
	}
}
