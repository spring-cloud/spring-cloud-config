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

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.config.client.ConfigClientProperties.Credentials;
import org.springframework.cloud.config.client.ConfigClientProperties.MultipleUriStrategy;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * {@link RestClient} based implementation of {@link ConfigResourceClient}. The config
 * server URIs and their credentials, the application name and the default profile and
 * label are provided by {@link ConfigClientProperties}, and are used the way the config
 * server import uses them: every URI is tried according to the
 * {@link MultipleUriStrategy}, a comma-separated label is tried label by label, and a
 * file larger than {@link ConfigClientProperties.ResourceClient#getMaxSize()} is
 * rejected.
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

	private static final int MAX_ERROR_BODY = 8 * 1024;

	private static final Log logger = LogFactory.getLog(DefaultConfigResourceClient.class);

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
		List<String> labels = labels(label);
		for (int i = 0; i < labels.size() - 1; i++) {
			try {
				return getResource(name, profile, labels.get(i), path, binary);
			}
			catch (HttpClientErrorException.NotFound ex) {
				logger.debug("File [" + path + "] not found in label [" + labels.get(i) + "], trying the next label");
			}
		}
		return getResource(name, profile, labels.get(labels.size() - 1), path, binary);
	}

	/**
	 * The labels to try in order: the given one, else the configured one, which may be a
	 * comma-separated list; {@code null} stands for the server's default label.
	 */
	private List<String> labels(String label) {
		if (label == null) {
			label = this.properties.getLabel();
		}
		List<String> labels = Arrays.stream(StringUtils.commaDelimitedListToStringArray(label))
			.map(String::trim)
			.filter(StringUtils::hasText)
			.toList();
		return labels.isEmpty() ? Collections.singletonList(null) : labels;
	}

	/**
	 * Try the config server URIs in order, the way the config server import does: an
	 * unreachable server is skipped, and an error response moves on to the next URI only
	 * with {@link MultipleUriStrategy#ALWAYS}.
	 */
	private Resource getResource(String name, String profile, String label, String path, boolean binary) {
		int last = this.properties.getUri().length - 1;
		for (int i = 0; i < last; i++) {
			try {
				return getResource(i, name, profile, label, path, binary);
			}
			catch (HttpClientErrorException | HttpServerErrorException ex) {
				if (this.properties.getMultipleUriStrategy() != MultipleUriStrategy.ALWAYS) {
					throw ex;
				}
				logSkippedUri(i, label, path, ex.getStatusCode().toString());
			}
			catch (ResourceAccessException ex) {
				logSkippedUri(i, label, path, ex.getMessage());
			}
		}
		return getResource(last, name, profile, label, path, binary);
	}

	private void logSkippedUri(int uriIndex, String label, String path, String reason) {
		logger.info("Failed to read file [" + path + "] with label [" + label + "] from config server at "
				+ this.properties.getCredentials(uriIndex).getUri() + ": " + reason
				+ ". Will try the next url if available");
	}

	private Resource getResource(int uriIndex, String name, String profile, String label, String path, boolean binary) {
		Credentials credentials = this.properties.getCredentials(uriIndex);
		URI uri = buildUri(credentials.getUri(), name, profile, label, path);
		return retrieve(uri, credentials, path, binary);
	}

	private static URI buildUri(String baseUri, String name, String profile, String label, String path) {
		UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUri).pathSegment(name, profile);
		if (StringUtils.hasText(label)) {
			builder.pathSegment(Environment.denormalize(label));
		}
		else {
			builder.queryParam(USE_DEFAULT_LABEL);
		}
		builder.pathSegment(StringUtils.tokenizeToStringArray(path, "/"));
		return builder.build().toUri();
	}

	private Resource retrieve(URI uri, Credentials credentials, String path, boolean binary) {
		RestClient.RequestHeadersSpec<?> request = this.restClient.get().uri(uri).headers(headers -> {
			addAuthorization(headers, credentials);
			if (StringUtils.hasText(this.properties.getToken())) {
				headers.add(ConfigClientProperties.TOKEN_HEADER, this.properties.getToken());
			}
		});
		if (binary) {
			request = request.accept(MediaType.APPLICATION_OCTET_STREAM);
		}
		long maxSize = this.properties.getResourceClient().getMaxSize().toBytes();
		return request.exchange((clientRequest, response) -> readResource(response, path, maxSize));
	}

	/**
	 * Add the credentials of the config server URI the way the config server import does:
	 * basic authentication from the username and password, else the configured
	 * {@code authorization} header.
	 */
	private void addAuthorization(HttpHeaders headers, Credentials credentials) {
		String authorization = this.properties.getHeaders().get(ConfigClientProperties.AUTHORIZATION);
		if (credentials.getPassword() != null && authorization != null) {
			throw new IllegalStateException("You must set either 'password' or 'authorization'");
		}
		if (credentials.getPassword() != null) {
			headers.setBasicAuth(credentials.getUsername(), credentials.getPassword());
		}
		else if (authorization != null) {
			headers.set(HttpHeaders.AUTHORIZATION, authorization);
		}
	}

	private static Resource readResource(ClientHttpResponse response, String path, long maxSize) throws IOException {
		HttpStatusCode status = response.getStatusCode();
		if (status.isError()) {
			byte[] body = response.getBody().readNBytes(MAX_ERROR_BODY);
			Charset charset = charset(response.getHeaders());
			throw status.is4xxClientError()
					? HttpClientErrorException.create(status, response.getStatusText(), response.getHeaders(), body,
							charset)
					: HttpServerErrorException.create(status, response.getStatusText(), response.getHeaders(), body,
							charset);
		}
		if (maxSize == 0) {
			return new ConfigServerResource(response.getBody().readAllBytes(), path);
		}
		if (response.getHeaders().getContentLength() > maxSize) {
			throw tooLarge(path, maxSize);
		}
		byte[] content = readAtMost(response.getBody(), maxSize);
		if (content.length > maxSize) {
			throw tooLarge(path, maxSize);
		}
		return new ConfigServerResource(content, path);
	}

	private static byte[] readAtMost(InputStream body, long maxSize) throws IOException {
		int limit = (maxSize >= Integer.MAX_VALUE) ? Integer.MAX_VALUE : (int) maxSize + 1;
		return body.readNBytes(limit);
	}

	private static IllegalStateException tooLarge(String path, long maxSize) {
		return new IllegalStateException("The file [" + path + "] exceeds the maximum size of " + maxSize
				+ " bytes; raise spring.cloud.config.resource-client.max-size to read it");
	}

	private static Charset charset(HttpHeaders headers) {
		MediaType contentType = headers.getContentType();
		return (contentType != null) ? contentType.getCharset() : null;
	}

	/**
	 * A file read from the config server, named after the last segment of its path.
	 */
	private static final class ConfigServerResource extends ByteArrayResource {

		private final String path;

		ConfigServerResource(byte[] content, String path) {
			super(content, "config server file [" + path + "]");
			this.path = path;
		}

		@Override
		public String getFilename() {
			return StringUtils.getFilename(this.path);
		}

	}

}
