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

package org.springframework.cloud.config.server.environment;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

import org.springframework.beans.factory.config.YamlProcessor;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.config.server.config.ConfigServerProperties;
import org.springframework.core.Ordered;
import org.springframework.core.io.InputStreamResource;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.ObjectUtils;
import org.springframework.util.PathMatcher;
import org.springframework.util.StringUtils;

import static org.springframework.cloud.config.server.environment.AwsS3EnvironmentRepository.PATH_SEPARATOR;


/**
 * @author Clay McCoy
 * @author Scott Frederick
 * @author Daniel Aiken
 * @author Geonwook Ham
 */
public class AwsS3EnvironmentRepository implements EnvironmentRepository, Ordered, SearchPathLocator {

	private final PathMatcher pathMatcher = new AntPathMatcher();

	protected static final String PATH_SEPARATOR = "/";

	private static final Log LOG = LogFactory.getLog(AwsS3EnvironmentRepository.class);

	private static final String AWS_S3_RESOURCE_SCHEME = "s3://";

	private final S3Client s3Client;

	private final String bucketName;

	private final ConfigServerProperties serverProperties;

	private final boolean useApplicationAsDirectory;

	protected int order = Ordered.LOWEST_PRECEDENCE;

	private final List<String> searchPaths;

	public AwsS3EnvironmentRepository(S3Client s3Client, String bucketName, ConfigServerProperties server) {
		this(s3Client, bucketName, false, server);
	}

	public AwsS3EnvironmentRepository(S3Client s3Client, String bucketName, boolean useApplicationAsDirectory,
			ConfigServerProperties server) {
		this(s3Client, bucketName, useApplicationAsDirectory, server, null);
	}

