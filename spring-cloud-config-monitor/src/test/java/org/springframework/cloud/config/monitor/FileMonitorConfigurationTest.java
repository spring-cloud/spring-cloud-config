/*
 * Copyright 2015-2019 the original author or authors.
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

package org.springframework.cloud.config.monitor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.springframework.cloud.config.server.environment.AbstractScmEnvironmentRepository;
import org.springframework.cloud.config.server.environment.JGitEnvironmentProperties;
import org.springframework.cloud.config.server.environment.JGitEnvironmentRepository;
import org.springframework.cloud.config.server.environment.NativeEnvironmentProperties;
import org.springframework.cloud.config.server.environment.NativeEnvironmentRepository;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.FileSystemResourceLoader;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Gilles Robert
 * @author Stefan Pfeiffer
 *
 */
public class FileMonitorConfigurationTest {

	private static final String SAMPLE_PATH = "resources/pathsamples";

	private static final String SAMPLE_FILE_URL = "file:///test";

	private FileMonitorConfiguration fileMonitorConfiguration = new FileMonitorConfiguration();

	private List<AbstractScmEnvironmentRepository> repositories = new ArrayList<>();

	@Before
	public void setup() {
		fileMonitorConfiguration.setResourceLoader(new FileSystemResourceLoader());
	}

	@After
	public void tearDown() {
		fileMonitorConfiguration.stop();
	}

	@Test
	public void testStart_whenRepositoriesAreNull() {
		// given

		// when
		fileMonitorConfiguration.start();

		// then
		Set<Path> directory = getDirectory();
		assertThat(directory).isNull();
	}

	@Test
	public void testStart_withNativeEnvironmentRepository() {
		// given
		NativeEnvironmentRepository repository = createNativeEnvironmentRepository();
		ReflectionTestUtils.setField(fileMonitorConfiguration,
				"nativeEnvironmentRepository", repository);

		// when
		fileMonitorConfiguration.start();

		// then
		assertOnDirectory(1);
	}

	@Test
	public void testStart_withOneScmRepository() {
		// given
		AbstractScmEnvironmentRepository repository = createScmEnvironmentRepository(
				SAMPLE_PATH);
		addScmRepository(repository);

		// when
		fileMonitorConfiguration.start();

		// then
		assertOnDirectory(1);
	}

	@Test
	public void testStart_withTwoScmRepositories() {
		// given
		AbstractScmEnvironmentRepository repository = createScmEnvironmentRepository(
				SAMPLE_PATH);
		AbstractScmEnvironmentRepository secondRepository = createScmEnvironmentRepository(
				"anotherPath");
		addScmRepository(repository);
		addScmRepository(secondRepository);

		// when
		fileMonitorConfiguration.start();

		// then
		assertOnDirectory(2);
	}

	@Test
	public void testStart_withOneFileUrlScmRepository() {
		// given
		AbstractScmEnvironmentRepository repository = createScmEnvironmentRepository(
				SAMPLE_FILE_URL);
		addScmRepository(repository);

		// when
		fileMonitorConfiguration.start();

		// then
		assertOnDirectory(1);
	}

	@Test
	public void testStart_withTwoMixedPathAndFileUrlScmRepositories() {
		// given
		AbstractScmEnvironmentRepository repository = createScmEnvironmentRepository(
				SAMPLE_PATH);
		AbstractScmEnvironmentRepository secondRepository = createScmEnvironmentRepository(
				SAMPLE_FILE_URL);
		addScmRepository(repository);
		addScmRepository(secondRepository);

		// when
		fileMonitorConfiguration.start();

		// then
		assertOnDirectory(2);
	}

	private void addScmRepository(AbstractScmEnvironmentRepository... repository) {
		repositories.addAll(Arrays.asList(repository));
		ReflectionTestUtils.setField(fileMonitorConfiguration, "scmRepositories",
				repositories);
	}

	private NativeEnvironmentRepository createNativeEnvironmentRepository() {
		ConfigurableEnvironment environment = createConfigurableEnvironment();
		NativeEnvironmentProperties properties = new NativeEnvironmentProperties();
		properties.setSearchLocations(new String[] { "classpath:pathsamples" });
		return new NativeEnvironmentRepository(environment, properties);
	}

	private AbstractScmEnvironmentRepository createScmEnvironmentRepository(String uri) {
		ConfigurableEnvironment environment = createConfigurableEnvironment();
		JGitEnvironmentProperties properties = new JGitEnvironmentProperties();
		properties.setUri(uri);
		return new JGitEnvironmentRepository(environment, properties);
	}

	private void assertOnDirectory(int expectedDirectorySize) {
		Set<Path> directory = getDirectory();
		assertThat(directory).isNotNull();
		assertThat(directory).hasSize(expectedDirectorySize);
	}

	private ConfigurableEnvironment createConfigurableEnvironment() {
		return new MockEnvironment();
	}

	@SuppressWarnings("unchecked")
	private Set<Path> getDirectory() {
		return (Set<Path>) ReflectionTestUtils.getField(fileMonitorConfiguration,
				"directory");
	}

}
