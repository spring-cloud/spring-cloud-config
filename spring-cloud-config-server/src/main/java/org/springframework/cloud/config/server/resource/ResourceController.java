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

package org.springframework.cloud.config.server.resource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.cloud.config.server.support.EnvironmentPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * An HTTP endpoint for serving up templated plain text resources from an underlying
 * repository. Can be used to supply config files for consumption by a wide variety of
 * applications and services. A {@link ResourceRepository} is used to locate a
 * {@link Resource}, specific to an application, and the contents are transformed to text.
 * Then an {@link EnvironmentRepository} is used to supply key-value pairs which are used
 * to replace placeholders in the resource text.
 *
 * @author Dave Syer
 *
 */
@RestController
@RequestMapping(method = RequestMethod.GET, path = "${spring.cloud.config.server.prefix:}")
public class ResourceController {

	private ResourceRepository resourceRepository;

	private EnvironmentRepository environmentRepository;

	public ResourceController(ResourceRepository resourceRepository,
			EnvironmentRepository environmentRepository) {
		this.resourceRepository = resourceRepository;
		this.environmentRepository = environmentRepository;
	}

	@RequestMapping("/{name}/{profile}/{label}/{path:.*}")
	public synchronized String resolve(@PathVariable String name,
			@PathVariable String profile, @PathVariable String label,
			@PathVariable String path) throws IOException {
		StandardEnvironment environment = new StandardEnvironment();
		if (label != null && label.contains("(_)")) {
			// "(_)" is uncommon in a git branch name, but "/" cannot be matched
			// by Spring MVC
			label = label.replace("(_)", "/");
		}
		environment.getPropertySources().addAfter(
				StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME,
				new EnvironmentPropertySource(
						this.environmentRepository.findOne(name, profile, label)));

		// ensure InputStream will be closed to prevent file locks on Windows
		try (InputStream is = this.resourceRepository.findOne(name, profile, label, path)
				.getInputStream()) {
			String text = StreamUtils.copyToString(is, Charset.forName("UTF-8"));
			// Mask out escaped placeholders
			text = text.replace("\\${", "$_{");
			return environment.resolvePlaceholders(text).replace("$_{", "${");
		}
	}

	@RequestMapping(value = "/{name}/{profile}/{label}/{path:.*}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
	public synchronized byte[] binary(@PathVariable String name,
			@PathVariable String profile, @PathVariable String label,
			@PathVariable String path) throws IOException {
		StandardEnvironment environment = new StandardEnvironment();
		if (label != null && label.contains("(_)")) {
			// "(_)" is uncommon in a git branch name, but "/" cannot be matched
			// by Spring MVC
			label = label.replace("(_)", "/");
		}
		environment.getPropertySources().addAfter(
				StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME,
				new EnvironmentPropertySource(
						this.environmentRepository.findOne(name, profile, label)));
		try (InputStream is = this.resourceRepository.findOne(name, profile, label, path)
				.getInputStream()) {
			return StreamUtils.copyToByteArray(is);
		}
	}

	@ExceptionHandler(NoSuchResourceException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public void notFound(NoSuchResourceException e) {
	}

}
