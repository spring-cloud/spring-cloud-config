/*
 * Copyright 2017 the original author or authors.
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

import org.springframework.validation.annotation.Validated;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

import static java.lang.String.format;

/**
 * JSR-303 Cross Field validator that ensures that a {@link SshUriProperties} bean for the constraints:
 * - Verifies that PreferredAuthentications property is properly formed
 *
 * Beans annotated with {@link PreferredAuthenticationsIsValid} and {@link Validated} will have the constraints applied.
 *
 * @author Edgars Jasmans
 */
public class PreferredAuthenticationsValidator implements ConstraintValidator<PreferredAuthenticationsIsValid, SshUriProperties> {

    private static final String propertyPattern = "([\\w -]+,)*([\\w -]+)";

    @Override
    public void initialize(PreferredAuthenticationsIsValid preferredAuthenticationsIsValid) {
    }

    @Override
    public boolean isValid(SshUriProperties sshUriProperties, ConstraintValidatorContext context) {
        String preferredAuthentications = sshUriProperties.getPreferredAuthentications();
        if (preferredAuthentications != null && !preferredAuthentications.matches(propertyPattern)) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                    format("Property 'spring.cloud.config.server.git.preferredAuthentications' has invalid value '%s'. " +
                            "It must consist of authentication method names separated by comma", preferredAuthentications))
                    .addConstraintViolation();
            return false;
        }
        return true;
    }

}
