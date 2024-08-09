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

import java.util.HashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.credhub.core.CredHubOperations;
import org.springframework.credhub.core.credential.CredHubCredentialOperations;
import org.springframework.credhub.support.CredentialDetails;
import org.springframework.credhub.support.CredentialSummary;
import org.springframework.credhub.support.CredentialType;
import org.springframework.credhub.support.SimpleCredentialName;
import org.springframework.credhub.support.json.JsonCredential;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * @author Alberto C. Ríos
 */
public class CredhubEnvironmentRepositoryTests {

	private CredhubEnvironmentRepository credhubEnvironmentRepository;

	private CredHubCredentialOperations credhubCredentialOperations;

	@BeforeEach
	public void setUp() {
		CredHubOperations credhubOperations = Mockito.mock(CredHubOperations.class);
		this.credhubCredentialOperations = Mockito.mock(CredHubCredentialOperations.class);
		when(credhubOperations.credentials()).thenReturn(this.credhubCredentialOperations);

		this.credhubEnvironmentRepository = new CredhubEnvironmentRepository(credhubOperations);
	}

	@Test
	public void shouldDisplayEmptyPropertiesWhenNoPathFound() {
		when(this.credhubCredentialOperations.findByPath("/myApp/prod/myLabel")).thenReturn(emptyList());

		Environment environment = this.credhubEnvironmentRepository.findOne("myApp", "prod", "myLabel");

		assertThat(environment.getName()).isEqualTo("myApp");
		assertThat(environment.getProfiles()).containsExactly("prod", "default");
		assertThat(environment.getLabel()).isEqualTo("myLabel");

		assertThat(environment.getPropertySources()).isEmpty();
	}

	@Test
	public void shouldRetrieveDefaultsWhenNoLabelNorProfileProvided() {
		stubCredentials("/myApp/default/master", "toggles", "key1", "value1");

		Environment environment = this.credhubEnvironmentRepository.findOne("myApp", null, null);

		assertThat(environment.getName()).isEqualTo("myApp");
		assertThat(environment.getProfiles()).containsExactly("default");
		assertThat(environment.getLabel()).isEqualTo("master");

		assertThat(environment.getPropertySources()).hasSize(1);

		assertThat(environment.getPropertySources().get(0).getName()).isEqualTo("credhub-myApp-default-master");
		assertThat(environment.getPropertySources().get(0).getSource()).isEqualTo(singletonMap("key1", "value1"));
	}

	@Test
	public void shouldRetrieveGivenProfileAndLabel() {
		stubCredentials("/myApp/prod/myLabel", "toggles", "key1", "value1");

		Environment environment = this.credhubEnvironmentRepository.findOne("myApp", "prod", "myLabel");

		assertThat(environment.getName()).isEqualTo("myApp");
		assertThat(environment.getProfiles()).containsExactly("prod", "default");
		assertThat(environment.getLabel()).isEqualTo("myLabel");

		assertThat(environment.getPropertySources()).hasSize(1);

		assertThat(environment.getPropertySources().get(0).getName()).isEqualTo("credhub-myApp-prod-myLabel");
		assertThat(environment.getPropertySources().get(0).getSource()).isEqualTo(singletonMap("key1", "value1"));
	}

	@Test
	public void shouldRetrieveGivenMultipleProfiles() {
		stubCredentials("/myApp/prod/myLabel", "toggles", "key1", "value1");
		stubCredentials("/myApp/cloud/myLabel", "abs", "key2", "value2");

		Environment environment = this.credhubEnvironmentRepository.findOne("myApp", "prod,cloud", "myLabel");

		assertThat(environment.getName()).isEqualTo("myApp");
		assertThat(environment.getProfiles()).containsExactly("prod", "cloud", "default");
		assertThat(environment.getLabel()).isEqualTo("myLabel");

		assertThat(environment.getPropertySources()).hasSize(2);

		assertThat(environment.getPropertySources().get(0).getName()).isEqualTo("credhub-myApp-prod-myLabel");
		assertThat(environment.getPropertySources().get(0).getSource()).isEqualTo(singletonMap("key1", "value1"));

		assertThat(environment.getPropertySources().get(1).getName()).isEqualTo("credhub-myApp-cloud-myLabel");
		assertThat(environment.getPropertySources().get(1).getSource()).isEqualTo(singletonMap("key2", "value2"));
	}

	@Test
	public void shouldRetrieveGivenMultipleApplicationNames() {
		stubCredentials("/app1/default/myLabel", "toggles", "key1", "value1");
		stubCredentials("/app2/default/myLabel", "abs", "key2", "value2");

		Environment environment = this.credhubEnvironmentRepository.findOne("app1,app2", null, "myLabel");

		assertThat(environment.getName()).isEqualTo("app1,app2");
		assertThat(environment.getProfiles()).containsExactly("default");
		assertThat(environment.getLabel()).isEqualTo("myLabel");

		assertThat(environment.getPropertySources()).hasSize(2);

		assertThat(environment.getPropertySources().get(0).getName()).isEqualTo("credhub-app1-default-myLabel");
		assertThat(environment.getPropertySources().get(0).getSource()).isEqualTo(singletonMap("key1", "value1"));

		assertThat(environment.getPropertySources().get(1).getName()).isEqualTo("credhub-app2-default-myLabel");
		assertThat(environment.getPropertySources().get(1).getSource()).isEqualTo(singletonMap("key2", "value2"));
	}

