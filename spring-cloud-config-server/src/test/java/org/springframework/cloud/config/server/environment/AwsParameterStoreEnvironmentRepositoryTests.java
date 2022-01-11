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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagement;
import com.amazonaws.services.simplesystemsmanagement.model.GetParametersByPathRequest;
import com.amazonaws.services.simplesystemsmanagement.model.GetParametersByPathResult;
import com.amazonaws.services.simplesystemsmanagement.model.Parameter;
import com.amazonaws.services.simplesystemsmanagement.model.ParameterType;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Test;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.config.server.config.ConfigServerProperties;
import org.springframework.util.StringUtils;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.config.server.environment.AwsParameterStoreEnvironmentProperties.DEFAULT_PATH_SEPARATOR;

/**
 * @author Iulian Antohe
 */
public class AwsParameterStoreEnvironmentRepositoryTests {

	private static final Map<String, String> SHARED_PROPERTIES = new HashMap<String, String>() {
		{
			put("logging.level.root", "warn");
			put("spring.cache.redis.time-to-live", "0");
		}
	};

	private static final Map<String, String> SHARED_DEFAULT_PROPERTIES = new HashMap<String, String>() {
		{
			put("logging.level.root", "error");
			put("spring.cache.redis.time-to-live", "1000");
		}
	};

	private static final Map<String, String> SHARED_PRODUCTION_PROPERTIES = new HashMap<String, String>() {
		{
			put("logging.level.root", "fatal");
			put("spring.cache.redis.time-to-live", "5000");
		}
	};

	private static final Map<String, String> APPLICATION_SPECIFIC_PROPERTIES = new HashMap<String, String>() {
		{
			put("logging.level.com.example.service", "trace");
			put("spring.cache.redis.time-to-live", "30000");
		}
	};

	private static final Map<String, String> APPLICATION_SPECIFIC_DEFAULT_PROPERTIES = new HashMap<String, String>() {
		{
			put("logging.level.com.example.service", "debug");
			put("spring.cache.redis.time-to-live", "60000");
		}
	};

	private static final Map<String, String> APPLICATION_SPECIFIC_PRODUCTION_PROPERTIES = new HashMap<String, String>() {
		{
			put("logging.level.com.example.service", "info");
			put("spring.cache.redis.time-to-live", "300000");
		}
	};

	private final AWSSimpleSystemsManagement awsSsmClientMock = mock(AWSSimpleSystemsManagement.class,
			"aws-ssm-client-mock");

	private final ConfigServerProperties configServerProperties = new ConfigServerProperties();

	private final AwsParameterStoreEnvironmentProperties environmentProperties = new AwsParameterStoreEnvironmentProperties();

	private final AwsParameterStoreEnvironmentRepository repository = new AwsParameterStoreEnvironmentRepository(
			awsSsmClientMock, configServerProperties, environmentProperties);

	@Test
	@SuppressWarnings("ConstantConditions")
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

		setupAwsSsmClientMocks(expected);

		// Act
		Environment result = repository.findOne(application, profile, null);

