/*
 * Copyright 2013-present the original author or authors.
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

package org.springframework.cloud.config.server.environment.compliance;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.cloud.config.server.environment.compliance.ConfigFile.Format;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Verifies that an {@link EnvironmentRepository} orders property sources the way Spring
 * Boot orders config data, which is how a standalone application would resolve the same
 * files. Subclasses supply an {@link EnvironmentRepositoryFixture} for the backend under
 * test.
 * <p>
 * Every stored document carries a {@link #ID} property identifying it, and a
 * {@link #VALUE} property with the same value. Scenarios assert the order of the
 * identifiers in the returned property sources, highest precedence first, and that a
 * client resolving {@link #VALUE} would see the value of the highest precedence document.
 * This keeps the assertions independent of how each backend names its property sources.
 * <p>
 * Scenarios that need an optional {@link Capability} are skipped when the fixture does
 * not declare it. If a backend supports a feature but resolves it incorrectly, override
 * the scenario in the subclass and annotate it with {@code @Disabled}, linking to the
 * issue that tracks the fix.
 *
 * @author Ryan Baxter
 */
public abstract class EnvironmentRepositoryComplianceTests {

	/**
	 * Property identifying the document a property source was created from.
	 */
	public static final String ID = "compliance.id";

	/**
	 * Property present in every document, used to check which value a client resolves.
	 */
	public static final String VALUE = "compliance.value";

	/**
	 * The fixture for the backend under test.
	 * @return the fixture
	 */
	protected abstract EnvironmentRepositoryFixture fixture();

	@BeforeEach
	void resetFixture() throws Exception {
		fixture().reset();
	}

	@Test
	public void defaultProfileResolvesFilesWithoutProfile() throws Exception {
		store("orders", null);
		store("application", null);
		store("orders", "dev");
		store("application", "dev");

		assertPrecedence(findOne("orders", "default", null), "orders", "application");
	}

	@Test
	public void defaultProfileResolvesDefaultProfileFilesFirst() throws Exception {
		assumeCapabilities(Capability.DISTINCT_DEFAULT_PROFILE);
		store("orders", null);
		store("application", null);
		store("orders", "default");
		store("application", "default");

		assertPrecedence(findOne("orders", "default", null), "orders-default", "application-default", "orders",
				"application");
	}

	@Test
	public void activeProfileExcludesDefaultProfileFiles() throws Exception {
		assumeCapabilities(Capability.DISTINCT_DEFAULT_PROFILE);
		assumeNoCapability(Capability.DEFAULT_PROFILE_ALWAYS_ACTIVE);
		store("orders", null);
		store("application", null);
		store("orders", "default");
		store("orders", "dev");

		assertPrecedence(findOne("orders", "dev", null), "orders-dev", "orders", "application");
	}

	@Test
	public void defaultProfileFilesAreAppliedWhenAnotherProfileIsActive() throws Exception {
		assumeCapabilities(Capability.DISTINCT_DEFAULT_PROFILE, Capability.DEFAULT_PROFILE_ALWAYS_ACTIVE);
		store("orders", null);
		store("application", null);
		store("orders", "default");
		store("application", "default");
		store("orders", "dev");

		assertPrecedence(findOne("orders", "dev", null), "orders-dev", "orders-default", "application-default",
				"orders", "application");
	}

	@Test
	public void profileSpecificFilesTakePrecedenceOverFilesWithoutProfile() throws Exception {
		store("orders", null);
		store("application", null);
		store("orders", "dev");
		store("application", "dev");
		store("orders", "prod");
		store("application", "prod");

		assertPrecedence(findOne("orders", "dev", null), "orders-dev", "application-dev", "orders", "application");
	}

	@Test
	public void lastProfileTakesPrecedence() throws Exception {
		store("orders", null);
		store("application", null);
		store("orders", "dev");
		store("application", "dev");
		store("orders", "mysql");
		store("application", "mysql");
		store("orders", "prod");

		assertPrecedence(findOne("orders", "dev,mysql", null), "orders-mysql", "application-mysql", "orders-dev",
				"application-dev", "orders", "application");
	}

