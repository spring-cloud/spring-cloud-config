/*
 * Copyright 2013-2020 the original author or authors.
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
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.RandomUtils;
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
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.DeleteParameterRequest;
import software.amazon.awssdk.services.ssm.model.Parameter;
import software.amazon.awssdk.services.ssm.model.ParameterType;
import software.amazon.awssdk.services.ssm.model.PutParameterRequest;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.config.server.config.ConfigServerProperties;
import org.springframework.core.Ordered;
import org.springframework.util.StringUtils;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.cloud.config.server.environment.AwsParameterStoreEnvironmentProperties.DEFAULT_PATH_SEPARATOR;
import static org.testcontainers.containers.localstack.LocalStackContainer.Service.SSM;

/**
 * @author Iulian Antohe
 * @author Matej Nedic
 */
@Testcontainers
@Tag("DockerRequired")
public class AwsParameterStoreEnvironmentRepositoryTests {

	@Container
	private static final LocalStackContainer localstack = new LocalStackContainer(
			DockerImageName.parse("localstack/localstack:1.3.1")).withServices(SSM);

	private final StaticCredentialsProvider staticCredentialsProvider = StaticCredentialsProvider
			.create(AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey()));

	private final SsmClient ssmClient = SsmClient.builder().region(Region.of(localstack.getRegion()))
			.credentialsProvider(staticCredentialsProvider).endpointOverride(localstack.getEndpointOverride(SSM))
			.build();

	private final ConfigServerProperties configServerProperties = new ConfigServerProperties();

	private final AwsParameterStoreEnvironmentProperties environmentProperties = new AwsParameterStoreEnvironmentProperties();

	private final AwsParameterStoreEnvironmentRepository repository = new AwsParameterStoreEnvironmentRepository(
			ssmClient, configServerProperties, environmentProperties);

	private final List<String> toBeRemoved = new ArrayList<>();

	@AfterEach
	public void cleanUp() {
		toBeRemoved.forEach(value -> ssmClient.deleteParameter(DeleteParameterRequest.builder().name(value).build()));
		toBeRemoved.clear();
	}

	@Test
	public void testFindOneWithNullApplicationAndNullProfile() {
		// Arrange
		String application = null;
		String profile = null;
		String defaultApp = configServerProperties.getDefaultApplicationName();
		String defaultProfile = configServerProperties.getDefaultProfile();
		String[] profiles = StringUtils.commaDelimitedListToStringArray(defaultProfile);

		String sharedDefaultParamsPsName = "aws:ssm:parameter:/config/application-default/";
		PropertySource sharedDefaultParamsPs = new PropertySource(sharedDefaultParamsPsName, SHARED_DEFAULT_PROPERTIES);

		String sharedParamsPsName = "aws:ssm:parameter:/config/application/";
		PropertySource sharedParamsPs = new PropertySource(sharedParamsPsName, SHARED_PROPERTIES);

		Environment expected = new Environment(defaultApp, profiles, null, null, null);
		expected.addAll(Arrays.asList(sharedDefaultParamsPs, sharedParamsPs));

		putParameters(expected);

		// Act
		Environment result = repository.findOne(application, profile, null);

		// Assert
		assertThat(result).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expected);
	}

	@Test
	public void testFindOneWithNullApplicationAndDefaultProfile() {
		// Arrange
		String application = null;
		String profile = configServerProperties.getDefaultProfile();
		String defaultApp = configServerProperties.getDefaultApplicationName();
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		String sharedDefaultParamsPsName = "aws:ssm:parameter:/config/application-default/";
		PropertySource sharedDefaultParamsPs = new PropertySource(sharedDefaultParamsPsName, SHARED_DEFAULT_PROPERTIES);

		String sharedParamsPsName = "aws:ssm:parameter:/config/application/";
		PropertySource sharedParamsPs = new PropertySource(sharedParamsPsName, SHARED_PROPERTIES);

		Environment expected = new Environment(defaultApp, profiles, null, null, null);
		expected.addAll(Arrays.asList(sharedDefaultParamsPs, sharedParamsPs));

		putParameters(expected);

		// Act
		Environment result = repository.findOne(application, profile, null);

		// Assert
		assertThat(result).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expected);
	}

	@Test
	public void testFindOneWithNullApplicationAndNonExistentProfile() {
		// Arrange
		String application = null;
		String profile = randomAlphabetic(RandomUtils.nextInt(3, 33));
		String defaultApp = configServerProperties.getDefaultApplicationName();
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		String name = "aws:ssm:parameter:/config/application/";
		PropertySource ps = new PropertySource(name, SHARED_PROPERTIES);

		Environment expected = new Environment(defaultApp, profiles, null, null, null);
		expected.add(ps);

		putParameters(expected);

		// Act
		Environment result = repository.findOne(application, profile, null);

		// Assert
		assertThat(result).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expected);
	}

	@Test
	public void testFindOneWithNullApplicationAndExistentProfile() {
		// Arrange
		String application = null;
		String profile = "production";
		String defaultApp = configServerProperties.getDefaultApplicationName();
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		String sharedProdParamsPsName = "aws:ssm:parameter:/config/application-production/";
		PropertySource sharedProdParamsPs = new PropertySource(sharedProdParamsPsName, SHARED_PRODUCTION_PROPERTIES);

		String sharedParamsPsName = "aws:ssm:parameter:/config/application/";
		PropertySource sharedParamsPs = new PropertySource(sharedParamsPsName, SHARED_PROPERTIES);

		Environment expected = new Environment(defaultApp, profiles, null, null, null);
		expected.addAll(Arrays.asList(sharedProdParamsPs, sharedParamsPs));

		putParameters(expected);

		// Act
		Environment result = repository.findOne(application, profile, null);

		// Assert
		assertThat(result).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expected);
	}

	@Test
	public void testFindOneWithDefaultApplicationAndNullProfile() {
		// Arrange
		String application = configServerProperties.getDefaultApplicationName();
		String profile = null;
		String defaultProfile = configServerProperties.getDefaultProfile();
		String[] profiles = StringUtils.commaDelimitedListToStringArray(defaultProfile);

		String sharedDefaultParamsPsName = "aws:ssm:parameter:/config/application-default/";
		PropertySource sharedDefaultParamsPs = new PropertySource(sharedDefaultParamsPsName, SHARED_DEFAULT_PROPERTIES);

		String sharedParamsPsName = "aws:ssm:parameter:/config/application/";
		PropertySource sharedParamsPs = new PropertySource(sharedParamsPsName, SHARED_PROPERTIES);

		Environment expected = new Environment(application, profiles, null, null, null);
		expected.addAll(Arrays.asList(sharedDefaultParamsPs, sharedParamsPs));

		putParameters(expected);

		// Act
		Environment result = repository.findOne(application, profile, null);

		// Assert
		assertThat(result).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expected);
	}

	@Test
	public void testFindOneWithDefaultApplicationAndDefaultProfile() {
		// Arrange
		String application = configServerProperties.getDefaultApplicationName();
		String profile = configServerProperties.getDefaultProfile();
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		String sharedDefaultParamsPsName = "aws:ssm:parameter:/config/application-default/";
		PropertySource sharedDefaultParamsPs = new PropertySource(sharedDefaultParamsPsName, SHARED_DEFAULT_PROPERTIES);

		String sharedParamsPsName = "aws:ssm:parameter:/config/application/";
		PropertySource sharedParamsPs = new PropertySource(sharedParamsPsName, SHARED_PROPERTIES);

		Environment expected = new Environment(application, profiles, null, null, null);
		expected.addAll(Arrays.asList(sharedDefaultParamsPs, sharedParamsPs));

		putParameters(expected);

		// Act
		Environment result = repository.findOne(application, profile, null);

		// Assert
		assertThat(result).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expected);
	}

	@Test
	public void testFindOneWithDefaultApplicationAndNonExistentProfile() {
		// Arrange
		String application = configServerProperties.getDefaultApplicationName();
		String profile = randomAlphabetic(RandomUtils.nextInt(3, 33));
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		String name = "aws:ssm:parameter:/config/application/";
		PropertySource ps = new PropertySource(name, SHARED_PROPERTIES);

		Environment expected = new Environment(application, profiles, null, null, null);
		expected.add(ps);

		putParameters(expected);

		// Act
		Environment result = repository.findOne(application, profile, null);

		// Assert
		assertThat(result).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expected);
	}

	@Test
	public void testFindOneWithDefaultApplicationAndExistentProfile() {
		// Arrange
		String application = configServerProperties.getDefaultApplicationName();
		String profile = "production";
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		String sharedProdParamsPsName = "aws:ssm:parameter:/config/application-production/";
		PropertySource sharedProdParamsPs = new PropertySource(sharedProdParamsPsName, SHARED_PRODUCTION_PROPERTIES);

		String sharedParamsPsName = "aws:ssm:parameter:/config/application/";
		PropertySource sharedParamsPs = new PropertySource(sharedParamsPsName, SHARED_PROPERTIES);

		Environment expected = new Environment(application, profiles, null, null, null);
		expected.addAll(Arrays.asList(sharedProdParamsPs, sharedParamsPs));

		putParameters(expected);

		// Act
		Environment result = repository.findOne(application, profile, null);

		// Assert
		assertThat(result).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expected);
	}

	@Test
	public void testFindOneWithNonExistentApplicationAndNullProfile() {
		// Arrange
		String application = randomAlphabetic(RandomUtils.nextInt(3, 33));
		String profile = null;
		String defaultProfile = configServerProperties.getDefaultProfile();
		String[] profiles = StringUtils.commaDelimitedListToStringArray(defaultProfile);

		String sharedDefaultParamsPsName = "aws:ssm:parameter:/config/application-default/";
		PropertySource sharedDefaultParamsPs = new PropertySource(sharedDefaultParamsPsName, SHARED_DEFAULT_PROPERTIES);

		String sharedParamsPsName = "aws:ssm:parameter:/config/application/";
		PropertySource sharedParamsPs = new PropertySource(sharedParamsPsName, SHARED_PROPERTIES);

		Environment expected = new Environment(application, profiles, null, null, null);
		expected.addAll(Arrays.asList(sharedDefaultParamsPs, sharedParamsPs));

		putParameters(expected);

		// Act
		Environment result = repository.findOne(application, profile, null);

		// Assert
		assertThat(result).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expected);
	}

	@Test
	public void testFindOneWithNonExistentApplicationAndDefaultProfile() {
		// Arrange
		String application = randomAlphabetic(RandomUtils.nextInt(3, 33));
		String profile = configServerProperties.getDefaultProfile();
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		String sharedDefaultParamsPsName = "aws:ssm:parameter:/config/application-default/";
		PropertySource sharedDefaultParamsPs = new PropertySource(sharedDefaultParamsPsName, SHARED_DEFAULT_PROPERTIES);

		String sharedParamsPsName = "aws:ssm:parameter:/config/application/";
		PropertySource sharedParamsPs = new PropertySource(sharedParamsPsName, SHARED_PROPERTIES);

		Environment expected = new Environment(application, profiles, null, null, null);
		expected.addAll(Arrays.asList(sharedDefaultParamsPs, sharedParamsPs));

		putParameters(expected);

		// Act
		Environment result = repository.findOne(application, profile, null);

		// Assert
		assertThat(result).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expected);
	}

	@Test
	public void testFindOneWithNonExistentApplicationAndNonExistentProfile() {
		// Arrange
		String application = randomAlphabetic(RandomUtils.nextInt(3, 33));
		String profile = randomAlphabetic(RandomUtils.nextInt(3, 33));
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		String name = "aws:ssm:parameter:/config/application/";
		PropertySource ps = new PropertySource(name, SHARED_PROPERTIES);

		Environment expected = new Environment(application, profiles, null, null, null);
		expected.add(ps);

		putParameters(expected);

		// Act
		Environment result = repository.findOne(application, profile, null);

		// Assert
		assertThat(result).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expected);
	}

	@Test
	public void testFindOneWithNonExistentApplicationAndExistentProfile() {
		// Arrange
		String application = randomAlphabetic(RandomUtils.nextInt(3, 33));
		String profile = "production";
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		String sharedProdParamsPsName = "aws:ssm:parameter:/config/application-production/";
		PropertySource sharedProdParamsPs = new PropertySource(sharedProdParamsPsName, SHARED_PRODUCTION_PROPERTIES);

		String sharedParamsPsName = "aws:ssm:parameter:/config/application/";
		PropertySource sharedParamsPs = new PropertySource(sharedParamsPsName, SHARED_PROPERTIES);

		Environment expected = new Environment(application, profiles, null, null, null);
		expected.addAll(Arrays.asList(sharedProdParamsPs, sharedParamsPs));

		putParameters(expected);

		// Act
		Environment result = repository.findOne(application, profile, null);

		// Assert
		assertThat(result).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expected);
	}

	@Test
	public void testFindOneWithExistentApplicationAndNullProfile() {
		// Arrange
		String application = "service";
		String profile = null;
		String defaultProfile = configServerProperties.getDefaultProfile();
		String[] profiles = StringUtils.commaDelimitedListToStringArray(defaultProfile);

		String appSpecificDefaultParamsPsName = "aws:ssm:parameter:/config/service-default/";
		PropertySource appSpecificDefaultParamsPs = new PropertySource(appSpecificDefaultParamsPsName,
				APPLICATION_SPECIFIC_DEFAULT_PROPERTIES);

		String sharedDefaultParamsPsName = "aws:ssm:parameter:/config/application-default/";
		PropertySource sharedDefaultParamsPs = new PropertySource(sharedDefaultParamsPsName, SHARED_DEFAULT_PROPERTIES);

		String appSpecificParamsPsName = "aws:ssm:parameter:/config/service/";
		PropertySource appSpecificParamsPs = new PropertySource(appSpecificParamsPsName,
				APPLICATION_SPECIFIC_PROPERTIES);

		String sharedParamsPsName = "aws:ssm:parameter:/config/application/";
		PropertySource sharedParamsPs = new PropertySource(sharedParamsPsName, SHARED_PROPERTIES);

		Environment expected = new Environment(application, profiles, null, null, null);

		expected.addAll(
				Arrays.asList(appSpecificDefaultParamsPs, sharedDefaultParamsPs, appSpecificParamsPs, sharedParamsPs));

		putParameters(expected);

		// Act
		Environment result = repository.findOne(application, profile, null);

		// Assert
		assertThat(result).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expected);
	}

	@Test
	public void testFindOneWithExistentApplicationAndDefaultProfile() {
		// Arrange
		String application = "service";
		String profile = configServerProperties.getDefaultProfile();
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		String appSpecificDefaultParamsPsName = "aws:ssm:parameter:/config/service-default/";
		PropertySource appSpecificDefaultParamsPs = new PropertySource(appSpecificDefaultParamsPsName,
				APPLICATION_SPECIFIC_DEFAULT_PROPERTIES);

		String sharedDefaultParamsPsName = "aws:ssm:parameter:/config/application-default/";
		PropertySource sharedDefaultParamsPs = new PropertySource(sharedDefaultParamsPsName, SHARED_DEFAULT_PROPERTIES);

		String appSpecificParamsPsName = "aws:ssm:parameter:/config/service/";
		PropertySource appSpecificParamsPs = new PropertySource(appSpecificParamsPsName,
				APPLICATION_SPECIFIC_PROPERTIES);

		String sharedParamsPsName = "aws:ssm:parameter:/config/application/";
		PropertySource sharedParamsPs = new PropertySource(sharedParamsPsName, SHARED_PROPERTIES);

		Environment expected = new Environment(application, profiles, null, null, null);

		expected.addAll(
				Arrays.asList(appSpecificDefaultParamsPs, sharedDefaultParamsPs, appSpecificParamsPs, sharedParamsPs));

		putParameters(expected);

		// Act
		Environment result = repository.findOne(application, profile, null);

		// Assert
		assertThat(result).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expected);
	}

	@Test
	public void testFindOneWithExistentApplicationAndNonExistentProfile() {
		// Arrange
		String application = "service";
		String profile = randomAlphabetic(RandomUtils.nextInt(3, 33));
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		String appSpecificParamsPsName = "aws:ssm:parameter:/config/service/";
		PropertySource appSpecificParamsPs = new PropertySource(appSpecificParamsPsName,
				APPLICATION_SPECIFIC_PROPERTIES);

		String sharedParamsPsName = "aws:ssm:parameter:/config/application/";
		PropertySource sharedParamsPs = new PropertySource(sharedParamsPsName, SHARED_PROPERTIES);

		Environment expected = new Environment(application, profiles, null, null, null);
		expected.addAll(Arrays.asList(appSpecificParamsPs, sharedParamsPs));

		putParameters(expected);

		// Act
		Environment result = repository.findOne(application, profile, null);

		// Assert
		assertThat(result).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expected);
	}

	@Test
	public void testFindOneWithExistentApplicationAndExistentProfile() {
		// Arrange
		String application = "service";
		String profile = "production";
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		String appSpecificProdParamsPsName = "aws:ssm:parameter:/config/service-production/";
		PropertySource appSpecificProdParamsPs = new PropertySource(appSpecificProdParamsPsName,
				APPLICATION_SPECIFIC_PRODUCTION_PROPERTIES);

		String sharedProdParamsPsName = "aws:ssm:parameter:/config/application-production/";
		PropertySource sharedProdParamsPs = new PropertySource(sharedProdParamsPsName, SHARED_PRODUCTION_PROPERTIES);

		String appSpecificParamsPsName = "aws:ssm:parameter:/config/service/";
		PropertySource appSpecificParamsPs = new PropertySource(appSpecificParamsPsName,
				APPLICATION_SPECIFIC_PROPERTIES);

		String sharedParamsPsName = "aws:ssm:parameter:/config/application/";
		PropertySource sharedParamsPs = new PropertySource(sharedParamsPsName, SHARED_PROPERTIES);

		Environment expected = new Environment(application, profiles, null, null, null);

		expected.addAll(
				Arrays.asList(appSpecificProdParamsPs, sharedProdParamsPs, appSpecificParamsPs, sharedParamsPs));

		putParameters(expected);

		// Act
		Environment result = repository.findOne(application, profile, null);

		// Assert
		assertThat(result).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expected);
	}

	@Test
	public void testFindOneWithExistentApplicationAndMultipleExistentProfiles() {
		// Arrange
		String application = "service";
		String profile = configServerProperties.getDefaultProfile() + ",production";
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		String appSpecificProdParamsPsName = "aws:ssm:parameter:/config/service-production/";
		PropertySource appSpecificProdParamsPs = new PropertySource(appSpecificProdParamsPsName,
				APPLICATION_SPECIFIC_PRODUCTION_PROPERTIES);

		String sharedProdParamsPsName = "aws:ssm:parameter:/config/application-production/";
		PropertySource sharedProdParamsPs = new PropertySource(sharedProdParamsPsName, SHARED_PRODUCTION_PROPERTIES);

		String appSpecificDefaultParamsPsName = "aws:ssm:parameter:/config/service-default/";
		PropertySource appSpecificDefaultParamsPs = new PropertySource(appSpecificDefaultParamsPsName,
				APPLICATION_SPECIFIC_DEFAULT_PROPERTIES);

		String sharedDefaultParamsPsName = "aws:ssm:parameter:/config/application-default/";
		PropertySource sharedDefaultParamsPs = new PropertySource(sharedDefaultParamsPsName, SHARED_DEFAULT_PROPERTIES);

		String appSpecificParamsPsName = "aws:ssm:parameter:/config/service/";
		PropertySource appSpecificParamsPs = new PropertySource(appSpecificParamsPsName,
				APPLICATION_SPECIFIC_PROPERTIES);

		String sharedParamsPsName = "aws:ssm:parameter:/config/application/";
		PropertySource sharedParamsPs = new PropertySource(sharedParamsPsName, SHARED_PROPERTIES);

		Environment expected = new Environment(application, profiles, null, null, null);

		expected.addAll(Arrays.asList(appSpecificProdParamsPs, sharedProdParamsPs, appSpecificDefaultParamsPs,
				sharedDefaultParamsPs, appSpecificParamsPs, sharedParamsPs));

		putParameters(expected);

		// Act
		Environment result = repository.findOne(application, profile, null);

		// Assert
		assertThat(result).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expected);
	}

	@Test
	public void testFindOneWithExistentApplicationAndMultipleOrderedExistentProfiles() {
		// Arrange
		String application = "application";
		String profile = "profile1,profile2,profile3";
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		String profile1ParamsPsName = "aws:ssm:parameter:/config/application-profile1/";
		PropertySource profile1ParamsPs = new PropertySource(profile1ParamsPsName, SHARED_PROPERTIES);

		String profile2ParamsPsName = "aws:ssm:parameter:/config/application-profile2/";
		PropertySource profile2ParamsPs = new PropertySource(profile2ParamsPsName, SHARED_DEFAULT_PROPERTIES);

		String profile3ParamsPsName = "aws:ssm:parameter:/config/application-profile3/";
		PropertySource profile3ParamsPs = new PropertySource(profile3ParamsPsName, SHARED_PRODUCTION_PROPERTIES);

		Environment expected = new Environment(application, profiles, null, null, null);

		expected.addAll(Arrays.asList(profile3ParamsPs, profile2ParamsPs, profile1ParamsPs));

		putParameters(expected);

		// Act
		Environment result = repository.findOne(application, profile, null);

		// Assert
		assertThat(result).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expected);
	}

	@Test
	public void testFindOneWithOverrides() {
		// Arrange
		String application = configServerProperties.getDefaultApplicationName();
		String profile = configServerProperties.getDefaultProfile();
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		Map<String, String> overrides = new HashMap<String, String>(4) {
			{
				put("logging.level.root", "boom");
				put("logging.level.com.example.service", "boom");
				put("spring.cache.redis.time-to-live", "-1");
			}
		};

		configServerProperties.setOverrides(overrides);

		PropertySource overridesPs = new PropertySource("overrides", overrides);

		String sharedDefaultParamsPsName = "aws:ssm:parameter:/config/application-default/";
		PropertySource sharedDefaultParamsPs = new PropertySource(sharedDefaultParamsPsName, SHARED_DEFAULT_PROPERTIES);

		String sharedParamsPsName = "aws:ssm:parameter:/config/application/";
		PropertySource sharedParamsPs = new PropertySource(sharedParamsPsName, SHARED_PROPERTIES);

		Environment expected = new Environment(application, profiles, null, null, null);

		expected.addAll(Arrays.asList(overridesPs, sharedDefaultParamsPs, sharedParamsPs));

		putParameters(expected);

		// Act
		Environment result = repository.findOne(application, profile, null);

		// Assert
		assertThat(result).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expected);
	}

	@Test
	public void testFindOneWithSlashesInTheParameterKeyPath() {
		// Arrange
		String application = configServerProperties.getDefaultApplicationName();
		String profile = configServerProperties.getDefaultProfile();
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		String sharedDefaultParamsPsName = "aws:ssm:parameter:/config/application-default/";
		PropertySource sharedDefaultParamsPs = new PropertySource(sharedDefaultParamsPsName, SHARED_DEFAULT_PROPERTIES);

		String sharedParamsPsName = "aws:ssm:parameter:/config/application/";
		PropertySource sharedParamsPs = new PropertySource(sharedParamsPsName, SHARED_PROPERTIES);

		Environment expected = new Environment(application, profiles, null, null, null);
		expected.addAll(Arrays.asList(sharedDefaultParamsPs, sharedParamsPs));

		putParameters(expected, true);

		// Act
		Environment result = repository.findOne(application, profile, null);

		// Assert
		assertThat(result).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expected);
	}

	@Test
	public void testFindOneWithNoParametersInThePaths() {
		// Arrange
		String application = configServerProperties.getDefaultApplicationName();
		String profile = configServerProperties.getDefaultProfile();
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		Environment expected = new Environment(application, profiles, null, null, null);

		// Act
		Environment result = repository.findOne(application, profile, null);

		// Assert
		assertThat(result).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expected);
	}

	@Test
	public void factoryCustomizableWithRegion() {
		AwsParameterStoreEnvironmentRepositoryFactory factory = new AwsParameterStoreEnvironmentRepositoryFactory(
				new ConfigServerProperties());
		AwsParameterStoreEnvironmentProperties properties = new AwsParameterStoreEnvironmentProperties();
		properties.setRegion("us-east-1");
		AwsParameterStoreEnvironmentRepository repository = factory.build(properties);
		assertThat(repository).isNotNull();
	}

	@Test
	public void factoryCustomizableWithRegionAndEndpoint() {
		AwsParameterStoreEnvironmentRepositoryFactory factory = new AwsParameterStoreEnvironmentRepositoryFactory(
				new ConfigServerProperties());
		AwsParameterStoreEnvironmentProperties properties = new AwsParameterStoreEnvironmentProperties();
		properties.setRegion("us-east-1");
		properties.setEndpoint("https://myawsendpoint/");
		AwsParameterStoreEnvironmentRepository repository = factory.build(properties);
		assertThat(repository).isNotNull();
	}

	private void putParameters(Environment environment) {
		putParameters(environment, false);
	}

	private void putParameters(Environment environment, boolean withSlashesForPropertyName) {
		for (PropertySource ps : environment.getPropertySources()) {
			String path = StringUtils.delete(ps.getName(), environmentProperties.getOrigin());
			Set<Parameter> parameters = getParameters(ps, path, withSlashesForPropertyName);
			parameters.forEach(value -> {
				ssmClient.putParameter(
						PutParameterRequest.builder().name(value.name()).dataType("text").value(value.value()).build());
				toBeRemoved.add(value.name());
			});
		}
	}

	private Set<Parameter> getParameters(PropertySource propertySource, String path,
			boolean withSlashesForPropertyName) {
		Function<Map.Entry<?, ?>, Parameter> mapper = p -> Parameter
				.builder().name(path + (withSlashesForPropertyName
						? ((String) p.getKey()).replace(".", DEFAULT_PATH_SEPARATOR) : p.getKey()))
				.type(ParameterType.STRING).value((String) p.getValue()).version(1L).build();

		return propertySource.getSource().entrySet().stream().map(mapper).collect(Collectors.toSet());
	}

	@Test
	public void testOrderPopulation() {
		int expectedOrder = Ordered.HIGHEST_PRECEDENCE;
		AwsParameterStoreEnvironmentRepositoryFactory factory = new AwsParameterStoreEnvironmentRepositoryFactory(
				new ConfigServerProperties());
		AwsParameterStoreEnvironmentProperties properties = new AwsParameterStoreEnvironmentProperties();
		properties.setRegion("us-east-1");
		properties.setEndpoint("https://myawsendpoint/");
		properties.setOrder(expectedOrder);
		AwsParameterStoreEnvironmentRepository repository = factory.build(properties);
		int actualOrder = repository.getOrder();
		assertThat(actualOrder).isEqualTo(expectedOrder);
	}

	private final Map<String, String> SHARED_PROPERTIES = new HashMap<String, String>() {
		{
			put("logging.level.root", "warn");
			put("spring.cache.redis.time-to-live", "0");
		}
	};

	private final Map<String, String> SHARED_DEFAULT_PROPERTIES = new HashMap<String, String>() {
		{
			put("logging.level.root", "error");
			put("spring.cache.redis.time-to-live", "1000");
		}
	};

	private final Map<String, String> SHARED_PRODUCTION_PROPERTIES = new HashMap<String, String>() {
		{
			put("logging.level.root", "fatal");
			put("spring.cache.redis.time-to-live", "5000");
		}
	};

	private final Map<String, String> APPLICATION_SPECIFIC_PROPERTIES = new HashMap<String, String>() {
		{
			put("logging.level.com.example.service", "trace");
			put("spring.cache.redis.time-to-live", "30000");
		}
	};

	private final Map<String, String> APPLICATION_SPECIFIC_DEFAULT_PROPERTIES = new HashMap<String, String>() {
		{
			put("logging.level.com.example.service", "debug");
			put("spring.cache.redis.time-to-live", "60000");
		}
	};

	private final Map<String, String> APPLICATION_SPECIFIC_PRODUCTION_PROPERTIES = new HashMap<String, String>() {
		{
			put("logging.level.com.example.service", "info");
			put("spring.cache.redis.time-to-live", "300000");
		}
	};

}