	public AwsS3EnvironmentRepository(S3Client s3Client, String bucketName, boolean useApplicationAsDirectory,
		ConfigServerProperties server, List<String> searchPaths) {
		this.s3Client = s3Client;
		this.bucketName = bucketName;
		this.serverProperties = server;
		this.useApplicationAsDirectory = useApplicationAsDirectory;
		this.searchPaths = (searchPaths == null ? Collections.emptyList() : searchPaths);
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	public void setOrder(int order) {
		this.order = order;
	}

	@Override
	public Environment findOne(String specifiedApplication, String specifiedProfiles, String specifiedLabel) {
		final String application = ObjectUtils.isEmpty(specifiedApplication)
				? serverProperties.getDefaultApplicationName() : specifiedApplication;
		final String profiles = ObjectUtils.isEmpty(specifiedProfiles) ? serverProperties.getDefaultProfile()
				: specifiedProfiles;
		final String label = ObjectUtils.isEmpty(specifiedLabel) ? serverProperties.getDefaultLabel() : specifiedLabel;

		String[] profileArray = parseProfiles(profiles);
		List<String> apps = Arrays.asList(StringUtils.commaDelimitedListToStringArray(application.replace(" ", "")));
		if (searchPaths.isEmpty() && !apps.contains(serverProperties.getDefaultApplicationName())) {
			Collections.reverse(apps);
			apps = new ArrayList<>(apps);
			apps.add(serverProperties.getDefaultApplicationName());
		}

		final Environment environment = new Environment(application, profileArray);
		environment.setLabel(label);

		List<String> labels;
		if (StringUtils.hasText(label) && label.contains(",")) {
			labels = Arrays.asList(StringUtils.commaDelimitedListToStringArray(label));
			Collections.reverse(labels);
		}
		else {
			labels = Collections.singletonList(label);
		}

		addPropertySources(environment, apps, profileArray, labels);

		if (LOG.isDebugEnabled()) {
			LOG.debug("Returning Environment: " + environment);
		}

		return environment;
	}

	private void addPropertySources(Environment environment, List<String> apps, String[] profiles,
			List<String> labels) {
		if (!this.searchPaths.isEmpty()) {
			for (String label : labels) {
				for (String profile : profiles) {
					for (String app : apps) {
						List<S3ConfigFile> s3ConfigFiles = getS3ConfigFileWithSearchPaths(app, profile, label);
						addPropertySource(environment, s3ConfigFiles);
					}
				}
			}
			return;
		}
		for (String label : labels) {
			// If we have profiles, add property sources with those profiles
			for (String profile : profiles) {
				addPropertySourcesForApps(apps,
						app -> addProfileSpecificPropertySource(environment, app, profile, label));
			}
		}

		// If we have no profiles just add property sources for all apps
		if (profiles.length == 0) {
			for (String label : labels) {
				addPropertySourcesForApps(apps,
						app -> addNonProfileSpecificPropertySource(environment, app, null, label));
			}
		}
		else {
			for (String label : labels) {
				// If we have profiles, we still need to add property sources from files
				// that
				// are not profile specific but we pass
				// along the profiles as well so we can check if any non-profile specific
				// YAML
				// files have profile specific documents
				// within them
				for (String profile : profiles) {
					addPropertySourcesForApps(apps,
							app -> addNonProfileSpecificPropertySource(environment, app, profile, label));
				}
			}
		}
	}

	private void addPropertySourcesForApps(List<String> apps, Consumer<String> addPropertySource) {
		apps.forEach(addPropertySource);
	}

	private void addProfileSpecificPropertySource(Environment environment, String app, String profile, String label) {
		if (!searchPaths.isEmpty() && app.equals(serverProperties.getDefaultApplicationName())) {
			return;
		}
		List<S3ConfigFile> s3ConfigFiles = searchPaths.isEmpty()
			? getS3ConfigFile(app, profile, label, this::getS3PropertiesOrJsonConfigFile, this::getProfileSpecificS3ConfigFileYaml)
			: getS3ConfigFileWithSearchPaths(app, profile, label);
		addPropertySource(environment, s3ConfigFiles);
	}

	private void addNonProfileSpecificPropertySource(Environment environment, String app, String profile, String label) {
		List<S3ConfigFile> s3ConfigFiles = searchPaths.isEmpty()
			? getS3ConfigFile(app, profile, label,
			this::getNonProfileSpecificPropertiesOrJsonConfigFile,
			this::getNonProfileSpecificS3ConfigFileYaml)
			: Collections.emptyList();
		addPropertySource(environment, s3ConfigFiles);
	}

	private void addPropertySource(Environment environment, List<S3ConfigFile> s3ConfigFiles) {
		for (S3ConfigFile s3ConfigFile : s3ConfigFiles) {
			final Properties config = s3ConfigFile.read();
			// This logic handles the case where the s3 file is a YAML file that is
			// not profile specific (ie it does not have -<profile> in the name)
			// and does not have any profile specific documents in it. In this case we do
			// not want to include this
			// property source we only want to include the document for the default
			// profile. When we create
			// the S3ConfigFile for this file we set the
			// shouldIncludeWithEmptyProperties to false
			// in ProfileSpecificYamlDocumentS3ConfigFile for this specific case.
			if (config != null) {
				if (!config.isEmpty() || s3ConfigFile.isShouldIncludeWithEmptyProperties()) {
					environment.setVersion(s3ConfigFile.getVersion());
					config.putAll(serverProperties.getOverrides());
					PropertySource propertySource = new PropertySource(s3ConfigFile.getName(), config);
					if (LOG.isDebugEnabled()) {
						LOG.debug("Adding property source to environment " + propertySource);
					}
					environment.add(propertySource);
				}
			}
		}
	}

	private String[] parseProfiles(String profiles) {
		return StringUtils.commaDelimitedListToStringArray(profiles);
	}

	private List<S3ConfigFile> getS3ConfigFile(String application, String profile, String label,
			JsonOrPropertiesS3ConfigFileCreator creator, YamlS3ConfigFileCreator yamlCreator) {
		S3ConfigFile configFile = creator.create(application, profile, label);
		if (configFile != null) {
			return List.of(configFile);
		}
		return new ArrayList<>(yamlCreator.create(application, profile, label));

	}

	private List<S3ConfigFile> getS3ConfigFileWithSearchPaths(
		String application, String profile, String label) {

		List<S3ConfigFile> result = new ArrayList<>();
		Set<String> seenKeys = new LinkedHashSet<>();

		for (String template : this.searchPaths) {
			String pattern = template
				.replace("{application}", application)
				.replace("{profile}", profile == null ? "" : profile)
				.replace("{label}", label == null ? "" : label);

			if (!pathMatcher.isPattern(pattern)) {
				boolean fileFound = false;
				for (String ext : List.of(".properties", ".json", ".yml", ".yaml")) {
					String key = pattern.endsWith(ext) ? pattern : pattern + ext;
					if (!seenKeys.add(key)) {
						continue;
					}
					try {
						s3Client.headObject(HeadObjectRequest.builder()
							.bucket(bucketName)
							.key(key)
							.build());
						result.addAll(wrapKeyWithConfigFiles(key, application, profile, label));
						fileFound = true;
						break;
					}
					catch (S3Exception e) {
						int status = e.statusCode();
						if (status != 404 && status != 403) {
							throw e;
						}
					}
				}
				if (fileFound) {
					continue;
				}

				String dirPrefix = pattern.endsWith("/") ? pattern : pattern + "/";
				String token = null;
				do {
					ListObjectsV2Response resp = s3Client.listObjectsV2(
						ListObjectsV2Request.builder()
							.bucket(bucketName)
							.prefix(dirPrefix)
							.continuationToken(token)
							.build());
					for (S3Object obj : resp.contents()) {
						String key = obj.key();
						if (!hasSupportedExtension(key)) {
							continue;
						}
						if (seenKeys.add(key)) {
							result.addAll(wrapKeyWithConfigFiles(key, application, profile, label));
						}
					}
					token = resp.nextContinuationToken();
				} while (token != null);

				continue;
			}

			if (pattern.endsWith(".*")) {
				String base = pattern.substring(0, pattern.length() - 2);
				for (String ext : List.of(".properties", ".json", ".yml", ".yaml")) {
					String key = base + ext;
					if (!seenKeys.add(key)) {
						continue;
					}
					try {
						s3Client.headObject(HeadObjectRequest.builder()
							.bucket(bucketName)
							.key(key)
							.build());
						result.addAll(wrapKeyWithConfigFiles(key, application, profile, label));
						break;
					}
					catch (S3Exception e) {
						int status = e.statusCode();
						if (status != 404 && status != 403) {
							throw e;
						}
					}
				}
				continue;
			}

			String prefix = extractPrefix(pattern);
			String token = null;
			do {
				ListObjectsV2Response resp = s3Client.listObjectsV2(
					ListObjectsV2Request.builder()
						.bucket(bucketName)
						.prefix(prefix)
						.continuationToken(token)
						.build());
				for (S3Object obj : resp.contents()) {
					String key = obj.key();
					if (!pathMatcher.match(pattern, key) || !hasSupportedExtension(key)) {
						continue;
					}
					if (seenKeys.add(key)) {
						result.addAll(wrapKeyWithConfigFiles(key, application, profile, label));
					}
				}
				token = resp.nextContinuationToken();
			} while (token != null);
		}

		return result;
	}

	private boolean hasSupportedExtension(String key) {
		return key.endsWith(".properties")
			|| key.endsWith(".json")
			|| key.endsWith(".yml")
			|| key.endsWith(".yaml");
	}

	private List<S3ConfigFile> wrapKeyWithConfigFiles(
		String key,
		String application,
		String profile,
		String label) {

		if (key.endsWith(".yml") || key.endsWith(".yaml")) {
			List<S3ConfigFile> files = new ArrayList<>();
			files.addAll(getProfileSpecificYamlFromKey(key, application, profile, label));
			files.addAll(getNonProfileSpecificYamlFromKey(key, application, profile, label));
			return files;
		}
		return createConfigFileFromKey(key, application, profile, label)
			.map(Collections::singletonList)
			.orElseGet(Collections::emptyList);
	}


	private List<S3ConfigFile> getProfileSpecificYamlFromKey(
		String key, String application, String profile, String label) {

		YamlConfigFileFromKey config = new YamlConfigFileFromKey(
			key, application, profile, label,
			bucketName, useApplicationAsDirectory, s3Client,
			properties -> YamlS3ConfigFile.profileMatchesActivateProperty(profile, properties)
				? YamlProcessor.MatchStatus.FOUND
				: YamlProcessor.MatchStatus.NOT_FOUND
		);
		config.setShouldIncludeWithEmptyProperties(false);
		return List.of(config);
	}

	private List<S3ConfigFile> getNonProfileSpecificYamlFromKey(
		String key, String application, String profile, String label) {

		YamlConfigFileFromKey config = new YamlConfigFileFromKey(
			key, application, profile, label,
			bucketName, useApplicationAsDirectory, s3Client,
			properties -> !YamlS3ConfigFile.onProfilePropertyExists(properties)
				? YamlProcessor.MatchStatus.FOUND
				: YamlProcessor.MatchStatus.NOT_FOUND
		);
		return List.of(config);
	}

	private String extractPrefix(String pattern) {
		int idx = pattern.indexOf('*');
		int q   = pattern.indexOf('?');
		if (q != -1 && (idx == -1 || q < idx)) {
			idx = q;
		}
		if (idx <= 0) {
			return "";
		}
		int slash = pattern.lastIndexOf('/', idx);
		return (slash == -1 ? "" : pattern.substring(0, slash + 1));
	}


	private Optional<S3ConfigFile> createConfigFileFromKey(String key,
		String application, String profile, String label) {
		String ext = key.substring(key.lastIndexOf('.') + 1);
		if ("properties".equalsIgnoreCase(ext)) {
			return Optional.of(new PropertyConfigFileFromKey(
				key, application, profile, label, bucketName, s3Client));
		}
		if ("json".equalsIgnoreCase(ext)) {
			return Optional.of(new JsonConfigFileFromKey(
				key, application, profile, label, bucketName, s3Client));
		}
		if ("yml".equalsIgnoreCase(ext) || "yaml".equalsIgnoreCase(ext)) {
			return Optional.of(new YamlConfigFileFromKey(
				key, application, profile, label, bucketName, s3Client));
		}
		return Optional.empty();
	}


	private List<YamlS3ConfigFile> getNonProfileSpecificS3ConfigFileYaml(String application, String profile,
			String label) {
		List<YamlS3ConfigFile> configFiles = new ArrayList<>();
		if (profile != null) {
			try {
				YamlS3ConfigFile configFileDocument = new ProfileSpecificYamlDocumentS3ConfigFile(application, profile,
						label, bucketName, useApplicationAsDirectory, s3Client);
				configFileDocument.setShouldIncludeWithEmptyProperties(false);
				configFiles.add(configFileDocument);
			}
			catch (Exception e) {
				if (LOG.isDebugEnabled()) {
					LOG.debug("Did not find specific yaml document in non-profile specific file using application <"
							+ application + "> profile <" + profile + "> label <" + label + ">.");
				}
			}
		}
		try {
			YamlS3ConfigFile configFile = new NonProfileSpecificYamlDocumentS3ConfigFile(application, null, label,
					bucketName, useApplicationAsDirectory, s3Client);
			configFiles.add(configFile);
		}
		catch (Exception e) {
			if (LOG.isDebugEnabled()) {
				LOG.debug(
						"Did not find non-profile specific yaml document in non-profile specific file using application <"
								+ application + "> profile <null>" + " label <" + label + ">.");
			}
		}
		return configFiles;
	}

	private List<YamlS3ConfigFile> getProfileSpecificS3ConfigFileYaml(String application, String profile,
			String label) {
		try {
			YamlS3ConfigFile configFile = new ProfileSpecificYamlS3ConfigFile(application, profile, label, bucketName,
					useApplicationAsDirectory, s3Client);
			return List.of(configFile);
		}
		catch (Exception e) {
			LOG.warn("Could not read YAML file", e);
			return Collections.emptyList();
		}
	}

	private S3ConfigFile getNonProfileSpecificPropertiesOrJsonConfigFile(String application, String profile,
			String label) {
		return getS3PropertiesOrJsonConfigFile(application, null, label);
	}

	private S3ConfigFile getS3PropertiesOrJsonConfigFile(String application, String profile, String label) {
		if (LOG.isDebugEnabled()) {
			LOG.debug("Getting S3 config file for " + application + " " + profile + " " + label);
		}
		try {
			return new PropertyS3ConfigFile(application, profile, label, bucketName, useApplicationAsDirectory,
					s3Client);
		}
		catch (Exception propertyException) {
			if (LOG.isDebugEnabled()) {
				LOG.debug("Did not find properties file using application <" + application + "> profile <" + profile
						+ "> label <" + label + ">.  Trying json extension", propertyException);
			}
			try {
				return new JsonS3ConfigFile(application, profile, label, bucketName, useApplicationAsDirectory,
						s3Client);
			}
			catch (Exception jsonException) {
				if (LOG.isDebugEnabled()) {
					LOG.debug("Did not find json file using application <" + application + "> profile <" + profile
							+ "> label <" + label + ">.  Trying yaml extension", jsonException);
				}
				return null;
			}
		}
	}

	@Override
	public Locations getLocations(String application, String profiles, String label) {
		StringBuilder baseLocation = new StringBuilder(AWS_S3_RESOURCE_SCHEME + bucketName + PATH_SEPARATOR);
		if (!StringUtils.hasText(label) && StringUtils.hasText(serverProperties.getDefaultLabel())) {
			label = serverProperties.getDefaultLabel();
		}
		// both the passed in label and the default label property could be null
		if (StringUtils.hasText(label)) {
			baseLocation.append(label);
		}

		return new Locations(application, profiles, label, null, new String[] { baseLocation.toString() });
	}

	interface YamlS3ConfigFileCreator {

		List<YamlS3ConfigFile> create(String application, String profile, String label);

	}

	interface JsonOrPropertiesS3ConfigFileCreator {

		S3ConfigFile create(String application, String profile, String label);

	}

}

abstract class S3ConfigFile {

