/*
 * Copyright 2018-present the original author or authors.
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

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

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
		this.credhubCredentialOperations = Mockito.mock(CredHubCredentialOperations.class);
		CredHubOperations credhubOperations = Mockito.mock(CredHubOperations.class);
		when(credhubOperations.credentials()).thenReturn(this.credhubCredentialOperations);

		this.credhubEnvironmentRepository = new CredhubEnvironmentRepository(credhubOperations);
	}

	@Test
	public void shouldDisplayEmptyPropertiesWhenNoPathFound() {
		stubCredentials(Path.of("/", "myApp", "prod", "myLabel").toString());

		Environment environment = this.credhubEnvironmentRepository.findOne("myApp", "prod", "myLabel");

		assertThat(environment.getName()).isEqualTo("myApp");
		assertThat(environment.getProfiles()).containsExactly("prod");
		assertThat(environment.getLabel()).isEqualTo("myLabel");

		assertThat(environment.getPropertySources()).hasSize(1);
		assertThat(environment.getPropertySources().get(0).getName()).isEqualTo("credhub-myApp-prod-myLabel");
		assertThat(environment.getPropertySources().get(0).getSource()).isEmpty();
	}

	@Test
	public void shouldRetrieveDefaultsWhenNoLabelNorProfileProvided() {
		stubCredentials(Path.of("/", "myApp", "default", "master").toString(), credential("c1", "k1", "v1"));

		Environment environment = this.credhubEnvironmentRepository.findOne("myApp", null, null);

		assertThat(environment.getName()).isEqualTo("myApp");
		assertThat(environment.getProfiles()).containsExactly("default");
		assertThat(environment.getLabel()).isEqualTo("master");

		assertThat(environment.getPropertySources()).hasSize(1);
		assertThat(environment.getPropertySources().get(0).getName()).isEqualTo("credhub-myApp-default-master");
		assertThat(environment.getPropertySources().get(0).getSource()).isEqualTo(Map.of("k1", "v1"));
	}

	@Test
	public void shouldRetrieveDefaultsWhenWhitespaceOnlyProfileAndLabelProvided() {
		stubCredentials(Path.of("/", "myApp", "default", "master").toString(), credential("c1", "k1", "v1"));

		Environment environment = this.credhubEnvironmentRepository.findOne("myApp", "   ", "   ");

		assertThat(environment.getName()).isEqualTo("myApp");
		assertThat(environment.getProfiles()).containsExactly("default");
		assertThat(environment.getLabel()).isEqualTo("master");

		assertThat(environment.getPropertySources()).hasSize(1);
		assertThat(environment.getPropertySources().get(0).getName()).isEqualTo("credhub-myApp-default-master");
		assertThat(environment.getPropertySources().get(0).getSource()).isEqualTo(Map.of("k1", "v1"));
	}

	@Test
	public void shouldRetrieveGivenProfileAndLabel() {
		stubCredentials(Path.of("/", "myApp", "prod", "myLabel").toString(), credential("c1", "k1", "v1"));

		Environment environment = this.credhubEnvironmentRepository.findOne("myApp", "prod", "myLabel");

		assertThat(environment.getName()).isEqualTo("myApp");
		assertThat(environment.getProfiles()).containsExactly("prod");
		assertThat(environment.getLabel()).isEqualTo("myLabel");

		assertThat(environment.getPropertySources()).hasSize(1);
		assertThat(environment.getPropertySources().get(0).getName()).isEqualTo("credhub-myApp-prod-myLabel");
		assertThat(environment.getPropertySources().get(0).getSource()).isEqualTo(Map.of("k1", "v1"));
	}

	@Test
	public void shouldRetrieveGivenMultipleProfiles() {
		stubCredentials(Path.of("/", "myApp", "prod", "myLabel").toString(), credential("c1", "k1", "v1"));
		stubCredentials(Path.of("/", "myApp", "cloud", "myLabel").toString(), credential("c2", "k2", "v2"));

		Environment environment = this.credhubEnvironmentRepository.findOne("myApp", "prod,cloud", "myLabel");

		assertThat(environment.getName()).isEqualTo("myApp");
		assertThat(environment.getProfiles()).containsExactly("prod", "cloud");
		assertThat(environment.getLabel()).isEqualTo("myLabel");

		assertThat(environment.getPropertySources()).hasSize(2);

		assertThat(environment.getPropertySources().get(0).getName()).isEqualTo("credhub-myApp-cloud-myLabel");
		assertThat(environment.getPropertySources().get(0).getSource()).isEqualTo(Map.of("k2", "v2"));

		assertThat(environment.getPropertySources().get(1).getName()).isEqualTo("credhub-myApp-prod-myLabel");
		assertThat(environment.getPropertySources().get(1).getSource()).isEqualTo(Map.of("k1", "v1"));
	}

	@Test
	public void shouldRetrieveGivenLabelsProfiles() {
		stubCredentials(Path.of("/", "myApp", "prod", "myLabel").toString(), credential("c1", "k1", "v1"));
		stubCredentials(Path.of("/", "myApp", "cloud", "mySecondLabel").toString(), credential("c2", "k2", "v2"));
		stubCredentials(Path.of("/", "myApp", "prod", "master").toString(), credential("c3", "k3", "v3"));

		Environment environment = this.credhubEnvironmentRepository.findOne("myApp", "prod,cloud",
				"myLabel,mySecondLabel");

		assertThat(environment.getName()).isEqualTo("myApp");
		assertThat(environment.getProfiles()).containsExactly("prod", "cloud");
		assertThat(environment.getLabel()).isEqualTo("myLabel,mySecondLabel");

		assertThat(environment.getPropertySources()).hasSize(2);

		assertThat(environment.getPropertySources().get(0).getName()).isEqualTo("credhub-myApp-cloud-mySecondLabel");
		assertThat(environment.getPropertySources().get(0).getSource()).isEqualTo(Map.of("k2", "v2"));

		assertThat(environment.getPropertySources().get(1).getName()).isEqualTo("credhub-myApp-prod-myLabel");
		assertThat(environment.getPropertySources().get(1).getSource()).isEqualTo(Map.of("k1", "v1"));
	}

	@Test
	public void shouldRetrieveGivenMultipleApplicationNames() {
		stubCredentials(Path.of("/", "app1", "default", "myLabel").toString(), credential("c1", "k1", "v1"));
		stubCredentials(Path.of("/", "app2", "default", "myLabel").toString(), credential("c2", "k2", "v2"));

		Environment environment = this.credhubEnvironmentRepository.findOne("app1,app2", null, "myLabel");

		assertThat(environment.getName()).isEqualTo("app1,app2");
		assertThat(environment.getProfiles()).containsExactly("default");
		assertThat(environment.getLabel()).isEqualTo("myLabel");

		assertThat(environment.getPropertySources()).hasSize(2);

		assertThat(environment.getPropertySources().get(0).getName()).isEqualTo("credhub-app2-default-myLabel");
		assertThat(environment.getPropertySources().get(0).getSource()).isEqualTo(Map.of("k2", "v2"));

		assertThat(environment.getPropertySources().get(1).getName()).isEqualTo("credhub-app1-default-myLabel");
		assertThat(environment.getPropertySources().get(1).getSource()).isEqualTo(Map.of("k1", "v1"));
	}

	@Test
	public void shouldTrimWhitespaceFromApplicationNames() {
		stubCredentials(Path.of("/", "myApp", "prod", "myLabel").toString(), credential("c1", "k1", "v1"));
		stubCredentials(Path.of("/", "application", "prod", "myLabel").toString(), credential("c2", "k2", "v2"));

		Environment environment = this.credhubEnvironmentRepository.findOne("myApp, application ", "prod", "myLabel");

		assertThat(environment.getPropertySources()).hasSize(2);
		assertThat(environment.getPropertySources().get(0).getSource()).isEqualTo(Map.of("k1", "v1"));
		assertThat(environment.getPropertySources().get(1).getSource()).isEqualTo(Map.of("k2", "v2"));
	}

	@Test
	public void shouldTrimWhitespaceFromEnvironmentMetadata() {
		Environment environment = this.credhubEnvironmentRepository.findOne(" myApp , app2 ", " prod , cloud ",
				" myLabel , mySecondLabel ");

		assertThat(environment.getName()).isEqualTo("myApp,app2");
		assertThat(environment.getProfiles()).containsExactly("prod", "cloud");
		assertThat(environment.getLabel()).isEqualTo("myLabel,mySecondLabel");
	}

	@Test
	public void shouldMergeWhenMoreThanOneCredentialsFound() {
		stubCredentials(Path.of("/", "myApp", "prod", "myLabel").toString(), credential("c1", Map.of("k1", "v1")),
				credential("c2", Map.of("k2", "v2")));

		Environment environment = this.credhubEnvironmentRepository.findOne("myApp", "prod", "myLabel");

		assertThat(environment.getName()).isEqualTo("myApp");
		assertThat(environment.getProfiles()).containsExactly("prod");
		assertThat(environment.getLabel()).isEqualTo("myLabel");

		assertThat(environment.getPropertySources()).hasSize(1);
		assertThat(environment.getPropertySources().get(0).getName()).isEqualTo("credhub-myApp-prod-myLabel");
		assertThat(environment.getPropertySources().get(0).getSource()).isEqualTo(Map.of("k1", "v1", "k2", "v2"));
	}

	@Test
	public void shouldIncludeDefaultApplicationWhenOtherProvided() {
		stubCredentials(Path.of("/", "app1", "prod", "myLabel").toString(), credential("c1", "k1", "v1"));
		stubCredentials(Path.of("/", "app2", "prod", "myLabel").toString(), credential("c2", "k2", "v2"));
		stubCredentials(Path.of("/", "application", "prod", "myLabel").toString(), credential("c3", "k3", "v3"));
		stubCredentials(Path.of("/", "app1", "default", "myLabel").toString(), credential("c4", "k4", "v4"));
		stubCredentials(Path.of("/", "app2", "default", "myLabel").toString(), credential("c5", "k5", "v5"));
		stubCredentials(Path.of("/", "application", "default", "myLabel").toString(), credential("c6", "k6", "v6"));

		Environment environment = this.credhubEnvironmentRepository.findOne("app1,app2", "prod", "myLabel");

		assertThat(environment.getName()).isEqualTo("app1,app2");
		assertThat(environment.getProfiles()).containsExactly("prod");
		assertThat(environment.getLabel()).isEqualTo("myLabel");

		assertThat(environment.getPropertySources()).hasSize(6);

		assertThat(environment.getPropertySources().get(0).getName()).isEqualTo("credhub-app2-prod-myLabel");
		assertThat(environment.getPropertySources().get(0).getSource()).isEqualTo(Map.of("k2", "v2"));

		assertThat(environment.getPropertySources().get(1).getName()).isEqualTo("credhub-app1-prod-myLabel");
		assertThat(environment.getPropertySources().get(1).getSource()).isEqualTo(Map.of("k1", "v1"));

		assertThat(environment.getPropertySources().get(2).getName()).isEqualTo("credhub-application-prod-myLabel");
		assertThat(environment.getPropertySources().get(2).getSource()).isEqualTo(Map.of("k3", "v3"));

		assertThat(environment.getPropertySources().get(3).getName()).isEqualTo("credhub-app2-default-myLabel");
		assertThat(environment.getPropertySources().get(3).getSource()).isEqualTo(Map.of("k5", "v5"));

		assertThat(environment.getPropertySources().get(4).getName()).isEqualTo("credhub-app1-default-myLabel");
		assertThat(environment.getPropertySources().get(4).getSource()).isEqualTo(Map.of("k4", "v4"));

		assertThat(environment.getPropertySources().get(5).getName()).isEqualTo("credhub-application-default-myLabel");
		assertThat(environment.getPropertySources().get(5).getSource()).isEqualTo(Map.of("k6", "v6"));
	}

	@Test
	public void shouldIncludeDefaultProfileWhenOtherProvided() {
		stubCredentials(Path.of("/", "myApp", "dev", "myLabel").toString(), credential("c1", "k1", "v1"));
		stubCredentials(Path.of("/", "application", "dev", "myLabel").toString(), credential("c2", "k2", "v2"));
		stubCredentials(Path.of("/", "myApp", "prod", "myLabel").toString(), credential("c3", "k3", "v3"));
		stubCredentials(Path.of("/", "application", "prod", "myLabel").toString(), credential("c4", "k4", "v4"));
		stubCredentials(Path.of("/", "myApp", "default", "myLabel").toString(), credential("c5", "k5", "v5"));
		stubCredentials(Path.of("/", "application", "default", "myLabel").toString(), credential("c6", "k6", "v6"));

		Environment environment = this.credhubEnvironmentRepository.findOne("myApp", "dev,prod", "myLabel");

		assertThat(environment.getName()).isEqualTo("myApp");
		assertThat(environment.getProfiles()).containsExactly("dev", "prod");
		assertThat(environment.getLabel()).isEqualTo("myLabel");

		assertThat(environment.getPropertySources()).hasSize(6);

		assertThat(environment.getPropertySources().get(0).getName()).isEqualTo("credhub-myApp-prod-myLabel");
		assertThat(environment.getPropertySources().get(0).getSource()).isEqualTo(Map.of("k3", "v3"));

		assertThat(environment.getPropertySources().get(1).getName()).isEqualTo("credhub-application-prod-myLabel");
		assertThat(environment.getPropertySources().get(1).getSource()).isEqualTo(Map.of("k4", "v4"));

		assertThat(environment.getPropertySources().get(2).getName()).isEqualTo("credhub-myApp-dev-myLabel");
		assertThat(environment.getPropertySources().get(2).getSource()).isEqualTo(Map.of("k1", "v1"));

		assertThat(environment.getPropertySources().get(3).getName()).isEqualTo("credhub-application-dev-myLabel");
		assertThat(environment.getPropertySources().get(3).getSource()).isEqualTo(Map.of("k2", "v2"));

		assertThat(environment.getPropertySources().get(4).getName()).isEqualTo("credhub-myApp-default-myLabel");
		assertThat(environment.getPropertySources().get(4).getSource()).isEqualTo(Map.of("k5", "v5"));

		assertThat(environment.getPropertySources().get(5).getName()).isEqualTo("credhub-application-default-myLabel");
		assertThat(environment.getPropertySources().get(5).getSource()).isEqualTo(Map.of("k6", "v6"));
	}

	@Test
	public void shouldIncludeDefaultProfileAndApplicationNameAtTheEnd() {
		stubCredentials(Path.of("/", "myApp", "dev", "myLabel").toString(), credential("c1", "k1", "v1"));
		stubCredentials(Path.of("/", "application", "dev", "myLabel").toString(), credential("c2", "k2", "v2"));
		stubCredentials(Path.of("/", "myApp", "default", "myLabel").toString(), credential("c3", "k3", "v3"));
		stubCredentials(Path.of("/", "application", "default", "myLabel").toString(), credential("c4", "k4", "v4"));

		Environment environment = this.credhubEnvironmentRepository.findOne("application,myApp", "default,dev",
				"myLabel");

		assertThat(environment.getName()).isEqualTo("application,myApp");
		assertThat(environment.getProfiles()).containsExactly("default", "dev");
		assertThat(environment.getLabel()).isEqualTo("myLabel");

		assertThat(environment.getPropertySources()).hasSize(4);

		assertThat(environment.getPropertySources().get(0).getName()).isEqualTo("credhub-myApp-dev-myLabel");
		assertThat(environment.getPropertySources().get(0).getSource()).isEqualTo(Map.of("k1", "v1"));

		assertThat(environment.getPropertySources().get(1).getName()).isEqualTo("credhub-application-dev-myLabel");
		assertThat(environment.getPropertySources().get(1).getSource()).isEqualTo(Map.of("k2", "v2"));

		assertThat(environment.getPropertySources().get(2).getName()).isEqualTo("credhub-myApp-default-myLabel");
		assertThat(environment.getPropertySources().get(2).getSource()).isEqualTo(Map.of("k3", "v3"));

		assertThat(environment.getPropertySources().get(3).getName()).isEqualTo("credhub-application-default-myLabel");
		assertThat(environment.getPropertySources().get(3).getSource()).isEqualTo(Map.of("k4", "v4"));
	}

	@Test
	public void shouldUseCustomDefaultLabelIfProvided() {
		stubCredentials(Path.of("/", "myApp", "default", "master").toString(), credential("c1", "k1", "v1"));
		stubCredentials(Path.of("/", "myApp", "default", "main").toString(), credential("c2", "k2", "v2"));

		var credhubOperations = Mockito.mock(CredHubOperations.class);
		when(credhubOperations.credentials()).thenReturn(this.credhubCredentialOperations);

		var properties = new CredhubEnvironmentProperties();
		properties.setDefaultLabel("main");

		var environment = new CredhubEnvironmentRepository(credhubOperations, properties).findOne("myApp", null, null);

		assertThat(environment.getName()).isEqualTo("myApp");
		assertThat(environment.getProfiles()).containsExactly("default");
		assertThat(environment.getLabel()).isEqualTo("main");

		assertThat(environment.getPropertySources()).hasSize(1);
		assertThat(environment.getPropertySources().get(0).getName()).isEqualTo("credhub-myApp-default-main");
		assertThat(environment.getPropertySources().get(0).getSource()).isEqualTo(Map.of("k2", "v2"));
	}

	@Test
	public void shouldUseBasePathIfProvided() {
		stubCredentials(Path.of("/", "base", "path", "myApp", "default", "master").toString(),
				credential("c1", "k1", "v1"));

		var credhubOperations = Mockito.mock(CredHubOperations.class);
		when(credhubOperations.credentials()).thenReturn(this.credhubCredentialOperations);

		var properties = new CredhubEnvironmentProperties();
		properties.setPath("/base/path");

		var environment = new CredhubEnvironmentRepository(credhubOperations, properties).findOne("myApp", null, null);

		assertThat(environment.getName()).isEqualTo("myApp");
		assertThat(environment.getProfiles()).containsExactly("default");
		assertThat(environment.getLabel()).isEqualTo("master");

		assertThat(environment.getPropertySources()).hasSize(1);
		assertThat(environment.getPropertySources().get(0).getName()).isEqualTo("credhub-myApp-default-master");
		assertThat(environment.getPropertySources().get(0).getSource()).isEqualTo(Map.of("k1", "v1"));
	}

	@SafeVarargs
	private void stubCredentials(String path, CredentialDetails<JsonCredential>... details) {
		when(this.credhubCredentialOperations.findByPath(path)).thenReturn(
				Arrays.stream(details).map(it -> new CredentialSummary(it.getName())).collect(Collectors.toList()));

		for (CredentialDetails<JsonCredential> d : details) {
			when(this.credhubCredentialOperations.getByName(d.getName(), JsonCredential.class)).thenReturn(d);
		}
	}

	private CredentialDetails<JsonCredential> credential(String name, String key, String value) {
		return credential(name, Map.of(key, value));
	}

	private CredentialDetails<JsonCredential> credential(String name, Map<String, String> secrets) {
		return new CredentialDetails<>("::id::", new SimpleCredentialName(name), CredentialType.JSON,
				new JsonCredential(secrets));
	}

}
