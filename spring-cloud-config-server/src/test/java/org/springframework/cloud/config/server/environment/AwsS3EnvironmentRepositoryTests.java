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

import java.io.UnsupportedEncodingException;
import java.util.Objects;
import java.util.Properties;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectId;
import com.amazonaws.util.StringInputStream;
import org.junit.Test;
import org.mockito.ArgumentMatcher;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.config.server.config.ConfigServerProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Clay McCoy
 */
public class AwsS3EnvironmentRepositoryTests {

	final ConfigServerProperties server = new ConfigServerProperties();

	final AmazonS3 s3Client = mock(AmazonS3.class, "config");

	final EnvironmentRepository envRepo = new AwsS3EnvironmentRepository(s3Client,
			"bucket1", server);

	final String propertyContent = "cloudfoundry.enabled=true\n"
			+ "cloudfoundry.accounts[0].name=acc1\n"
			+ "cloudfoundry.accounts[0].user=user1\n"
			+ "cloudfoundry.accounts[0].password=password1\n"
			+ "cloudfoundry.accounts[0].api=api.sys.acc1.cf-app.com\n"
			+ "cloudfoundry.accounts[0].environment=test1\n"
			+ "cloudfoundry.accounts[1].name=acc2\n"
			+ "cloudfoundry.accounts[1].user=user2\n"
			+ "cloudfoundry.accounts[1].password=password2\n"
			+ "cloudfoundry.accounts[1].api=api.sys.acc2.cf-app.com\n"
			+ "cloudfoundry.accounts[1].environment=test2\n";

	final String yamlContent = "cloudfoundry:\n" + "  enabled: true\n" + "  accounts:\n"
			+ "    - name: acc1\n" + "      user: 'user1'\n"
			+ "      password: 'password1'\n" + "      api: api.sys.acc1.cf-app.com\n"
			+ "      environment: test1\n" + "    - name: acc2\n"
			+ "      user: 'user2'\n" + "      password: 'password2'\n"
			+ "      api: api.sys.acc2.cf-app.com\n" + "      environment: test2\n";

	final String jsonContent = "{\n" + " \"cloudfoundry\": {\n" + "  \"enabled\": true,\n"
			+ "  \"accounts\": [{\n" + "   \"name\": \"acc1\",\n"
			+ "   \"user\": \"user1\",\n" + "   \"password\": \"password1\",\n"
			+ "   \"api\": \"api.sys.acc1.cf-app.com\",\n"
			+ "   \"environment\": \"test1\"\n" + "  }, {\n" + "   \"name\": \"acc2\",\n"
			+ "   \"user\": \"user2\",\n" + "   \"password\": \"password2\",\n"
			+ "   \"api\": \"api.sys.acc2.cf-app.com\",\n"
			+ "   \"environment\": \"test2\"\n" + "  }]\n" + " }\n" + "}";

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
	public void findPropertiesObject() throws UnsupportedEncodingException {
		setupS3("foo-bar.properties", propertyContent);

		// Pulling content from a .properties file forces a boolean into a String
		expectedProperties.put("cloudfoundry.enabled", "true");

		final Environment env = envRepo.findOne("foo", "bar", null);

		assertExpectedEnvironment(env, "foo", null, null, 1, "bar");
	}

	@Test
	public void findJsonObject() throws UnsupportedEncodingException {
		setupS3("foo-bar.json", jsonContent);

		final Environment env = envRepo.findOne("foo", "bar", null);

		assertExpectedEnvironment(env, "foo", null, null, 1, "bar");
	}

	@Test
	public void findYamlObject() throws UnsupportedEncodingException {
		setupS3("foo-bar.yml", yamlContent);

		final Environment env = envRepo.findOne("foo", "bar", null);

		assertExpectedEnvironment(env, "foo", null, null, 1, "bar");
	}