	@Test
	public void lastApplicationTakesPrecedence() throws Exception {
		assumeCapabilities(Capability.MULTIPLE_APPLICATIONS);
		store("orders", null);
		store("billing", null);
		store("application", null);
		store("orders", "dev");
		store("billing", "dev");
		store("application", "dev");

		assertPrecedence(findOne("orders,billing", "dev", null), "billing-dev", "orders-dev", "application-dev",
				"billing", "orders", "application");
	}

	@Test
	public void applicationNamedApplicationIsResolvedOnce() throws Exception {
		store("application", null);
		store("application", "dev");

		assertPrecedence(findOne("application", "dev", null), "application-dev", "application");
	}

	@Test
	public void applicationNameStartingWithApplicationIncludesSharedFiles() throws Exception {
		store("applicationfoo", null);
		store("application", null);
		store("applicationfoo", "dev");
		store("application", "dev");

		assertPrecedence(findOne("applicationfoo", "dev", null), "applicationfoo-dev", "application-dev",
				"applicationfoo", "application");
	}

	@Test
	public void missingFilesAreSkipped() throws Exception {
		store("orders", "dev");
		store(file("application", null, null, Format.YAML, "application", Map.of("fallback", "application")));

		Environment environment = findOne("orders", "dev", null);

		assertPrecedence(environment, "orders-dev", "application");
		assertThat(resolve(environment, "fallback")).isEqualTo("application");
	}

	@Test
	public void profileDocumentsInFilesWithoutProfileAreActivatedByProfile() throws Exception {
		assumeCapabilities(Capability.MULTI_DOCUMENT_YAML);
		store(new ConfigFile("orders", null, null, Format.YAML,
				List.of(document(null, "orders"), document("dev", "orders#dev"), document("prod", "orders#prod"))));
		store("application", null);
		store("orders", "dev");

		assertPrecedence(findOne("orders", "dev", null), "orders-dev", "orders#dev", "orders", "application");
		assertPrecedence(findOne("orders", "default", null), "orders", "application");
	}

	@Test
	public void profileExpressionsActivateDocuments() throws Exception {
		assumeCapabilities(Capability.MULTI_DOCUMENT_YAML, Capability.PROFILE_EXPRESSIONS);
		store(new ConfigFile("orders", null, null, Format.YAML,
				List.of(document(null, "orders"), document("!dev", "orders#!dev"))));
		store("application", null);

		assertPrecedence(findOne("orders", "prod", null), "orders#!dev", "orders", "application");
		assertPrecedence(findOne("orders", "dev", null), "orders", "application");
	}

	@Test
	public void labelSelectsConfiguration() throws Exception {
		assumeCapabilities(Capability.LABELS);
		storeLabelled("v1");
		storeLabelled("v2");

		assertPrecedence(findOne("orders", "dev", "v1"), "v1/orders-dev", "v1/application-dev", "v1/orders",
				"v1/application");
	}

	@Test
	public void lastLabelTakesPrecedence() throws Exception {
		assumeCapabilities(Capability.LABELS, Capability.MULTIPLE_LABELS);
		storeLabelled("v1");
		storeLabelled("v2");

		assertPrecedence(findOne("orders", "dev", "v1,v2"), "v2/orders-dev", "v2/application-dev", "v2/orders",
				"v2/application", "v1/orders-dev", "v1/application-dev", "v1/orders", "v1/application");
	}

	@Test
	public void propertiesFilesTakePrecedenceOverYamlFiles() throws Exception {
		assumeCapabilities(Capability.MIXED_FORMATS);
		store(file("orders", null, null, Format.YAML, "orders.yml", Map.of()));
		store(file("orders", null, null, Format.PROPERTIES, "orders.properties", Map.of()));
		store("application", null);

		assertPrecedence(findOne("orders", "default", null), "orders.properties", "orders.yml", "application");
	}

	/**
	 * Store a single-document YAML file without a label.
	 * @param application the application name
	 * @param profile the profile, or {@code null}
	 * @throws Exception if the file cannot be stored
	 */
	protected void store(String application, String profile) throws Exception {
		store(file(application, profile, null));
	}

	/**
	 * Store a file in the backend.
	 * @param file the file
	 * @throws Exception if the file cannot be stored
	 */
	protected void store(ConfigFile file) throws Exception {
		fixture().store(file);
	}

	private void storeLabelled(String label) throws Exception {
		store(file("orders", null, label));
		store(file("application", null, label));
		store(file("orders", "dev", label));
		store(file("application", "dev", label));
	}

