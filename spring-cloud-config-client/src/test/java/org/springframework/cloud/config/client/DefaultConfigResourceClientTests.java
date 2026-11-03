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
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.cloud.config.client.ConfigClientProperties.MultipleUriStrategy;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.unit.DataSize;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Tests for {@link DefaultConfigResourceClient}.
 *
 * @author Sharang Gupta
 */
@ExtendWith(OutputCaptureExtension.class)
public class DefaultConfigResourceClientTests {

	private final RestTemplate restTemplate = new RestTemplate();

	private final MockRestServiceServer server = MockRestServiceServer.bindTo(this.restTemplate).build();

	private final ConfigClientProperties properties = new ConfigClientProperties(new MockEnvironment());

	private final ConfigResourceClient client = new DefaultConfigResourceClient(RestClient.create(this.restTemplate),
			this.properties);

	@BeforeEach
	void setUp() {
		this.properties.setName("app");
		this.properties.setProfile("dev");
		this.properties.setLabel("main");
		this.properties.setUri(new String[] { "http://configserver:8888" });
	}

	@Test
	void plainTextResourceUsesTheConfiguredProfileAndLabel() throws IOException {
		this.server.expect(requestTo("http://configserver:8888/app/dev/main/nginx.conf"))
			.andExpect(method(HttpMethod.GET))
			.andExpect(request -> assertThat(request.getHeaders().getAccept())
				.doesNotContain(MediaType.APPLICATION_OCTET_STREAM))
			.andRespond(withSuccess("server { }", MediaType.TEXT_PLAIN));

		Resource resource = this.client.getPlainTextResource("nginx.conf");

		assertThat(resource.getContentAsString(StandardCharsets.UTF_8)).isEqualTo("server { }");
		this.server.verify();
	}

