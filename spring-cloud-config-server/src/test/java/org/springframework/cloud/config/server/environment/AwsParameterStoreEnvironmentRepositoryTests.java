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

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.RandomUtils;
import org.junit.Test;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.config.server.config.ConfigServerProperties;
import org.springframework.util.StringUtils;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.Assertions.assertThat;

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

	private final ConfigServerProperties configServerProperties = new ConfigServerProperties();

	private final AwsParameterStoreEnvironmentRepository repository = new AwsParameterStoreEnvironmentRepository();

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
		PropertySource sharedDefaultParamsPs = new PropertySource(
				sharedDefaultParamsPsName, SHARED_DEFAULT_PROPERTIES);

		String sharedParamsPsName = "aws:ssm:parameter:/config/application/";
		PropertySource sharedParamsPs = new PropertySource(sharedParamsPsName,
				SHARED_PROPERTIES);

		Environment expected = new Environment(defaultApp, profiles, null, null, null);
		expected.addAll(Arrays.asList(sharedDefaultParamsPs, sharedParamsPs));

		// Act
		Environment result = repository.findOne(application, profile, null);

		// Assert
		assertThat(result).usingRecursiveComparison().withStrictTypeChecking()
				.isEqualTo(expected);
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
		PropertySource sharedDefaultParamsPs = new PropertySource(
				sharedDefaultParamsPsName, SHARED_DEFAULT_PROPERTIES);

		String sharedParamsPsName = "aws:ssm:parameter:/config/application/";
		PropertySource sharedParamsPs = new PropertySource(sharedParamsPsName,
				SHARED_PROPERTIES);

		Environment expected = new Environment(defaultApp, profiles, null, null, null);
		expected.addAll(Arrays.asList(sharedDefaultParamsPs, sharedParamsPs));

		// Act
		Environment result = repository.findOne(application, profile, null);

		// Assert
		assertThat(result).usingRecursiveComparison().withStrictTypeChecking()
				.isEqualTo(expected);
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

		// Act
		Environment result = repository.findOne(application, profile, null);

		// Assert
		assertThat(result).usingRecursiveComparison().withStrictTypeChecking()
				.isEqualTo(expected);
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
		PropertySource sharedProdParamsPs = new PropertySource(sharedProdParamsPsName,
				SHARED_PRODUCTION_PROPERTIES);

		String sharedParamsPsName = "aws:ssm:parameter:/config/application/";
		PropertySource sharedParamsPs = new PropertySource(sharedParamsPsName,
				SHARED_PROPERTIES);

		Environment expected = new Environment(defaultApp, profiles, null, null, null);
		expected.addAll(Arrays.asList(sharedProdParamsPs, sharedParamsPs));

		// Act
		Environment result = repository.findOne(application, profile, null);

		// Assert
		assertThat(result).usingRecursiveComparison().withStrictTypeChecking()
				.isEqualTo(expected);
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
		PropertySource sharedDefaultParamsPs = new PropertySource(
				sharedDefaultParamsPsName, SHARED_DEFAULT_PROPERTIES);

		String sharedParamsPsName = "aws:ssm:parameter:/config/application/";
		PropertySource sharedParamsPs = new PropertySource(sharedParamsPsName,
				SHARED_PROPERTIES);

		Environment expected = new Environment(application, profiles, null, null, null);
		expected.addAll(Arrays.asList(sharedDefaultParamsPs, sharedParamsPs));

		// Act
		Environment result = repository.findOne(application, profile, null);

		// Assert
		assertThat(result).usingRecursiveComparison().withStrictTypeChecking()
				.isEqualTo(expected);
	}

	@Test
	public void testFindOneWithDefaultApplicationAndDefaultProfile() {
		// Arrange
		String application = configServerProperties.getDefaultApplicationName();
		String profile = configServerProperties.getDefaultProfile();
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		String sharedDefaultParamsPsName = "aws:ssm:parameter:/config/application-default/";
		PropertySource sharedDefaultParamsPs = new PropertySource(
				sharedDefaultParamsPsName, SHARED_DEFAULT_PROPERTIES);

		String sharedParamsPsName = "aws:ssm:parameter:/config/application/";
		PropertySource sharedParamsPs = new PropertySource(sharedParamsPsName,
				SHARED_PROPERTIES);

		Environment expected = new Environment(application, profiles, null, null, null);
		expected.addAll(Arrays.asList(sharedDefaultParamsPs, sharedParamsPs));

		// Act
		Environment result = repository.findOne(application, profile, null);

		// Assert
		assertThat(result).usingRecursiveComparison().withStrictTypeChecking()
				.isEqualTo(expected);
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

		// Act
		Environment result = repository.findOne(application, profile, null);

		// Assert
		assertThat(result).usingRecursiveComparison().withStrictTypeChecking()
				.isEqualTo(expected);
	}

	@Test
	public void testFindOneWithDefaultApplicationAndExistentProfile() {
		// Arrange
		String application = configServerProperties.getDefaultApplicationName();
		String profile = "production";
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		String sharedProdParamsPsName = "aws:ssm:parameter:/config/application-production/";
		PropertySource sharedProdParamsPs = new PropertySource(sharedProdParamsPsName,
				SHARED_PRODUCTION_PROPERTIES);

		String sharedParamsPsName = "aws:ssm:parameter:/config/application/";
		PropertySource sharedParamsPs = new PropertySource(sharedParamsPsName,
				SHARED_PROPERTIES);

		Environment expected = new Environment(application, profiles, null, null, null);
		expected.addAll(Arrays.asList(sharedProdParamsPs, sharedParamsPs));

		// Act
		Environment result = repository.findOne(application, profile, null);

		// Assert
		assertThat(result).usingRecursiveComparison().withStrictTypeChecking()
				.isEqualTo(expected);
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
		PropertySource sharedDefaultParamsPs = new PropertySource(
				sharedDefaultParamsPsName, SHARED_DEFAULT_PROPERTIES);

		String sharedParamsPsName = "aws:ssm:parameter:/config/application/";
		PropertySource sharedParamsPs = new PropertySource(sharedParamsPsName,
				SHARED_PROPERTIES);

		Environment expected = new Environment(application, profiles, null, null, null);
		expected.addAll(Arrays.asList(sharedDefaultParamsPs, sharedParamsPs));

		// Act
		Environment result = repository.findOne(application, profile, null);

		// Assert
		assertThat(result).usingRecursiveComparison().withStrictTypeChecking()
				.isEqualTo(expected);
	}

	@Test
	public void testFindOneWithNonExistentApplicationAndDefaultProfile() {
		// Arrange
		String application = randomAlphabetic(RandomUtils.nextInt(3, 33));
		String profile = configServerProperties.getDefaultProfile();
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		String sharedDefaultParamsPsName = "aws:ssm:parameter:/config/application-default/";
		PropertySource sharedDefaultParamsPs = new PropertySource(
				sharedDefaultParamsPsName, SHARED_DEFAULT_PROPERTIES);

		String sharedParamsPsName = "aws:ssm:parameter:/config/application/";
		PropertySource sharedParamsPs = new PropertySource(sharedParamsPsName,
				SHARED_PROPERTIES);

		Environment expected = new Environment(application, profiles, null, null, null);
		expected.addAll(Arrays.asList(sharedDefaultParamsPs, sharedParamsPs));

		// Act
		Environment result = repository.findOne(application, profile, null);

		// Assert
		assertThat(result).usingRecursiveComparison().withStrictTypeChecking()
				.isEqualTo(expected);
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

		// Act
		Environment result = repository.findOne(application, profile, null);

		// Assert
		assertThat(result).usingRecursiveComparison().withStrictTypeChecking()
				.isEqualTo(expected);
	}

	@Test
	public void testFindOneWithNonExistentApplicationAndExistentProfile() {
		// Arrange
		String application = randomAlphabetic(RandomUtils.nextInt(3, 33));
		String profile = "production";
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		String sharedProdParamsPsName = "aws:ssm:parameter:/config/application-production/";
		PropertySource sharedProdParamsPs = new PropertySource(sharedProdParamsPsName,
				SHARED_PRODUCTION_PROPERTIES);

		String sharedParamsPsName = "aws:ssm:parameter:/config/application/";
		PropertySource sharedParamsPs = new PropertySource(sharedParamsPsName,
				SHARED_PROPERTIES);

		Environment expected = new Environment(application, profiles, null, null, null);
		expected.addAll(Arrays.asList(sharedProdParamsPs, sharedParamsPs));

		// Act
		Environment result = repository.findOne(application, profile, null);

		// Assert
		assertThat(result).usingRecursiveComparison().withStrictTypeChecking()
				.isEqualTo(expected);
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
		PropertySource appSpecificDefaultParamsPs = new PropertySource(
				appSpecificDefaultParamsPsName, APPLICATION_SPECIFIC_DEFAULT_PROPERTIES);

		String sharedDefaultParamsPsName = "aws:ssm:parameter:/config/application-default/";
		PropertySource sharedDefaultParamsPs = new PropertySource(
				sharedDefaultParamsPsName, SHARED_DEFAULT_PROPERTIES);

		String appSpecificParamsPsName = "aws:ssm:parameter:/config/service/";
		PropertySource appSpecificParamsPs = new PropertySource(appSpecificParamsPsName,
				APPLICATION_SPECIFIC_PROPERTIES);

		String sharedParamsPsName = "aws:ssm:parameter:/config/application/";
		PropertySource sharedParamsPs = new PropertySource(sharedParamsPsName,
				SHARED_PROPERTIES);

		Environment expected = new Environment(application, profiles, null, null, null);

		expected.addAll(Arrays.asList(appSpecificDefaultParamsPs, sharedDefaultParamsPs,
				appSpecificParamsPs, sharedParamsPs));

		// Act
		Environment result = repository.findOne(application, profile, null);

		// Assert
		assertThat(result).usingRecursiveComparison().withStrictTypeChecking()
				.isEqualTo(expected);
	}

	@Test
	public void testFindOneWithExistentApplicationAndDefaultProfile() {
		// Arrange
		String application = "service";
		String profile = configServerProperties.getDefaultProfile();
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		String appSpecificDefaultParamsPsName = "aws:ssm:parameter:/config/service-default/";
		PropertySource appSpecificDefaultParamsPs = new PropertySource(
				appSpecificDefaultParamsPsName, APPLICATION_SPECIFIC_DEFAULT_PROPERTIES);

		String sharedDefaultParamsPsName = "aws:ssm:parameter:/config/application-default/";
		PropertySource sharedDefaultParamsPs = new PropertySource(
				sharedDefaultParamsPsName, SHARED_DEFAULT_PROPERTIES);

		String appSpecificParamsPsName = "aws:ssm:parameter:/config/service/";
		PropertySource appSpecificParamsPs = new PropertySource(appSpecificParamsPsName,
				APPLICATION_SPECIFIC_PROPERTIES);

		String sharedParamsPsName = "aws:ssm:parameter:/config/application/";
		PropertySource sharedParamsPs = new PropertySource(sharedParamsPsName,
				SHARED_PROPERTIES);

		Environment expected = new Environment(application, profiles, null, null, null);

		expected.addAll(Arrays.asList(appSpecificDefaultParamsPs, sharedDefaultParamsPs,
				appSpecificParamsPs, sharedParamsPs));

		// Act
		Environment result = repository.findOne(application, profile, null);

		// Assert
		assertThat(result).usingRecursiveComparison().withStrictTypeChecking()
				.isEqualTo(expected);
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
		PropertySource sharedParamsPs = new PropertySource(sharedParamsPsName,
				SHARED_PROPERTIES);

		Environment expected = new Environment(application, profiles, null, null, null);
		expected.addAll(Arrays.asList(appSpecificParamsPs, sharedParamsPs));

		// Act
		Environment result = repository.findOne(application, profile, null);

		// Assert
		assertThat(result).usingRecursiveComparison().withStrictTypeChecking()
				.isEqualTo(expected);
	}

	@Test
	public void testFindOneWithExistentApplicationAndExistentProfile() {
		// Arrange
		String application = "service";
		String profile = "production";
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		String appSpecificProdParamsPsName = "aws:ssm:parameter:/config/service-production/";
		PropertySource appSpecificProdParamsPs = new PropertySource(
				appSpecificProdParamsPsName, APPLICATION_SPECIFIC_PRODUCTION_PROPERTIES);

		String sharedProdParamsPsName = "aws:ssm:parameter:/config/application-production/";
		PropertySource sharedProdParamsPs = new PropertySource(sharedProdParamsPsName,
				SHARED_PRODUCTION_PROPERTIES);

		String appSpecificParamsPsName = "aws:ssm:parameter:/config/service/";
		PropertySource appSpecificParamsPs = new PropertySource(appSpecificParamsPsName,
				APPLICATION_SPECIFIC_PROPERTIES);

		String sharedParamsPsName = "aws:ssm:parameter:/config/application/";
		PropertySource sharedParamsPs = new PropertySource(sharedParamsPsName,
				SHARED_PROPERTIES);

		Environment expected = new Environment(application, profiles, null, null, null);

		expected.addAll(Arrays.asList(appSpecificProdParamsPs, sharedProdParamsPs,
				appSpecificParamsPs, sharedParamsPs));

		// Act
		Environment result = repository.findOne(application, profile, null);

		// Assert
		assertThat(result).usingRecursiveComparison().withStrictTypeChecking()
				.isEqualTo(expected);
	}

	@Test
	public void testFindOneWithExistentApplicationAndMultipleExistentProfiles() {
		// Arrange
		String application = "service";
		String profile = configServerProperties.getDefaultProfile() + ",production";
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		String appSpecificProdParamsPsName = "aws:ssm:parameter:/config/service-production/";
		PropertySource appSpecificProdParamsPs = new PropertySource(
				appSpecificProdParamsPsName, APPLICATION_SPECIFIC_PRODUCTION_PROPERTIES);

		String sharedProdParamsPsName = "aws:ssm:parameter:/config/application-production/";
		PropertySource sharedProdParamsPs = new PropertySource(sharedProdParamsPsName,
				SHARED_PRODUCTION_PROPERTIES);

		String appSpecificDefaultParamsPsName = "aws:ssm:parameter:/config/service-default/";
		PropertySource appSpecificDefaultParamsPs = new PropertySource(
				appSpecificDefaultParamsPsName, APPLICATION_SPECIFIC_DEFAULT_PROPERTIES);

		String sharedDefaultParamsPsName = "aws:ssm:parameter:/config/application-default/";
		PropertySource sharedDefaultParamsPs = new PropertySource(
				sharedDefaultParamsPsName, SHARED_DEFAULT_PROPERTIES);

		String appSpecificParamsPsName = "aws:ssm:parameter:/config/service/";
		PropertySource appSpecificParamsPs = new PropertySource(appSpecificParamsPsName,
				APPLICATION_SPECIFIC_PROPERTIES);

		String sharedParamsPsName = "aws:ssm:parameter:/config/application/";
		PropertySource sharedParamsPs = new PropertySource(sharedParamsPsName,
				SHARED_PROPERTIES);

		Environment expected = new Environment(application, profiles, null, null, null);

		expected.addAll(Arrays.asList(appSpecificProdParamsPs, sharedProdParamsPs,
				appSpecificDefaultParamsPs, sharedDefaultParamsPs, appSpecificParamsPs,
				sharedParamsPs));

		// Act
		Environment result = repository.findOne(application, profile, null);

		// Assert
		assertThat(result).usingRecursiveComparison().withStrictTypeChecking()
				.isEqualTo(expected);
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
		PropertySource sharedDefaultParamsPs = new PropertySource(
				sharedDefaultParamsPsName, SHARED_DEFAULT_PROPERTIES);

		String sharedParamsPsName = "aws:ssm:parameter:/config/application/";
		PropertySource sharedParamsPs = new PropertySource(sharedParamsPsName,
				SHARED_PROPERTIES);

		Environment expected = new Environment(application, profiles, null, null, null);

		expected.addAll(
				Arrays.asList(overridesPs, sharedDefaultParamsPs, sharedParamsPs));

		// Act
		Environment result = repository.findOne(application, profile, null);

		// Assert
		assertThat(result).usingRecursiveComparison().withStrictTypeChecking()
				.isEqualTo(expected);
	}

	@Test
	public void testFindOneWithSlashesInTheParameterKeyPath() {
		// Arrange
		String application = configServerProperties.getDefaultApplicationName();
		String profile = configServerProperties.getDefaultProfile();
		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);

		String sharedDefaultParamsPsName = "aws:ssm:parameter:/config/application-default/";
		PropertySource sharedDefaultParamsPs = new PropertySource(
				sharedDefaultParamsPsName, SHARED_DEFAULT_PROPERTIES);

		String sharedParamsPsName = "aws:ssm:parameter:/config/application/";
		PropertySource sharedParamsPs = new PropertySource(sharedParamsPsName,
				SHARED_PROPERTIES);

		Environment expected = new Environment(application, profiles, null, null, null);
		expected.addAll(Arrays.asList(sharedDefaultParamsPs, sharedParamsPs));

		// Act
		Environment result = repository.findOne(application, profile, null);

		// Assert
		assertThat(result).usingRecursiveComparison().withStrictTypeChecking()
				.isEqualTo(expected);
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
		assertThat(result).usingRecursiveComparison().withStrictTypeChecking()
				.isEqualTo(expected);
	}

}
