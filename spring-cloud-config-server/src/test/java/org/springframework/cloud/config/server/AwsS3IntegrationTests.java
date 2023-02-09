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

import org.json.JSONException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.server.test.TestConfigServerApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.util.TestSocketUtils;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Ryan Baxter
 */
@Testcontainers
public class AwsS3IntegrationTests {

	private static final int configServerPort = TestSocketUtils.findAvailableTcpPort();

	private static S3Client s3Client;

	private static ConfigurableApplicationContext server;

	@Container
	static LocalStackContainer localstack = new LocalStackContainer(
			DockerImageName.parse("localstack/localstack:1.3.1")).withServices(LocalStackContainer.Service.S3);

	@BeforeAll
	public static void startConfigServer() throws IOException, InterruptedException, JSONException {
		server = SpringApplication.run(new Class[] { TestConfigServerApplication.class },
				new String[] { "--spring.config.name=server", "--spring.profiles.active=awss3",
						"--server.port=" + configServerPort,
						"--spring.cloud.config.server.awss3.endpoint="
								+ localstack.getEndpointOverride(LocalStackContainer.Service.S3).toString(),
						"--spring.cloud.config.server.awss3.bucket=test-bucket",
						"--spring.cloud.config.server.awss3.region=" + localstack.getRegion(),
						"--spring.cloud.aws.endpoint="
								+ localstack.getEndpointOverride(LocalStackContainer.Service.S3).toString(),
						"--spring.cloud.aws.credentials.access-key=" + localstack.getAccessKey(),
						"--spring.cloud.aws.credentials.secret-key=" + localstack.getSecretKey(),
						"--spring.cloud.aws.region.static=" + localstack.getRegion() });

		if (server.containsBean(S3Client.class.getName())) {
			s3Client = server.getBean(S3Client.class);
			s3Client.createBucket((request) -> request.bucket("test-bucket"));
			s3Client.putObject((request) -> request.bucket("test-bucket").key("data.txt"),
					RequestBody.fromString("this is a test"));
			s3Client.putObject((request) -> request.bucket("test-bucket").key("main/data.txt"),
					RequestBody.fromString("this is a test in main"));
			s3Client.putObject((request) -> request.bucket("test-bucket").key("application.properties"),
					RequestBody.fromString("foo=1"));
		}

	}

	@Test
	@Disabled
	// TODO uncomment when we have an RC or GA release of Spring Cloud AWS with this fix
	// https://github.com/awspring/spring-cloud-aws/pull/652
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
