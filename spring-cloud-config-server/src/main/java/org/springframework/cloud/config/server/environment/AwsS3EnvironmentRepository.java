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
import org.springframework.core.io.Resource;
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
		String[] profilesByPrecedence = profilesByPrecedence(profiles);
		for (String label : labels) {
			for (String profile : profilesByPrecedence) {
				addPropertySourcesForApps(apps,
						app -> addProfileSpecificPropertySource(environment, app, profile, profiles, label));
			}
			if (profiles.length == 0 && !this.searchPaths.isEmpty()) {
				addPropertySourcesForApps(apps,
						app -> addProfileSpecificPropertySource(environment, app, null, profiles, label));
			}
		}
		if (this.searchPaths.isEmpty()) {
			for (String label : labels) {
				addPropertySourcesForApps(apps,
						app -> addNonProfileSpecificPropertySource(environment, app, profiles, label));
			}
		}
	}

	/**
	 * Orders the profiles from the highest precedence to the lowest. As in the other
	 * environment repositories, the last profile wins.
	 * @param profiles the profiles in the order they were requested
	 * @return the profiles in reverse order
	 */
	private static String[] profilesByPrecedence(String[] profiles) {
		List<String> reversed = new ArrayList<>(Arrays.asList(profiles));
		Collections.reverse(reversed);
		return reversed.toArray(new String[0]);
	}

	private void addPropertySourcesForApps(List<String> apps, Consumer<String> addPropertySource) {
		apps.forEach(addPropertySource);
	}

	private void addProfileSpecificPropertySource(Environment environment, String app, String profile,
			String[] profiles, String label) {
		List<S3ConfigFile> s3ConfigFiles = searchPaths.isEmpty()
				? getS3ConfigFile(app, profile, label, this::getS3PropertiesOrJsonConfigFile,
						(application, fileProfile, fileLabel) -> getYamlDocuments(null, application, fileProfile,
								profiles, fileLabel))
				: getS3ConfigFileWithSearchPaths(app, profile, label,
						key -> wrapKeyWithConfigFiles(key, app, profiles, label));
		addPropertySource(environment, s3ConfigFiles);
	}

	private void addNonProfileSpecificPropertySource(Environment environment, String app, String[] profiles,
			String label) {
		List<S3ConfigFile> s3ConfigFiles = new ArrayList<>();
		S3ConfigFile configFile = getS3PropertiesOrJsonConfigFile(app, null, label);
		if (configFile != null) {
			s3ConfigFiles.add(configFile);
		}
		s3ConfigFiles.addAll(getYamlDocuments(null, app, null, profiles, label));
		addPropertySource(environment, s3ConfigFiles);
	}

	/**
	 * Returns the documents of a YAML file that apply to the profiles, from the highest
	 * precedence to the lowest. As in Spring Boot, a later document of a multi-document
	 * file takes precedence over an earlier one.
	 * @param key the key of the file, or {@code null} for the file of the application
	 * @param application the application
	 * @param profile the profile of a profile-specific file, or {@code null}
	 * @param profiles the requested profiles
	 * @param label the label
	 * @return the documents that apply to the profiles
	 */
	private List<S3ConfigFile> getYamlDocuments(String key, String application, String profile, String[] profiles,
			String label) {
		try {
			return new MultiDocumentYamlS3ConfigFile(key, application, profile, label, bucketName,
					useApplicationAsDirectory, s3Client)
				.documentsFor(profiles);
		}
		catch (Exception e) {
			if (LOG.isDebugEnabled()) {
				LOG.debug("Did not find yaml documents using application <" + application + "> profile <" + profile
						+ "> key <" + key + "> label <" + label + ">.");
			}
			return Collections.emptyList();
		}
	}

	private void addPropertySource(Environment environment, List<S3ConfigFile> s3ConfigFiles) {
		for (S3ConfigFile s3ConfigFile : s3ConfigFiles) {
			if (s3ConfigFile == null) {
				continue;
			}
			try {
				final Properties config = s3ConfigFile.read();
				if (config == null) {
					continue;
				}
				// Apply the overrides before looking for the same source, which already
				// has them
				config.putAll(serverProperties.getOverrides());
				String name = s3ConfigFile.getName();
				boolean exists = environment.getPropertySources()
					.stream()
					.anyMatch(p -> p.getName().equals(name) && p.getSource().equals(config));
				if (exists) {
					continue;
				}
				environment.setVersion(s3ConfigFile.getVersion());
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

	private List<S3ConfigFile> wrapKeyWithConfigFiles(String key, String application, String[] profiles, String label) {
		if (key.endsWith(".yml") || key.endsWith(".yaml")) {
			return getYamlDocuments(key, application, null, profiles, label);
		}
		return createConfigFileFromKey(key, application, null, label).map(Collections::singletonList)
			.orElseGet(Collections::emptyList);
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
						+ "> label <" + label + ">. Trying json extension", propertyException);
			}
			try {
				return new JsonS3ConfigFile(application, profile, label, bucketName, useApplicationAsDirectory,
						s3Client);
			}
			catch (Exception jsonException) {
				if (LOG.isDebugEnabled()) {
					LOG.debug("Did not find json file using application <" + application + "> profile <" + profile
							+ "> label <" + label + ">. Trying yaml extension", jsonException);
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

	static boolean isSimpleProfileName(String expression) {
		return !expression.contains("!") && !expression.contains("&") && !expression.contains("|")
				&& !expression.contains("(") && !expression.contains(",");
	}

	interface YamlS3ConfigFileCreator {

		List<S3ConfigFile> create(String application, String profile, String label);

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

	void setVersion(String version) {
		this.version = version;
	}

	abstract Properties read();

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
						LOG.debug("Did not find " + key + ". Trying next extension", e);
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

	String createPropertySourceName(String app, String profile) {
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

	YamlS3ConfigFile(String application, String profile, String label, String bucketName,
			boolean useApplicationAsDirectory, S3Client s3Client) {
		super(application, profile, label, bucketName, useApplicationAsDirectory, s3Client);
		this.properties = read();
	}

	@Override
	public Properties read() {
		if (properties != null) {
			return properties;
		}
		final YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
		try (InputStream in = getObject()) {
			yaml.setResources(new InputStreamResource(in));
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

	S3ConfigFileFromKey(String key, String application, String profile, String label, String bucketName,
			boolean useApplicationAsDirectory, S3Client s3Client) {
		super(application, profile, label, bucketName, useApplicationAsDirectory, s3Client);
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
		else if ("json".equals(ext)) {
			// YAML is a superset of JSON, which means you can parse JSON with a YAML
			// parser
			final YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
			try (InputStream in = getObject()) {
				yaml.setResources(new InputStreamResource(in));
				return yaml.getObject();
			}
			catch (Exception e) {
				LOG.warn("Could not read JSON file", e);
				throw new IllegalStateException("Cannot load environment", e);
			}
		}
		throw new IllegalStateException("Unsupported extension: " + ext);
	}

}

/**
 * A YAML file of possibly several documents. As in Spring Boot, a later document takes
 * precedence over an earlier one, and a document applies to the requested profiles when
 * it has no {@code spring.config.activate.on-profile} property or when one of its profile
 * expressions matches them.
 */
class MultiDocumentYamlS3ConfigFile extends S3ConfigFile {

	private static final List<String> ACTIVATION_PROPERTIES = List.of("spring.config.activate.on-profile",
			"spring.config.activate.onProfile");

	private final String key;

	private final List<Properties> documents;

	MultiDocumentYamlS3ConfigFile(String key, String application, String profile, String label, String bucketName,
			boolean useApplicationAsDirectory, S3Client s3Client) {
		super(application, profile, label, bucketName, useApplicationAsDirectory, s3Client);
		this.key = key;
		this.documents = readDocuments();
	}

	/**
	 * Returns a config file for each non-empty document that applies to the profiles,
	 * from the highest precedence to the lowest. Documents that would get the same name
	 * are told apart by their position in the file.
	 * @param profiles the requested profiles
	 * @return the config files of the documents
	 */
	List<S3ConfigFile> documentsFor(String[] profiles) {
		List<Document> applying = new ArrayList<>();
		for (int index = this.documents.size() - 1; index >= 0; index--) {
			Properties document = this.documents.get(index);
			if (!document.isEmpty() && appliesTo(document, index, profiles)) {
				applying.add(new Document(index, document, nameOf(document, profiles)));
			}
		}
		List<String> names = applying.stream().map(Document::name).toList();
		return applying.stream().map(document -> {
			String name = (Collections.frequency(names, document.name()) > 1)
					? document.name() + " (document #" + (document.index() + 1) + ")" : document.name();
			return (S3ConfigFile) new YamlDocumentS3ConfigFile(name, document.properties(), getVersion());
		}).toList();
	}

	private boolean appliesTo(Properties document, int index, String[] profiles) {
		List<String> expressions = activationExpressions(document);
		if (expressions.isEmpty()) {
			return true;
		}
		try {
			List<String> activeProfiles = Arrays.asList(profiles);
			return Profiles.of(expressions.toArray(new String[0])).matches(activeProfiles::contains);
		}
		catch (IllegalArgumentException e) {
			LOG.warn("Skipping document #" + (index + 1) + " of " + getName() + " with the invalid profile expression "
					+ expressions + ": " + e.getMessage());
			return false;
		}
	}

	private String nameOf(Properties document, String[] profiles) {
		if (this.key != null) {
			return "s3:" + this.bucketName + "/" + this.key;
		}
		if (this.profile != null) {
			return createPropertySourceName(this.application, this.profile);
		}
		List<String> expressions = activationExpressions(document);
		if (expressions.isEmpty()) {
			return createPropertySourceName(this.application, null);
		}
		if (expressions.size() == 1 && AwsS3EnvironmentRepository.isSimpleProfileName(expressions.get(0))) {
			return createPropertySourceName(this.application, expressions.get(0));
		}
		// A document matched by a complex or negated expression is named after the
		// requested profiles, so that its name never collides with the one of the
		// document that applies to every profile
		String suffix = String.join(",", profiles);
		return suffix.isEmpty() ? createPropertySourceName(this.application, null)
				: createPropertySourceName(this.application, suffix);
	}

	/**
	 * Returns the profile expressions of a document: a comma-separated list, or the
	 * elements of a YAML list.
	 */
	private static List<String> activationExpressions(Properties document) {
		for (String activationProperty : ACTIVATION_PROPERTIES) {
			Object value = document.get(activationProperty);
			if (value != null) {
				return Arrays.stream(StringUtils.commaDelimitedListToStringArray(value.toString()))
					.map(String::trim)
					.toList();
			}
			List<String> elements = new ArrayList<>();
			for (int i = 0; document.get(activationProperty + "[" + i + "]") != null; i++) {
				elements.add(document.get(activationProperty + "[" + i + "]").toString().trim());
			}
			if (!elements.isEmpty()) {
				return elements;
			}
		}
		return Collections.emptyList();
	}

	private List<Properties> readDocuments() {
		try (InputStream in = getObject()) {
			return new DocumentsLoader(new InputStreamResource(in)).load();
		}
		catch (Exception e) {
			LOG.warn("Could not read YAML file", e);
			throw new IllegalStateException("Cannot load environment", e);
		}
	}

	/**
	 * Returns the properties of all the documents, a later document overriding an earlier
	 * one. The documents are added with {@link #documentsFor(String[])}.
	 * @return the properties of the file
	 */
	@Override
	Properties read() {
		Properties properties = new Properties();
		this.documents.forEach(properties::putAll);
		return properties;
	}

	@Override
	protected String buildObjectKeyPrefix() {
		return (this.key != null) ? this.key.substring(0, this.key.lastIndexOf('.')) : super.buildObjectKeyPrefix();
	}

	@Override
	protected List<String> getExtensions() {
		return (this.key != null) ? List.of(this.key.substring(this.key.lastIndexOf('.') + 1)) : List.of("yml", "yaml");
	}

	private record Document(int index, Properties properties, String name) {

	}

	private static final class DocumentsLoader extends YamlProcessor {

		DocumentsLoader(Resource resource) {
			setResources(resource);
		}

		List<Properties> load() {
			List<Properties> documents = new ArrayList<>();
			process((properties, map) -> documents.add(properties));
			return documents;
		}

	}

}

/**
 * A document of a YAML file.
 */
class YamlDocumentS3ConfigFile extends S3ConfigFile {

	private final String name;

	YamlDocumentS3ConfigFile(String name, Properties properties, String version) {
		super(null, null, null, null, false, null);
		this.name = name;
		this.properties = properties;
		setVersion(version);
	}

	@Override
	public String getName() {
		return this.name;
	}

	@Override
	Properties read() {
		return this.properties;
	}

	@Override
	protected List<String> getExtensions() {
		return Collections.emptyList();
	}

}
