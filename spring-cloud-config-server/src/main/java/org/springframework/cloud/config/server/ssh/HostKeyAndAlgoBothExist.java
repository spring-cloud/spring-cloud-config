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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import org.springframework.validation.annotation.Validated;

/**
 * Beans annotated with {@link HostKeyAndAlgoBothExist} and {@link Validated} will have
 * the constraints applied.
 *
 * @author Ollie Hughes
 */
@Constraint(validatedBy = HostKeyAndAlgoBothExistValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface HostKeyAndAlgoBothExist {

	String message() default "{HostKeyAndAlgoBothExist.message}";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
