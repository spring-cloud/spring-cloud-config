/*
 * Copyright 2013-2023 the original author or authors.
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

package org.springframework.cloud.config.server;

import java.io.IOException;
import java.util.Optional;

import io.awspring.cloud.s3.InMemoryBufferingS3OutputStreamProvider;
import io.awspring.cloud.s3.PropertiesS3ObjectContentTypeResolver;
import io.awspring.cloud.s3.S3ObjectContentTypeResolver;
import io.awspring.cloud.s3.S3OutputStreamProvider;
import io.awspring.cloud.s3.S3ProtocolResolver;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.json.JSONException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketResponse;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.test.TestConfigServerApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.TestSocketUtils;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Ryan Baxter
 */
@Testcontainers
public class AwsS3IntegrationTests {

	private static final Log LOG = LogFactory.getLog(AwsS3IntegrationTests.class);

	private static final int configServerPort = TestSocketUtils.findAvailableTcpPort();

	@Container
	static LocalStackContainer localstack = new LocalStackContainer(
			DockerImageName.parse("localstack/localstack:1.3.1")).withServices(LocalStackContainer.Service.S3);

	private static ConfigurableApplicationContext server;

	@BeforeAll
	public static void startConfigServer() throws IOException, InterruptedException, JSONException {
		System.setProperty("aws.accessKeyId", localstack.getAccessKey());
		System.setProperty("aws.secretAccessKey", localstack.getSecretKey());
		server = SpringApplication.run(new Class[] { TestConfigServerApplication.class, S3AutoConfiguration.class },
				new String[] { "--spring.config.name=server", "--spring.profiles.active=awss3",
						"--server.port=" + configServerPort,
						"--spring.cloud.config.server.awss3.endpoint="
								+ localstack.getEndpointOverride(LocalStackContainer.Service.S3).toString(),
						"--spring.cloud.config.server.awss3.bucket=test-bucket",
						"--spring.cloud.config.server.awss3.region=" + localstack.getRegion(),
						"--spring.cloud.aws.endpoint="
								+ localstack.getEndpointOverride(LocalStackContainer.Service.S3).toString(),
						"--spring.cloud.aws.region.static=" + localstack.getRegion(),
						"--logging.level.org.springframework.cloud.config.server.environment=DEBUG", "--debug=true" });

		S3Client s3Client = server.getBean(S3Client.class);
		CreateBucketResponse bucketResponse = s3Client.createBucket((request) -> request.bucket("test-bucket"));
		LOG.info("bucket response " + bucketResponse);
		PutObjectResponse objectResponse = s3Client.putObject(
				(request) -> request.bucket("test-bucket").key("data.txt"), RequestBody.fromString("this is a test"));
		LOG.info("object response " + objectResponse);
		objectResponse = s3Client.putObject((request) -> request.bucket("test-bucket").key("main/data.txt"),
				RequestBody.fromString("this is a test in main"));
		LOG.info("object response " + objectResponse);
		objectResponse = s3Client.putObject((request) -> request.bucket("test-bucket").key("application.properties"),
				RequestBody.fromString("foo=1"));
		LOG.info("object response " + objectResponse);
		objectResponse = s3Client.putObject((request) -> request.bucket("test-bucket").key("data.properties"),
				RequestBody.fromString("bar=1"));
		LOG.info("object response " + objectResponse);
		objectResponse = s3Client.putObject((request) -> request.bucket("test-bucket").key("data-dev.properties"),
				RequestBody.fromString("bar=1"));
		LOG.info("object response " + objectResponse);
	}

	@AfterAll
	public static void after() {
		server.close();
		System.clearProperty("aws.accessKeyId");
		System.clearProperty("aws.secretAccessKey");
	}

	@Test
	public void context() throws IOException {
		RestTemplate rest = new RestTemplateBuilder().build();
		String configServerUrl = "http://localhost:" + configServerPort;
		Environment env = rest.getForObject(configServerUrl + "/application/default", Environment.class);
		assertThat(env.getPropertySources().get(0).getSource().get("foo")).isEqualTo("1");
		assertThat(rest.getForObject(configServerUrl + "/application/default/main/data.txt", String.class))
				.isEqualTo("this is a test in main");
		assertThat(rest.getForObject(configServerUrl + "/application/default/data.txt?useDefaultLabel", String.class))
				.isEqualTo("this is a test");
	}

	@Test
	public void defaultApplicationAndProfileIncluded() throws IOException {
		RestTemplate rest = new RestTemplateBuilder().build();
		String configServerUrl = "http://localhost:" + configServerPort;
		Environment env = rest.getForObject(configServerUrl + "/data/dev", Environment.class);
		assertThat(env.getPropertySources()).hasSize(3);
		assertThat(env.getPropertySources().get(0).getName()).isEqualTo("s3:data-dev");
		assertThat(env.getPropertySources().get(1).getName()).isEqualTo("s3:data");
		assertThat(env.getPropertySources().get(2).getName()).isEqualTo("s3:application");
	}

	@Import(S3ProtocolResolver.class)
	static class S3AutoConfiguration {

		@Bean
		S3Client s3Client() {
			return S3Client.builder().region(Region.of(localstack.getRegion()))
					.endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.S3)).build();
		}

		@Bean
		S3OutputStreamProvider inMemoryBufferingS3StreamProvider(S3Client s3Client,
				Optional<S3ObjectContentTypeResolver> contentTypeResolver) {
			return new InMemoryBufferingS3OutputStreamProvider(s3Client,
					contentTypeResolver.orElseGet(PropertiesS3ObjectContentTypeResolver::new));
		}

	}

}
