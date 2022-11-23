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

import org.apache.commons.lang3.RandomUtils;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParametersByPathRequest;
import software.amazon.awssdk.services.ssm.model.GetParametersByPathResponse;
import software.amazon.awssdk.services.ssm.model.Parameter;
import software.amazon.awssdk.services.ssm.model.ParameterType;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.config.server.config.ConfigServerProperties;
import org.springframework.util.StringUtils;

import static org.apache.commons.lang3.RandomStringUtils.randomAlphabetic;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.cloud.config.server.environment.AwsParameterStoreEnvironmentProperties.DEFAULT_PATH_SEPARATOR;

/**
 * Unit test is must for testing paginated logic, since doing it with integration test is
 * not that easy.
 *
 * @author Iulian Antohe
 */
public class AWSParameterStoreEnvironmentRepositoryUnitTest {

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

	private final SsmClient ssmClient = mock(SsmClient.class, "aws-ssm-client-mock");

	private final ConfigServerProperties configServerProperties = new ConfigServerProperties();

	private final AwsParameterStoreEnvironmentProperties environmentProperties = new AwsParameterStoreEnvironmentProperties();

	private final AwsParameterStoreEnvironmentRepository repository = new AwsParameterStoreEnvironmentRepository(
			ssmClient, configServerProperties, environmentProperties);

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

	private void setupAwsSsmClientMocks(Environment environment, boolean withSlashesForPropertyName,
			boolean paginatedResponse) {
		for (PropertySource ps : environment.getPropertySources()) {
			String path = StringUtils.delete(ps.getName(), environmentProperties.getOrigin());

			GetParametersByPathRequest request = GetParametersByPathRequest.builder().path(path)
					.recursive(environmentProperties.isRecursive())
					.withDecryption(environmentProperties.isDecryptValues())
					.maxResults(environmentProperties.getMaxResults()).build();

			Set<Parameter> parameters = getParameters(ps, path, withSlashesForPropertyName);

			GetParametersByPathResponse response = GetParametersByPathResponse.builder().parameters(parameters).build();

			if (paginatedResponse && environmentProperties.getMaxResults() < parameters.size()) {
				List<Set<Parameter>> chunks = splitParametersIntoChunks(parameters);

				String nextToken = null;

				for (int i = 0; i < chunks.size(); i++) {
					Set<Parameter> chunk = chunks.get(i);

					if (i == 0) {
						nextToken = generateNextToken();

						GetParametersByPathResponse responseClone = response.toBuilder().parameters(chunk)
								.nextToken(nextToken).build();

						when(ssmClient.getParametersByPath(eq(request))).thenReturn(responseClone);
					}
					else if (i == chunks.size() - 1) {
						GetParametersByPathRequest requestClone = request.toBuilder().nextToken(nextToken).build();
						GetParametersByPathResponse responseClone = response.toBuilder().parameters(chunk).build();

						when(ssmClient.getParametersByPath(eq(requestClone))).thenReturn(responseClone);
					}
					else {
						String newNextToken = generateNextToken();

						GetParametersByPathRequest requestClone = request.toBuilder().nextToken(nextToken).build();

						GetParametersByPathResponse responseClone = response.toBuilder().parameters(chunk)
								.nextToken(newNextToken).build();

						when(ssmClient.getParametersByPath(eq(requestClone))).thenReturn(responseClone);

						nextToken = newNextToken;
					}
				}
			}
			else {
				when(ssmClient.getParametersByPath(eq(request))).thenReturn(response);
			}
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
