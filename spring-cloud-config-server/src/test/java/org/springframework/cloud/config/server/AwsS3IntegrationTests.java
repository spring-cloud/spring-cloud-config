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

import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import io.awspring.cloud.context.config.annotation.ContextResourceLoaderConfiguration;
import org.json.JSONException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.test.TestConfigServerApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.util.SocketUtils;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Ryan Baxter
 */
@Testcontainers
public class AwsS3IntegrationTests {

	private static final int configServerPort = SocketUtils.findAvailableTcpPort();

	private static AmazonS3 s3Client;

	private static ConfigurableApplicationContext server;

	@Container
	static LocalStackContainer localstack = new LocalStackContainer(
			DockerImageName.parse("localstack/localstack:1.3.1")).withServices(LocalStackContainer.Service.S3)
					.withReuse(true);

	@BeforeAll
	public static void startConfigServer() throws IOException, InterruptedException, JSONException {
		server = SpringApplication.run(
				new Class[] { TestConfigServerApplication.class, ContextResourceLoaderConfiguration.class },
				new String[] { "--spring.config.name=server", "--spring.profiles.active=awss3",
						"--server.port=" + configServerPort,
						"--spring.cloud.config.server.awss3.endpoint="
								+ localstack.getEndpointOverride(LocalStackContainer.Service.S3).toString(),
						"--spring.cloud.config.server.awss3.bucket=test-bucket",
						"--spring.cloud.config.server.awss3.region=" + localstack.getRegion(),
						"--cloud.aws.s3.endpoint="
								+ localstack.getEndpointOverride(LocalStackContainer.Service.S3).toString(),
						"--cloud.aws.credentials.access-key=" + localstack.getAccessKey(),
						"--cloud.aws.credentials.secret-key=" + localstack.getSecretKey(),
						"--cloud.aws.region.static=" + localstack.getRegion() });

		AmazonS3ClientBuilder builder = AmazonS3ClientBuilder.standard();
		AwsClientBuilder.EndpointConfiguration endpointConfiguration = new AwsClientBuilder.EndpointConfiguration(
				localstack.getEndpointOverride(LocalStackContainer.Service.S3).toString(), "us-east-1");
		builder.withEndpointConfiguration(endpointConfiguration);
		s3Client = builder.build();
		s3Client.createBucket("test-bucket");
		s3Client.putObject("test-bucket", "data.txt", "this is a test");
		s3Client.putObject("test-bucket", "main/data.txt", "this is a test in main");
		s3Client.putObject("test-bucket", "application.properties", "foo=1");

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

	@AfterAll
	public static void after() {
		server.close();
	}

}