	protected static final Log LOG = LogFactory.getLog(S3ConfigFile.class);

	protected String application;

	protected String label;

	protected String profile;

	protected String bucketName;

	protected S3Client s3Client;

	protected Properties properties;

	private String version;

	private boolean shouldIncludeWithEmptyProperties = true;

	private final boolean useApplicationAsDirectory;

	protected S3ConfigFile(String application, String profile, String label, String bucketName,
			boolean useApplicationAsDirectory, S3Client s3Client) {
		this.application = application;
		this.profile = profile;
		this.label = label;
		this.bucketName = bucketName;
		this.s3Client = s3Client;
		this.useApplicationAsDirectory = useApplicationAsDirectory;
	}

	String getVersion() {
		return version;
	}

	abstract Properties read();

	boolean isShouldIncludeWithEmptyProperties() {
		return shouldIncludeWithEmptyProperties;
	}

	void setShouldIncludeWithEmptyProperties(boolean shouldIncludeWithEmptyProperties) {
		this.shouldIncludeWithEmptyProperties = shouldIncludeWithEmptyProperties;
	}

	public String getName() {
		return createPropertySourceName(application, profile);
	}

	protected ResponseInputStream<GetObjectResponse> getObject() throws Exception {
		assert (getExtensions() != null && !getExtensions().isEmpty());
		List<String> extensions = getExtensions();
		for (int i = 0; i < extensions.size(); i++) {
			String key = buildObjectKeyPrefix() + "." + extensions.get(i);
			if (LOG.isDebugEnabled()) {
				LOG.debug("Getting object with key " + key);
			}
			try {
				ResponseInputStream<GetObjectResponse> inputStream = s3Client
					.getObject(GetObjectRequest.builder().bucket(bucketName).key(key).build());
				this.version = inputStream.response().versionId();
				return inputStream;
			}
			catch (Exception e) {
				if (i < extensions.size() - 1) {
					if (LOG.isDebugEnabled()) {
						LOG.debug("Did not find " + key + ".  Trying next extension", e);
					}
				}
				else {
					throw e;
				}
			}
		}
		return null;
	}

