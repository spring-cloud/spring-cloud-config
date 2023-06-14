/*
 * Copyright 2018-2023 the original author or authors.
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

package org.springframework.cloud.config.server.resource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.config.server.environment.NoSuchLabelException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * @author Ryan Baxter
 */
@RestControllerAdvice(basePackages = { "org.springframework.cloud.config.server.resource" })
@Order
public class ResourceControllerAdvice {

	private static Log logger = LogFactory.getLog(ResourceControllerAdvice.class);

	@ExceptionHandler(NoSuchResourceException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	void notFound(NoSuchResourceException e) {
	}

	@ExceptionHandler(NoSuchLabelException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	void noSuchLabel(NoSuchLabelException e) {
		logger.debug("Error when fetching resource", e);
	}

}