		// Assert
		assertThat(result).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expected);
	}

	@Test
	@SuppressWarnings("ConstantConditions")
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

		setupAwsSsmClientMocks(expected);

		// Act
		Environment result = repository.findOne(application, profile, null);

		// Assert
		assertThat(result).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expected);
	}

	@Test
	@SuppressWarnings("ConstantConditions")
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

		setupAwsSsmClientMocks(expected);

		// Act
		Environment result = repository.findOne(application, profile, null);

		// Assert
		assertThat(result).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expected);
	}

	@Test
	@SuppressWarnings("ConstantConditions")
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

		setupAwsSsmClientMocks(expected);

		// Act
		Environment result = repository.findOne(application, profile, null);

		// Assert
		assertThat(result).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expected);
	}

	@Test
	@SuppressWarnings("ConstantConditions")
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

		setupAwsSsmClientMocks(expected);

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

		setupAwsSsmClientMocks(expected);

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

		setupAwsSsmClientMocks(expected);

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

		setupAwsSsmClientMocks(expected);

		// Act
		Environment result = repository.findOne(application, profile, null);

		// Assert
		assertThat(result).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expected);
	}

	@Test
	@SuppressWarnings("ConstantConditions")
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

		setupAwsSsmClientMocks(expected);

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

		setupAwsSsmClientMocks(expected);

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

		setupAwsSsmClientMocks(expected);

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

		setupAwsSsmClientMocks(expected);

		// Act
		Environment result = repository.findOne(application, profile, null);

		// Assert
		assertThat(result).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expected);
	}

	@Test
	@SuppressWarnings("ConstantConditions")
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

		setupAwsSsmClientMocks(expected);

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

		setupAwsSsmClientMocks(expected);

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

		setupAwsSsmClientMocks(expected);

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

		setupAwsSsmClientMocks(expected);

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

		setupAwsSsmClientMocks(expected);

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

		setupAwsSsmClientMocks(expected);

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

		setupAwsSsmClientMocks(expected, true, false);

		// Act
		Environment result = repository.findOne(application, profile, null);

		// Assert
		assertThat(result).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expected);
	}

	@Test
	public void testFindOneWithPaginatedAwsSsmClientResponse() {
		// Arrange
		String application = configServerProperties.getDefaultApplicationName();
		String profile = configServerProperties.getDefaultProfile();
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		environmentProperties.setMaxResults(1);

		String sharedDefaultParamsPsName = "aws:ssm:parameter:/config/application-default/";
		PropertySource sharedDefaultParamsPs = new PropertySource(sharedDefaultParamsPsName, SHARED_DEFAULT_PROPERTIES);

		String sharedParamsPsName = "aws:ssm:parameter:/config/application/";
		PropertySource sharedParamsPs = new PropertySource(sharedParamsPsName, SHARED_PROPERTIES);

		Environment expected = new Environment(application, profiles, null, null, null);
		expected.addAll(Arrays.asList(sharedDefaultParamsPs, sharedParamsPs));

		setupAwsSsmClientMocks(expected, false, true);

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

		when(awsSsmClientMock.getParametersByPath(any(GetParametersByPathRequest.class)))
				.thenReturn(new GetParametersByPathResult());

		// Act
		Environment result = repository.findOne(application, profile, null);

		// Assert
		assertThat(result).usingRecursiveComparison().withStrictTypeChecking().isEqualTo(expected);
	}

	@Test
	public void factoryCustomizableWithRegion() {
		AwsParameterStoreEnvironmentRepositoryFactory factory = new AwsParameterStoreEnvironmentRepositoryFactory(new ConfigServerProperties());
		AwsParameterStoreEnvironmentProperties properties = new AwsParameterStoreEnvironmentProperties();
		properties.setRegion("us-east-1");
		AwsParameterStoreEnvironmentRepository repository = factory.build(properties);
		assertThat(repository).isNotNull();
	}

	@Test
	public void factoryCustomizableWithRegionAndEndpoint() {
		AwsParameterStoreEnvironmentRepositoryFactory factory = new AwsParameterStoreEnvironmentRepositoryFactory(new ConfigServerProperties());
		AwsParameterStoreEnvironmentProperties properties = new AwsParameterStoreEnvironmentProperties();
		properties.setRegion("us-east-1");
		properties.setEndpoint("https://myawsendpoint/");
		AwsParameterStoreEnvironmentRepository repository = factory.build(properties);
		assertThat(repository).isNotNull();
	}

	private void setupAwsSsmClientMocks(Environment environment) {
		setupAwsSsmClientMocks(environment, false, false);
	}

	private void setupAwsSsmClientMocks(Environment environment, boolean withSlashesForPropertyName,
			boolean paginatedResponse) {
		for (PropertySource ps : environment.getPropertySources()) {
			String path = StringUtils.delete(ps.getName(), environmentProperties.getOrigin());

			GetParametersByPathRequest request = new GetParametersByPathRequest().withPath(path)
					.withRecursive(environmentProperties.isRecursive())
					.withWithDecryption(environmentProperties.isDecryptValues())
					.withMaxResults(environmentProperties.getMaxResults());

			Set<Parameter> parameters = getParameters(ps, path, withSlashesForPropertyName);

			GetParametersByPathResult response = new GetParametersByPathResult().withParameters(parameters);

			if (paginatedResponse && environmentProperties.getMaxResults() < parameters.size()) {
				List<Set<Parameter>> chunks = splitParametersIntoChunks(parameters);

				String nextToken = null;

				for (int i = 0; i < chunks.size(); i++) {
					Set<Parameter> chunk = chunks.get(i);

					if (i == 0) {
						nextToken = generateNextToken();

						GetParametersByPathResult responseClone = response.clone().withParameters(chunk)
								.withNextToken(nextToken);

						when(awsSsmClientMock.getParametersByPath(eq(request))).thenReturn(responseClone);
					}
					else if (i == chunks.size() - 1) {
						GetParametersByPathRequest requestClone = request.clone().withNextToken(nextToken);
						GetParametersByPathResult responseClone = response.clone().withParameters(chunk);

						when(awsSsmClientMock.getParametersByPath(eq(requestClone))).thenReturn(responseClone);
					}
					else {
						String newNextToken = generateNextToken();

						GetParametersByPathRequest requestClone = request.clone().withNextToken(nextToken);

						GetParametersByPathResult responseClone = response.clone().withParameters(chunk)
								.withNextToken(newNextToken);

						when(awsSsmClientMock.getParametersByPath(eq(requestClone))).thenReturn(responseClone);

						nextToken = newNextToken;
					}
				}
			}
			else {
				when(awsSsmClientMock.getParametersByPath(eq(request))).thenReturn(response);
			}
		}
	}

	private Set<Parameter> getParameters(PropertySource propertySource, String path,
			boolean withSlashesForPropertyName) {
		Function<Map.Entry<?, ?>, Parameter> mapper = p -> new Parameter()
				.withName(path + (withSlashesForPropertyName
						? ((String) p.getKey()).replace(".", DEFAULT_PATH_SEPARATOR) : p.getKey()))
				.withType(ParameterType.String).withValue((String) p.getValue()).withVersion(1L);

		return propertySource.getSource().entrySet().stream().map(mapper).collect(Collectors.toSet());
	}

	private List<Set<Parameter>> splitParametersIntoChunks(Set<Parameter> parameters) {
		AtomicInteger counter = new AtomicInteger();

		Collector<Parameter, ?, Map<Integer, Set<Parameter>>> collector = Collectors
				.groupingBy(p -> counter.getAndIncrement() / environmentProperties.getMaxResults(), Collectors.toSet());

		return new ArrayList<>(parameters.stream().collect(collector).values());
	}

	private String generateNextToken() {
		String random = randomAlphabetic(RandomUtils.nextInt(3, 33));

		return Base64.getEncoder().encodeToString(random.getBytes(StandardCharsets.UTF_8));
	}

}