	protected abstract List<String> getExtensions();

	protected String buildObjectKeyPrefix() {
		return buildObjectKeyPrefix(true);
	}

	String buildObjectKeyPrefix(boolean includeProfile) {
		StringBuilder objectKeyPrefix = new StringBuilder();
		if (!ObjectUtils.isEmpty(label)) {
			objectKeyPrefix.append(label).append(PATH_SEPARATOR);
		}
		objectKeyPrefix.append(application);
		if (this.useApplicationAsDirectory) {
			objectKeyPrefix.append(PATH_SEPARATOR).append("application");
		}
		if (!ObjectUtils.isEmpty(profile) && includeProfile) {
			objectKeyPrefix.append("-").append(profile);
		}
		return objectKeyPrefix.toString();
	}

	private String createPropertySourceName(String app, String profile) {
		StringBuilder propertySourceName = new StringBuilder().append("s3:").append(app);
		if (this.useApplicationAsDirectory) {
			propertySourceName.append(PATH_SEPARATOR).append("application");
		}
		if (profile != null) {
			propertySourceName.append("-").append(profile);
		}
		return propertySourceName.toString();
	}

}

class PropertyS3ConfigFile extends S3ConfigFile {

	PropertyS3ConfigFile(String application, String profile, String label,
		String bucketName, boolean useApplicationAsDirectory,
		S3Client s3Client) {
		this(application, profile, label, bucketName, useApplicationAsDirectory, s3Client, true);
	}

