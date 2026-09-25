/*
 * Copyright © 2012 Broadcom Inc. and/or its subsidiaries. All Rights Reserved.
 * Copyright 2012-present the original author or authors.
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
 * Beans annotated with {@link HostKeyOrKnownHostsFileRequiredValidator} and
 * {@link Validated} will have the constraints applied.
 *
 * @author Ryan Baxter
 */
@Constraint(validatedBy = HostKeyOrKnownHostsFileRequiredValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface HostKeyOrKnownHostsFileRequired {

	String message() default "{HostKeyOrKnownHostsFileRequired.message}";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
