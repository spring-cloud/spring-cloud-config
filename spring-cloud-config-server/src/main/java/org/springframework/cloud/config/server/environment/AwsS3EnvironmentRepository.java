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
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

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
import org.springframework.core.env.Profiles;
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

	private static final List<String> SUPPORTED_EXTENSIONS = List.of(".properties", ".json", ".yml", ".yaml");

	private static final List<String> EMPTY_EXTENSION = List.of("");

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
		this(s3Client, bucketName, useApplicationAsDirectory, server, Collections.emptyList());
	}

	public AwsS3EnvironmentRepository(S3Client s3Client, String bucketName, boolean useApplicationAsDirectory,
			ConfigServerProperties server, List<String> searchPaths) {
		this.s3Client = s3Client;
		this.bucketName = bucketName;
		this.serverProperties = server;
		this.useApplicationAsDirectory = useApplicationAsDirectory;
		this.searchPaths = (searchPaths == null ? Collections.emptyList() : searchPaths);
	}

	public AwsS3EnvironmentRepository(S3Client s3Client, AwsS3EnvironmentProperties properties,
			ConfigServerProperties server) {
		this(s3Client, properties.getBucket(), properties.isUseDirectoryLayout(), server, properties.getSearchPaths());
		this.order = properties.getOrder();
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
		Collections.reverse(apps);
		if (!apps.contains(serverProperties.getDefaultApplicationName())) {
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
		for (String label : labels) {
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
				// Even with no profiles, negated profile documents (e.g. on-profile:
				// "!my-profile") should be included because no profile is active,
				// so all negations are satisfied
				addPropertySourcesForApps(apps,
						app -> addNegatedProfilePropertySource(environment, app, profiles, label));
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
				// Handle documents with negated profile expressions (e.g. on-profile:
				// "!my-profile")
				// once per label rather than once per profile to avoid duplicates
				addPropertySourcesForApps(apps,
						app -> addNegatedProfilePropertySource(environment, app, profiles, label));
			}
		}
	}

	private void addPropertySourcesForApps(List<String> apps, Consumer<String> addPropertySource) {
		apps.forEach(addPropertySource);
	}

	private void addNegatedProfilePropertySource(Environment environment, String app, String[] allProfiles,
			String label) {
		List<S3ConfigFile> s3ConfigFiles = this.searchPaths.isEmpty()
				? getNegatedProfileS3ConfigFileYaml(app, allProfiles, label) : getS3ConfigFileWithSearchPaths(app, null,
						label, key -> wrapKeyWithNegatedConfigFiles(key, app, allProfiles, label));
		addPropertySource(environment, s3ConfigFiles);
	}

	private List<S3ConfigFile> getNegatedProfileS3ConfigFileYaml(String application, String[] allProfiles,
			String label) {
		List<S3ConfigFile> configFiles = new ArrayList<>();
		try {
			S3ConfigFile configFile = new NegatedProfileYamlDocumentS3ConfigFile(application, label, bucketName,
					useApplicationAsDirectory, s3Client, allProfiles);
			configFiles.add(configFile);
		}
		catch (IllegalStateException e) {
			LOG.warn("Could not read YAML file using application <" + application + "> label <" + label + ">.", e);
		}
		catch (Exception e) {
			if (LOG.isDebugEnabled()) {
				LOG.debug("Did not find negated profile yaml document in non-profile specific file using application <"
						+ application + "> label <" + label + ">.");
			}
		}
		return configFiles;
	}

	private void addProfileSpecificPropertySource(Environment environment, String app, String profile, String label) {
		List<S3ConfigFile> s3ConfigFiles = searchPaths.isEmpty()
				? getS3ConfigFile(app, profile, label, this::getS3PropertiesOrJsonConfigFile,
						this::getProfileSpecificS3ConfigFileYaml)
				: getS3ConfigFileWithSearchPaths(app, profile, label,
						key -> wrapKeyWithConfigFiles(key, app, profile, label));
		addPropertySource(environment, s3ConfigFiles);
	}

	private void addNonProfileSpecificPropertySource(Environment environment, String app, String profile,
			String label) {
		List<S3ConfigFile> s3ConfigFiles = searchPaths.isEmpty() ? getS3ConfigFile(app, profile, label,
				this::getNonProfileSpecificPropertiesOrJsonConfigFile, this::getNonProfileSpecificS3ConfigFileYaml)
				: Collections.emptyList();
		if (s3ConfigFiles != null) {
			addPropertySource(environment, s3ConfigFiles);
		}
	}

	private void addPropertySource(Environment environment, List<S3ConfigFile> s3ConfigFiles) {
		for (S3ConfigFile s3ConfigFile : s3ConfigFiles) {
			if (s3ConfigFile == null) {
				continue;
			}
			try {
				final Properties config = s3ConfigFile.read();
				// This logic handles the case where the s3 file is a YAML file that is
				// not profile specific (ie it does not have -<profile> in the name)
				// and does not have any profile specific documents in it. In this case we
				// do
				// not want to include this
				// property source we only want to include the document for the default
				// profile. When we create
				// the S3ConfigFile for this file we set the
				// shouldIncludeWithEmptyProperties to false
				// in ProfileSpecificYamlDocumentS3ConfigFile for this specific case.
				if (config == null || (config.isEmpty() && !s3ConfigFile.isShouldIncludeWithEmptyProperties())) {
					continue;
				}
				String name = s3ConfigFile.getName();
				boolean exists = environment.getPropertySources()
					.stream()
					.anyMatch(p -> p.getName().equals(name) && p.getSource().equals(config));
				if (exists) {
					continue;
				}
				environment.setVersion(s3ConfigFile.getVersion());
				config.putAll(serverProperties.getOverrides());
				PropertySource propertySource = new PropertySource(name, config);
				if (LOG.isDebugEnabled()) {
					LOG.debug("Adding property source to environment " + propertySource);
				}
				environment.add(propertySource);
			}
			catch (Exception e) {
				LOG.warn("Could not read properties from " + s3ConfigFile.getName(), e);
			}
		}
	}

	private String[] parseProfiles(String profiles) {
		return StringUtils.commaDelimitedListToStringArray(profiles);
	}

	private List<S3ConfigFile> getS3ConfigFile(String application, String profile, String label,
			JsonOrPropertiesS3ConfigFileCreator creator, YamlS3ConfigFileCreator yamlCreator) {
		List<S3ConfigFile> configFiles = new ArrayList<>();
		S3ConfigFile configFile = creator.create(application, profile, label);
		if (configFile != null) {
			configFiles.add(configFile);
		}
		configFiles.addAll(yamlCreator.create(application, profile, label));
		return configFiles;
	}

	private List<S3ConfigFile> getS3ConfigFileWithSearchPaths(String application, String profile, String label,
			Function<String, List<S3ConfigFile>> keyWrapper) {
		List<S3ConfigFile> result = new ArrayList<>();
		Set<String> seenKeys = new LinkedHashSet<>();
		for (String template : this.searchPaths) {
			String pattern = resolvePattern(template, application, profile, label);
			if (!pathMatcher.isPattern(pattern)) {
				boolean fileFound = probeLiteralPattern(pattern, seenKeys, result, keyWrapper);
				if (!fileFound) {
					scanDirectoryPattern(pattern, seenKeys, result, keyWrapper);
				}
				continue;
			}
			else if (pattern.endsWith(".*")) {
				probeDotWildcardPattern(pattern, seenKeys, result, keyWrapper);
				continue;
			}
			scanWildcardPattern(pattern, seenKeys, result, keyWrapper);
		}
		return result;
	}

	private String resolvePattern(String template, String application, String profile, String label) {
		String resolvedLabel = (label == null ? "" : label);
		String resolvedProfile = (profile == null ? "" : profile);
		String pattern = template.replace("{application}", application)
			.replace("{profile}", resolvedProfile)
			.replace("{label}", resolvedLabel);
		return StringUtils.trimLeadingCharacter(pattern.replaceAll("/{2,}", "/"), '/');
	}

	private boolean probeLiteralPattern(String pattern, Set<String> seenKeys, List<S3ConfigFile> result,
			Function<String, List<S3ConfigFile>> keyWrapper) {
		boolean fileFound = false;
		List<String> extensionsToProbe = hasSupportedExtension(pattern) ? EMPTY_EXTENSION : SUPPORTED_EXTENSIONS;
		for (String ext : extensionsToProbe) {
			String key = pattern + ext;
			if (!seenKeys.add(key)) {
				continue;
			}
			else if (probeKeyAndAddResult(key, result, keyWrapper)) {
				fileFound = true;
			}
		}
		return fileFound;
	}

	private void probeDotWildcardPattern(String pattern, Set<String> seenKeys, List<S3ConfigFile> result,
			Function<String, List<S3ConfigFile>> keyWrapper) {
		String base = pattern.substring(0, pattern.length() - 2);
		for (String ext : SUPPORTED_EXTENSIONS) {
			String key = base + ext;
			if (seenKeys.add(key)) {
				probeKeyAndAddResult(key, result, keyWrapper);
			}
		}
	}

	private boolean probeKeyAndAddResult(String key, List<S3ConfigFile> result,
			Function<String, List<S3ConfigFile>> keyWrapper) {
		try {
			s3Client.headObject(HeadObjectRequest.builder().bucket(bucketName).key(key).build());
			result.addAll(keyWrapper.apply(key));
			return true;
		}
		catch (S3Exception e) {
			int status = e.statusCode();
			if (status != 404 && status != 403) {
				if (LOG.isInfoEnabled()) {
					LOG.info("Error checking S3 object key: " + key, e);
				}
				throw e;
			}
			return false;
		}
	}

	private void scanDirectoryPattern(String pattern, Set<String> seenKeys, List<S3ConfigFile> result,
			Function<String, List<S3ConfigFile>> keyWrapper) {
		String dirPrefix = pattern.endsWith("/") ? pattern : pattern + "/";
		String token = null;
		do {
			ListObjectsV2Response resp = s3Client.listObjectsV2(ListObjectsV2Request.builder()
				.bucket(bucketName)
				.prefix(dirPrefix)
				.continuationToken(token)
				.build());
			for (S3Object obj : resp.contents()) {
				String key = obj.key();
				if (!hasSupportedExtension(key)) {
					continue;
				}
				else if (seenKeys.add(key)) {
					result.addAll(keyWrapper.apply(key));
				}
			}
			token = resp.nextContinuationToken();
		}
		while (token != null);
	}

	private void scanWildcardPattern(String pattern, Set<String> seenKeys, List<S3ConfigFile> result,
			Function<String, List<S3ConfigFile>> keyWrapper) {
		String prefix = extractPrefix(pattern);
		String token = null;
		do {
			ListObjectsV2Response resp = s3Client.listObjectsV2(
					ListObjectsV2Request.builder().bucket(bucketName).prefix(prefix).continuationToken(token).build());
			for (S3Object obj : resp.contents()) {
				String key = obj.key();
				if (!pathMatcher.match(pattern, key) || !hasSupportedExtension(key)) {
					continue;
				}
				else if (seenKeys.add(key)) {
					result.addAll(keyWrapper.apply(key));
				}
			}
			token = resp.nextContinuationToken();
		}
		while (token != null);
	}

	private boolean hasSupportedExtension(String key) {
		return key.endsWith(".properties") || key.endsWith(".json") || key.endsWith(".yml") || key.endsWith(".yaml");
	}

	private List<S3ConfigFile> wrapKeyWithConfigFiles(String key, String application, String profile, String label) {

		if (key.endsWith(".yml") || key.endsWith(".yaml")) {
			List<S3ConfigFile> files = new ArrayList<>();
			files.addAll(getProfileSpecificYamlFromKey(key, application, profile, label));
			files.addAll(getNonProfileSpecificYamlFromKey(key, application, profile, label));
			return files;
		}
		return createConfigFileFromKey(key, application, profile, label).map(Collections::singletonList)
			.orElseGet(Collections::emptyList);
	}

	private List<S3ConfigFile> getProfileSpecificYamlFromKey(String key, String application, String profile,
			String label) {

		S3ConfigFileFromKey config = new S3ConfigFileFromKey(key, application, profile, label, bucketName,
				useApplicationAsDirectory, s3Client,
				properties -> YamlS3ConfigFile.profileMatchesActivateProperty(profile, properties)
						? YamlProcessor.MatchStatus.FOUND : YamlProcessor.MatchStatus.NOT_FOUND);
		config.setShouldIncludeWithEmptyProperties(false);
		return List.of(config);
	}

	private List<S3ConfigFile> getNonProfileSpecificYamlFromKey(String key, String application, String profile,
			String label) {

		S3ConfigFileFromKey config = new S3ConfigFileFromKey(key, application, profile, label, bucketName,
				useApplicationAsDirectory, s3Client, properties -> !YamlS3ConfigFile.onProfilePropertyExists(properties)
						? YamlProcessor.MatchStatus.FOUND : YamlProcessor.MatchStatus.NOT_FOUND);
		return List.of(config);
	}

	private String extractPrefix(String pattern) {
		int firstWildcardIdx = -1;
		int starIdx = pattern.indexOf('*');
		int questionIdx = pattern.indexOf('?');
		if (starIdx != -1 && questionIdx != -1) {
			firstWildcardIdx = Math.min(starIdx, questionIdx);
		}
		else if (starIdx != -1) {
			firstWildcardIdx = starIdx;
		}
		else {
			firstWildcardIdx = questionIdx;
		}

		if (firstWildcardIdx <= 0) {
			return "";
		}
		int slash = pattern.lastIndexOf('/', firstWildcardIdx);
		return (slash == -1 ? "" : pattern.substring(0, slash + 1));
	}

	private Optional<S3ConfigFile> createConfigFileFromKey(String key, String application, String profile,
			String label) {
		String ext = key.substring(key.lastIndexOf('.') + 1);
		if (SUPPORTED_EXTENSIONS.contains("." + ext.toLowerCase(Locale.ROOT))) {
			return Optional.of(new S3ConfigFileFromKey(key, application, profile, label, bucketName,
					useApplicationAsDirectory, s3Client));
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

	private List<S3ConfigFile> wrapKeyWithNegatedConfigFiles(String key, String application, String[] allProfiles,
			String label) {
		if (key.endsWith(".yml") || key.endsWith(".yaml")) {
			S3ConfigFileFromKey config = new S3ConfigFileFromKey(key, application, null, label, bucketName,
					this.useApplicationAsDirectory, s3Client, properties -> {
						Object onProfileValue = properties.get("spring.config.activate.on-profile");
						if (onProfileValue == null) {
							onProfileValue = properties.get("spring.config.activate.onProfile");
						}
						if (onProfileValue == null) {
							return YamlProcessor.MatchStatus.NOT_FOUND;
						}

						String expression = onProfileValue.toString().trim();
						if (AwsS3EnvironmentRepository.isSimpleProfileName(expression)) {
							return YamlProcessor.MatchStatus.NOT_FOUND;
						}
						List<String> allProfilesList = Arrays.asList(allProfiles);
						boolean matches = Profiles.of(expression).matches(allProfilesList::contains);
						return matches ? YamlProcessor.MatchStatus.FOUND : YamlProcessor.MatchStatus.NOT_FOUND;
					});
			config.setShouldIncludeWithEmptyProperties(false);
			return List.of(config);
		}
		return Collections.emptyList();
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

	static boolean isSimpleProfileName(String expression) {
		return !expression.contains("!") && !expression.contains("&") && !expression.contains("|")
				&& !expression.contains("(") && !expression.contains(",");
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

	PropertyS3ConfigFile(String application, String profile, String label, String bucketName,
			boolean useApplicationAsDirectory, S3Client s3Client) {
		super(application, profile, label, bucketName, useApplicationAsDirectory, s3Client);
		this.properties = read();
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
		this(application, profile, label, bucketName, useApplicationAsDirectory, s3Client,
				new YamlProcessor.DocumentMatcher[] {});
	}

	YamlS3ConfigFile(String application, String profile, String label, String bucketName,
			boolean useApplicationAsDirectory, S3Client s3Client,
			final YamlProcessor.DocumentMatcher... documentMatchers) {
		super(application, profile, label, bucketName, useApplicationAsDirectory, s3Client);
		this.documentMatchers = documentMatchers;
		this.properties = read();
	}

	protected static boolean profileMatchesActivateProperty(String profile, Properties properties) {
		if (profile == null) {
			return false;
		}
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
		super(application, profile, label, bucketName, useApplicationAsDirectory, s3Client,
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
		super(application, profile, label, bucketName, useApplicationAsDirectory, s3Client,
				properties -> !onProfilePropertyExists(properties) ? YamlProcessor.MatchStatus.FOUND
						: YamlProcessor.MatchStatus.NOT_FOUND);
	}

}

class ProfileSpecificYamlS3ConfigFile extends YamlS3ConfigFile {

	ProfileSpecificYamlS3ConfigFile(String application, String profile, String label, String bucketName,
			boolean useApplicationAsDirectory, S3Client s3Client) {
		super(application, profile, label, bucketName, useApplicationAsDirectory, s3Client,
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

	@Override
	protected List<String> getExtensions() {
		return List.of("json");
	}

}

class S3ConfigFileFromKey extends S3ConfigFile {

	private final String key;

	private final YamlProcessor.DocumentMatcher[] documentMatchers;

	S3ConfigFileFromKey(String key, String application, String profile, String label, String bucketName,
			S3Client s3Client) {
		this(key, application, profile, label, bucketName, false, s3Client, new YamlProcessor.DocumentMatcher[] {});
	}

	S3ConfigFileFromKey(String key, String application, String profile, String label, String bucketName,
			boolean useApplicationAsDirectory, S3Client s3Client, YamlProcessor.DocumentMatcher... documentMatchers) {
		super(application, profile, label, bucketName, useApplicationAsDirectory, s3Client);
		this.key = key;
		this.documentMatchers = documentMatchers;
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

	@Override
	protected List<String> getExtensions() {
		return List.of(key.substring(key.lastIndexOf('.') + 1));
	}

	@Override
	public Properties read() {
		if (this.properties != null) {
			return this.properties;
		}
		String ext = key.substring(key.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
		if ("properties".equals(ext)) {
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
		else if ("json".equals(ext) || "yml".equals(ext) || "yaml".equals(ext)) {
			final YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
			try (InputStream in = getObject()) {
				yaml.setResources(new InputStreamResource(in));
				yaml.setDocumentMatchers(documentMatchers);
				return yaml.getObject();
			}
			catch (Exception e) {
				LOG.warn("Could not read YAML/JSON file", e);
				throw new IllegalStateException("Cannot load environment", e);
			}
		}
		throw new IllegalStateException("Unsupported extension: " + ext);
	}

}

class NegatedProfileYamlDocumentS3ConfigFile extends YamlS3ConfigFile {

	NegatedProfileYamlDocumentS3ConfigFile(String application, String label, String bucketName,
			boolean useApplicationAsDirectory, S3Client s3Client, String[] allProfiles) {
		super(application, null, label, bucketName, useApplicationAsDirectory, s3Client, properties -> {
			Object onProfileValue = properties.get("spring.config.activate.on-profile");
			if (onProfileValue == null) {
				onProfileValue = properties.get("spring.config.activate.onProfile");
			}
			if (onProfileValue == null) {
				return YamlProcessor.MatchStatus.NOT_FOUND;
			}
			String expression = onProfileValue.toString().trim();
			// Simple positive profile names are already handled by
			// ProfileSpecificYamlDocumentS3ConfigFile. Only process complex or negated
			// expressions here to avoid adding duplicate property sources.
			if (AwsS3EnvironmentRepository.isSimpleProfileName(expression)) {
				return YamlProcessor.MatchStatus.NOT_FOUND;
			}
			List<String> allProfilesList = Arrays.asList(allProfiles);
			boolean matches = Profiles.of(expression).matches(allProfilesList::contains);
			return matches ? YamlProcessor.MatchStatus.FOUND : YamlProcessor.MatchStatus.NOT_FOUND;
		});
	}

	@Override
	protected String buildObjectKeyPrefix() {
		return super.buildObjectKeyPrefix(false);
	}

	@Override
	public boolean isShouldIncludeWithEmptyProperties() {
		return false;
	}

}