	PropertyS3ConfigFile(String application, String profile, String label, String bucketName,
			boolean useApplicationAsDirectory, S3Client s3Client, boolean callReadImmediately) {
		super(application, profile, label, bucketName, useApplicationAsDirectory, s3Client);
		if (callReadImmediately) {
			this.properties = read();
		}
	}

	@Override
	public Properties read() {
		if (this.properties != null) {
			return this.properties;
		}
		Properties props = new Properties();
		try (InputStream in = getObject()) {
			props.load(in);
		}
		catch (Exception e) {
			LOG.warn("Exception thrown when reading property file", e);
			throw new IllegalStateException("Cannot load environment", e);
		}
		return props;
	}

	@Override
	protected List<String> getExtensions() {
		return List.of("properties");
	}

}

class YamlS3ConfigFile extends S3ConfigFile {

	final YamlProcessor.DocumentMatcher[] documentMatchers;

	YamlS3ConfigFile(String application, String profile, String label, String bucketName,
			boolean useApplicationAsDirectory, S3Client s3Client) {
		this(application, profile, label, bucketName, useApplicationAsDirectory, s3Client, true,
				new YamlProcessor.DocumentMatcher[] {});
	}

	YamlS3ConfigFile(String application, String profile, String label, String bucketName,
			boolean useApplicationAsDirectory, S3Client s3Client, boolean callReadImmediately,
			final YamlProcessor.DocumentMatcher... documentMatchers) {
		super(application, profile, label, bucketName, useApplicationAsDirectory, s3Client);
		this.documentMatchers = documentMatchers;
		if (callReadImmediately) {
			this.properties = read();
		}
	}