	@Test
	void binaryResourceAcceptsOctetStream() throws IOException {
		this.server.expect(requestTo("http://configserver:8888/app/dev/main/images/logo.png"))
			.andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_OCTET_STREAM_VALUE))
			.andRespond(withSuccess(new byte[] { 1, 2, 3 }, MediaType.APPLICATION_OCTET_STREAM));

		Resource resource = this.client.getBinaryResource("images/logo.png");

		assertThat(resource.getContentAsByteArray()).containsExactly(1, 2, 3);
		this.server.verify();
	}

	@Test
	void profileAndLabelOverrideTheConfiguredOnes() throws IOException {
		this.server.expect(requestTo("http://configserver:8888/app/test,cloud/release/nginx.conf"))
			.andRespond(withSuccess("release", MediaType.TEXT_PLAIN));

		Resource resource = this.client.getPlainTextResource("test,cloud", "release", "nginx.conf");

		assertThat(resource.getContentAsString(StandardCharsets.UTF_8)).isEqualTo("release");
		this.server.verify();
	}

	@Test
	void defaultProfileIsUsedWhenNoneIsConfigured() throws IOException {
		this.properties.setProfile("");
		this.server.expect(requestTo("http://configserver:8888/app/default/main/nginx.conf"))
			.andRespond(withSuccess("default", MediaType.TEXT_PLAIN));

		Resource resource = this.client.getPlainTextResource("nginx.conf");

		assertThat(resource.getContentAsString(StandardCharsets.UTF_8)).isEqualTo("default");
		this.server.verify();
	}

	@Test
	void serverDefaultLabelIsUsedWhenNoneIsConfigured() throws IOException {
		this.properties.setLabel(null);
		this.server.expect(requestTo("http://configserver:8888/app/dev/nginx.conf?useDefaultLabel"))
			.andRespond(withSuccess("default label", MediaType.TEXT_PLAIN));

		Resource resource = this.client.getPlainTextResource("nginx.conf");

		assertThat(resource.getContentAsString(StandardCharsets.UTF_8)).isEqualTo("default label");
		this.server.verify();
	}

	@Test
	void slashInLabelIsEscaped() throws IOException {
		this.server.expect(requestTo("http://configserver:8888/app/dev/feature(_)x/nginx.conf"))
			.andRespond(withSuccess("feature", MediaType.TEXT_PLAIN));

		Resource resource = this.client.getPlainTextResource(null, "feature/x", "nginx.conf");

		assertThat(resource.getContentAsString(StandardCharsets.UTF_8)).isEqualTo("feature");
		this.server.verify();
	}

	@Test
	void tokenIsSent() throws IOException {
		this.properties.setToken("secret");
		this.server.expect(requestTo("http://configserver:8888/app/dev/main/nginx.conf"))
			.andExpect(header(ConfigClientProperties.TOKEN_HEADER, "secret"))
			.andRespond(withSuccess("server { }", MediaType.TEXT_PLAIN));

		this.client.getPlainTextResource("nginx.conf");

		this.server.verify();
	}

	@Test
	void basicAuthenticationComesFromTheUsernameAndPassword() {
		this.properties.setUsername("alice");
		this.properties.setPassword("secret");
		this.server.expect(requestTo("http://configserver:8888/app/dev/main/nginx.conf"))
			.andExpect(header(HttpHeaders.AUTHORIZATION, "Basic YWxpY2U6c2VjcmV0"))
			.andRespond(withSuccess("server { }", MediaType.TEXT_PLAIN));

		this.client.getPlainTextResource("nginx.conf");

		this.server.verify();
	}

	@Test
	void credentialsInTheUriAreUsed() {
		this.properties.setUri(new String[] { "http://bob:pw@configserver:8888" });
		this.server.expect(requestTo("http://configserver:8888/app/dev/main/nginx.conf"))
			.andExpect(header(HttpHeaders.AUTHORIZATION, "Basic Ym9iOnB3"))
			.andRespond(withSuccess("server { }", MediaType.TEXT_PLAIN));

		this.client.getPlainTextResource("nginx.conf");

		this.server.verify();
	}

	@Test
	void configuredAuthorizationHeaderIsUsed() {
		this.properties.setHeaders(Map.of(ConfigClientProperties.AUTHORIZATION, "Bearer token"));
		this.server.expect(requestTo("http://configserver:8888/app/dev/main/nginx.conf"))
			.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer token"))
			.andRespond(withSuccess("server { }", MediaType.TEXT_PLAIN));

		this.client.getPlainTextResource("nginx.conf");

		this.server.verify();
	}

	@Test
	void passwordAndAuthorizationHeaderTogetherAreRejected() {
		this.properties.setUsername("alice");
		this.properties.setPassword("secret");
		this.properties.setHeaders(Map.of(ConfigClientProperties.AUTHORIZATION, "Bearer token"));

		assertThatThrownBy(() -> this.client.getPlainTextResource("nginx.conf"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("'password' or 'authorization'");
	}

	@Test
	void nextUriIsTriedWhenOneCannotBeReached() throws IOException {
		this.properties.setUri(new String[] { "http://first:8888", "http://second:8888" });
		this.server.expect(requestTo("http://first:8888/app/dev/main/nginx.conf"))
			.andRespond(withException(new IOException("connection refused")));
		this.server.expect(requestTo("http://second:8888/app/dev/main/nginx.conf"))
			.andRespond(withSuccess("second", MediaType.TEXT_PLAIN));

		Resource resource = this.client.getPlainTextResource("nginx.conf");

		assertThat(resource.getContentAsString(StandardCharsets.UTF_8)).isEqualTo("second");
		this.server.verify();
	}

	@Test
	void lastUnreachableUriFails() {
		this.properties.setUri(new String[] { "http://first:8888", "http://second:8888" });
		this.server.expect(requestTo("http://first:8888/app/dev/main/nginx.conf"))
			.andRespond(withException(new IOException("connection refused")));
		this.server.expect(requestTo("http://second:8888/app/dev/main/nginx.conf"))
			.andRespond(withException(new IOException("connection refused")));

		assertThatThrownBy(() -> this.client.getPlainTextResource("nginx.conf"))
			.isInstanceOf(ResourceAccessException.class);
		this.server.verify();
	}

	@Test
	void nextUriIsTriedOnAnErrorWithTheAlwaysStrategy() throws IOException {
		this.properties.setUri(new String[] { "http://first:8888", "http://second:8888" });
		this.server.expect(requestTo("http://first:8888/app/dev/main/nginx.conf")).andRespond(withServerError());
		this.server.expect(requestTo("http://second:8888/app/dev/main/nginx.conf"))
			.andRespond(withSuccess("second", MediaType.TEXT_PLAIN));

		Resource resource = this.client.getPlainTextResource("nginx.conf");

		assertThat(resource.getContentAsString(StandardCharsets.UTF_8)).isEqualTo("second");
		this.server.verify();
	}

	@Test
	void errorIsReportedWithTheConnectionTimeoutOnlyStrategy() {
		this.properties.setUri(new String[] { "http://first:8888", "http://second:8888" });
		this.properties.setMultipleUriStrategy(MultipleUriStrategy.CONNECTION_TIMEOUT_ONLY);
		this.server.expect(requestTo("http://first:8888/app/dev/main/nginx.conf")).andRespond(withServerError());

		assertThatThrownBy(() -> this.client.getPlainTextResource("nginx.conf"))
			.isInstanceOf(HttpServerErrorException.class);
		this.server.verify();
	}

	@Test
	void labelsAreTriedInOrder() throws IOException {
		this.properties.setLabel("feature, main");
		this.server.expect(requestTo("http://configserver:8888/app/dev/feature/nginx.conf"))
			.andRespond(withStatus(HttpStatus.NOT_FOUND));
		this.server.expect(requestTo("http://configserver:8888/app/dev/main/nginx.conf"))
			.andRespond(withSuccess("main", MediaType.TEXT_PLAIN));

		Resource resource = this.client.getPlainTextResource("nginx.conf");

		assertThat(resource.getContentAsString(StandardCharsets.UTF_8)).isEqualTo("main");
		this.server.verify();
	}

	@Test
	void fileMissingInEveryLabelIsReportedAsNotFound() {
		this.server.expect(requestTo("http://configserver:8888/app/dev/feature/nginx.conf"))
			.andRespond(withStatus(HttpStatus.NOT_FOUND));
		this.server.expect(requestTo("http://configserver:8888/app/dev/main/nginx.conf"))
			.andRespond(withStatus(HttpStatus.NOT_FOUND));

		assertThatThrownBy(() -> this.client.getPlainTextResource(null, "feature,main", "nginx.conf"))
			.isInstanceOf(HttpClientErrorException.NotFound.class);
		this.server.verify();
	}

	@Test
	void emptyLabelsInAListAreSkipped() throws IOException {
		this.server.expect(requestTo("http://configserver:8888/app/dev/feature/nginx.conf"))
			.andRespond(withStatus(HttpStatus.NOT_FOUND));
		this.server.expect(requestTo("http://configserver:8888/app/dev/main/nginx.conf"))
			.andRespond(withSuccess("server {}", MediaType.TEXT_PLAIN));

		assertThat(this.client.getPlainTextResource(null, "feature,,main", "nginx.conf")
			.getContentAsString(StandardCharsets.UTF_8)).isEqualTo("server {}");
		this.server.verify();
	}

	@Test
	void listOfOnlyEmptyLabelsUsesTheServerDefaultLabel() throws IOException {
		this.server.expect(requestTo("http://configserver:8888/app/dev/nginx.conf?useDefaultLabel"))
			.andRespond(withSuccess("server {}", MediaType.TEXT_PLAIN));

		assertThat(this.client.getPlainTextResource(null, ",", "nginx.conf").getContentAsString(StandardCharsets.UTF_8))
			.isEqualTo("server {}");
		this.server.verify();
	}

	@Test
	void skippedUriAndLabelAreLogged(CapturedOutput output) throws IOException {
		this.properties.setUri(new String[] { "http://down:8888", "http://configserver:8888" });
		this.server.expect(requestTo("http://down:8888/app/dev/main/nginx.conf"))
			.andRespond(withException(new SocketTimeoutException("timed out")));
		this.server.expect(requestTo("http://configserver:8888/app/dev/main/nginx.conf"))
			.andRespond(withSuccess("server {}", MediaType.TEXT_PLAIN));

		this.client.getPlainTextResource("nginx.conf");

		assertThat(output).contains("with label [main] from config server at http://down:8888");
	}

	@Test
	void uriSkippedOnAnErrorWithTheAlwaysStrategyIsLogged(CapturedOutput output) throws IOException {
		this.properties.setUri(new String[] { "http://failing:8888", "http://configserver:8888" });
		this.properties.setMultipleUriStrategy(MultipleUriStrategy.ALWAYS);
		this.server.expect(requestTo("http://failing:8888/app/dev/main/nginx.conf")).andRespond(withServerError());
		this.server.expect(requestTo("http://configserver:8888/app/dev/main/nginx.conf"))
			.andRespond(withSuccess("server {}", MediaType.TEXT_PLAIN));

		this.client.getPlainTextResource("nginx.conf");

		assertThat(output).contains("with label [main] from config server at http://failing:8888")
			.contains("500 INTERNAL_SERVER_ERROR");
	}

	@Test
	void negativeLimitIsRejected() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> this.properties.getResourceClient().setMaxSize(DataSize.ofBytes(-1)))
			.withMessage("maxSize must not be negative");
	}

	@Test
	void zeroLimitDisablesTheLimit() throws IOException {
		this.properties.getResourceClient().setMaxSize(DataSize.ofBytes(0));
		HttpHeaders headers = new HttpHeaders();
		headers.setContentLength(5);
		this.server.expect(requestTo("http://configserver:8888/app/dev/main/nginx.conf"))
			.andRespond(withSuccess("12345", MediaType.TEXT_PLAIN).headers(headers));

		assertThat(this.client.getPlainTextResource("nginx.conf").contentLength()).isEqualTo(5);
	}

	@Test
	void fileLargerThanTheLimitIsRejected() {
		this.properties.getResourceClient().setMaxSize(DataSize.ofBytes(4));
		this.server.expect(requestTo("http://configserver:8888/app/dev/main/nginx.conf"))
			.andRespond(withSuccess("12345", MediaType.TEXT_PLAIN));

		assertThatThrownBy(() -> this.client.getPlainTextResource("nginx.conf"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("exceeds the maximum size of 4 bytes");
	}

	@Test
	void declaredContentLengthAboveTheLimitIsRejected() {
		this.properties.getResourceClient().setMaxSize(DataSize.ofBytes(4));
		HttpHeaders headers = new HttpHeaders();
		headers.setContentLength(10);
		this.server.expect(requestTo("http://configserver:8888/app/dev/main/nginx.conf"))
			.andRespond(withSuccess("12", MediaType.TEXT_PLAIN).headers(headers));

		assertThatThrownBy(() -> this.client.getPlainTextResource("nginx.conf"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("exceeds the maximum size");
	}

	@Test
	void fileWithinTheLimitIsReadAndNamedAfterItsPath() throws IOException {
		this.properties.getResourceClient().setMaxSize(DataSize.ofBytes(5));
		this.server.expect(requestTo("http://configserver:8888/app/dev/main/conf/nginx.conf"))
			.andRespond(withSuccess("12345", MediaType.TEXT_PLAIN));

		Resource resource = this.client.getPlainTextResource("conf/nginx.conf");

		assertThat(resource.getContentAsString(StandardCharsets.UTF_8)).isEqualTo("12345");
		assertThat(resource.getFilename()).isEqualTo("nginx.conf");
	}

	@Test
	void missingFileIsReportedAsClientError() {
		this.server.expect(requestTo("http://configserver:8888/app/dev/main/missing.conf"))
			.andRespond(withStatus(HttpStatus.NOT_FOUND));

		assertThatThrownBy(() -> this.client.getPlainTextResource("missing.conf"))
			.isInstanceOf(HttpClientErrorException.class);
	}

	@Test
	void missingApplicationNameIsRejected() {
		this.properties.setName("");

		assertThatIllegalArgumentException().isThrownBy(() -> this.client.getPlainTextResource("nginx.conf"))
			.withMessageContaining("application name");
	}

	@Test
	void missingConfigServerUriIsRejected() {
		this.properties.setUri(new String[] { "" });

		assertThatIllegalArgumentException().isThrownBy(() -> this.client.getBinaryResource("nginx.conf"))
			.withMessageContaining("URI");
	}

	@Test
	void missingPathIsRejected() {
		assertThatIllegalArgumentException().isThrownBy(() -> this.client.getPlainTextResource(""))
			.withMessageContaining("path");
	}

}
