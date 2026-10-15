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

import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

/**
 * Request factories that answer every config client request with a fixed response.
 *
 * @author Sharang Gupta
 */
final class MockConfigServerResponses {

	private MockConfigServerResponses() {
	}

	/**
	 * Answers with the HTML login page that a secured config server serves when the
	 * credentials of the config client are missing or wrong.
	 * @return the request factory
	 */
	static ClientHttpRequestFactory loginPage() {
		return respondingWith(MediaType.TEXT_HTML, "<html><body>Please sign in</body></html>");
	}

	/**
	 * Answers with a {@code 200 OK} response that has the given content type and body.
	 * @param contentType the content type of the response
	 * @param body the body of the response
	 * @return the request factory
	 */
	static ClientHttpRequestFactory respondingWith(MediaType contentType, String body) {
		MockClientHttpResponse response = new MockClientHttpResponse(body.getBytes(StandardCharsets.UTF_8),
				HttpStatus.OK);
		response.getHeaders().setContentType(contentType);
		MockClientHttpRequest request = new MockClientHttpRequest();
		request.setResponse(response);
		return (uri, httpMethod) -> request;
	}

}