	protected static boolean profileMatchesActivateProperty(String profile, Properties properties) {
		return profile.equals(properties.get("spring.config.activate.on-profile"))
				|| profile.equals(properties.get("spring.config.activate.onProfile"));
	}

	protected static boolean onProfilePropertyExists(Properties properties) {
		return properties.get("spring.config.activate.on-profile") != null
				|| properties.get("spring.config.activate.onProfile") != null;
	}

	@Override
	public Properties read() {
		if (properties != null) {
			return properties;
		}
		final YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
		try (InputStream in = getObject()) {
			yaml.setResources(new InputStreamResource(in));
			yaml.setDocumentMatchers(documentMatchers);
			return yaml.getObject();
		}
		catch (Exception e) {
			LOG.warn("Could not read YAML file", e);
			throw new IllegalStateException("Cannot load environment", e);
		}
	}

	@Override
	protected List<String> getExtensions() {
		return List.of("yml", "yaml");
	}

}

class ProfileSpecificYamlDocumentS3ConfigFile extends YamlS3ConfigFile {

	ProfileSpecificYamlDocumentS3ConfigFile(String application, String profile, String label, String bucketName,
			boolean useApplicationAsDirectory, S3Client s3Client) {
		super(application, profile, label, bucketName, useApplicationAsDirectory, s3Client, true,
				properties -> profileMatchesActivateProperty(profile, properties) ? YamlProcessor.MatchStatus.FOUND
						: YamlProcessor.MatchStatus.NOT_FOUND);
	}

	@Override
	public boolean isShouldIncludeWithEmptyProperties() {
		return false;
	}

