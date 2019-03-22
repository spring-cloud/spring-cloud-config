/*
 * Copyright 2017-2019 the original author or authors.
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

import java.io.File;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

import org.springframework.cloud.config.server.environment.MultipleJGitEnvironmentProperties;
import org.springframework.validation.annotation.Validated;

import static java.lang.String.format;

/**
 * JSR-303 Cross Field validator that ensures that a
 * {@link MultipleJGitEnvironmentProperties} bean for the constraints: - Verifies that
 * known hosts file exists
 * <p>
 * Beans annotated with {@link KnownHostsFileIsValid} and {@link Validated} will have the
 * constraints applied.
 *
 * @author Edgars Jasmans
 */
public class KnownHostsFileValidator implements
		ConstraintValidator<KnownHostsFileIsValid, MultipleJGitEnvironmentProperties> {

	@Override
	public void initialize(KnownHostsFileIsValid knownHostsFileIsValid) {
		// No initialization required
	}

	@Override
	public boolean isValid(MultipleJGitEnvironmentProperties sshUriProperties,
			ConstraintValidatorContext context) {
		String knownHostsFile = sshUriProperties.getKnownHostsFile();
		if (knownHostsFile != null && !new File(knownHostsFile).exists()) {
			context.disableDefaultConstraintViolation();
			context.buildConstraintViolationWithTemplate(format(
					"File '%s' specified in property 'spring.cloud.config.server.git.knownHostsFile' could not be located",
					knownHostsFile)).addConstraintViolation();
			return false;
		}
		return true;
	}

}
