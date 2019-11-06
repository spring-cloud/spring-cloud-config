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

package org.springframework.cloud.config.server.resource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.encryption.ResourceEncryptor;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.util.UrlPathHelper;

import static org.springframework.cloud.config.server.support.EnvironmentPropertySource.prepareEnvironment;
import static org.springframework.cloud.config.server.support.EnvironmentPropertySource.resolvePlaceholders;

/**
 * An HTTP endpoint for serving up templated plain text resources from an underlying
 * repository. Can be used to supply config files for consumption by a wide variety of
 * applications and services. A {@link ResourceRepository} is used to locate a
 * {@link Resource}, specific to an application, and the contents are transformed to text.
 * Then an {@link EnvironmentRepository} is used to supply key-value pairs which are used
 * to replace placeholders in the resource text.
 *
 * @author Dave Syer
 * @author Daniel Lavoie
 *
 */
@RestController
@RequestMapping(method = RequestMethod.GET,
		path = "${spring.cloud.config.server.prefix:}")
public class ResourceController {

	private static Log logger = LogFactory.getLog(ResourceController.class);

	private ResourceRepository resourceRepository;

	private EnvironmentRepository environmentRepository;

	private Map<String, ResourceEncryptor> resourceEncryptorMap = new HashMap<>();

	private UrlPathHelper helper = new UrlPathHelper();

	private boolean encryptEnabled = false;

	private boolean plainTextEncryptEnabled = false;

	public ResourceController(ResourceRepository resourceRepository,
			EnvironmentRepository environmentRepository,
			Map<String, ResourceEncryptor> resourceEncryptorMap) {
		this.resourceRepository = resourceRepository;
		this.environmentRepository = environmentRepository;
		this.resourceEncryptorMap = resourceEncryptorMap;
		this.helper.setAlwaysUseFullPath(true);
	}

	public ResourceController(ResourceRepository resourceRepository,
			EnvironmentRepository environmentRepository) {
		this.resourceRepository = resourceRepository;
		this.environmentRepository = environmentRepository;
		this.helper.setAlwaysUseFullPath(true);
	}

	public void setEncryptEnabled(boolean encryptEnabled) {
		this.encryptEnabled = encryptEnabled;
	}

	public void setPlainTextEncryptEnabled(boolean plainTextEncryptEnabled) {
		this.plainTextEncryptEnabled = plainTextEncryptEnabled;
	}

	@RequestMapping("/{name}/{profile}/{label}/**")
	public String retrieve(@PathVariable String name, @PathVariable String profile,
			@PathVariable String label, ServletWebRequest request,
			@RequestParam(defaultValue = "true") boolean resolvePlaceholders)
			throws IOException {
		String path = getFilePath(request, name, profile, label);
		return retrieve(request, name, profile, label, path, resolvePlaceholders);
	}

	@RequestMapping(value = "/{name}/{profile}/**", params = "useDefaultLabel")
	public String retrieve(@PathVariable String name, @PathVariable String profile,
			ServletWebRequest request,
			@RequestParam(defaultValue = "true") boolean resolvePlaceholders)
			throws IOException {
		String path = getFilePath(request, name, profile, null);
		return retrieve(request, name, profile, null, path, resolvePlaceholders);
	}

	private String getFilePath(ServletWebRequest request, String name, String profile,
			String label) {
		String stem;
		if (label != null) {
			stem = String.format("/%s/%s/%s/", name, profile, label);
		}
		else {
			stem = String.format("/%s/%s/", name, profile);
		}
		String path = this.helper.getPathWithinApplication(request.getRequest());
		path = path.substring(path.indexOf(stem) + stem.length());
		return path;
	}

	synchronized String retrieve(ServletWebRequest request, String name, String profile,
			String label, String path, boolean resolvePlaceholders) throws IOException {
		name = resolveName(name);
		label = resolveLabel(label);
		Resource resource = this.resourceRepository.findOne(name, profile, label, path);
		if (checkNotModified(request, resource)) {
			// Content was not modified. Just return.
			return null;
		}
		// ensure InputStream will be closed to prevent file locks on Windows
		try (InputStream is = resource.getInputStream()) {
			String text = StreamUtils.copyToString(is, Charset.forName("UTF-8"));
			String ext = StringUtils.getFilenameExtension(resource.getFilename())
					.toLowerCase();
			Environment environment = this.environmentRepository.findOne(name, profile,
					label, false);
			if (resolvePlaceholders) {
				text = resolvePlaceholders(prepareEnvironment(environment), text);
			}
			if (encryptEnabled && plainTextEncryptEnabled) {
				ResourceEncryptor re = this.resourceEncryptorMap.get(ext);
				if (re == null) {
					logger.warn("Cannot decrypt for extension " + ext);
				}
				else {
					text = re.decrypt(text, environment);
				}
			}
			return text;
		}
	}

	/*
	 * Used only for unit tests.
	 */
	String retrieve(String name, String profile, String label, String path,
			boolean resolvePlaceholders) throws IOException {
		return retrieve(null, name, profile, label, path, resolvePlaceholders);
	}

	@RequestMapping(value = "/{name}/{profile}/{label}/**",
			produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
	public byte[] binary(@PathVariable String name, @PathVariable String profile,
			@PathVariable String label, ServletWebRequest request) throws IOException {
		String path = getFilePath(request, name, profile, label);
		return binary(request, name, profile, label, path);
	}

	@RequestMapping(value = "/{name}/{profile}/**", params = "useDefaultLabel",
			produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
	public byte[] binary(@PathVariable String name, @PathVariable String profile,
			ServletWebRequest request) throws IOException {
		String path = getFilePath(request, name, profile, null);
		return binary(request, name, profile, null, path);
	}

	/*
	 * Used only for unit tests.
	 */
	byte[] binary(String name, String profile, String label, String path)
			throws IOException {
		return binary(null, name, profile, label, path);
	}

	private synchronized byte[] binary(ServletWebRequest request, String name,
			String profile, String label, String path) throws IOException {
		name = resolveName(name);
		label = resolveLabel(label);
		Resource resource = this.resourceRepository.findOne(name, profile, label, path);
		if (checkNotModified(request, resource)) {
			// Content was not modified. Just return.
			return null;
		}
		// TODO: is this line needed for side effects?
		prepareEnvironment(this.environmentRepository.findOne(name, profile, label));
		try (InputStream is = resource.getInputStream()) {
			return StreamUtils.copyToByteArray(is);
		}
	}

	private boolean checkNotModified(ServletWebRequest request, Resource resource) {
		try {
			return request != null && request.checkNotModified(resource.lastModified());
		}
		catch (Exception ex) {
			// Ignore the exception since caching is optional.
		}
		return false;
	}

	private String resolveName(String name) {
		if (name != null && name.contains("(_)")) {
			// "(_)" is uncommon in a git repo name, but "/" cannot be matched
			// by Spring MVC
			name = name.replace("(_)", "/");
		}
		return name;
	}

	private String resolveLabel(String label) {
		if (label != null && label.contains("(_)")) {
			// "(_)" is uncommon in a git branch name, but "/" cannot be matched
			// by Spring MVC
			label = label.replace("(_)", "/");
		}
		return label;
	}

	@ExceptionHandler(NoSuchResourceException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public void notFound(NoSuchResourceException e) {
	}

}
