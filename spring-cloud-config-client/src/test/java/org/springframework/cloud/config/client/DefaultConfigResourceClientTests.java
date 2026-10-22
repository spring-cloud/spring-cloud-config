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
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Tests for {@link DefaultConfigResourceClient}.
 *
 * @author Sharang Gupta
 */
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
