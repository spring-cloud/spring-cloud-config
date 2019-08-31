/*
 * Copyright 2018-2019 the original author or authors.
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

package org.springframework.cloud.config.server.environment;

import java.io.IOException;
import java.net.Proxy;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.eclipse.jgit.transport.http.HttpConnection;
import org.eclipse.jgit.transport.http.apache.HttpClientConnection;

import org.springframework.cloud.config.server.support.HttpClientSupport;
import org.springframework.util.StringUtils;

import static java.util.stream.Collectors.toMap;

/**
 * @author Dylan Roberts
 */
public class HttpClientConfigurableHttpConnectionFactory
		implements ConfigurableHttpConnectionFactory {

	private static final String PLACEHOLDER_PATTERN = "\\{(\\w+)}";

	Log log = LogFactory.getLog(getClass());

	Map<String, HttpClientBuilder> httpClientBuildersByUri = new LinkedHashMap<>();

	@Override
	public void addConfiguration(MultipleJGitEnvironmentProperties environmentProperties)
			throws GeneralSecurityException {
		addHttpClient(environmentProperties);
		for (JGitEnvironmentProperties repo : environmentProperties.getRepos().values()) {
			addHttpClient(repo);
		}
	}

	@Override
	public HttpConnection create(URL url) throws IOException {
		return create(url, null);
	}

	@Override
	public HttpConnection create(URL url, Proxy proxy) throws IOException {
		return new HttpClientConnection(url.toString(), null,
				lookupHttpClientBuilder(url).build());
	}

	private void addHttpClient(JGitEnvironmentProperties properties)
			throws GeneralSecurityException {
		if (properties.getUri() != null && properties.getUri().startsWith("http")) {
			this.httpClientBuildersByUri.put(properties.getUri(),
					HttpClientSupport.builder(properties));
		}
	}

	private HttpClientBuilder lookupHttpClientBuilder(final URL url) {
		Map<String, HttpClientBuilder> builderMap = this.httpClientBuildersByUri
				.entrySet().stream().filter(entry -> {
					String key = entry.getKey();
					String spec = getUrlWithPlaceholders(url, key);
					if (spec.equals(key)) {
						return true;
					}
					int index = spec.lastIndexOf("/");
					while (index != -1) {
						spec = spec.substring(0, index);
						if (spec.equals(key)) {
							return true;
						}
						index = spec.lastIndexOf("/");
					}
					return false;
				}).collect(toMap(Map.Entry::getKey, Map.Entry::getValue));

		if (builderMap.isEmpty()) {
			this.log.warn(String.format("No custom http config found for URL: %s", url));
			return HttpClients.custom();
		}
		if (builderMap.size() > 1) {
			this.log.error(String.format(
					"More than one git repo URL template matched URL:"
							+ " %s, proxy and skipSslValidation config won't be applied. Matched templates: %s",
					url, builderMap.keySet().stream().collect(Collectors.joining(", "))));
			return HttpClients.custom();
		}
		return new ArrayList<>(builderMap.values()).get(0);
	}

	private String getUrlWithPlaceholders(URL url, String key) {
		String spec = url.toString();
		String[] tokens = key.split(PLACEHOLDER_PATTERN);
		// if token[0] equals url then there was no placeholder in the the url, so
		// matching needed
		if (tokens.length >= 1 && !tokens[0].equals(url.toString())) {
			List<String> placeholders = getPlaceholders(key);
			List<String> values = getValues(spec, tokens);
			if (placeholders.size() == values.size()) {
				for (int i = 0; i < values.size(); i++) {
					spec = spec.replace(values.get(i),
							String.format("{%s}", placeholders.get(i)));
				}
			}
		}
		return spec;
	}

	private List<String> getValues(String spec, String[] tokens) {
		List<String> values = new LinkedList<>();
		for (String token : tokens) {
			String[] valueTokens = spec.split(token);
			if (!StringUtils.isEmpty(valueTokens[0])) {
				values.add(valueTokens[0]);
			}
			if (valueTokens.length > 1) {
				spec = valueTokens[1];
			}
		}
		if (tokens.length == 1 && !StringUtils.isEmpty(spec)) {
			values.add(spec);
		}
		return values;
	}

	private List<String> getPlaceholders(String key) {
		Pattern pattern = Pattern.compile(PLACEHOLDER_PATTERN);
		Matcher matcher = pattern.matcher(key);
		List<String> placeholders = new LinkedList<>();
		while (matcher.find()) {
			placeholders.add(matcher.group(1));
		}
		return placeholders;
	}

}
