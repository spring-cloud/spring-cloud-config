/*
 * Copyright 2013-present the original author or authors.
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

package org.springframework.cloud.config.client;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * {@link RestClient} based implementation of {@link ConfigResourceClient}. The config
 * server URI, the application name and the default profile and label are provided by
 * {@link ConfigClientProperties}.
 *
 * @author Daniel Lavoie
 * @author Anshul Mehra
 * @author Sharang Gupta
 * @since 5.1.0
 */
public class DefaultConfigResourceClient implements ConfigResourceClient {

	/**
	 * Request parameter that asks the config server to use its default label.
	 */
	static final String USE_DEFAULT_LABEL = "useDefaultLabel";

	private final RestClient restClient;

	private final ConfigClientProperties properties;

	public DefaultConfigResourceClient(RestClient restClient, ConfigClientProperties properties) {
		Assert.notNull(restClient, "RestClient must not be null");
		Assert.notNull(properties, "ConfigClientProperties must not be null");
		this.restClient = restClient;
		this.properties = properties;
	}

	@Override
	public Resource getPlainTextResource(String path) {
		return getPlainTextResource(null, null, path);
	}

	@Override
	public Resource getPlainTextResource(String profile, String label, String path) {
		return getResource(profile, label, path, false);
	}

	@Override
	public Resource getBinaryResource(String path) {
		return getBinaryResource(null, null, path);
	}

	@Override
	public Resource getBinaryResource(String profile, String label, String path) {
		return getResource(profile, label, path, true);
	}

	private Resource getResource(String profile, String label, String path, boolean binary) {
		String name = this.properties.getName();
		Assert.hasText(name, "Spring application name is undefined");
		String[] uris = this.properties.getUri();
		Assert.isTrue(!ObjectUtils.isEmpty(uris) && StringUtils.hasText(uris[0]), "Config server URI is undefined");
		Assert.hasText(path, "path is undefined");
		if (!StringUtils.hasText(profile)) {
			profile = this.properties.getProfile();
			if (!StringUtils.hasText(profile)) {
				profile = ConfigClientProperties.DEFAULT_PROFILE;
			}
		}
		if (label == null) {
			label = this.properties.getLabel();
		}
		UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(uris[0]).pathSegment(name, profile);
		if (StringUtils.hasText(label)) {
			builder.pathSegment(Environment.denormalize(label));
		}
		else {
			builder.queryParam(USE_DEFAULT_LABEL);
		}
		builder.pathSegment(StringUtils.tokenizeToStringArray(path, "/"));
		RestClient.RequestHeadersSpec<?> request = this.restClient.get().uri(builder.build().toUri());
		if (StringUtils.hasText(this.properties.getToken())) {
			request = request.header(ConfigClientProperties.TOKEN_HEADER, this.properties.getToken());
		}
		if (binary) {
			request = request.accept(MediaType.APPLICATION_OCTET_STREAM);
		}
		return request.retrieve().body(Resource.class);
	}

}
