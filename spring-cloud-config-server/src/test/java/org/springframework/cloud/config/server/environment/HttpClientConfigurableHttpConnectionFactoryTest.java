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

import java.lang.reflect.Field;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.eclipse.jgit.transport.http.HttpConnection;
import org.junit.Before;
import org.junit.Test;

import org.springframework.util.ReflectionUtils;

import static org.assertj.core.api.Assertions.assertThat;

public class HttpClientConfigurableHttpConnectionFactoryTest {

	private HttpClientConfigurableHttpConnectionFactory connectionFactory;

	@Before
	public void setUp() {
		this.connectionFactory = new HttpClientConfigurableHttpConnectionFactory();
	}

	@Test
	public void noConfigAdded() throws Exception {
		HttpConnection actual = this.connectionFactory
				.create(new URL("http://localhost/test.git"));

		assertThat(actual).isNotNull();
	}

	@Test
	public void nullPointerCheckGh1051() throws Exception {
		MultipleJGitEnvironmentProperties properties = new MultipleJGitEnvironmentProperties();
		this.connectionFactory.addConfiguration(properties);
	}

	@Test
	public void matchingUrl() throws Exception {
		String url = "http://localhost/test.git";
		MultipleJGitEnvironmentProperties properties = new MultipleJGitEnvironmentProperties();
		properties.setUri(url);
		this.connectionFactory.addConfiguration(properties);

		HttpConnection actualConnection = this.connectionFactory.create(new URL(url));

		HttpClientBuilder expectedHttpClientBuilder = this.connectionFactory.httpClientBuildersByUri
				.values().stream().findFirst().get();
		HttpClientBuilder actualHttpClientBuilder = getActualHttpClientBuilder(
				actualConnection);
		assertThat(actualHttpClientBuilder).isSameAs(expectedHttpClientBuilder);
	}

	@Test
	public void longerUrl() throws Exception {
		MultipleJGitEnvironmentProperties properties = new MultipleJGitEnvironmentProperties();
		String url = "http://localhost/test.git";
		properties.setUri(url);
		this.connectionFactory.addConfiguration(properties);

		HttpConnection actualConnection = this.connectionFactory
				.create(new URL(url + "/some/path.properties"));

		HttpClientBuilder expectedHttpClientBuilder = this.connectionFactory.httpClientBuildersByUri
				.values().stream().findFirst().get();
		HttpClientBuilder actualHttpClientBuilder = getActualHttpClientBuilder(
				actualConnection);
		assertThat(actualHttpClientBuilder).isSameAs(expectedHttpClientBuilder);
	}

	@Test
	public void urlWithPlaceholders() throws Exception {
		MultipleJGitEnvironmentProperties properties = new MultipleJGitEnvironmentProperties();
		properties.setUri("http://localhost/{placeholder}-test.git");
		this.connectionFactory.addConfiguration(properties);

		HttpConnection actualConnection = this.connectionFactory.create(
				new URL("http://localhost/value-test.git" + "/some/path.properties"));

		HttpClientBuilder expectedHttpClientBuilder = this.connectionFactory.httpClientBuildersByUri
				.values().stream().findFirst().get();
		HttpClientBuilder actualHttpClientBuilder = getActualHttpClientBuilder(
				actualConnection);
		assertThat(actualHttpClientBuilder).isSameAs(expectedHttpClientBuilder);
	}

	@Test
	public void urlWithPlaceholdersAtEnd() throws Exception {
		MultipleJGitEnvironmentProperties properties = new MultipleJGitEnvironmentProperties();
		properties.setUri("https://localhost/v1/repos/pvvts_configs-{application}");
		this.connectionFactory.addConfiguration(properties);

		HttpConnection actualConnection = this.connectionFactory.create(
				new URL("https://localhost/v1/repos/pvvts_configs-applicationPasswords"
						+ "/some/path.properties"));

		HttpClientBuilder expectedHttpClientBuilder = this.connectionFactory.httpClientBuildersByUri
				.values().stream().findFirst().get();
		HttpClientBuilder actualHttpClientBuilder = getActualHttpClientBuilder(
				actualConnection);
		assertThat(actualHttpClientBuilder).isSameAs(expectedHttpClientBuilder);
	}

	@Test
	public void composite_sameHost() throws Exception {
		MultipleJGitEnvironmentProperties properties1 = new MultipleJGitEnvironmentProperties();
		properties1.setUri("http://localhost/test1.git");
		MultipleJGitEnvironmentProperties properties2 = new MultipleJGitEnvironmentProperties();
		properties2.setUri("http://localhost/test2.git");
		this.connectionFactory.addConfiguration(properties1);
		this.connectionFactory.addConfiguration(properties2);

		HttpConnection actualConnection = this.connectionFactory
				.create(new URL(properties1.getUri()));

		HttpClientBuilder expectedHttpClientBuilder = this.connectionFactory.httpClientBuildersByUri
				.get(properties1.getUri());
		HttpClientBuilder actualHttpClientBuilder = getActualHttpClientBuilder(
				actualConnection);
		assertThat(actualHttpClientBuilder).isSameAs(expectedHttpClientBuilder);
	}

