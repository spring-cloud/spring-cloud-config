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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import com.amazonaws.services.secretsmanager.AWSSecretsManager;
import com.amazonaws.services.secretsmanager.model.GetSecretValueRequest;
import com.amazonaws.services.secretsmanager.model.GetSecretValueResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.config.server.config.ConfigServerProperties;
import org.springframework.util.StringUtils;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Tejas Pandilwar
 */
public class AwsSecretsManagerEnvironmentRepositoryTests {

	private static final Log log = LogFactory.getLog(AwsSecretsManagerEnvironmentRepository.class);

	private final AWSSecretsManager awsSmClientMock = mock(AWSSecretsManager.class, "aws-sm-client-mock");

	private final ConfigServerProperties configServerProperties = new ConfigServerProperties();

	private final AwsSecretsManagerEnvironmentProperties environmentProperties = new AwsSecretsManagerEnvironmentProperties();

	private final AwsSecretsManagerEnvironmentRepository repository = new AwsSecretsManagerEnvironmentRepository(
			awsSmClientMock, configServerProperties, environmentProperties);

	private final ObjectMapper objectMapper = new ObjectMapper().configure(SerializationFeature.INDENT_OUTPUT, true);

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

		Environment expectedEnv = new Environment(defaultApplication, profiles, null, null, null);
		expectedEnv.addAll(Arrays.asList(applicationDefaultProperties, applicationProperties));

		setupAwsSmClientMocks(expectedEnv);

		Environment resultEnv = repository.findOne(application, profile, null);

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

		Environment expectedEnv = new Environment(defaultApplication, profiles, null, null, null);
		expectedEnv.addAll(Arrays.asList(applicationDefaultProperties, applicationProperties));

		setupAwsSmClientMocks(expectedEnv);

		Environment resultEnv = repository.findOne(application, profile, null);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
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

		Environment expectedEnv = new Environment(defaultApplication, profiles, null, null, null);
		expectedEnv.addAll(Arrays.asList(applicationDefaultProperties, applicationProperties));

		setupAwsSmClientMocks(expectedEnv);

		Environment resultEnv = repository.findOne(application, profile, null);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
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

		Environment expectedEnv = new Environment(defaultApplication, profiles, null, null, null);
		expectedEnv
				.addAll(Arrays.asList(applicationProdProperties, applicationDefaultProperties, applicationProperties));

		setupAwsSmClientMocks(expectedEnv);

		Environment resultEnv = repository.findOne(application, profile, null);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
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

		Environment expectedEnv = new Environment(application, profiles, null, null, null);
		expectedEnv.addAll(Arrays.asList(applicationDefaultProperties, applicationProperties));

		setupAwsSmClientMocks(expectedEnv);

		Environment resultEnv = repository.findOne(application, profile, null);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
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

		Environment expectedEnv = new Environment(application, profiles, null, null, null);
		expectedEnv.addAll(Arrays.asList(applicationDefaultProperties, applicationProperties));

		setupAwsSmClientMocks(expectedEnv);

		Environment resultEnv = repository.findOne(application, profile, null);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
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

		Environment expectedEnv = new Environment(application, profiles, null, null, null);
		expectedEnv.addAll(Arrays.asList(applicationDefaultProperties, applicationProperties));

		setupAwsSmClientMocks(expectedEnv);

		Environment resultEnv = repository.findOne(application, profile, null);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
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

		Environment expectedEnv = new Environment(application, profiles, null, null, null);
		expectedEnv
				.addAll(Arrays.asList(applicationProdProperties, applicationDefaultProperties, applicationProperties));

		setupAwsSmClientMocks(expectedEnv);

		Environment resultEnv = repository.findOne(application, profile, null);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
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

		Environment expectedEnv = new Environment(application, profiles, null, null, null);
		expectedEnv.addAll(Arrays.asList(applicationDefaultProperties, applicationProperties));

		setupAwsSmClientMocks(expectedEnv);

		Environment resultEnv = repository.findOne(application, profile, null);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
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

		Environment expectedEnv = new Environment(application, profiles, null, null, null);
		expectedEnv.addAll(Arrays.asList(applicationDefaultProperties, applicationProperties));

		setupAwsSmClientMocks(expectedEnv);

		Environment resultEnv = repository.findOne(application, profile, null);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
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

		Environment expectedEnv = new Environment(application, profiles, null, null, null);
		expectedEnv.addAll(Arrays.asList(applicationDefaultProperties, applicationProperties));

		setupAwsSmClientMocks(expectedEnv);

		Environment resultEnv = repository.findOne(application, profile, null);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
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

		Environment expectedEnv = new Environment(application, profiles, null, null, null);
		expectedEnv
				.addAll(Arrays.asList(applicationProdProperties, applicationDefaultProperties, applicationProperties));

		setupAwsSmClientMocks(expectedEnv);

		Environment resultEnv = repository.findOne(application, profile, null);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
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

		Environment expectedEnv = new Environment(application, profiles, null, null, null);
		expectedEnv.addAll(Arrays.asList(fooDefaultProperties, applicationDefaultProperties, fooProperties,
				applicationProperties));

		setupAwsSmClientMocks(expectedEnv);

		Environment resultEnv = repository.findOne(application, profile, null);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
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

