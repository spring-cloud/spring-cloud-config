/*
 * Copyright 2016-2022 the original author or authors.
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

import java.util.Objects;
import java.util.Properties;

import org.junit.Test;
import org.mockito.ArgumentMatcher;
import org.mockito.internal.stubbing.answers.ThrowsExceptionForClassType;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.utils.StringInputStream;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.config.server.config.ConfigServerProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * @author Clay McCoy
 */
public class AwsS3EnvironmentRepositoryTests {

	final ConfigServerProperties server = new ConfigServerProperties();

	final S3Client s3Client = mock(S3Client.class, new ThrowsExceptionForClassType(NoSuchKeyException.class));

	final EnvironmentRepository envRepo = new AwsS3EnvironmentRepository(s3Client, "bucket1", server);

	final String propertyContent = "cloudfoundry.enabled=true\n" + "cloudfoundry.accounts[0].name=acc1\n"
			+ "cloudfoundry.accounts[0].user=user1\n" + "cloudfoundry.accounts[0].password=password1\n"
			+ "cloudfoundry.accounts[0].api=api.sys.acc1.cf-app.com\n" + "cloudfoundry.accounts[0].environment=test1\n"
			+ "cloudfoundry.accounts[1].name=acc2\n" + "cloudfoundry.accounts[1].user=user2\n"
			+ "cloudfoundry.accounts[1].password=password2\n" + "cloudfoundry.accounts[1].api=api.sys.acc2.cf-app.com\n"
			+ "cloudfoundry.accounts[1].environment=test2\n";

	final String yamlContent = "cloudfoundry:\n" + "  enabled: true\n" + "  accounts:\n" + "    - name: acc1\n"
			+ "      user: 'user1'\n" + "      password: 'password1'\n" + "      api: api.sys.acc1.cf-app.com\n"
			+ "      environment: test1\n" + "    - name: acc2\n" + "      user: 'user2'\n"
			+ "      password: 'password2'\n" + "      api: api.sys.acc2.cf-app.com\n" + "      environment: test2\n";

	final String jsonContent = "{\n" + " \"cloudfoundry\": {\n" + "  \"enabled\": true,\n" + "  \"accounts\": [{\n"
			+ "   \"name\": \"acc1\",\n" + "   \"user\": \"user1\",\n" + "   \"password\": \"password1\",\n"
			+ "   \"api\": \"api.sys.acc1.cf-app.com\",\n" + "   \"environment\": \"test1\"\n" + "  }, {\n"
			+ "   \"name\": \"acc2\",\n" + "   \"user\": \"user2\",\n" + "   \"password\": \"password2\",\n"
			+ "   \"api\": \"api.sys.acc2.cf-app.com\",\n" + "   \"environment\": \"test2\"\n" + "  }]\n" + " }\n"
			+ "}";

	final Properties expectedProperties = new Properties();

	{
		expectedProperties.put("cloudfoundry.enabled", true);
		expectedProperties.put("cloudfoundry.accounts[0].name", "acc1");
		expectedProperties.put("cloudfoundry.accounts[0].user", "user1");
		expectedProperties.put("cloudfoundry.accounts[0].password", "password1");
		expectedProperties.put("cloudfoundry.accounts[0].api", "api.sys.acc1.cf-app.com");
		expectedProperties.put("cloudfoundry.accounts[0].environment", "test1");
		expectedProperties.put("cloudfoundry.accounts[1].name", "acc2");
		expectedProperties.put("cloudfoundry.accounts[1].user", "user2");
		expectedProperties.put("cloudfoundry.accounts[1].password", "password2");
		expectedProperties.put("cloudfoundry.accounts[1].api", "api.sys.acc2.cf-app.com");
		expectedProperties.put("cloudfoundry.accounts[1].environment", "test2");
	}

	@Test
	public void failToFindNonexistentObject() {
		Environment env = envRepo.findOne("foo", "bar", null);
		assertThat(env.getPropertySources().size()).isEqualTo(0);
	}

	@Test
	public void findPropertiesObject() {
		setupS3("foo-bar.properties", propertyContent);

		// Pulling content from a .properties file forces a boolean into a String
		expectedProperties.put("cloudfoundry.enabled", "true");

		final Environment env = envRepo.findOne("foo", "bar", null);

		assertExpectedEnvironment(env, "foo", null, null, 1, "bar");
	}

	@Test
	public void findJsonObject() {
		setupS3("foo-bar.json", jsonContent);

		final Environment env = envRepo.findOne("foo", "bar", null);

		assertExpectedEnvironment(env, "foo", null, null, 1, "bar");
	}