	/**
	 * Create a single-document YAML file identified by its {@link ConfigFile#path()}
	 * without extension, for example {@code v1/orders-dev}.
	 * @param application the application name
	 * @param profile the profile, or {@code null}
	 * @param label the label, or {@code null}
	 * @return the file
	 */
	protected static ConfigFile file(String application, String profile, String label) {
		String id = (label != null ? label + "/" : "") + (profile != null ? application + "-" + profile : application);
		return file(application, profile, label, Format.YAML, id, Map.of());
	}

	/**
	 * Create a single-document file.
	 * @param application the application name
	 * @param profile the profile, or {@code null}
	 * @param label the label, or {@code null}
	 * @param format the format
	 * @param id the identifier of the document
	 * @param properties additional properties
	 * @return the file
	 */
	protected static ConfigFile file(String application, String profile, String label, Format format, String id,
			Map<String, Object> properties) {
		Map<String, Object> all = new LinkedHashMap<>(properties);
		all.putAll(document(null, id).properties());
		return new ConfigFile(application, profile, label, format, List.of(new ConfigDocument(null, all)));
	}

	/**
	 * Create a document identified by {@code id}.
	 * @param activateOnProfile the profile expression activating the document, or
	 * {@code null}
	 * @param id the identifier of the document
	 * @return the document
	 */
	protected static ConfigDocument document(String activateOnProfile, String id) {
		Map<String, Object> properties = new LinkedHashMap<>();
		properties.put(ID, id);
		properties.put(VALUE, id);
		return new ConfigDocument(activateOnProfile, properties);
	}

	/**
	 * Query the repository under test.
	 * @param application the application
	 * @param profile the profile
	 * @param label the label
	 * @return the environment
	 * @throws Exception if the repository cannot be queried
	 */
	protected Environment findOne(String application, String profile, String label) throws Exception {
		fixture().prepare();
		return fixture().repository().findOne(application, profile, label);
	}

	/**
	 * Skip the current scenario if the fixture supports the capability.
	 * @param capability the capability that makes the scenario not applicable
	 */
	protected void assumeNoCapability(Capability capability) {
		assumeFalse(fixture().capabilities().contains(capability), () -> "Fixture has capability " + capability);
	}

	/**
	 * Skip the current scenario unless the fixture supports all the capabilities.
	 * @param capabilities the required capabilities
	 */
	protected void assumeCapabilities(Capability... capabilities) {
		List<Capability> missing = new ArrayList<>(Arrays.asList(capabilities));
		missing.removeAll(fixture().capabilities());
		assumeTrue(missing.isEmpty(), () -> "Fixture does not support " + missing);
	}

	/**
	 * Assert the documents the environment was built from, highest precedence first, and
	 * that a client resolves {@link #VALUE} from the first of them.
	 * @param environment the environment
	 * @param ids the expected document identifiers
	 */
	protected static void assertPrecedence(Environment environment, String... ids) {
		assertThat(ids(environment)).as("Documents in %s", names(environment)).containsExactly(ids);
		assertThat(resolve(environment, VALUE)).as("Resolved %s", VALUE).isEqualTo(ids[0]);
	}

	/**
	 * The identifiers of the documents the environment was built from, highest precedence
	 * first. Property sources that were not created from a stored document, for example
	 * overrides, are ignored.
	 * @param environment the environment
	 * @return the identifiers
	 */
	protected static List<String> ids(Environment environment) {
		List<String> ids = new ArrayList<>();
		for (PropertySource source : environment.getPropertySources()) {
			Object id = source.getSource().get(ID);
			if (id != null) {
				ids.add(String.valueOf(id));
			}
		}
		return ids;
	}

	/**
	 * Resolve a property the way a client does: the first property source containing the
	 * key wins.
	 * @param environment the environment
	 * @param key the property key
	 * @return the resolved value as a string, or {@code null}
	 */
	protected static String resolve(Environment environment, String key) {
		for (PropertySource source : environment.getPropertySources()) {
			Object value = source.getSource().get(key);
			if (value != null) {
				return String.valueOf(value);
			}
		}
		return null;
	}

	private static List<String> names(Environment environment) {
		return environment.getPropertySources().stream().map(PropertySource::getName).toList();
	}

}