		Environment expectedEnv = new Environment(application, profiles, null, null, null);
		expectedEnv.addAll(Arrays.asList(fooDefaultProperties, applicationDefaultProperties, fooProperties,
				applicationProperties));

		setupAwsSmClientMocks(expectedEnv);

		Environment resultEnv = repository.findOne(application, profile, null);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
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

		Environment expectedEnv = new Environment(application, profiles, null, null, null);
		expectedEnv.addAll(Arrays.asList(fooDefaultProperties, applicationDefaultProperties, fooProperties,
				applicationProperties));

		setupAwsSmClientMocks(expectedEnv);

		Environment resultEnv = repository.findOne(application, profile, null);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
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

		Environment expectedEnv = new Environment(application, profiles, null, null, null);
		expectedEnv.addAll(Arrays.asList(fooProperties, applicationProperties));

		setupAwsSmClientMocks(expectedEnv);

		Environment resultEnv = repository.findOne(application, profile, null);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
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

		Environment expectedEnv = new Environment(application, profiles, null, null, null);
		expectedEnv.addAll(Arrays.asList(applicationDefaultProperties, fooProperties, applicationProperties));

		setupAwsSmClientMocks(expectedEnv);

		Environment resultEnv = repository.findOne(application, profile, null);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
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

		Environment expectedEnv = new Environment(application, profiles, null, null, null);
		expectedEnv.addAll(Arrays.asList(fooProdProperties, applicationProdProperties, fooDefaultProperties,
				applicationDefaultProperties, fooProperties, applicationProperties));

		setupAwsSmClientMocks(expectedEnv);

		Environment resultEnv = repository.findOne(application, profile, null);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
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

		Environment expectedEnv = new Environment(application, profiles, null, null, null);
		expectedEnv.addAll(
				Arrays.asList(fooProdProperties, applicationProdProperties, fooProperties, applicationProperties));

		setupAwsSmClientMocks(expectedEnv);

		Environment resultEnv = repository.findOne(application, profile, null);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
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

		Environment expectedEnv = new Environment(application, profiles, null, null, null);
		expectedEnv.addAll(Arrays.asList(fooProdProperties, applicationProdProperties, fooEastProperties,
				applicationEastProperties, fooDefaultProperties, applicationDefaultProperties, fooProperties,
				applicationProperties));

		setupAwsSmClientMocks(expectedEnv);

		Environment resultEnv = repository.findOne(application, profile, null);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
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

		Environment expectedEnv = new Environment(application, profiles, null, null, null);
		expectedEnv.addAll(Arrays.asList(fooProdProperties, applicationProdProperties, fooEastProperties,
				applicationEastProperties, fooProperties, applicationProperties));

		setupAwsSmClientMocks(expectedEnv);

		Environment resultEnv = repository.findOne(application, profile, null);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
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

		Environment expectedEnv = new Environment(application, profiles, null, null, null);
		expectedEnv.addAll(Arrays.asList(overrideProperties, applicationDefaultProperties, applicationProperties));

		setupAwsSmClientMocks(expectedEnv);

		Environment resultEnv = repository.findOne(application, profile, null);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void testFindOneWithNoSecretsStored() {
		String application = configServerProperties.getDefaultApplicationName();
		String profile = configServerProperties.getDefaultProfile();
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		Environment expectedEnv = new Environment(application, profiles, null, null, null);
		setupAwsSmClientMocks(expectedEnv);

		Environment resultEnv = repository.findOne(application, profile, null);

		assertThat(resultEnv).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expectedEnv);
	}

	@Test
	public void factoryCustomizableWithRegion() {
		AwsSecretsManagerEnvironmentRepositoryFactory factory = new AwsSecretsManagerEnvironmentRepositoryFactory(new ConfigServerProperties());
		AwsSecretsManagerEnvironmentProperties properties = new AwsSecretsManagerEnvironmentProperties();
		properties.setRegion("us-east-1");
		AwsSecretsManagerEnvironmentRepository repository = factory.build(properties);
		assertThat(repository).isNotNull();
	}

	@Test
	public void factoryCustomizableWithRegionAndEndpoint() {
		AwsSecretsManagerEnvironmentRepositoryFactory factory = new AwsSecretsManagerEnvironmentRepositoryFactory(new ConfigServerProperties());
		AwsSecretsManagerEnvironmentProperties properties = new AwsSecretsManagerEnvironmentProperties();
		properties.setRegion("us-east-1");
		properties.setEndpoint("https://myawsendpoint/");
		AwsSecretsManagerEnvironmentRepository repository = factory.build(properties);
		assertThat(repository).isNotNull();
	}

	private void setupAwsSmClientMocks(Environment environment) {
		for (PropertySource ps : environment.getPropertySources()) {
			String path = StringUtils.delete(ps.getName(), environmentProperties.getOrigin());
			GetSecretValueRequest request = new GetSecretValueRequest().withSecretId(path);

			String secrets = getSecrets(ps);
			GetSecretValueResult response = new GetSecretValueResult().withSecretString(secrets);

			when(awsSmClientMock.getSecretValue(eq(request))).thenReturn(response);
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

	private static Map<String, String> getApplicationProperties() {
		return new HashMap<String, String>() {
			{
				put("s3.accessKey", "application-shared-s3");
				put("s3.secretKey", "25300773-eb3b-4ace-b6fc-500c87331da7");
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

}