	@Test
	public void shouldMergeWhenMoreThanOneCredentialsFound() {
		String expectedPath = "/myApp/prod/myLabel";

		SimpleCredentialName togglesCredentialName = new SimpleCredentialName(expectedPath + "/toggles");
		SimpleCredentialName absCredentialName = new SimpleCredentialName(expectedPath + "/abs");
		when(this.credhubCredentialOperations.findByPath(expectedPath))
			.thenReturn(asList(new CredentialSummary(togglesCredentialName), new CredentialSummary(absCredentialName)));
		JsonCredential credentials = new JsonCredential();
		credentials.put("key1", "value1");
		when(this.credhubCredentialOperations.getByName(togglesCredentialName, JsonCredential.class))
			.thenReturn(new CredentialDetails<>("id1", togglesCredentialName, CredentialType.JSON, credentials));

		JsonCredential otherCredentials = new JsonCredential();
		otherCredentials.put("key2", "value2");
		when(this.credhubCredentialOperations.getByName(absCredentialName, JsonCredential.class))
			.thenReturn(new CredentialDetails<>("id2", absCredentialName, CredentialType.JSON, otherCredentials));

		Environment environment = this.credhubEnvironmentRepository.findOne("myApp", "prod", "myLabel");

		assertThat(environment.getName()).isEqualTo("myApp");
		assertThat(environment.getProfiles()).containsExactly("prod", "default");
		assertThat(environment.getLabel()).isEqualTo("myLabel");

		assertThat(environment.getPropertySources()).hasSize(1);
		assertThat(environment.getPropertySources().get(0).getName()).isEqualTo("credhub-myApp-prod-myLabel");
		HashMap<Object, Object> expectedValues = new HashMap<>();
		expectedValues.put("key1", "value1");
		expectedValues.put("key2", "value2");
		assertThat(environment.getPropertySources().get(0).getSource()).isEqualTo(expectedValues);
	}

	@Test
	public void shouldIncludeDefaultApplicationWhenOtherProvided() {
		stubCredentials("/app1/prod/myLabel", "toggles", "app1-prod", "value1");
		stubCredentials("/app2/prod/myLabel", "toggles", "app2-prod", "value2");
		stubCredentials("/application/prod/myLabel", "abs", "application-prod", "value3");
		stubCredentials("/app1/default/myLabel", "toggles", "app1-default", "value4");
		stubCredentials("/app2/default/myLabel", "toggles", "app2-default", "value5");
		stubCredentials("/application/default/myLabel", "abs", "application-default", "value6");

		Environment environment = this.credhubEnvironmentRepository.findOne("app1,app2", "prod", "myLabel");

		assertThat(environment.getName()).isEqualTo("app1,app2");
		assertThat(environment.getProfiles()).containsExactly("prod", "default");
		assertThat(environment.getLabel()).isEqualTo("myLabel");

		assertThat(environment.getPropertySources()).hasSize(6);

		assertThat(environment.getPropertySources().get(0).getName()).isEqualTo("credhub-app1-prod-myLabel");
		assertThat(environment.getPropertySources().get(0).getSource()).isEqualTo(singletonMap("app1-prod", "value1"));

		assertThat(environment.getPropertySources().get(1).getName()).isEqualTo("credhub-app2-prod-myLabel");
		assertThat(environment.getPropertySources().get(1).getSource()).isEqualTo(singletonMap("app2-prod", "value2"));

		assertThat(environment.getPropertySources().get(2).getName()).isEqualTo("credhub-application-prod-myLabel");
		assertThat(environment.getPropertySources().get(2).getSource())
			.isEqualTo(singletonMap("application-prod", "value3"));

		assertThat(environment.getPropertySources().get(3).getName()).isEqualTo("credhub-app1-default-myLabel");
		assertThat(environment.getPropertySources().get(3).getSource())
			.isEqualTo(singletonMap("app1-default", "value4"));

		assertThat(environment.getPropertySources().get(4).getName()).isEqualTo("credhub-app2-default-myLabel");
		assertThat(environment.getPropertySources().get(4).getSource())
			.isEqualTo(singletonMap("app2-default", "value5"));

		assertThat(environment.getPropertySources().get(5).getName()).isEqualTo("credhub-application-default-myLabel");
		assertThat(environment.getPropertySources().get(5).getSource())
			.isEqualTo(singletonMap("application-default", "value6"));
	}