	@Test
	public void findYamlObject() {
		setupS3("foo-bar.yml", yamlContent);

		final Environment env = envRepo.findOne("foo", "bar", null);

		assertExpectedEnvironment(env, "foo", null, null, 1, "bar");
	}

	@Test
	public void findWithDefaultProfile() {
		setupS3("foo.yml", yamlContent);

		final Environment env = envRepo.findOne("foo", null, null);

		assertExpectedEnvironment(env, "foo", null, null, 1, "default", null);
	}

	@Test
	public void findWithDefaultProfileUsingSuffix() {
		setupS3("foo-default.yml", yamlContent);

		final Environment env = envRepo.findOne("foo", null, null);

		assertExpectedEnvironment(env, "foo", null, null, 1, "default", null);
	}

	@Test
	public void findWithMultipleProfilesAllFound() {
		setupS3("foo-profile1.yml", yamlContent);
		setupS3("foo-profile2.yml", jsonContent);

		final Environment env = envRepo.findOne("foo", "profile1,profile2", null);

		assertExpectedEnvironment(env, "foo", null, null, 2, "profile1", "profile2");
	}

	@Test
	public void findWithMultipleProfilesOneFound() {
		setupS3("foo-profile2.yml", jsonContent);

		final Environment env = envRepo.findOne("foo", "profile1,profile2", null);

		assertExpectedEnvironment(env, "foo", null, null, 1, "profile1", "profile2");
	}

	@Test
	public void findWithLabel() {
		setupS3("label1/foo-bar.yml", yamlContent);

		final Environment env = envRepo.findOne("foo", "bar", "label1");

		assertExpectedEnvironment(env, "foo", "label1", null, 1, "bar");
	}

	@Test
	public void findWithVersion() {
		setupS3("foo-bar.yml", "v1", yamlContent);

		final Environment env = envRepo.findOne("foo", "bar", null);

		assertExpectedEnvironment(env, "foo", null, "v1", 1, "bar");
	}

	@Test
	public void findWithMultipleApplicationAllFound() {
		setupS3("foo-profile1.yml", jsonContent);
		setupS3("bar-profile1.yml", jsonContent);

		final Environment env = envRepo.findOne("foo,bar", "profile1", null);

		assertExpectedEnvironment(env, "foo,bar", null, null, 2, "profile1");
	}

	@Test
	public void factoryCustomizable() {
		AwsS3EnvironmentRepositoryFactory factory = new AwsS3EnvironmentRepositoryFactory(new ConfigServerProperties());
		AwsS3EnvironmentProperties properties = new AwsS3EnvironmentProperties();
		properties.setRegion("us-east-1");
		properties.setEndpoint("https://myawsendpoint/");
		AwsS3EnvironmentRepository repository = factory.build(properties);
		assertThat(repository).isNotNull();
	}

	private void setupS3(String fileName, String propertyContent) {
		setupS3(fileName, null, propertyContent);
	}

	private void setupS3(String fileName, String version, String propertyContent) {
		final GetObjectRequest request = GetObjectRequest.builder().bucket("bucket1").key(fileName).build();

		GetObjectResponse.Builder responseBuilder = GetObjectResponse.builder();
		if (version != null) {
			responseBuilder.versionId(version);
		}
		ResponseInputStream<GetObjectResponse> responseInputStream = new ResponseInputStream<>(responseBuilder.build(),
				AbortableInputStream.create(new StringInputStream(propertyContent)));

		doReturn(responseInputStream).when(s3Client).getObject(argThat(new GetObjectRequestMatcher(request)));
	}

	private void assertExpectedEnvironment(Environment env, String applicationName, String label, String version,
			int propertySourceCount, String... profiles) {
		assertThat(env.getName()).isEqualTo(applicationName);
		assertThat(env.getProfiles()).isEqualTo(profiles);
		assertThat(env.getLabel()).isEqualTo(label);
		assertThat(env.getVersion()).isEqualTo(version);
		assertThat(env.getPropertySources().size()).isEqualTo(propertySourceCount);
		for (PropertySource ps : env.getPropertySources()) {
			assertThat(ps.getSource()).isEqualTo(expectedProperties);
		}
	}

	private static class GetObjectRequestMatcher implements ArgumentMatcher<GetObjectRequest> {

		private final GetObjectRequest expected;

		GetObjectRequestMatcher(GetObjectRequest expected) {
			this.expected = expected;
		}

		@Override
		public boolean matches(GetObjectRequest actual) {
			if (actual == null) {
				return false;
			}
			return Objects.equals(actual.bucket(), expected.bucket()) && Objects.equals(actual.key(), expected.key())
					&& Objects.equals(actual.versionId(), expected.versionId());
		}

	}

}