	@Test
	public void composite_differentHost() throws Exception {
		MultipleJGitEnvironmentProperties properties1 = new MultipleJGitEnvironmentProperties();
		properties1.setUri("http://localhost1/test.git");
		MultipleJGitEnvironmentProperties properties2 = new MultipleJGitEnvironmentProperties();
		properties2.setUri("http://localhost2/test.git");
		this.connectionFactory.addConfiguration(properties1);
		this.connectionFactory.addConfiguration(properties2);

		HttpConnection actualConnection = this.connectionFactory
				.create(new URL(properties1.getUri()));

		HttpClientBuilder expectedHttpClientBuilder = this.connectionFactory.httpClientBuildersByUri
				.get(properties1.getUri());
		HttpClientBuilder actualHttpClientBuilder = getActualHttpClientBuilder(
				actualConnection);
		assertThat(actualHttpClientBuilder).isSameAs(expectedHttpClientBuilder);
	}

	@Test
	public void composite_urlsWithPlaceholders() throws Exception {
		MultipleJGitEnvironmentProperties properties1 = new MultipleJGitEnvironmentProperties();
		properties1.setUri("http://localhost/path/{placeholder3}/more/test.git");
		MultipleJGitEnvironmentProperties properties2 = new MultipleJGitEnvironmentProperties();
		properties2
				.setUri("http://localhost/{placeholder1}/path/{placeholder2}-test.git");
		this.connectionFactory.addConfiguration(properties1);
		this.connectionFactory.addConfiguration(properties2);

		HttpConnection actualConnection = this.connectionFactory
				.create(new URL(properties2.getUri().replace("{placeholder1}", "value1")
						.replace("{placeholder2}", "value2")));

		HttpClientBuilder expectedHttpClientBuilder = this.connectionFactory.httpClientBuildersByUri
				.get(properties2.getUri());
		HttpClientBuilder actualHttpClientBuilder = getActualHttpClientBuilder(
				actualConnection);
		assertThat(actualHttpClientBuilder).isSameAs(expectedHttpClientBuilder);
	}

	@Test
	public void composite_urlsWithPlaceholders_identicalTemplatesWontBeResolvedProperly()
			throws Exception {
		MultipleJGitEnvironmentProperties properties1 = new MultipleJGitEnvironmentProperties();
		properties1
				.setUri("http://localhost/{placeholder3}/path/{placeholder4}-test.git");
		MultipleJGitEnvironmentProperties properties2 = new MultipleJGitEnvironmentProperties();
		properties2
				.setUri("http://localhost/{placeholder1}/path/{placeholder2}-test.git");
		this.connectionFactory.addConfiguration(properties1);
		this.connectionFactory.addConfiguration(properties2);

		HttpConnection actualConnection = this.connectionFactory
				.create(new URL(properties2.getUri().replace("{placeholder1}", "value1")
						.replace("{placeholder2}", "value2")));

		HttpClientBuilder expectedHttpClientBuilder = this.connectionFactory.httpClientBuildersByUri
				.get(properties2.getUri());
		HttpClientBuilder actualHttpClientBuilder = getActualHttpClientBuilder(
				actualConnection);
		assertThat(actualHttpClientBuilder).isNotSameAs(expectedHttpClientBuilder);
	}

	@Test
	public void composite_longerUrlsWithPlaceholders() throws Exception {
		MultipleJGitEnvironmentProperties properties1 = new MultipleJGitEnvironmentProperties();
		properties1
				.setUri("http://localhost/path/{placeholder3}/{placeholder4}-test.git");
		MultipleJGitEnvironmentProperties properties2 = new MultipleJGitEnvironmentProperties();
		properties2
				.setUri("http://localhost/{placeholder1}/path/{placeholder2}-test.git");
		this.connectionFactory.addConfiguration(properties1);
		this.connectionFactory.addConfiguration(properties2);

		HttpConnection actualConnection = this.connectionFactory
				.create(new URL(properties2.getUri().replace("{placeholder1}", "value1")
						.replace("{placeholder2}", "value2") + "/some/path.properties"));

		HttpClientBuilder expectedHttpClientBuilder = this.connectionFactory.httpClientBuildersByUri
				.get(properties2.getUri());
		HttpClientBuilder actualHttpClientBuilder = getActualHttpClientBuilder(
				actualConnection);
		assertThat(actualHttpClientBuilder).isSameAs(expectedHttpClientBuilder);
	}

	private HttpClient getActualHttpClient(HttpConnection actualConnection) {
		Field clientField = ReflectionUtils.findField(actualConnection.getClass(),
				"client");
		ReflectionUtils.makeAccessible(clientField);
		return (HttpClient) ReflectionUtils.getField(clientField, actualConnection);
	}

	private HttpClientBuilder getActualHttpClientBuilder(
			HttpConnection actualConnection) {
		HttpClient actualHttpClient = getActualHttpClient(actualConnection);
		Field closeablesField = ReflectionUtils.findField(actualHttpClient.getClass(),
				"closeables");
		ReflectionUtils.makeAccessible(closeablesField);
		List<?> closables = (List<?>) ReflectionUtils.getField(closeablesField,
				actualHttpClient);
		return closables.stream().map(o -> {
			Field builderField = Arrays.stream(o.getClass().getDeclaredFields()).filter(
					field -> HttpClientBuilder.class.isAssignableFrom(field.getType()))
					.findFirst().orElse(null);
			if (builderField != null) {
				ReflectionUtils.makeAccessible(builderField);
				return ReflectionUtils.getField(builderField, o);
			}
			return null;
		}).filter(Objects::nonNull).map(HttpClientBuilder.class::cast).findFirst().get();
	}

}
