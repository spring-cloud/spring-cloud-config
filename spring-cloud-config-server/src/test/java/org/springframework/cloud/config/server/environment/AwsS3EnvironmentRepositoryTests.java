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
import org.springframework.cloud.config.server.config.ConfigServerProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
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

	final S3Object s3Object = new S3Object();

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
	public void failToFindNonexistantObject() {

		Throwable thrown = catchThrowable(() -> {
			envRepo.findOne("foo", "bar", null);
		});

		assertThat(thrown).isInstanceOf(NoSuchRepositoryException.class);
		assertThat(thrown).hasMessage(
				"No such repository: (bucket: bucket1, key: foo-bar(.properties | .yml | .json), versionId: null)");
	}

	@Test
	public void findPropertiesObject() throws UnsupportedEncodingException {
		final S3ObjectId S3ObjectId = new S3ObjectId("bucket1", "foo-bar.properties");
		final GetObjectRequest request = new GetObjectRequest(S3ObjectId);
		s3Object.setObjectContent(new StringInputStream(propertyContent));
		when(s3Client.getObject(argThat(new GetObjectRequestMatcher(request))))
				.thenReturn(s3Object);

		// Pulling content from a .properties file forces a boolean into a String
		expectedProperties.put("cloudfoundry.enabled", "true");

		final Environment env = envRepo.findOne("foo", "bar", null);

		assertThat(env.getName()).isEqualTo("foo");
		assertThat(env.getProfiles()).isEqualTo(new String[] { "bar" });
		assertThat(env.getLabel()).isEqualTo(null);
		assertThat(env.getVersion()).isEqualTo(null);
		assertThat(env.getPropertySources().size()).isEqualTo(1);
		assertThat(env.getPropertySources().get(0).getSource())
				.isEqualTo(expectedProperties);
	}

	@Test
	public void findJsonObject() throws UnsupportedEncodingException {
		final S3ObjectId s3ObjectId = new S3ObjectId("bucket1", "foo-bar.json");
		final GetObjectRequest request = new GetObjectRequest(s3ObjectId);
		s3Object.setObjectContent(new StringInputStream(jsonContent));
		when(s3Client.getObject(argThat(new GetObjectRequestMatcher(request))))
				.thenReturn(s3Object);

		final Environment env = envRepo.findOne("foo", "bar", null);

		assertThat(env.getName()).isEqualTo("foo");
		assertThat(env.getProfiles()).isEqualTo(new String[] { "bar" });
		assertThat(env.getLabel()).isEqualTo(null);
		assertThat(env.getVersion()).isEqualTo(null);
		assertThat(env.getPropertySources().size()).isEqualTo(1);
		assertThat(env.getPropertySources().get(0).getSource())
				.isEqualTo(expectedProperties);
	}

	@Test
	public void findYamlObject() throws UnsupportedEncodingException {
		final S3ObjectId s3ObjectId = new S3ObjectId("bucket1", "foo-bar.yml");
		final GetObjectRequest request = new GetObjectRequest(s3ObjectId);
		s3Object.setObjectContent(new StringInputStream(yamlContent));
		when(s3Client.getObject(argThat(new GetObjectRequestMatcher(request))))
				.thenReturn(s3Object);

		final Environment env = envRepo.findOne("foo", "bar", null);

		assertThat(env.getName()).isEqualTo("foo");
		assertThat(env.getProfiles()).isEqualTo(new String[] { "bar" });
		assertThat(env.getLabel()).isEqualTo(null);
		assertThat(env.getVersion()).isEqualTo(null);
		assertThat(env.getPropertySources().size()).isEqualTo(1);
		assertThat(env.getPropertySources().get(0).getSource())
				.isEqualTo(expectedProperties);
	}

	@Test
	public void findDefaultProfileObject() throws UnsupportedEncodingException {
		final S3ObjectId s3ObjectId = new S3ObjectId("bucket1", "foo-default.yml");
		final GetObjectRequest request = new GetObjectRequest(s3ObjectId);
		s3Object.setObjectContent(new StringInputStream(yamlContent));
		when(s3Client.getObject(argThat(new GetObjectRequestMatcher(request))))
				.thenReturn(s3Object);

		final Environment env = envRepo.findOne("foo", null, null);

		assertThat(env.getName()).isEqualTo("foo");
		assertThat(env.getProfiles()).isEqualTo(new String[] { "default" });
		assertThat(env.getLabel()).isEqualTo(null);
		assertThat(env.getVersion()).isEqualTo(null);
		assertThat(env.getPropertySources().size()).isEqualTo(1);
		assertThat(env.getPropertySources().get(0).getSource())
				.isEqualTo(expectedProperties);
	}

	@Test
	public void findLabeledObject() throws UnsupportedEncodingException {
		final S3ObjectId s3ObjectId = new S3ObjectId("bucket1", "label1/foo-bar.yml");
		final GetObjectRequest request = new GetObjectRequest(s3ObjectId);
		s3Object.setObjectContent(new StringInputStream(yamlContent));
		when(s3Client.getObject(argThat(new GetObjectRequestMatcher(request))))
				.thenReturn(s3Object);

		final Environment env = envRepo.findOne("foo", "bar", "label1");

		assertThat(env.getName()).isEqualTo("foo");
		assertThat(env.getProfiles()).isEqualTo(new String[] { "bar" });
		assertThat(env.getLabel()).isEqualTo("label1");
		assertThat(env.getVersion()).isEqualTo(null);
		assertThat(env.getPropertySources().size()).isEqualTo(1);
		assertThat(env.getPropertySources().get(0).getSource())
				.isEqualTo(expectedProperties);
	}

	@Test
	public void findVersionedObject() throws UnsupportedEncodingException {
		final S3ObjectId s3ObjectId = new S3ObjectId("bucket1", "foo-bar.yml");
		final GetObjectRequest request = new GetObjectRequest(s3ObjectId);
		s3Object.setObjectContent(new StringInputStream(yamlContent));
		final ObjectMetadata metadata = new ObjectMetadata();
		metadata.setHeader("x-amz-version-id", "v1");
		s3Object.setObjectMetadata(metadata);
		when(s3Client.getObject(argThat(new GetObjectRequestMatcher(request))))
				.thenReturn(s3Object);

		final Environment env = envRepo.findOne("foo", "bar", null);

		assertThat(env.getName()).isEqualTo("foo");
		assertThat(env.getProfiles()).isEqualTo(new String[] { "bar" });
		assertThat(env.getLabel()).isEqualTo(null);
		assertThat(env.getVersion()).isEqualTo("v1");
		assertThat(env.getPropertySources().size()).isEqualTo(1);
		assertThat(env.getPropertySources().get(0).getSource())
				.isEqualTo(expectedProperties);
	}

	private class GetObjectRequestMatcher implements ArgumentMatcher<GetObjectRequest> {

		private final GetObjectRequest expected;

		GetObjectRequestMatcher(GetObjectRequest expected) {
			this.expected = expected;
		}

		@Override
		public boolean matches(GetObjectRequest actual) {
			if (!(actual instanceof GetObjectRequest)) {
				return false;
			}
			return Objects.equals(actual.getBucketName(), expected.getBucketName())
					&& Objects.equals(actual.getKey(), expected.getKey())
					&& Objects.equals(actual.getVersionId(), expected.getVersionId());
		}

	}

}
