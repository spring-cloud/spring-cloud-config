/*
 * Copyright 2016-2020 the original author or authors.
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretRequest;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretResponse;
import software.amazon.awssdk.services.secretsmanager.model.DeleteSecretRequest;
import software.amazon.awssdk.services.secretsmanager.model.RestoreSecretRequest;
import software.amazon.awssdk.services.secretsmanager.model.UpdateSecretVersionStageRequest;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.config.server.config.ConfigServerProperties;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.SECRETSMANAGER;

/**
 * @author Tejas Pandilwar
 * @author Matej Nedić
 */
@Testcontainers
@Tag("DockerRequired")
public class AwsSecretsManagerEnvironmentRepositoryTests {

	@Container
	private static final LocalStackContainer localstack = new LocalStackContainer(
			DockerImageName.parse("localstack/localstack:1.3.1")).withServices(SECRETSMANAGER);

	private static final Log log = LogFactory.getLog(AwsSecretsManagerEnvironmentRepository.class);

	private final StaticCredentialsProvider staticCredentialsProvider = StaticCredentialsProvider
			.create(AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey()));

	private final SecretsManagerClient smClient = SecretsManagerClient.builder()
			.region(Region.of(localstack.getRegion())).credentialsProvider(staticCredentialsProvider)
			.endpointOverride(localstack.getEndpointOverride(SECRETSMANAGER)).build();

	private final ConfigServerProperties configServerProperties = new ConfigServerProperties();

	private final AwsSecretsManagerEnvironmentProperties environmentProperties = new AwsSecretsManagerEnvironmentProperties();

	private final AwsSecretsManagerEnvironmentRepository repository = new AwsSecretsManagerEnvironmentRepository(
			smClient, configServerProperties, environmentProperties);

	private final AwsSecretsManagerEnvironmentProperties labeledEnvironmentProperties = new AwsSecretsManagerEnvironmentProperties();

	private final AwsSecretsManagerEnvironmentRepository labeledRepository = new AwsSecretsManagerEnvironmentRepository(
			smClient, configServerProperties, labeledEnvironmentProperties);

	private final AwsSecretsManagerEnvironmentProperties ignoreLabelEnvironmentProperties = new AwsSecretsManagerEnvironmentProperties() {
		{
			setIgnoreLabel(true);
		}
	};

	private final AwsSecretsManagerEnvironmentRepository ignoreLabelRepository = new AwsSecretsManagerEnvironmentRepository(
			smClient, configServerProperties, ignoreLabelEnvironmentProperties);

	private final ObjectMapper objectMapper = new ObjectMapper().configure(SerializationFeature.INDENT_OUTPUT, true);

	private final List<String> toBeRemoved = new ArrayList<>();

	private final List<String> markedForDeletion = new ArrayList<>();

	private static Map<String, String> getFooProperties() {
		return new HashMap<String, String>() {
			{
				put("s3.accessKey", "foo-s3");
				put("s3.secretKey", "ce945da2-740a-4915-a090-2978428dad05");
			}
		};
	}

	private static Map<String, String> getFooDefaultProperties() {
		return new HashMap<String, String>() {
			{
				put("s3.accessKey", "foo-default-s3");
				put("s3.secretKey", "8c3c58c9-daef-4d21-96b0-c2b68a7a8234");
			}
		};
	}

	private static Map<String, String> getFooProdProperties() {
		return new HashMap<String, String>() {
			{
				put("s3.accessKey", "foo-prod-s3");
				put("s3.secretKey", "42ca062d-8e4b-435e-9e4a-d058835817c0");
			}
		};
	}

	private static Map<String, String> getFooEastProperties() {
		return new HashMap<String, String>() {
			{
				put("s3.accessKey", "foo-east-s3");
				put("s3.secretKey", "657f6ac5-2e1c-487d-9d61-1df109b29edf");
			}
		};
	}

	private static Map<String, String> getApplicationDefaultProperties() {
		return new HashMap<String, String>() {
			{
				put("s3.accessKey", "application-shared-default-s3");
				put("s3.secretKey", "691972aa-68d2-4e55-8d9b-eedd4c63a998");
			}
		};
	}

	private static Map<String, String> getApplicationProdProperties() {
		return new HashMap<String, String>() {
			{
				put("s3.accessKey", "application-shared-prod-s3");
				put("s3.secretKey", "90c1dd88-5b20-41fa-a4e9-e1d638188732");
			}
		};
	}

	private static Map<String, String> getApplicationEastProperties() {
		return new HashMap<String, String>() {
			{
				put("s3.accessKey", "application-east-s3");
				put("s3.secretKey", "236e01a7-623b-40f4-88c1-eb4d89229dd6");
			}
		};
	}

	private static Map<String, String> getApplicationProperties() {
		return new HashMap<String, String>() {
			{
				put("s3.accessKey", "application-shared-s3");
				put("s3.secretKey", "25300773-eb3b-4ace-b6fc-500c87331da7");
			}
		};
	}

	private static Map<String, String> getApplicationReleaseProperties() {
		return new HashMap<String, String>() {
			{
				put("s3.accessKey", "application-shared-s3");
				put("s3.secretKey", "f616d232-e777-11ec-8fea-0242ac120002");
			}
		};
	}

	private static Map<String, String> getApplicationDefaultReleaseProperties() {
		return new HashMap<String, String>() {
			{
				put("s3.accessKey", "application-shared-default-s3");
				put("s3.secretKey", "02db4214-e778-11ec-8fea-0242ac120002");
			}
		};
	}

	private static Map<String, String> getApplicationProdReleaseProperties() {
		return new HashMap<String, String>() {
			{
				put("s3.accessKey", "application-shared-prod-s3");
				put("s3.secretKey", "db0d3eae-e78b-11ec-8fea-0242ac120002");
			}
		};
	}

	private static Map<String, String> getApplicationEastReleaseProperties() {
		return new HashMap<String, String>() {
			{
				put("s3.accessKey", "application-east-s3");
				put("s3.secretKey", "e7e99834-e78b-11ec-8fea-0242ac120002");
			}
		};
	}

	private static Map<String, String> getFooReleaseProperties() {
		return new HashMap<String, String>() {
			{
				put("s3.accessKey", "foo-s3");
				put("s3.secretKey", "edec8728-e78b-11ec-8fea-0242ac120002");
			}
		};
	}

	private static Map<String, String> getFooDefaultReleaseProperties() {
		return new HashMap<String, String>() {
			{
				put("s3.accessKey", "foo-default-s3");
				put("s3.secretKey", "f3ebef4c-e78b-11ec-8fea-0242ac120002");
			}
		};
	}

	private static Map<String, String> getFooProdReleaseProperties() {
		return new HashMap<String, String>() {
			{
				put("s3.accessKey", "foo-prod-s3");
				put("s3.secretKey", "004ba75a-e78c-11ec-8fea-0242ac120002");
			}
		};
	}

	private static Map<String, String> getFooEastReleaseProperties() {
		return new HashMap<String, String>() {
			{
				put("s3.accessKey", "foo-east-s3");
				put("s3.secretKey", "044f287c-e78c-11ec-8fea-0242ac120002");
			}
		};
	}

	@AfterEach
	public void cleanUp() {
		markedForDeletion
				.forEach(value -> smClient.restoreSecret(RestoreSecretRequest.builder().secretId(value).build()));
		markedForDeletion.clear();

		toBeRemoved.forEach(value -> smClient
				.deleteSecret(DeleteSecretRequest.builder().secretId(value).forceDeleteWithoutRecovery(true).build()));
		toBeRemoved.clear();
	}

	@Test
	public void testFindOneWithNullApplicationAndNonExistingProfileAndNullLabelWhenDefaultLabelIsSet() {
		String application = null;
		String profile = randomAlphabetic(RandomUtils.nextInt(3, 25));
		String defaultApplication = configServerProperties.getDefaultApplicationName();
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);
		String defaultLabel = labeledEnvironmentProperties.getDefaultLabel();

		String applicationDefaultPropertiesName = "aws:secrets:/secret/application-default/";
		PropertySource applicationDefaultProperties = new PropertySource(applicationDefaultPropertiesName,
				getApplicationDefaultProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationProperties());

		Environment expectedEnv = new Environment(defaultApplication, profiles, defaultLabel, null, null);
		expectedEnv.addAll(Arrays.asList(applicationDefaultProperties, applicationProperties));

		putSecrets(expectedEnv);

		Environment resultEnv = labeledRepository.findOne(application, profile, defaultLabel);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithNullApplicationAndDefaultProfileAndNullLabelWhenDefaultLabelIsSet() {
		String application = null;
		String profile = configServerProperties.getDefaultProfile();
		String defaultApplication = configServerProperties.getDefaultApplicationName();
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);
		String defaultLabel = labeledEnvironmentProperties.getDefaultLabel();

		String applicationDefaultPropertiesName = "aws:secrets:/secret/application-default/";
		PropertySource applicationDefaultProperties = new PropertySource(applicationDefaultPropertiesName,
				getApplicationDefaultProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationProperties());

		Environment expectedEnv = new Environment(defaultApplication, profiles, defaultLabel, null, null);
		expectedEnv.addAll(Arrays.asList(applicationDefaultProperties, applicationProperties));

		putSecrets(expectedEnv);

		Environment resultEnv = labeledRepository.findOne(application, profile, defaultLabel);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithNullApplicationAndExistingProfileAndNullLabelWhenDefaultLabelIsSet() {
		String application = null;
		String profile = "prod";
		String defaultApplication = configServerProperties.getDefaultApplicationName();
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);
		String defaultLabel = labeledEnvironmentProperties.getDefaultLabel();

		String applicationProdPropertiesName = "aws:secrets:/secret/application-prod/";
		PropertySource applicationProdProperties = new PropertySource(applicationProdPropertiesName,
				getApplicationProdProperties());

		String applicationDefaultPropertiesName = "aws:secrets:/secret/application-default/";
		PropertySource applicationDefaultProperties = new PropertySource(applicationDefaultPropertiesName,
				getApplicationDefaultProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationProperties());

		Environment expectedEnv = new Environment(defaultApplication, profiles, defaultLabel, null, null);
		expectedEnv
				.addAll(Arrays.asList(applicationProdProperties, applicationDefaultProperties, applicationProperties));

		putSecrets(expectedEnv);

		Environment resultEnv = labeledRepository.findOne(application, profile, defaultLabel);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithDefaultApplicationAndNullProfileAndNullLabelWhenDefaultLabelIsSet() {
		String application = configServerProperties.getDefaultApplicationName();
		String profile = null;
		String defaultProfile = configServerProperties.getDefaultProfile();
		String[] profiles = StringUtils.commaDelimitedListToStringArray(defaultProfile);
		String defaultLabel = labeledEnvironmentProperties.getDefaultLabel();

		String applicationDefaultPropertiesName = "aws:secrets:/secret/application-default/";
		PropertySource applicationDefaultProperties = new PropertySource(applicationDefaultPropertiesName,
				getApplicationDefaultProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationProperties());

		Environment expectedEnv = new Environment(application, profiles, defaultLabel, null, null);
		expectedEnv.addAll(Arrays.asList(applicationDefaultProperties, applicationProperties));

		putSecrets(expectedEnv);

		Environment resultEnv = labeledRepository.findOne(application, profile, defaultLabel);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithDefaultApplicationAndDefaultProfileAndNullLabelWhenDefaultLabelIsSet() {
		String application = configServerProperties.getDefaultApplicationName();
		String profile = configServerProperties.getDefaultProfile();
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);
		String defaultLabel = labeledEnvironmentProperties.getDefaultLabel();

		String applicationDefaultPropertiesName = "aws:secrets:/secret/application-default/";
		PropertySource applicationDefaultProperties = new PropertySource(applicationDefaultPropertiesName,
				getApplicationDefaultProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationProperties());

		Environment expectedEnv = new Environment(application, profiles, defaultLabel, null, null);
		expectedEnv.addAll(Arrays.asList(applicationDefaultProperties, applicationProperties));

		putSecrets(expectedEnv);

		Environment resultEnv = labeledRepository.findOne(application, profile, defaultLabel);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithDefaultApplicationAndNonExistingProfileAndNullLabelWhenDefaultLabelIsSet() {
		String application = configServerProperties.getDefaultApplicationName();
		String profile = randomAlphabetic(RandomUtils.nextInt(3, 25));
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);
		String defaultLabel = labeledEnvironmentProperties.getDefaultLabel();

		String applicationDefaultPropertiesName = "aws:secrets:/secret/application-default/";
		PropertySource applicationDefaultProperties = new PropertySource(applicationDefaultPropertiesName,
				getApplicationDefaultProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationProperties());

		Environment expectedEnv = new Environment(application, profiles, defaultLabel, null, null);
		expectedEnv.addAll(Arrays.asList(applicationDefaultProperties, applicationProperties));

		putSecrets(expectedEnv);

		Environment resultEnv = labeledRepository.findOne(application, profile, defaultLabel);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithDefaultApplicationAndExistingProfileAndNullLabelWhenDefaultLabelIsSet() {
		String application = configServerProperties.getDefaultApplicationName();
		String profile = "prod";
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);
		String defaultLabel = labeledEnvironmentProperties.getDefaultLabel();

		String applicationProdPropertiesName = "aws:secrets:/secret/application-prod/";
		PropertySource applicationProdProperties = new PropertySource(applicationProdPropertiesName,
				getApplicationProdProperties());

		String applicationDefaultPropertiesName = "aws:secrets:/secret/application-default/";
		PropertySource applicationDefaultProperties = new PropertySource(applicationDefaultPropertiesName,
				getApplicationDefaultProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationProperties());

		Environment expectedEnv = new Environment(application, profiles, defaultLabel, null, null);
		expectedEnv
				.addAll(Arrays.asList(applicationProdProperties, applicationDefaultProperties, applicationProperties));

		putSecrets(expectedEnv);

		Environment resultEnv = labeledRepository.findOne(application, profile, defaultLabel);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithNonExistingApplicationAndNullProfileAndNullLabelWhenDefaultLabelIsSet() {
		String application = randomAlphabetic(RandomUtils.nextInt(3, 25));
		String profile = null;
		String defaultProfile = configServerProperties.getDefaultProfile();
		String[] profiles = StringUtils.commaDelimitedListToStringArray(defaultProfile);
		String defaultLabel = labeledEnvironmentProperties.getDefaultLabel();

		String applicationDefaultPropertiesName = "aws:secrets:/secret/application-default/";
		PropertySource applicationDefaultProperties = new PropertySource(applicationDefaultPropertiesName,
				getApplicationDefaultProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationProperties());

		Environment expectedEnv = new Environment(application, profiles, defaultLabel, null, null);
		expectedEnv.addAll(Arrays.asList(applicationDefaultProperties, applicationProperties));

		putSecrets(expectedEnv);

		Environment resultEnv = labeledRepository.findOne(application, profile, defaultLabel);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithNonExistingApplicationAndDefaultProfileAndNullLabelWhenDefaultLabelIsSet() {
		String application = randomAlphabetic(RandomUtils.nextInt(3, 25));
		String profile = configServerProperties.getDefaultProfile();
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);
		String defaultLabel = labeledEnvironmentProperties.getDefaultLabel();

		String applicationDefaultPropertiesName = "aws:secrets:/secret/application-default/";
		PropertySource applicationDefaultProperties = new PropertySource(applicationDefaultPropertiesName,
				getApplicationDefaultProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationProperties());

		Environment expectedEnv = new Environment(application, profiles, defaultLabel, null, null);
		expectedEnv.addAll(Arrays.asList(applicationDefaultProperties, applicationProperties));

		putSecrets(expectedEnv);

		Environment resultEnv = labeledRepository.findOne(application, profile, defaultLabel);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithNonExistingApplicationAndNonExistingProfileAndNullLabelWhenDefaultLabelIsSet() {
		String application = randomAlphabetic(RandomUtils.nextInt(3, 25));
		String profile = randomAlphabetic(RandomUtils.nextInt(3, 25));
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);
		String defaultLabel = labeledEnvironmentProperties.getDefaultLabel();

		String applicationDefaultPropertiesName = "aws:secrets:/secret/application-default/";
		PropertySource applicationDefaultProperties = new PropertySource(applicationDefaultPropertiesName,
				getApplicationDefaultProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationProperties());

		Environment expectedEnv = new Environment(application, profiles, defaultLabel, null, null);
		expectedEnv.addAll(Arrays.asList(applicationDefaultProperties, applicationProperties));

		putSecrets(expectedEnv);

		Environment resultEnv = labeledRepository.findOne(application, profile, defaultLabel);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithNonExistingApplicationAndExistingProfileAndNullLabelWhenDefaultLabelIsSet() {
		String application = randomAlphabetic(RandomUtils.nextInt(3, 25));
		String profile = "prod";
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);
		String defaultLabel = labeledEnvironmentProperties.getDefaultLabel();

		String applicationProdPropertiesName = "aws:secrets:/secret/application-prod/";
		PropertySource applicationProdProperties = new PropertySource(applicationProdPropertiesName,
				getApplicationProdProperties());

		String applicationDefaultPropertiesName = "aws:secrets:/secret/application-default/";
		PropertySource applicationDefaultProperties = new PropertySource(applicationDefaultPropertiesName,
				getApplicationDefaultProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationProperties());

		Environment expectedEnv = new Environment(application, profiles, defaultLabel, null, null);
		expectedEnv
				.addAll(Arrays.asList(applicationProdProperties, applicationDefaultProperties, applicationProperties));

		putSecrets(expectedEnv);

		Environment resultEnv = labeledRepository.findOne(application, profile, defaultLabel);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithExistingApplicationAndNullProfileAndNullLabelWhenDefaultLabelIsSet() {
		String application = "foo";
		String profile = null;
		String defaultProfile = configServerProperties.getDefaultProfile();
		String[] profiles = StringUtils.commaDelimitedListToStringArray(defaultProfile);
		String defaultLabel = labeledEnvironmentProperties.getDefaultLabel();

		String fooPropertiesName = "aws:secrets:/secret/foo/";
		PropertySource fooProperties = new PropertySource(fooPropertiesName, getFooProperties());

		String fooDefaultPropertiesName = "aws:secrets:/secret/foo-default/";
		PropertySource fooDefaultProperties = new PropertySource(fooDefaultPropertiesName, getFooDefaultProperties());

		String applicationDefaultPropertiesName = "aws:secrets:/secret/application-default/";
		PropertySource applicationDefaultProperties = new PropertySource(applicationDefaultPropertiesName,
				getApplicationDefaultProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationProperties());

		Environment expectedEnv = new Environment(application, profiles, defaultLabel, null, null);
		expectedEnv.addAll(Arrays.asList(fooDefaultProperties, applicationDefaultProperties, fooProperties,
				applicationProperties));

		putSecrets(expectedEnv);

		Environment resultEnv = labeledRepository.findOne(application, profile, defaultLabel);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithExistingApplicationAndDefaultProfileAndNullLabelWhenDefaultLabelIsSet() {
		String application = "foo";
		String profile = configServerProperties.getDefaultProfile();
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);
		String defaultLabel = labeledEnvironmentProperties.getDefaultLabel();

		String fooPropertiesName = "aws:secrets:/secret/foo/";
		PropertySource fooProperties = new PropertySource(fooPropertiesName, getFooProperties());

		String fooDefaultPropertiesName = "aws:secrets:/secret/foo-default/";
		PropertySource fooDefaultProperties = new PropertySource(fooDefaultPropertiesName, getFooDefaultProperties());

		String applicationDefaultPropertiesName = "aws:secrets:/secret/application-default/";
		PropertySource applicationDefaultProperties = new PropertySource(applicationDefaultPropertiesName,
				getApplicationDefaultProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationProperties());

		Environment expectedEnv = new Environment(application, profiles, defaultLabel, null, null);
		expectedEnv.addAll(Arrays.asList(fooDefaultProperties, applicationDefaultProperties, fooProperties,
				applicationProperties));

		putSecrets(expectedEnv);

		Environment resultEnv = labeledRepository.findOne(application, profile, defaultLabel);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithExistingApplicationAndNonExistingProfileAndNullLabelWhenDefaultLabelIsSet() {
		String application = "foo";
		String profile = randomAlphabetic(RandomUtils.nextInt(2, 25));
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);
		String defaultLabel = labeledEnvironmentProperties.getDefaultLabel();

		String fooPropertiesName = "aws:secrets:/secret/foo/";
		PropertySource fooProperties = new PropertySource(fooPropertiesName, getFooProperties());

		String fooDefaultPropertiesName = "aws:secrets:/secret/foo-default/";
		PropertySource fooDefaultProperties = new PropertySource(fooDefaultPropertiesName, getFooDefaultProperties());

		String applicationDefaultPropertiesName = "aws:secrets:/secret/application-default/";
		PropertySource applicationDefaultProperties = new PropertySource(applicationDefaultPropertiesName,
				getApplicationDefaultProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationProperties());

		Environment expectedEnv = new Environment(application, profiles, defaultLabel, null, null);
		expectedEnv.addAll(Arrays.asList(fooDefaultProperties, applicationDefaultProperties, fooProperties,
				applicationProperties));

		putSecrets(expectedEnv);

		Environment resultEnv = labeledRepository.findOne(application, profile, defaultLabel);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithExistingApplicationAndNonExistingProfileAndNoDefaultProfileAndNullLabelWhenDefaultLabelIsSet() {
		String application = "foo";
		String profile = randomAlphabetic(RandomUtils.nextInt(2, 25));
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);
		String defaultLabel = labeledEnvironmentProperties.getDefaultLabel();

		String fooPropertiesName = "aws:secrets:/secret/foo/";
		PropertySource fooProperties = new PropertySource(fooPropertiesName, getFooProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationProperties());

		Environment expectedEnv = new Environment(application, profiles, defaultLabel, null, null);
		expectedEnv.addAll(Arrays.asList(fooProperties, applicationProperties));

		putSecrets(expectedEnv);

		Environment resultEnv = labeledRepository.findOne(application, profile, defaultLabel);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithExistingApplicationAndNonExistingProfileAndNoDefaultProfileForFooAndNullLabelWhenDefaultLabelIsSet() {
		String application = "foo";
		String profile = randomAlphabetic(RandomUtils.nextInt(2, 25));
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);
		String defaultLabel = labeledEnvironmentProperties.getDefaultLabel();

		String fooPropertiesName = "aws:secrets:/secret/foo/";
		PropertySource fooProperties = new PropertySource(fooPropertiesName, getFooProperties());

		String applicationDefaultPropertiesName = "aws:secrets:/secret/application-default/";
		PropertySource applicationDefaultProperties = new PropertySource(applicationDefaultPropertiesName,
				getApplicationDefaultProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationProperties());

		Environment expectedEnv = new Environment(application, profiles, defaultLabel, null, null);
		expectedEnv.addAll(Arrays.asList(applicationDefaultProperties, fooProperties, applicationProperties));

		putSecrets(expectedEnv);

		Environment resultEnv = labeledRepository.findOne(application, profile, defaultLabel);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithExistingApplicationAndExistingProfileAndNullLabelWhenDefaultLabelIsSet() {
		String application = "foo";
		String profile = "prod";
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);
		String defaultLabel = labeledEnvironmentProperties.getDefaultLabel();

		String fooProdPropertiesName = "aws:secrets:/secret/foo-prod/";
		PropertySource fooProdProperties = new PropertySource(fooProdPropertiesName, getFooProdProperties());

		String fooPropertiesName = "aws:secrets:/secret/foo/";
		PropertySource fooProperties = new PropertySource(fooPropertiesName, getFooProperties());

		String fooDefaultPropertiesName = "aws:secrets:/secret/foo-default/";
		PropertySource fooDefaultProperties = new PropertySource(fooDefaultPropertiesName, getFooDefaultProperties());

		String applicationProdPropertiesName = "aws:secrets:/secret/application-prod/";
		PropertySource applicationProdProperties = new PropertySource(applicationProdPropertiesName,
				getApplicationProdProperties());

		String applicationDefaultPropertiesName = "aws:secrets:/secret/application-default/";
		PropertySource applicationDefaultProperties = new PropertySource(applicationDefaultPropertiesName,
				getApplicationDefaultProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationProperties());

		Environment expectedEnv = new Environment(application, profiles, defaultLabel, null, null);
		expectedEnv.addAll(Arrays.asList(fooProdProperties, applicationProdProperties, fooDefaultProperties,
				applicationDefaultProperties, fooProperties, applicationProperties));

		putSecrets(expectedEnv);

		Environment resultEnv = labeledRepository.findOne(application, profile, defaultLabel);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithExistingApplicationAndExistingProfileAndNoDefaultProfilesAndNullLabelWhenDefaultLabelIsSet() {
		String application = "foo";
		String profile = "prod";
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);
		String defaultLabel = labeledEnvironmentProperties.getDefaultLabel();

		String fooProdPropertiesName = "aws:secrets:/secret/foo-prod/";
		PropertySource fooProdProperties = new PropertySource(fooProdPropertiesName, getFooProdProperties());

		String fooPropertiesName = "aws:secrets:/secret/foo/";
		PropertySource fooProperties = new PropertySource(fooPropertiesName, getFooProperties());

		String applicationProdPropertiesName = "aws:secrets:/secret/application-prod/";
		PropertySource applicationProdProperties = new PropertySource(applicationProdPropertiesName,
				getApplicationProdProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationProperties());

		Environment expectedEnv = new Environment(application, profiles, defaultLabel, null, null);
		expectedEnv.addAll(
				Arrays.asList(fooProdProperties, applicationProdProperties, fooProperties, applicationProperties));

		putSecrets(expectedEnv);

		Environment resultEnv = labeledRepository.findOne(application, profile, defaultLabel);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithExistingApplicationAndMultipleExistingProfileAndNullLabelWhenDefaultLabelIsSet() {
		String application = "foo";
		String profile = "prod,east";
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);
		String defaultLabel = labeledEnvironmentProperties.getDefaultLabel();

		String fooProdPropertiesName = "aws:secrets:/secret/foo-prod/";
		PropertySource fooProdProperties = new PropertySource(fooProdPropertiesName, getFooProdProperties());

		String fooEastPropertiesName = "aws:secrets:/secret/foo-east/";
		PropertySource fooEastProperties = new PropertySource(fooEastPropertiesName, getFooEastProperties());

		String fooPropertiesName = "aws:secrets:/secret/foo/";
		PropertySource fooProperties = new PropertySource(fooPropertiesName, getFooProperties());

		String fooDefaultPropertiesName = "aws:secrets:/secret/foo-default/";
		PropertySource fooDefaultProperties = new PropertySource(fooDefaultPropertiesName, getFooDefaultProperties());

		String applicationProdPropertiesName = "aws:secrets:/secret/application-prod/";
		PropertySource applicationProdProperties = new PropertySource(applicationProdPropertiesName,
				getApplicationProdProperties());

		String applicationDefaultPropertiesName = "aws:secrets:/secret/application-default/";
		PropertySource applicationDefaultProperties = new PropertySource(applicationDefaultPropertiesName,
				getApplicationDefaultProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationProperties());

		String applicationEastPropertiesName = "aws:secrets:/secret/application-east/";
		PropertySource applicationEastProperties = new PropertySource(applicationEastPropertiesName,
				getApplicationEastProperties());

		Environment expectedEnv = new Environment(application, profiles, defaultLabel, null, null);
		expectedEnv.addAll(Arrays.asList(fooEastProperties, applicationEastProperties, fooProdProperties,
				applicationProdProperties, fooDefaultProperties, applicationDefaultProperties, fooProperties,
				applicationProperties));

		putSecrets(expectedEnv);

		Environment resultEnv = labeledRepository.findOne(application, profile, defaultLabel);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithExistingApplicationAndMultipleExistingProfileAndNoDefaultsAndNullLabelWhenDefaultLabelIsSet() {
		String application = "foo";
		String profile = "prod,east";
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);
		String defaultLabel = labeledEnvironmentProperties.getDefaultLabel();

		String fooProdPropertiesName = "aws:secrets:/secret/foo-prod/";
		PropertySource fooProdProperties = new PropertySource(fooProdPropertiesName, getFooProdProperties());

		String fooEastPropertiesName = "aws:secrets:/secret/foo-east/";
		PropertySource fooEastProperties = new PropertySource(fooEastPropertiesName, getFooEastProperties());

		String fooPropertiesName = "aws:secrets:/secret/foo/";
		PropertySource fooProperties = new PropertySource(fooPropertiesName, getFooProperties());

		String applicationProdPropertiesName = "aws:secrets:/secret/application-prod/";
		PropertySource applicationProdProperties = new PropertySource(applicationProdPropertiesName,
				getApplicationProdProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationProperties());

		String applicationEastPropertiesName = "aws:secrets:/secret/application-east/";
		PropertySource applicationEastProperties = new PropertySource(applicationEastPropertiesName,
				getApplicationEastProperties());

		Environment expectedEnv = new Environment(application, profiles, defaultLabel, null, null);
		expectedEnv.addAll(Arrays.asList(fooEastProperties, applicationEastProperties, fooProdProperties,
				applicationProdProperties, fooProperties, applicationProperties));

		putSecrets(expectedEnv);

		Environment resultEnv = labeledRepository.findOne(application, profile, defaultLabel);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithNullApplicationAndNullProfileAndNonExistingLabelWhenDefaultLabelIsSet() {
		String application = null;
		String profile = null;
		String label = randomAlphabetic(RandomUtils.nextInt(3, 25));
		String defaultApplication = configServerProperties.getDefaultApplicationName();
		String defaultProfile = configServerProperties.getDefaultProfile();
		String[] profiles = StringUtils.commaDelimitedListToStringArray(defaultProfile);

		Environment expectedEnv = new Environment(defaultApplication, profiles, label, null, null);

		putSecrets(expectedEnv);

		Environment resultEnv = labeledRepository.findOne(application, profile, label);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithNullApplicationAndNonExistingProfileAndNonExistingLabelWhenDefaultLabelIsSet() {
		String application = null;
		String profile = randomAlphabetic(RandomUtils.nextInt(3, 25));
		String label = randomAlphabetic(RandomUtils.nextInt(3, 25));
		String defaultApplication = configServerProperties.getDefaultApplicationName();
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		Environment expectedEnv = new Environment(defaultApplication, profiles, label, null, null);

		putSecrets(expectedEnv);

		Environment resultEnv = labeledRepository.findOne(application, profile, label);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithNullApplicationAndDefaultProfileAndNonExistingLabelWhenDefaultLabelIsSet() {
		String application = null;
		String profile = configServerProperties.getDefaultProfile();
		String label = randomAlphabetic(RandomUtils.nextInt(3, 25));
		String defaultApplication = configServerProperties.getDefaultApplicationName();
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		Environment expectedEnv = new Environment(defaultApplication, profiles, label, null, null);

		putSecrets(expectedEnv);

		Environment resultEnv = labeledRepository.findOne(application, profile, label);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithNullApplicationAndExistingProfileAndNonExistingLabelWhenDefaultLabelIsSet() {
		String application = null;
		String profile = "prod";
		String label = randomAlphabetic(RandomUtils.nextInt(3, 25));
		String defaultApplication = configServerProperties.getDefaultApplicationName();
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		Environment expectedEnv = new Environment(defaultApplication, profiles, label, null, null);

		putSecrets(expectedEnv);

		Environment resultEnv = labeledRepository.findOne(application, profile, label);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithDefaultApplicationAndNullProfileAndNonExistingLabelWhenDefaultLabelIsSet() {
		String application = configServerProperties.getDefaultApplicationName();
		String profile = null;
		String label = randomAlphabetic(RandomUtils.nextInt(3, 25));
		String defaultProfile = configServerProperties.getDefaultProfile();
		String[] profiles = StringUtils.commaDelimitedListToStringArray(defaultProfile);

		Environment expectedEnv = new Environment(application, profiles, label, null, null);

		putSecrets(expectedEnv);

		Environment resultEnv = labeledRepository.findOne(application, profile, label);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithDefaultApplicationAndDefaultProfileAndNonExistingLabelWhenDefaultLabelIsSet() {
		String application = configServerProperties.getDefaultApplicationName();
		String profile = configServerProperties.getDefaultProfile();
		String label = randomAlphabetic(RandomUtils.nextInt(3, 25));
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		Environment expectedEnv = new Environment(application, profiles, label, null, null);

		putSecrets(expectedEnv);

		Environment resultEnv = labeledRepository.findOne(application, profile, label);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithDefaultApplicationAndNonExistingProfileAndNonExistingLabelWhenDefaultLabelIsSet() {
		String application = configServerProperties.getDefaultApplicationName();
		String profile = randomAlphabetic(RandomUtils.nextInt(3, 25));
		String label = randomAlphabetic(RandomUtils.nextInt(3, 25));
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		Environment expectedEnv = new Environment(application, profiles, label, null, null);

		putSecrets(expectedEnv);

		Environment resultEnv = labeledRepository.findOne(application, profile, label);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithDefaultApplicationAndExistingProfileAndNonExistingLabelWhenDefaultLabelIsSet() {
		String application = configServerProperties.getDefaultApplicationName();
		String profile = "prod";
		String label = randomAlphabetic(RandomUtils.nextInt(3, 25));
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		Environment expectedEnv = new Environment(application, profiles, label, null, null);

		putSecrets(expectedEnv);

		Environment resultEnv = labeledRepository.findOne(application, profile, label);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithNonExistingApplicationAndNullProfileAndNonExistingLabelWhenDefaultLabelIsSet() {
		String application = randomAlphabetic(RandomUtils.nextInt(3, 25));
		String profile = null;
		String label = randomAlphabetic(RandomUtils.nextInt(3, 25));
		String defaultProfile = configServerProperties.getDefaultProfile();
		String[] profiles = StringUtils.commaDelimitedListToStringArray(defaultProfile);

		Environment expectedEnv = new Environment(application, profiles, label, null, null);

		putSecrets(expectedEnv);

		Environment resultEnv = labeledRepository.findOne(application, profile, label);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithNonExistingApplicationAndDefaultProfileAndNonExistingLabelWhenDefaultLabelIsSet() {
		String application = randomAlphabetic(RandomUtils.nextInt(3, 25));
		String profile = configServerProperties.getDefaultProfile();
		String label = randomAlphabetic(RandomUtils.nextInt(3, 25));
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		Environment expectedEnv = new Environment(application, profiles, label, null, null);

		putSecrets(expectedEnv);

		Environment resultEnv = labeledRepository.findOne(application, profile, label);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithNonExistingApplicationAndNonExistingProfileAndNonExistingLabelWhenDefaultLabelIsSet() {
		String application = randomAlphabetic(RandomUtils.nextInt(3, 25));
		String profile = randomAlphabetic(RandomUtils.nextInt(3, 25));
		String label = randomAlphabetic(RandomUtils.nextInt(3, 25));
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		Environment expectedEnv = new Environment(application, profiles, label, null, null);

		putSecrets(expectedEnv);

		Environment resultEnv = labeledRepository.findOne(application, profile, label);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithNonExistingApplicationAndExistingProfileAndNonExistingLabelWhenDefaultLabelIsSet() {
		String application = randomAlphabetic(RandomUtils.nextInt(3, 25));
		String profile = "prod";
		String label = randomAlphabetic(RandomUtils.nextInt(3, 25));
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		Environment expectedEnv = new Environment(application, profiles, label, null, null);

		putSecrets(expectedEnv);

		Environment resultEnv = labeledRepository.findOne(application, profile, label);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithExistingApplicationAndNullProfileAndNonExistingLabelWhenDefaultLabelIsSet() {
		String application = "foo";
		String profile = null;
		String label = randomAlphabetic(RandomUtils.nextInt(3, 25));
		String defaultProfile = configServerProperties.getDefaultProfile();
		String[] profiles = StringUtils.commaDelimitedListToStringArray(defaultProfile);

		Environment expectedEnv = new Environment(application, profiles, label, null, null);

		putSecrets(expectedEnv);

		Environment resultEnv = labeledRepository.findOne(application, profile, label);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithExistingApplicationAndDefaultProfileAndNonExistingLabelWhenDefaultLabelIsSet() {
		String application = "foo";
		String profile = configServerProperties.getDefaultProfile();
		String label = randomAlphabetic(RandomUtils.nextInt(3, 25));
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		Environment expectedEnv = new Environment(application, profiles, label, null, null);

		putSecrets(expectedEnv);

		Environment resultEnv = labeledRepository.findOne(application, profile, label);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithExistingApplicationAndNonExistingProfileAndNonExistingLabelWhenDefaultLabelIsSet() {
		String application = "foo";
		String profile = randomAlphabetic(RandomUtils.nextInt(2, 25));
		String label = randomAlphabetic(RandomUtils.nextInt(3, 25));
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		Environment expectedEnv = new Environment(application, profiles, label, null, null);

		putSecrets(expectedEnv);

		Environment resultEnv = labeledRepository.findOne(application, profile, label);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithExistingApplicationAndNonExistingProfileAndNoDefaultProfileAndNonExistingLabelWhenDefaultLabelIsSet() {
		String application = "foo";
		String profile = randomAlphabetic(RandomUtils.nextInt(2, 25));
		String label = randomAlphabetic(RandomUtils.nextInt(3, 25));
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		Environment expectedEnv = new Environment(application, profiles, label, null, null);

		putSecrets(expectedEnv);

		Environment resultEnv = labeledRepository.findOne(application, profile, label);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithExistingApplicationAndNonExistingProfileAndNoDefaultProfileForFooAndNonExistingLabelWhenDefaultLabelIsSet() {
		String application = "foo";
		String profile = randomAlphabetic(RandomUtils.nextInt(2, 25));
		String label = randomAlphabetic(RandomUtils.nextInt(3, 25));
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		Environment expectedEnv = new Environment(application, profiles, label, null, null);

		putSecrets(expectedEnv);

		Environment resultEnv = labeledRepository.findOne(application, profile, label);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithExistingApplicationAndExistingProfileAndNonExistingLabelWhenDefaultLabelIsSet() {
		String application = "foo";
		String profile = "prod";
		String label = randomAlphabetic(RandomUtils.nextInt(3, 25));
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		Environment expectedEnv = new Environment(application, profiles, label, null, null);

		putSecrets(expectedEnv);

		Environment resultEnv = labeledRepository.findOne(application, profile, label);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithExistingApplicationAndExistingProfileAndNoDefaultProfilesAndNonExistingLabelWhenDefaultLabelIsSet() {
		String application = "foo";
		String profile = "prod";
		String label = randomAlphabetic(RandomUtils.nextInt(3, 25));
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		Environment expectedEnv = new Environment(application, profiles, label, null, null);

		putSecrets(expectedEnv);

		Environment resultEnv = labeledRepository.findOne(application, profile, label);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithExistingApplicationAndMultipleExistingProfileAndNonExistingLabelWhenDefaultLabelIsSet() {
		String application = "foo";
		String profile = "prod,east";
		String label = randomAlphabetic(RandomUtils.nextInt(3, 25));
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		Environment expectedEnv = new Environment(application, profiles, label, null, null);

		putSecrets(expectedEnv);

		Environment resultEnv = labeledRepository.findOne(application, profile, label);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithExistingApplicationAndMultipleExistingProfileAndNoDefaultsAndNonExistingLabelWhenDefaultLabelIsSet() {
		String application = "foo";
		String profile = "prod,east";
		String label = randomAlphabetic(RandomUtils.nextInt(3, 25));
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		Environment expectedEnv = new Environment(application, profiles, label, null, null);

		putSecrets(expectedEnv);

		Environment resultEnv = labeledRepository.findOne(application, profile, label);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithNullApplicationAndNullProfileAndExistingLabelWhenDefaultLabelIsSet() {
		String application = null;
		String profile = null;
		String label = "release";
		String defaultApplication = configServerProperties.getDefaultApplicationName();
		String defaultProfile = configServerProperties.getDefaultProfile();
		String[] profiles = StringUtils.commaDelimitedListToStringArray(defaultProfile);

		String applicationDefaultPropertiesName = "aws:secrets:/secret/application-default/";
		PropertySource applicationDefaultProperties = new PropertySource(applicationDefaultPropertiesName,
				getApplicationDefaultReleaseProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationReleaseProperties());

		Environment expectedEnv = new Environment(defaultApplication, profiles, label, null, null);
		expectedEnv.addAll(Arrays.asList(applicationDefaultProperties, applicationProperties));

		putSecrets(expectedEnv);

		Environment resultEnv = labeledRepository.findOne(application, profile, label);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithNullApplicationAndNonExistingProfileAndExistingLabelWhenDefaultLabelIsSet() {
		String application = null;
		String profile = randomAlphabetic(RandomUtils.nextInt(3, 25));
		String label = "release";
		String defaultApplication = configServerProperties.getDefaultApplicationName();
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		String applicationDefaultPropertiesName = "aws:secrets:/secret/application-default/";
		PropertySource applicationDefaultProperties = new PropertySource(applicationDefaultPropertiesName,
				getApplicationDefaultReleaseProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationReleaseProperties());

		Environment expectedEnv = new Environment(defaultApplication, profiles, label, null, null);
		expectedEnv.addAll(Arrays.asList(applicationDefaultProperties, applicationProperties));

		putSecrets(expectedEnv);

		Environment resultEnv = labeledRepository.findOne(application, profile, label);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithNullApplicationAndDefaultProfileAndExistingLabelWhenDefaultLabelIsSet() {
		String application = null;
		String profile = configServerProperties.getDefaultProfile();
		String defaultApplication = configServerProperties.getDefaultApplicationName();
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);
		String label = "release";

		String applicationDefaultPropertiesName = "aws:secrets:/secret/application-default/";
		PropertySource applicationDefaultProperties = new PropertySource(applicationDefaultPropertiesName,
				getApplicationDefaultReleaseProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationReleaseProperties());

		Environment expectedEnv = new Environment(defaultApplication, profiles, label, null, null);
		expectedEnv.addAll(Arrays.asList(applicationDefaultProperties, applicationProperties));

		putSecrets(expectedEnv);

		Environment resultEnv = labeledRepository.findOne(application, profile, label);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithNullApplicationAndExistingProfileAndExistingLabelWhenDefaultLabelIsSet() {
		String application = null;
		String profile = "prod";
		String label = "release";
		String defaultApplication = configServerProperties.getDefaultApplicationName();
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		String applicationProdPropertiesName = "aws:secrets:/secret/application-prod/";
		PropertySource applicationProdProperties = new PropertySource(applicationProdPropertiesName,
				getApplicationProdReleaseProperties());

		String applicationDefaultPropertiesName = "aws:secrets:/secret/application-default/";
		PropertySource applicationDefaultProperties = new PropertySource(applicationDefaultPropertiesName,
				getApplicationDefaultReleaseProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationReleaseProperties());

		Environment expectedEnv = new Environment(defaultApplication, profiles, label, null, null);
		expectedEnv
				.addAll(Arrays.asList(applicationProdProperties, applicationDefaultProperties, applicationProperties));

		putSecrets(expectedEnv);

		Environment resultEnv = labeledRepository.findOne(application, profile, label);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithDefaultApplicationAndNullProfileAndExistingLabelWhenDefaultLabelIsSet() {
		String application = configServerProperties.getDefaultApplicationName();
		String profile = null;
		String label = "release";
		String defaultProfile = configServerProperties.getDefaultProfile();
		String[] profiles = StringUtils.commaDelimitedListToStringArray(defaultProfile);

		String applicationDefaultPropertiesName = "aws:secrets:/secret/application-default/";
		PropertySource applicationDefaultProperties = new PropertySource(applicationDefaultPropertiesName,
				getApplicationDefaultReleaseProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationReleaseProperties());

		Environment expectedEnv = new Environment(application, profiles, label, null, null);
		expectedEnv.addAll(Arrays.asList(applicationDefaultProperties, applicationProperties));

		putSecrets(expectedEnv);

		Environment resultEnv = labeledRepository.findOne(application, profile, label);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithDefaultApplicationAndDefaultProfileAndExistingLabelWhenDefaultLabelIsSet() {
		String application = configServerProperties.getDefaultApplicationName();
		String profile = configServerProperties.getDefaultProfile();
		String label = "release";
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		String applicationDefaultPropertiesName = "aws:secrets:/secret/application-default/";
		PropertySource applicationDefaultProperties = new PropertySource(applicationDefaultPropertiesName,
				getApplicationDefaultReleaseProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationReleaseProperties());

		Environment expectedEnv = new Environment(application, profiles, label, null, null);
		expectedEnv.addAll(Arrays.asList(applicationDefaultProperties, applicationProperties));

		putSecrets(expectedEnv);

		Environment resultEnv = labeledRepository.findOne(application, profile, label);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithDefaultApplicationAndNonExistingProfileAndExistingLabelWhenDefaultLabelIsSet() {
		String application = configServerProperties.getDefaultApplicationName();
		String profile = randomAlphabetic(RandomUtils.nextInt(3, 25));
		String label = "release";
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		String applicationDefaultPropertiesName = "aws:secrets:/secret/application-default/";
		PropertySource applicationDefaultProperties = new PropertySource(applicationDefaultPropertiesName,
				getApplicationDefaultReleaseProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationReleaseProperties());

		Environment expectedEnv = new Environment(application, profiles, label, null, null);
		expectedEnv.addAll(Arrays.asList(applicationDefaultProperties, applicationProperties));

		putSecrets(expectedEnv);

		Environment resultEnv = labeledRepository.findOne(application, profile, label);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithDefaultApplicationAndExistingProfileAndExistingLabelWhenDefaultLabelIsSet() {
		String application = configServerProperties.getDefaultApplicationName();
		String profile = "prod";
		String label = "release";
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		String applicationProdPropertiesName = "aws:secrets:/secret/application-prod/";
		PropertySource applicationProdProperties = new PropertySource(applicationProdPropertiesName,
				getApplicationProdReleaseProperties());

		String applicationDefaultPropertiesName = "aws:secrets:/secret/application-default/";
		PropertySource applicationDefaultProperties = new PropertySource(applicationDefaultPropertiesName,
				getApplicationDefaultReleaseProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationReleaseProperties());

		Environment expectedEnv = new Environment(application, profiles, label, null, null);
		expectedEnv
				.addAll(Arrays.asList(applicationProdProperties, applicationDefaultProperties, applicationProperties));

		putSecrets(expectedEnv);

		Environment resultEnv = labeledRepository.findOne(application, profile, label);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithNonExistingApplicationAndNullProfileAndExistingLabelWhenDefaultLabelIsSet() {
		String application = randomAlphabetic(RandomUtils.nextInt(3, 25));
		String profile = null;
		String label = "release";
		String defaultProfile = configServerProperties.getDefaultProfile();
		String[] profiles = StringUtils.commaDelimitedListToStringArray(defaultProfile);

		String applicationDefaultPropertiesName = "aws:secrets:/secret/application-default/";
		PropertySource applicationDefaultProperties = new PropertySource(applicationDefaultPropertiesName,
				getApplicationDefaultReleaseProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationReleaseProperties());

		Environment expectedEnv = new Environment(application, profiles, label, null, null);
		expectedEnv.addAll(Arrays.asList(applicationDefaultProperties, applicationProperties));

		putSecrets(expectedEnv);

		Environment resultEnv = labeledRepository.findOne(application, profile, label);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithNonExistingApplicationAndDefaultProfileAndExistingLabelWhenDefaultLabelIsSet() {
		String application = randomAlphabetic(RandomUtils.nextInt(3, 25));
		String profile = configServerProperties.getDefaultProfile();
		String label = "release";
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		String applicationDefaultPropertiesName = "aws:secrets:/secret/application-default/";
		PropertySource applicationDefaultProperties = new PropertySource(applicationDefaultPropertiesName,
				getApplicationDefaultReleaseProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationReleaseProperties());

		Environment expectedEnv = new Environment(application, profiles, label, null, null);
		expectedEnv.addAll(Arrays.asList(applicationDefaultProperties, applicationProperties));

		putSecrets(expectedEnv);

		Environment resultEnv = labeledRepository.findOne(application, profile, label);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithNonExistingApplicationAndNonExistingProfileAndExistingLabelWhenDefaultLabelIsSet() {
		String application = randomAlphabetic(RandomUtils.nextInt(3, 25));
		String profile = randomAlphabetic(RandomUtils.nextInt(3, 25));
		String label = "release";
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		String applicationDefaultPropertiesName = "aws:secrets:/secret/application-default/";
		PropertySource applicationDefaultProperties = new PropertySource(applicationDefaultPropertiesName,
				getApplicationDefaultReleaseProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationReleaseProperties());

		Environment expectedEnv = new Environment(application, profiles, label, null, null);
		expectedEnv.addAll(Arrays.asList(applicationDefaultProperties, applicationProperties));

		putSecrets(expectedEnv);

		Environment resultEnv = labeledRepository.findOne(application, profile, label);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithNonExistingApplicationAndExistingProfileAndExistingLabelWhenDefaultLabelIsSet() {
		String application = randomAlphabetic(RandomUtils.nextInt(3, 25));
		String profile = "prod";
		String label = "release";
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		String applicationProdPropertiesName = "aws:secrets:/secret/application-prod/";
		PropertySource applicationProdProperties = new PropertySource(applicationProdPropertiesName,
				getApplicationProdReleaseProperties());

		String applicationDefaultPropertiesName = "aws:secrets:/secret/application-default/";
		PropertySource applicationDefaultProperties = new PropertySource(applicationDefaultPropertiesName,
				getApplicationDefaultReleaseProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationReleaseProperties());

		Environment expectedEnv = new Environment(application, profiles, label, null, null);
		expectedEnv
				.addAll(Arrays.asList(applicationProdProperties, applicationDefaultProperties, applicationProperties));

		putSecrets(expectedEnv);

		Environment resultEnv = labeledRepository.findOne(application, profile, label);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithExistingApplicationAndNullProfileAndExistingLabelWhenDefaultLabelIsSet() {
		String application = "foo";
		String profile = null;
		String label = "release";
		String defaultProfile = configServerProperties.getDefaultProfile();
		String[] profiles = StringUtils.commaDelimitedListToStringArray(defaultProfile);

		String fooPropertiesName = "aws:secrets:/secret/foo/";
		PropertySource fooProperties = new PropertySource(fooPropertiesName, getFooReleaseProperties());

		String fooDefaultPropertiesName = "aws:secrets:/secret/foo-default/";
		PropertySource fooDefaultProperties = new PropertySource(fooDefaultPropertiesName,
				getFooDefaultReleaseProperties());

		String applicationDefaultPropertiesName = "aws:secrets:/secret/application-default/";
		PropertySource applicationDefaultProperties = new PropertySource(applicationDefaultPropertiesName,
				getApplicationDefaultReleaseProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationReleaseProperties());

		Environment expectedEnv = new Environment(application, profiles, label, null, null);
		expectedEnv.addAll(Arrays.asList(fooDefaultProperties, applicationDefaultProperties, fooProperties,
				applicationProperties));

		putSecrets(expectedEnv);

		Environment resultEnv = labeledRepository.findOne(application, profile, label);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithExistingApplicationAndDefaultProfileAndExistingLabelWhenDefaultLabelIsSet() {
		String application = "foo";
		String profile = configServerProperties.getDefaultProfile();
		String label = "release";
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		String fooPropertiesName = "aws:secrets:/secret/foo/";
		PropertySource fooProperties = new PropertySource(fooPropertiesName, getFooReleaseProperties());

		String fooDefaultPropertiesName = "aws:secrets:/secret/foo-default/";
		PropertySource fooDefaultProperties = new PropertySource(fooDefaultPropertiesName,
				getFooDefaultReleaseProperties());

		String applicationDefaultPropertiesName = "aws:secrets:/secret/application-default/";
		PropertySource applicationDefaultProperties = new PropertySource(applicationDefaultPropertiesName,
				getApplicationDefaultReleaseProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationReleaseProperties());

		Environment expectedEnv = new Environment(application, profiles, label, null, null);
		expectedEnv.addAll(Arrays.asList(fooDefaultProperties, applicationDefaultProperties, fooProperties,
				applicationProperties));

		putSecrets(expectedEnv);

		Environment resultEnv = labeledRepository.findOne(application, profile, label);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithExistingApplicationAndNonExistingProfileAndExistingLabelWhenDefaultLabelIsSet() {
		String application = "foo";
		String profile = randomAlphabetic(RandomUtils.nextInt(2, 25));
		String label = "release";
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		String fooPropertiesName = "aws:secrets:/secret/foo/";
		PropertySource fooProperties = new PropertySource(fooPropertiesName, getFooReleaseProperties());

		String fooDefaultPropertiesName = "aws:secrets:/secret/foo-default/";
		PropertySource fooDefaultProperties = new PropertySource(fooDefaultPropertiesName,
				getFooDefaultReleaseProperties());

		String applicationDefaultPropertiesName = "aws:secrets:/secret/application-default/";
		PropertySource applicationDefaultProperties = new PropertySource(applicationDefaultPropertiesName,
				getApplicationDefaultReleaseProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationReleaseProperties());

		Environment expectedEnv = new Environment(application, profiles, label, null, null);
		expectedEnv.addAll(Arrays.asList(fooDefaultProperties, applicationDefaultProperties, fooProperties,
				applicationProperties));

		putSecrets(expectedEnv);

		Environment resultEnv = labeledRepository.findOne(application, profile, label);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithExistingApplicationAndNonExistingProfileAndNoDefaultProfileAndExistingLabelWhenDefaultLabelIsSet() {
		String application = "foo";
		String profile = randomAlphabetic(RandomUtils.nextInt(2, 25));
		String label = "release";
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		String fooPropertiesName = "aws:secrets:/secret/foo/";
		PropertySource fooProperties = new PropertySource(fooPropertiesName, getFooReleaseProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationReleaseProperties());

		Environment expectedEnv = new Environment(application, profiles, label, null, null);
		expectedEnv.addAll(Arrays.asList(fooProperties, applicationProperties));

		putSecrets(expectedEnv);

		Environment resultEnv = labeledRepository.findOne(application, profile, label);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithExistingApplicationAndNonExistingProfileAndNoDefaultProfileForFooAndExistingLabelWhenDefaultLabelIsSet() {
		String application = "foo";
		String profile = randomAlphabetic(RandomUtils.nextInt(2, 25));
		String label = "release";
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		String fooPropertiesName = "aws:secrets:/secret/foo/";
		PropertySource fooProperties = new PropertySource(fooPropertiesName, getFooReleaseProperties());

		String applicationDefaultPropertiesName = "aws:secrets:/secret/application-default/";
		PropertySource applicationDefaultProperties = new PropertySource(applicationDefaultPropertiesName,
				getApplicationDefaultReleaseProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationReleaseProperties());

		Environment expectedEnv = new Environment(application, profiles, label, null, null);
		expectedEnv.addAll(Arrays.asList(applicationDefaultProperties, fooProperties, applicationProperties));

		putSecrets(expectedEnv);

		Environment resultEnv = labeledRepository.findOne(application, profile, label);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithExistingApplicationAndExistingProfileAndExistingLabelWhenDefaultLabelIsSet() {
		String application = "foo";
		String profile = "prod";
		String label = "release";
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		String fooProdPropertiesName = "aws:secrets:/secret/foo-prod/";
		PropertySource fooProdProperties = new PropertySource(fooProdPropertiesName, getFooProdReleaseProperties());

		String fooPropertiesName = "aws:secrets:/secret/foo/";
		PropertySource fooProperties = new PropertySource(fooPropertiesName, getFooReleaseProperties());

		String fooDefaultPropertiesName = "aws:secrets:/secret/foo-default/";
		PropertySource fooDefaultProperties = new PropertySource(fooDefaultPropertiesName,
				getFooDefaultReleaseProperties());

		String applicationProdPropertiesName = "aws:secrets:/secret/application-prod/";
		PropertySource applicationProdProperties = new PropertySource(applicationProdPropertiesName,
				getApplicationProdReleaseProperties());

		String applicationDefaultPropertiesName = "aws:secrets:/secret/application-default/";
		PropertySource applicationDefaultProperties = new PropertySource(applicationDefaultPropertiesName,
				getApplicationDefaultReleaseProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationReleaseProperties());

		Environment expectedEnv = new Environment(application, profiles, label, null, null);
		expectedEnv.addAll(Arrays.asList(fooProdProperties, applicationProdProperties, fooDefaultProperties,
				applicationDefaultProperties, fooProperties, applicationProperties));

		putSecrets(expectedEnv);

		Environment resultEnv = labeledRepository.findOne(application, profile, label);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithExistingApplicationAndExistingProfileAndNoDefaultProfilesAndExistingLabelWhenDefaultLabelIsSet() {
		String application = "foo";
		String profile = "prod";
		String label = "release";
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		String fooProdPropertiesName = "aws:secrets:/secret/foo-prod/";
		PropertySource fooProdProperties = new PropertySource(fooProdPropertiesName, getFooProdReleaseProperties());

		String fooPropertiesName = "aws:secrets:/secret/foo/";
		PropertySource fooProperties = new PropertySource(fooPropertiesName, getFooReleaseProperties());

		String applicationProdPropertiesName = "aws:secrets:/secret/application-prod/";
		PropertySource applicationProdProperties = new PropertySource(applicationProdPropertiesName,
				getApplicationProdReleaseProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationReleaseProperties());

		Environment expectedEnv = new Environment(application, profiles, label, null, null);
		expectedEnv.addAll(
				Arrays.asList(fooProdProperties, applicationProdProperties, fooProperties, applicationProperties));

		putSecrets(expectedEnv);

		Environment resultEnv = labeledRepository.findOne(application, profile, label);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithExistingApplicationAndMultipleExistingProfileAndExistingLabelWhenDefaultLabelIsSet() {
		String application = "foo";
		String profile = "prod,east";
		String label = "release";
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		String fooProdPropertiesName = "aws:secrets:/secret/foo-prod/";
		PropertySource fooProdProperties = new PropertySource(fooProdPropertiesName, getFooProdReleaseProperties());

		String fooEastPropertiesName = "aws:secrets:/secret/foo-east/";
		PropertySource fooEastProperties = new PropertySource(fooEastPropertiesName, getFooEastReleaseProperties());

		String fooPropertiesName = "aws:secrets:/secret/foo/";
		PropertySource fooProperties = new PropertySource(fooPropertiesName, getFooReleaseProperties());

		String fooDefaultPropertiesName = "aws:secrets:/secret/foo-default/";
		PropertySource fooDefaultProperties = new PropertySource(fooDefaultPropertiesName,
				getFooDefaultReleaseProperties());

		String applicationProdPropertiesName = "aws:secrets:/secret/application-prod/";
		PropertySource applicationProdProperties = new PropertySource(applicationProdPropertiesName,
				getApplicationProdReleaseProperties());

		String applicationDefaultPropertiesName = "aws:secrets:/secret/application-default/";
		PropertySource applicationDefaultProperties = new PropertySource(applicationDefaultPropertiesName,
				getApplicationDefaultReleaseProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationReleaseProperties());

		String applicationEastPropertiesName = "aws:secrets:/secret/application-east/";
		PropertySource applicationEastProperties = new PropertySource(applicationEastPropertiesName,
				getApplicationEastReleaseProperties());

		Environment expectedEnv = new Environment(application, profiles, label, null, null);
		expectedEnv.addAll(Arrays.asList(fooEastProperties, applicationEastProperties, fooProdProperties,
				applicationProdProperties, fooDefaultProperties, applicationDefaultProperties, fooProperties,
				applicationProperties));

		putSecrets(expectedEnv);

		Environment resultEnv = labeledRepository.findOne(application, profile, label);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithExistingApplicationAndMultipleExistingProfileAndNoDefaultsAndExistingLabelWhenDefaultLabelIsSet() {
		String application = "foo";
		String profile = "prod,east";
		String label = "release";
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		String fooProdPropertiesName = "aws:secrets:/secret/foo-prod/";
		PropertySource fooProdProperties = new PropertySource(fooProdPropertiesName, getFooProdReleaseProperties());

		String fooEastPropertiesName = "aws:secrets:/secret/foo-east/";
		PropertySource fooEastProperties = new PropertySource(fooEastPropertiesName, getFooEastReleaseProperties());

		String fooPropertiesName = "aws:secrets:/secret/foo/";
		PropertySource fooProperties = new PropertySource(fooPropertiesName, getFooReleaseProperties());

		String applicationProdPropertiesName = "aws:secrets:/secret/application-prod/";
		PropertySource applicationProdProperties = new PropertySource(applicationProdPropertiesName,
				getApplicationProdReleaseProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationReleaseProperties());

		String applicationEastPropertiesName = "aws:secrets:/secret/application-east/";
		PropertySource applicationEastProperties = new PropertySource(applicationEastPropertiesName,
				getApplicationEastReleaseProperties());

		Environment expectedEnv = new Environment(application, profiles, label, null, null);
		expectedEnv.addAll(Arrays.asList(fooEastProperties, applicationEastProperties, fooProdProperties,
				applicationProdProperties, fooProperties, applicationProperties));

		putSecrets(expectedEnv);

		Environment resultEnv = labeledRepository.findOne(application, profile, label);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithExistingApplicationAndExistingProfileAndExistingLabelWhenIgnoreLabelIsSet() {
		String application = "foo";
		String profile = "prod";
		String label = "release";
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		String fooProdPropertiesName = "aws:secrets:/secret/foo-prod/";
		PropertySource fooProdProperties = new PropertySource(fooProdPropertiesName, getFooProdReleaseProperties());

		String fooPropertiesName = "aws:secrets:/secret/foo/";
		PropertySource fooProperties = new PropertySource(fooPropertiesName, getFooReleaseProperties());

		String fooDefaultPropertiesName = "aws:secrets:/secret/foo-default/";
		PropertySource fooDefaultProperties = new PropertySource(fooDefaultPropertiesName,
				getFooDefaultReleaseProperties());

		String applicationProdPropertiesName = "aws:secrets:/secret/application-prod/";
		PropertySource applicationProdProperties = new PropertySource(applicationProdPropertiesName,
				getApplicationProdReleaseProperties());

		String applicationDefaultPropertiesName = "aws:secrets:/secret/application-default/";
		PropertySource applicationDefaultProperties = new PropertySource(applicationDefaultPropertiesName,
				getApplicationDefaultReleaseProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationReleaseProperties());

		Environment expectedEnv = new Environment(application, profiles, null, null, null);
		expectedEnv.addAll(Arrays.asList(fooProdProperties, applicationProdProperties, fooDefaultProperties,
				applicationDefaultProperties, fooProperties, applicationProperties));

		putSecrets(expectedEnv);

		Environment resultEnv = ignoreLabelRepository.findOne(application, profile, label);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithNullApplicationAndNullProfile() {
		String application = null;
		String profile = null;
		String defaultApplication = configServerProperties.getDefaultApplicationName();
		String defaultProfile = configServerProperties.getDefaultProfile();
		String[] profiles = StringUtils.commaDelimitedListToStringArray(defaultProfile);

		String applicationDefaultPropertiesName = "aws:secrets:/secret/application-default/";
		PropertySource applicationDefaultProperties = new PropertySource(applicationDefaultPropertiesName,
				getApplicationDefaultProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationProperties());

		Environment environment = new Environment(defaultApplication, profiles, null, null, null);
		environment.addAll(Arrays.asList(applicationDefaultProperties, applicationProperties));

		putSecrets(environment);

		Environment resultEnv = repository.findOne(application, profile, null);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(environment);
	}

	@Test
	public void testFindOneWithNullApplicationAndNullProfileAndNullLabelWhenDefaultLabelIsSet() {
		String application = null;
		String profile = null;
		String defaultApplication = configServerProperties.getDefaultApplicationName();
		String defaultProfile = configServerProperties.getDefaultProfile();
		String[] profiles = StringUtils.commaDelimitedListToStringArray(defaultProfile);
		String defaultLabel = labeledEnvironmentProperties.getDefaultLabel();

		String applicationDefaultPropertiesName = "aws:secrets:/secret/application-default/";
		PropertySource applicationDefaultProperties = new PropertySource(applicationDefaultPropertiesName,
				getApplicationDefaultProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationProperties());

		Environment expectedEnv = new Environment(defaultApplication, profiles, defaultLabel, null, null);
		expectedEnv.addAll(Arrays.asList(applicationDefaultProperties, applicationProperties));

		putSecrets(expectedEnv);

		Environment resultEnv = labeledRepository.findOne(application, profile, defaultLabel);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithNullApplicationAndNonExistingProfile() {
		String application = null;
		String profile = randomAlphabetic(RandomUtils.nextInt(3, 25));
		String defaultApplication = configServerProperties.getDefaultApplicationName();
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		String applicationDefaultPropertiesName = "aws:secrets:/secret/application-default/";
		PropertySource applicationDefaultProperties = new PropertySource(applicationDefaultPropertiesName,
				getApplicationDefaultProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationProperties());

		Environment environment = new Environment(defaultApplication, profiles, null, null, null);
		environment.addAll(Arrays.asList(applicationDefaultProperties, applicationProperties));

		putSecrets(environment);

		Environment resultEnv = repository.findOne(application, profile, null);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(environment);
	}

	@Test
	public void testFindOneWithNullApplicationAndDefaultProfile() {
		String application = null;
		String profile = configServerProperties.getDefaultProfile();
		String defaultApplication = configServerProperties.getDefaultApplicationName();
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		String applicationDefaultPropertiesName = "aws:secrets:/secret/application-default/";
		PropertySource applicationDefaultProperties = new PropertySource(applicationDefaultPropertiesName,
				getApplicationDefaultProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationProperties());

		Environment environment = new Environment(defaultApplication, profiles, null, null, null);
		environment.addAll(Arrays.asList(applicationDefaultProperties, applicationProperties));

		putSecrets(environment);

		Environment resultEnv = repository.findOne(application, profile, null);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(environment);
	}

	@Test
	public void testFindOneWithNullApplicationAndExistingProfile() {
		String application = null;
		String profile = "prod";
		String defaultApplication = configServerProperties.getDefaultApplicationName();
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		String applicationProdPropertiesName = "aws:secrets:/secret/application-prod/";
		PropertySource applicationProdProperties = new PropertySource(applicationProdPropertiesName,
				getApplicationProdProperties());

		String applicationDefaultPropertiesName = "aws:secrets:/secret/application-default/";
		PropertySource applicationDefaultProperties = new PropertySource(applicationDefaultPropertiesName,
				getApplicationDefaultProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationProperties());

		Environment environment = new Environment(defaultApplication, profiles, null, null, null);
		environment
				.addAll(Arrays.asList(applicationProdProperties, applicationDefaultProperties, applicationProperties));

		putSecrets(environment);

		Environment resultEnv = repository.findOne(application, profile, null);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(environment);
	}

	@Test
	public void testFindOneWithDefaultApplicationAndNullProfile() {
		String application = configServerProperties.getDefaultApplicationName();
		String profile = null;
		String defaultProfile = configServerProperties.getDefaultProfile();
		String[] profiles = StringUtils.commaDelimitedListToStringArray(defaultProfile);

		String applicationDefaultPropertiesName = "aws:secrets:/secret/application-default/";
		PropertySource applicationDefaultProperties = new PropertySource(applicationDefaultPropertiesName,
				getApplicationDefaultProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationProperties());

		Environment environment = new Environment(application, profiles, null, null, null);
		environment.addAll(Arrays.asList(applicationDefaultProperties, applicationProperties));

		putSecrets(environment);

		Environment resultEnv = repository.findOne(application, profile, null);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(environment);
	}

	@Test
	public void testFindOneWithDefaultApplicationAndDefaultProfile() {
		String application = configServerProperties.getDefaultApplicationName();
		String profile = configServerProperties.getDefaultProfile();
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		String applicationDefaultPropertiesName = "aws:secrets:/secret/application-default/";
		PropertySource applicationDefaultProperties = new PropertySource(applicationDefaultPropertiesName,
				getApplicationDefaultProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationProperties());

		Environment environment = new Environment(application, profiles, null, null, null);
		environment.addAll(Arrays.asList(applicationDefaultProperties, applicationProperties));

		putSecrets(environment);

		Environment resultEnv = repository.findOne(application, profile, null);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(environment);
	}

	@Test
	public void testFindOneWithDefaultApplicationAndNonExistingProfile() {
		String application = configServerProperties.getDefaultApplicationName();
		String profile = randomAlphabetic(RandomUtils.nextInt(3, 25));
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		String applicationDefaultPropertiesName = "aws:secrets:/secret/application-default/";
		PropertySource applicationDefaultProperties = new PropertySource(applicationDefaultPropertiesName,
				getApplicationDefaultProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationProperties());

		Environment environment = new Environment(application, profiles, null, null, null);
		environment.addAll(Arrays.asList(applicationDefaultProperties, applicationProperties));

		putSecrets(environment);

		Environment resultEnv = repository.findOne(application, profile, null);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(environment);
	}

	@Test
	public void testFindOneWithDefaultApplicationAndExistingProfile() {
		String application = configServerProperties.getDefaultApplicationName();
		String profile = "prod";
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		String applicationProdPropertiesName = "aws:secrets:/secret/application-prod/";
		PropertySource applicationProdProperties = new PropertySource(applicationProdPropertiesName,
				getApplicationProdProperties());

		String applicationDefaultPropertiesName = "aws:secrets:/secret/application-default/";
		PropertySource applicationDefaultProperties = new PropertySource(applicationDefaultPropertiesName,
				getApplicationDefaultProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationProperties());

		Environment environment = new Environment(application, profiles, null, null, null);
		environment
				.addAll(Arrays.asList(applicationProdProperties, applicationDefaultProperties, applicationProperties));

		putSecrets(environment);

		Environment resultEnv = repository.findOne(application, profile, null);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(environment);
	}

	@Test
	public void testFindOneWithNonExistingApplicationAndNullProfile() {
		String application = randomAlphabetic(RandomUtils.nextInt(3, 25));
		String profile = null;
		String defaultProfile = configServerProperties.getDefaultProfile();
		String[] profiles = StringUtils.commaDelimitedListToStringArray(defaultProfile);

		String applicationDefaultPropertiesName = "aws:secrets:/secret/application-default/";
		PropertySource applicationDefaultProperties = new PropertySource(applicationDefaultPropertiesName,
				getApplicationDefaultProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationProperties());

		Environment environment = new Environment(application, profiles, null, null, null);
		environment.addAll(Arrays.asList(applicationDefaultProperties, applicationProperties));

		putSecrets(environment);

		Environment resultEnv = repository.findOne(application, profile, null);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(environment);
	}

	@Test
	public void testFindOneWithNonExistingApplicationAndDefaultProfile() {
		String application = randomAlphabetic(RandomUtils.nextInt(3, 25));
		String profile = configServerProperties.getDefaultProfile();
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		String applicationDefaultPropertiesName = "aws:secrets:/secret/application-default/";
		PropertySource applicationDefaultProperties = new PropertySource(applicationDefaultPropertiesName,
				getApplicationDefaultProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationProperties());

		Environment environment = new Environment(application, profiles, null, null, null);
		environment.addAll(Arrays.asList(applicationDefaultProperties, applicationProperties));

		putSecrets(environment);

		Environment resultEnv = repository.findOne(application, profile, null);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(environment);
	}

	@Test
	public void testFindOneWithNonExistingApplicationAndNonExistingProfile() {
		String application = randomAlphabetic(RandomUtils.nextInt(3, 25));
		String profile = randomAlphabetic(RandomUtils.nextInt(3, 25));
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		String applicationDefaultPropertiesName = "aws:secrets:/secret/application-default/";
		PropertySource applicationDefaultProperties = new PropertySource(applicationDefaultPropertiesName,
				getApplicationDefaultProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationProperties());

		Environment environment = new Environment(application, profiles, null, null, null);
		environment.addAll(Arrays.asList(applicationDefaultProperties, applicationProperties));

		putSecrets(environment);

		Environment resultEnv = repository.findOne(application, profile, null);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(environment);
	}

	@Test
	public void testFindOneWithNonExistingApplicationAndExistingProfile() {
		String application = randomAlphabetic(RandomUtils.nextInt(3, 25));
		String profile = "prod";
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		String applicationProdPropertiesName = "aws:secrets:/secret/application-prod/";
		PropertySource applicationProdProperties = new PropertySource(applicationProdPropertiesName,
				getApplicationProdProperties());

		String applicationDefaultPropertiesName = "aws:secrets:/secret/application-default/";
		PropertySource applicationDefaultProperties = new PropertySource(applicationDefaultPropertiesName,
				getApplicationDefaultProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationProperties());

		Environment environment = new Environment(application, profiles, null, null, null);
		environment
				.addAll(Arrays.asList(applicationProdProperties, applicationDefaultProperties, applicationProperties));

		putSecrets(environment);

		Environment resultEnv = repository.findOne(application, profile, null);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(environment);
	}

	@Test
	public void testFindOneWithExistingApplicationAndNullProfile() {
		String application = "foo";
		String profile = null;
		String defaultProfile = configServerProperties.getDefaultProfile();
		String[] profiles = StringUtils.commaDelimitedListToStringArray(defaultProfile);

		String fooPropertiesName = "aws:secrets:/secret/foo/";
		PropertySource fooProperties = new PropertySource(fooPropertiesName, getFooProperties());

		String fooDefaultPropertiesName = "aws:secrets:/secret/foo-default/";
		PropertySource fooDefaultProperties = new PropertySource(fooDefaultPropertiesName, getFooDefaultProperties());

		String applicationDefaultPropertiesName = "aws:secrets:/secret/application-default/";
		PropertySource applicationDefaultProperties = new PropertySource(applicationDefaultPropertiesName,
				getApplicationDefaultProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationProperties());

		Environment environment = new Environment(application, profiles, null, null, null);
		environment.addAll(Arrays.asList(fooDefaultProperties, applicationDefaultProperties, fooProperties,
				applicationProperties));

		putSecrets(environment);

		Environment resultEnv = repository.findOne(application, profile, null);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(environment);
	}

	@Test
	public void testFindOneWithExistingApplicationAndDefaultProfile() {
		String application = "foo";
		String profile = configServerProperties.getDefaultProfile();
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		String fooPropertiesName = "aws:secrets:/secret/foo/";
		PropertySource fooProperties = new PropertySource(fooPropertiesName, getFooProperties());

		String fooDefaultPropertiesName = "aws:secrets:/secret/foo-default/";
		PropertySource fooDefaultProperties = new PropertySource(fooDefaultPropertiesName, getFooDefaultProperties());

		String applicationDefaultPropertiesName = "aws:secrets:/secret/application-default/";
		PropertySource applicationDefaultProperties = new PropertySource(applicationDefaultPropertiesName,
				getApplicationDefaultProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationProperties());

		Environment environment = new Environment(application, profiles, null, null, null);
		environment.addAll(Arrays.asList(fooDefaultProperties, applicationDefaultProperties, fooProperties,
				applicationProperties));

		putSecrets(environment);

		Environment resultEnv = repository.findOne(application, profile, null);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(environment);
	}

	@Test
	public void testFindOneWithExistingApplicationAndNonExistingProfile() {
		String application = "foo";
		String profile = randomAlphabetic(RandomUtils.nextInt(2, 25));
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		String fooPropertiesName = "aws:secrets:/secret/foo/";
		PropertySource fooProperties = new PropertySource(fooPropertiesName, getFooProperties());

		String fooDefaultPropertiesName = "aws:secrets:/secret/foo-default/";
		PropertySource fooDefaultProperties = new PropertySource(fooDefaultPropertiesName, getFooDefaultProperties());

		String applicationDefaultPropertiesName = "aws:secrets:/secret/application-default/";
		PropertySource applicationDefaultProperties = new PropertySource(applicationDefaultPropertiesName,
				getApplicationDefaultProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationProperties());

		Environment environment = new Environment(application, profiles, null, null, null);
		environment.addAll(Arrays.asList(fooDefaultProperties, applicationDefaultProperties, fooProperties,
				applicationProperties));

		putSecrets(environment);

		Environment resultEnv = repository.findOne(application, profile, null);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(environment);
	}

	@Test
	public void testFindOneWithExistingApplicationAndNonExistingProfileAndNoDefaultProfile() {
		String application = "foo";
		String profile = randomAlphabetic(RandomUtils.nextInt(2, 25));
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		String fooPropertiesName = "aws:secrets:/secret/foo/";
		PropertySource fooProperties = new PropertySource(fooPropertiesName, getFooProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationProperties());

		Environment environment = new Environment(application, profiles, null, null, null);
		environment.addAll(Arrays.asList(fooProperties, applicationProperties));

		putSecrets(environment);

		Environment resultEnv = repository.findOne(application, profile, null);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(environment);
	}

	@Test
	public void testFindOneWithExistingApplicationAndNonExistingProfileAndNoDefaultProfileForFoo() {
		String application = "foo";
		String profile = randomAlphabetic(RandomUtils.nextInt(2, 25));
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		String fooPropertiesName = "aws:secrets:/secret/foo/";
		PropertySource fooProperties = new PropertySource(fooPropertiesName, getFooProperties());

		String applicationDefaultPropertiesName = "aws:secrets:/secret/application-default/";
		PropertySource applicationDefaultProperties = new PropertySource(applicationDefaultPropertiesName,
				getApplicationDefaultProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationProperties());

		Environment environment = new Environment(application, profiles, null, null, null);
		environment.addAll(Arrays.asList(applicationDefaultProperties, fooProperties, applicationProperties));

		putSecrets(environment);

		Environment resultEnv = repository.findOne(application, profile, null);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(environment);
	}

	@Test
	public void testFindOneWithExistingApplicationAndExistingProfile() {
		String application = "foo";
		String profile = "prod";
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		String fooProdPropertiesName = "aws:secrets:/secret/foo-prod/";
		PropertySource fooProdProperties = new PropertySource(fooProdPropertiesName, getFooProdProperties());

		String fooPropertiesName = "aws:secrets:/secret/foo/";
		PropertySource fooProperties = new PropertySource(fooPropertiesName, getFooProperties());

		String fooDefaultPropertiesName = "aws:secrets:/secret/foo-default/";
		PropertySource fooDefaultProperties = new PropertySource(fooDefaultPropertiesName, getFooDefaultProperties());

		String applicationProdPropertiesName = "aws:secrets:/secret/application-prod/";
		PropertySource applicationProdProperties = new PropertySource(applicationProdPropertiesName,
				getApplicationProdProperties());

		String applicationDefaultPropertiesName = "aws:secrets:/secret/application-default/";
		PropertySource applicationDefaultProperties = new PropertySource(applicationDefaultPropertiesName,
				getApplicationDefaultProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationProperties());

		Environment environment = new Environment(application, profiles, null, null, null);
		environment.addAll(Arrays.asList(fooProdProperties, applicationProdProperties, fooDefaultProperties,
				applicationDefaultProperties, fooProperties, applicationProperties));

		putSecrets(environment);

		Environment resultEnv = repository.findOne(application, profile, null);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(environment);
	}

	@Test
	public void testFindOneWithExistingApplicationAndExistingProfileAndNoDefaultProfiles() {
		String application = "foo";
		String profile = "prod";
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		String fooProdPropertiesName = "aws:secrets:/secret/foo-prod/";
		PropertySource fooProdProperties = new PropertySource(fooProdPropertiesName, getFooProdProperties());

		String fooPropertiesName = "aws:secrets:/secret/foo/";
		PropertySource fooProperties = new PropertySource(fooPropertiesName, getFooProperties());

		String applicationProdPropertiesName = "aws:secrets:/secret/application-prod/";
		PropertySource applicationProdProperties = new PropertySource(applicationProdPropertiesName,
				getApplicationProdProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationProperties());

		Environment environment = new Environment(application, profiles, null, null, null);
		environment.addAll(
				Arrays.asList(fooProdProperties, applicationProdProperties, fooProperties, applicationProperties));

		putSecrets(environment);

		Environment resultEnv = repository.findOne(application, profile, null);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(environment);
	}

	@Test
	public void testFindOneWithExistingApplicationAndMultipleExistingProfile() {
		String application = "foo";
		String profile = "prod,east";
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		String fooProdPropertiesName = "aws:secrets:/secret/foo-prod/";
		PropertySource fooProdProperties = new PropertySource(fooProdPropertiesName, getFooProdProperties());

		String fooEastPropertiesName = "aws:secrets:/secret/foo-east/";
		PropertySource fooEastProperties = new PropertySource(fooEastPropertiesName, getFooEastProperties());

		String fooPropertiesName = "aws:secrets:/secret/foo/";
		PropertySource fooProperties = new PropertySource(fooPropertiesName, getFooProperties());

		String fooDefaultPropertiesName = "aws:secrets:/secret/foo-default/";
		PropertySource fooDefaultProperties = new PropertySource(fooDefaultPropertiesName, getFooDefaultProperties());

		String applicationProdPropertiesName = "aws:secrets:/secret/application-prod/";
		PropertySource applicationProdProperties = new PropertySource(applicationProdPropertiesName,
				getApplicationProdProperties());

		String applicationDefaultPropertiesName = "aws:secrets:/secret/application-default/";
		PropertySource applicationDefaultProperties = new PropertySource(applicationDefaultPropertiesName,
				getApplicationDefaultProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationProperties());

		String applicationEastPropertiesName = "aws:secrets:/secret/application-east/";
		PropertySource applicationEastProperties = new PropertySource(applicationEastPropertiesName,
				getApplicationEastProperties());

		Environment environment = new Environment(application, profiles, null, null, null);
		environment.addAll(Arrays.asList(fooEastProperties, applicationEastProperties, fooProdProperties,
				applicationProdProperties, fooDefaultProperties, applicationDefaultProperties, fooProperties,
				applicationProperties));

		putSecrets(environment);

		Environment resultEnv = repository.findOne(application, profile, null);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(environment);
	}

	@Test
	public void testFindOneWithExistingApplicationAndMultipleExistingProfileAndNoDefaults() {
		String application = "foo";
		String profile = "prod,east";
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		String fooProdPropertiesName = "aws:secrets:/secret/foo-prod/";
		PropertySource fooProdProperties = new PropertySource(fooProdPropertiesName, getFooProdProperties());

		String fooEastPropertiesName = "aws:secrets:/secret/foo-east/";
		PropertySource fooEastProperties = new PropertySource(fooEastPropertiesName, getFooEastProperties());

		String fooPropertiesName = "aws:secrets:/secret/foo/";
		PropertySource fooProperties = new PropertySource(fooPropertiesName, getFooProperties());

		String applicationProdPropertiesName = "aws:secrets:/secret/application-prod/";
		PropertySource applicationProdProperties = new PropertySource(applicationProdPropertiesName,
				getApplicationProdProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationProperties());

		String applicationEastPropertiesName = "aws:secrets:/secret/application-east/";
		PropertySource applicationEastProperties = new PropertySource(applicationEastPropertiesName,
				getApplicationEastProperties());

		Environment environment = new Environment(application, profiles, null, null, null);
		environment.addAll(Arrays.asList(fooEastProperties, applicationEastProperties, fooProdProperties,
				applicationProdProperties, fooProperties, applicationProperties));

		putSecrets(environment);

		Environment resultEnv = repository.findOne(application, profile, null);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(environment);
	}

	@Test
	public void testFindOneWithExistingApplicationAndOrderedMultipleExistingProfileAndNoDefaults() {
		String application = "foo";
		String profile = "profile1,profile2,profile3";
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		String fooProfile1PropertiesName = "aws:secrets:/secret/foo-profile1/";
		PropertySource fooProfile1Properties = new PropertySource(fooProfile1PropertiesName, getFooDefaultProperties());

		String fooProfile2PropertiesName = "aws:secrets:/secret/foo-profile2/";
		PropertySource fooProfile2Properties = new PropertySource(fooProfile2PropertiesName, getFooEastProperties());

		String fooProfile3PropertiesName = "aws:secrets:/secret/foo-profile3/";
		PropertySource fooProfile3Properties = new PropertySource(fooProfile3PropertiesName, getFooProdProperties());

		Environment environment = new Environment(application, profiles, null, null, null);
		environment.addAll(Arrays.asList(fooProfile3Properties, fooProfile2Properties, fooProfile1Properties));

		putSecrets(environment);

		Environment resultEnv = repository.findOne(application, profile, null);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(environment);
	}

	@Test
	public void testFindOneWithOverrides() {
		String application = configServerProperties.getDefaultApplicationName();
		String profile = configServerProperties.getDefaultProfile();
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		Map<String, String> overrides = new HashMap<String, String>(4) {
			{
				put("s3.accessKey", "override-s3");
				put("s3.secretKey", "e7437a7d-dfa0-48a4-86d6-668fc0c157a7");
			}
		};

		configServerProperties.setOverrides(overrides);
		PropertySource overrideProperties = new PropertySource("overrides", overrides);

		String applicationDefaultPropertiesName = "aws:secrets:/secret/application-default/";
		PropertySource applicationDefaultProperties = new PropertySource(applicationDefaultPropertiesName,
				getApplicationDefaultProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationProperties());

		Environment environment = new Environment(application, profiles, null, null, null);
		environment.addAll(Arrays.asList(overrideProperties, applicationDefaultProperties, applicationProperties));

		putSecrets(environment);

		Environment resultEnv = repository.findOne(application, profile, null);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(environment);
	}

	@Test
	public void testFindOneWithNoSecretsStored() {
		String application = configServerProperties.getDefaultApplicationName();
		String profile = configServerProperties.getDefaultProfile();
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		Environment environment = new Environment(application, profiles, null, null, null);
		putSecrets(environment);

		Environment resultEnv = repository.findOne(application, profile, null);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(environment);
	}

	@Test
	public void testFindOneWithExistingApplicationAndNonExistingProfileAndNoDefaultProfileForFooMarkedForDeletion() {
		String application = "foo";
		String profile = randomAlphabetic(RandomUtils.nextInt(2, 25));
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		String fooPropertiesName = "aws:secrets:/secret/foo/";
		PropertySource fooProperties = new PropertySource(fooPropertiesName, getFooProperties());

		String applicationDefaultPropertiesName = "aws:secrets:/secret/application-default/";
		PropertySource applicationDefaultProperties = new PropertySource(applicationDefaultPropertiesName,
				getApplicationDefaultProperties());

		String applicationPropertiesName = "aws:secrets:/secret/application/";
		PropertySource applicationProperties = new PropertySource(applicationPropertiesName,
				getApplicationProperties());

		Environment environment = new Environment(application, profiles, null, null, null);
		environment.addAll(Arrays.asList(applicationDefaultProperties, fooProperties, applicationProperties));

		putSecrets(environment);
		deleteSecrets(environment);

		Environment emptyEnvironment = new Environment(application, profiles, null, null, null);

		Environment resultEnv = repository.findOne(application, profile, null);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(emptyEnvironment);
	}

	@Test
	public void factoryCustomizableWithRegion() {
		AwsSecretsManagerEnvironmentRepositoryFactory factory = new AwsSecretsManagerEnvironmentRepositoryFactory(
				new ConfigServerProperties());
		AwsSecretsManagerEnvironmentProperties properties = new AwsSecretsManagerEnvironmentProperties();
		properties.setRegion("us-east-1");
		AwsSecretsManagerEnvironmentRepository repository = factory.build(properties);
		assertThat(repository).isNotNull();
	}

	@Test
	public void factoryCustomizableWithRegionAndEndpoint() {
		AwsSecretsManagerEnvironmentRepositoryFactory factory = new AwsSecretsManagerEnvironmentRepositoryFactory(
				new ConfigServerProperties());
		AwsSecretsManagerEnvironmentProperties properties = new AwsSecretsManagerEnvironmentProperties();
		properties.setRegion("us-east-1");
		properties.setEndpoint("https://myawsendpoint/");
		AwsSecretsManagerEnvironmentRepository repository = factory.build(properties);
		assertThat(repository).isNotNull();
	}

	private void putSecrets(Environment environment) {
		String label = environment.getLabel() != null ? environment.getLabel()
				: environmentProperties.getDefaultLabel();
		for (PropertySource ps : environment.getPropertySources()) {
			String path = StringUtils.delete(ps.getName(), environmentProperties.getOrigin());
			String secrets = getSecrets(ps);
			CreateSecretResponse response = smClient
					.createSecret(CreateSecretRequest.builder().name(path).secretString(secrets).build());
			if (!ObjectUtils.isEmpty(label)) {
				smClient.updateSecretVersionStage(UpdateSecretVersionStageRequest.builder().secretId(path)
						.moveToVersionId(response.versionId()).versionStage(label).build());
			}
			toBeRemoved.add(path);
		}
	}

	private void deleteSecrets(Environment environment) {
		for (PropertySource ps : environment.getPropertySources()) {
			String path = StringUtils.delete(ps.getName(), environmentProperties.getOrigin());
			smClient.deleteSecret(DeleteSecretRequest.builder().secretId(path).recoveryWindowInDays(30L).build());
			markedForDeletion.add(path);
		}
	}

	private String getSecrets(PropertySource ps) {
		Map<String, String> map = (Map<String, String>) ps.getSource();
		try {
			return objectMapper.writeValueAsString(map);
		}
		catch (JsonProcessingException e) {
			log.error("Unable to generate secret string", e);
		}
		return "";
	}

}