	@Test
	public void shouldIncludeDefaultProfileWhenOtherProvided() {
		stubCredentials("/myApp/dev/myLabel", "toggles", "myApp-dev", "value1");
		stubCredentials("/application/dev/myLabel", "abs", "application-dev", "value2");
		stubCredentials("/myApp/prod/myLabel", "toggles", "myApp-prod", "value3");
		stubCredentials("/application/prod/myLabel", "abs", "application-prod", "value4");
		stubCredentials("/myApp/default/myLabel", "abs", "myApp-default", "value5");
		stubCredentials("/application/default/myLabel", "abs", "application-default", "value6");

		Environment environment = this.credhubEnvironmentRepository.findOne("myApp", "dev,prod", "myLabel");

		assertThat(environment.getName()).isEqualTo("myApp");
		assertThat(environment.getProfiles()).containsExactly("dev", "prod", "default");
		assertThat(environment.getLabel()).isEqualTo("myLabel");

		assertThat(environment.getPropertySources()).hasSize(6);

		assertThat(environment.getPropertySources().get(0).getName()).isEqualTo("credhub-myApp-dev-myLabel");
		assertThat(environment.getPropertySources().get(0).getSource()).isEqualTo(singletonMap("myApp-dev", "value1"));

		assertThat(environment.getPropertySources().get(1).getName()).isEqualTo("credhub-application-dev-myLabel");
		assertThat(environment.getPropertySources().get(1).getSource())
			.isEqualTo(singletonMap("application-dev", "value2"));

		assertThat(environment.getPropertySources().get(2).getName()).isEqualTo("credhub-myApp-prod-myLabel");
		assertThat(environment.getPropertySources().get(2).getSource()).isEqualTo(singletonMap("myApp-prod", "value3"));

		assertThat(environment.getPropertySources().get(3).getName()).isEqualTo("credhub-application-prod-myLabel");
		assertThat(environment.getPropertySources().get(3).getSource())
			.isEqualTo(singletonMap("application-prod", "value4"));

		assertThat(environment.getPropertySources().get(4).getName()).isEqualTo("credhub-myApp-default-myLabel");
		assertThat(environment.getPropertySources().get(4).getSource())
			.isEqualTo(singletonMap("myApp-default", "value5"));

		assertThat(environment.getPropertySources().get(5).getName()).isEqualTo("credhub-application-default-myLabel");
		assertThat(environment.getPropertySources().get(5).getSource())
			.isEqualTo(singletonMap("application-default", "value6"));
	}

	@Test
	public void shouldIncludeDefaultProfileAndApplicationNameAtTheEnd() {
		stubCredentials("/myApp/dev/myLabel", "toggles", "myApp-dev", "value1");
		stubCredentials("/application/dev/myLabel", "abs", "application-dev", "value2");
		stubCredentials("/myApp/default/myLabel", "abs", "myApp-default", "value3");
		stubCredentials("/application/default/myLabel", "abs", "application-default", "value4");

		Environment environment = this.credhubEnvironmentRepository.findOne("application,myApp", "default,dev",
				"myLabel");

		assertThat(environment.getName()).isEqualTo("application,myApp");
		assertThat(environment.getProfiles()).containsExactly("dev", "default");
		assertThat(environment.getLabel()).isEqualTo("myLabel");

		assertThat(environment.getPropertySources()).hasSize(4);

		assertThat(environment.getPropertySources().get(0).getName()).isEqualTo("credhub-myApp-dev-myLabel");
		assertThat(environment.getPropertySources().get(0).getSource()).isEqualTo(singletonMap("myApp-dev", "value1"));

		assertThat(environment.getPropertySources().get(1).getName()).isEqualTo("credhub-application-dev-myLabel");
		assertThat(environment.getPropertySources().get(1).getSource())
			.isEqualTo(singletonMap("application-dev", "value2"));

		assertThat(environment.getPropertySources().get(2).getName()).isEqualTo("credhub-myApp-default-myLabel");
		assertThat(environment.getPropertySources().get(2).getSource())
			.isEqualTo(singletonMap("myApp-default", "value3"));

		assertThat(environment.getPropertySources().get(3).getName()).isEqualTo("credhub-application-default-myLabel");
		assertThat(environment.getPropertySources().get(3).getSource())
			.isEqualTo(singletonMap("application-default", "value4"));
	}

	private void stubCredentials(String expectedPath, String name, String key, String value) {
		SimpleCredentialName credentialsName = new SimpleCredentialName(expectedPath + "/" + name);
		when(this.credhubCredentialOperations.findByPath(expectedPath))
			.thenReturn(singletonList(new CredentialSummary(credentialsName)));
		JsonCredential credentials = new JsonCredential();
		credentials.put(key, value);
		when(this.credhubCredentialOperations.getByName(new SimpleCredentialName(expectedPath + "/" + name),
				JsonCredential.class))
			.thenReturn(new CredentialDetails<>("id1", credentialsName, CredentialType.JSON, credentials));
	}

}