	@Test
	public void findWithDefaultProfile() throws UnsupportedEncodingException {
		setupS3("foo.yml", yamlContent);

		final Environment env = envRepo.findOne("foo", null, null);

		assertExpectedEnvironment(env, "foo", null, null, 1, "default", null);
	}

	@Test
	public void findWithDefaultProfileUsingSuffix() throws UnsupportedEncodingException {
		setupS3("foo-default.yml", yamlContent);

		final Environment env = envRepo.findOne("foo", null, null);

		assertExpectedEnvironment(env, "foo", null, null, 1, "default", null);
	}

	@Test
	public void findWithMultipleProfilesAllFound() throws UnsupportedEncodingException {
		setupS3("foo-profile1.yml", yamlContent);
		setupS3("foo-profile2.yml", jsonContent);

		final Environment env = envRepo.findOne("foo", "profile1,profile2", null);

		assertExpectedEnvironment(env, "foo", null, null, 2, "profile1", "profile2");
	}

	@Test
	public void findWithMultipleProfilesOneFound() throws UnsupportedEncodingException {
		setupS3("foo-profile2.yml", jsonContent);

		final Environment env = envRepo.findOne("foo", "profile1,profile2", null);

		assertExpectedEnvironment(env, "foo", null, null, 1, "profile1", "profile2");
	}

	@Test
	public void findWithLabel() throws UnsupportedEncodingException {
		setupS3("label1/foo-bar.yml", yamlContent);

		final Environment env = envRepo.findOne("foo", "bar", "label1");

		assertExpectedEnvironment(env, "foo", "label1", null, 1, "bar");
	}

	@Test
	public void findWithVersion() throws UnsupportedEncodingException {
		setupS3("foo-bar.yml", "v1", yamlContent);

		final Environment env = envRepo.findOne("foo", "bar", null);

		assertExpectedEnvironment(env, "foo", null, "v1", 1, "bar");
	}

	private void setupS3(String fileName, String propertyContent)
			throws UnsupportedEncodingException {
		setupS3(fileName, null, propertyContent);
	}

	private void setupS3(String fileName, String version, String propertyContent)
			throws UnsupportedEncodingException {
		final S3ObjectId s3ObjectId = new S3ObjectId("bucket1", fileName);
		final GetObjectRequest request = new GetObjectRequest(s3ObjectId);

		final S3Object s3Object = new S3Object();
		s3Object.setObjectContent(new StringInputStream(propertyContent));

		if (version != null) {
			final ObjectMetadata metadata = new ObjectMetadata();
			metadata.setHeader("x-amz-version-id", version);
			s3Object.setObjectMetadata(metadata);
		}

		when(s3Client.getObject(argThat(new GetObjectRequestMatcher(request))))
				.thenReturn(s3Object);
	}

	private void assertExpectedEnvironment(Environment env, String applicationName,
			String label, String version, int propertySourceCount, String... profiles) {
		assertThat(env.getName()).isEqualTo(applicationName);
		assertThat(env.getProfiles()).isEqualTo(profiles);
		assertThat(env.getLabel()).isEqualTo(label);
		assertThat(env.getVersion()).isEqualTo(version);
		assertThat(env.getPropertySources().size()).isEqualTo(propertySourceCount);
		for (PropertySource ps : env.getPropertySources()) {
			assertThat(ps.getSource()).isEqualTo(expectedProperties);
		}
	}

	private static class GetObjectRequestMatcher
			implements ArgumentMatcher<GetObjectRequest> {

		private final GetObjectRequest expected;

		GetObjectRequestMatcher(GetObjectRequest expected) {
			this.expected = expected;
		}

		@Override
		public boolean matches(GetObjectRequest actual) {
			if (actual == null) {
				return false;
			}
			return Objects.equals(actual.getBucketName(), expected.getBucketName())
					&& Objects.equals(actual.getKey(), expected.getKey())
					&& Objects.equals(actual.getVersionId(), expected.getVersionId());
		}

	}

}
