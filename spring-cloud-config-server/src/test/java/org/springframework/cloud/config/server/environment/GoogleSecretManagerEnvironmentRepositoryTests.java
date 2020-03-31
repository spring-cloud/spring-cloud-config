/*
 * Copyright 2016-2019 the original author or authors.
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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;

import org.springframework.cloud.config.server.environment.secretManager.GoogleSecretManagerAccessStrategyFactory;
import org.springframework.cloud.config.server.environment.secretManager.GoogleSecretManagerV1AccessStrategy;

import static org.assertj.core.api.Assertions.assertThat;

public class GoogleSecretManagerEnvironmentRepositoryTests {

	private ObjectMapper objectMapper;

	@Before
	public void init() {
		this.objectMapper = new ObjectMapper();
	}

	@Test
	public void testSupportedStrategy() {
		assertThat(GoogleSecretManagerAccessStrategyFactory
			.forVersion(null, null, 1) instanceof GoogleSecretManagerV1AccessStrategy).isTrue();
	}

	@Test(expected = IllegalArgumentException.class)
	public void testGetUnsupportedStrategy() {
		GoogleSecretManagerAccessStrategyFactory.forVersion(null, null, 0);
	}

//	@Test
//	@SuppressWarnings("unchecked")
//	public void testListSecrets() throws IOException {
//		RestTemplate rest = mock(RestTemplate.class);
//		HttpHeaders headers = new HttpHeaders();
//		headers.set("Metadata-Flavor", "Google");
//		HttpEntity<String> entity = new HttpEntity<String>("parameters",
//			headers);
//		ResponseEntity<String> responseEntity = ResponseEntity.of(Optional.of("test-project"));
//		when(rest
//			.exchange(GoogleSecretManagerEnvironmentProperties.GOOGLE_METADATA_PROJECT_URL, HttpMethod.GET, entity, String.class))
//			.thenReturn(responseEntity);
//		GoogleConfigProvider provider = mock(HttpHeaderGoogleConfigProvider.class);
//		//when(provider.getValue(HttpHeaderGoogleConfigProvider.PROJECT_ID_HEADER)).thenReturn("test-project");
//		//SecretManagerServiceClient client = SecretManagerServiceClient.create();
//		SecretManagerServiceClient mock = PowerMockito.mock(SecretManagerServiceClient.class);
//		SecretManagerServiceClient.ListSecretsPagedResponse response = mock(SecretManagerServiceClient.ListSecretsPagedResponse.class);
//		Secret secret = Secret.newBuilder().setName("projects/111111/secrets/test").build();
//		List<Secret> secrets = new ArrayList<Secret>();
//		secrets.add(secret);
//		when(response.iterateAll()).thenReturn(secrets);
//		//Mockito.doReturn(response).when(spyClient).listSecrets(any(ListSecretsRequest.class));
//		when(mock.listSecrets(any(ListSecretsRequest.class))).thenReturn(response);
//		GoogleSecretManagerV1AccessStrategy strategy = new GoogleSecretManagerV1AccessStrategy(rest, provider, mock);
//		assertThat(strategy.getSecrets().size()).isEqualTo(1);
//		assertThat(strategy.getSecrets().get(0).getName()).isEqualTo("projects/111111/secrets/test");
//	}
//
//	@SuppressWarnings("unchecked")
//	private ObjectProvider<HttpServletRequest> mockHttpRequest() {
//		ObjectProvider<HttpServletRequest> objectProvider = mock(ObjectProvider.class);
//		when(objectProvider.getIfAvailable()).thenReturn(null);
//		return objectProvider;
//	}


}