	@Override
	protected String buildObjectKeyPrefix() {
		return super.buildObjectKeyPrefix(false);
	}

}

class NonProfileSpecificYamlDocumentS3ConfigFile extends YamlS3ConfigFile {

	NonProfileSpecificYamlDocumentS3ConfigFile(String application, String profile, String label, String bucketName,
			boolean useApplicationAsDirectory, S3Client s3Client) {
		super(application, profile, label, bucketName, useApplicationAsDirectory, s3Client, true,
				properties -> !onProfilePropertyExists(properties) ? YamlProcessor.MatchStatus.FOUND
						: YamlProcessor.MatchStatus.NOT_FOUND);
	}

}

class ProfileSpecificYamlS3ConfigFile extends YamlS3ConfigFile {

	ProfileSpecificYamlS3ConfigFile(String application, String profile, String label, String bucketName,
			boolean useApplicationAsDirectory, S3Client s3Client) {
		super(application, profile, label, bucketName, useApplicationAsDirectory, s3Client, true,
				properties -> !onProfilePropertyExists(properties) ? YamlProcessor.MatchStatus.ABSTAIN
						: profileMatchesActivateProperty(profile, properties) ? YamlProcessor.MatchStatus.FOUND
								: YamlProcessor.MatchStatus.NOT_FOUND);
	}

}

class JsonS3ConfigFile extends YamlS3ConfigFile {

	// YAML is a superset of JSON, which means you can parse JSON with a YAML parser

	JsonS3ConfigFile(String application, String profile, String label, String bucketName,
			boolean useApplicationAsDirectory, S3Client s3Client) {
		super(application, profile, label, bucketName, useApplicationAsDirectory, s3Client);
		this.properties = read();
	}

	JsonS3ConfigFile(String application, String profile, String label, String bucketName,
		boolean useApplicationAsDirectory, S3Client s3Client, boolean callReadImmediately) {
		super(application, profile, label, bucketName, useApplicationAsDirectory, s3Client, callReadImmediately);
	}

	@Override
	protected List<String> getExtensions() {
		return List.of("json");
	}

}

class PropertyConfigFileFromKey extends PropertyS3ConfigFile {

	private final String key;

	PropertyConfigFileFromKey(String key,
		String application,
		String profile,
		String label,
		String bucketName,
		S3Client s3Client) {
		super(application, profile, label, bucketName, false, s3Client, false);
		this.key = key;
		this.properties = read();
	}

	@Override
	public String getName() {
		return "s3:" + bucketName + "/" + key;
	}

	@Override
	protected String buildObjectKeyPrefix() {
		return key.substring(0, key.lastIndexOf('.'));
	}
}

class YamlConfigFileFromKey extends YamlS3ConfigFile {

	private final String key;

	YamlConfigFileFromKey(String key,
		String application,
		String profile,
		String label,
		String bucketName,
		S3Client s3Client) {
		super(application, profile, label, bucketName, false, s3Client, false);
		this.key = key;
		this.properties = read();
	}

	YamlConfigFileFromKey(String key,
		String application,
		String profile,
		String label,
		String bucketName,
		boolean useApplicationAsDirectory,
		S3Client s3Client,
		YamlProcessor.DocumentMatcher... matchers) {
		super(application, profile, label, bucketName, useApplicationAsDirectory, s3Client, false, matchers);
		this.key = key;
		this.properties = read();
	}

	@Override
	public String getName() {
		return "s3:" + bucketName + "/" + key;
	}

	@Override
	protected String buildObjectKeyPrefix() {
		return key.substring(0, key.lastIndexOf('.'));
	}
}

class JsonConfigFileFromKey extends JsonS3ConfigFile {

	private final String key;

	JsonConfigFileFromKey(String key, String application, String profile,
		String label, String bucketName, S3Client s3Client) {
		super(application, profile, label, bucketName, false, s3Client, false);
		this.key = key;
		this.properties = read();
	}

	@Override
	public String getName() {
		return "s3:" + bucketName + "/" + key;
	}

	@Override
	protected String buildObjectKeyPrefix() {
		return key.substring(0, key.lastIndexOf('.'));
	}
}

