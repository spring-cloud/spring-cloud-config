/*
 * Copyright 2013-2019 the original author or authors.
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

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.config.server.config.ConfigServerProperties;
import org.springframework.core.Ordered;
import org.springframework.core.io.InputStreamResource;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * @author Clay McCoy
 * @author Scott Frederick
 * @author Daniel Aiken
 */
public class AwsS3EnvironmentRepository implements EnvironmentRepository, Ordered, SearchPathLocator {

	private static final String AWS_S3_RESOURCE_SCHEME = "s3://";

	private static final String PATH_SEPARATOR = "/";

	private final S3Client s3Client;

	private final String bucketName;

	private final ConfigServerProperties serverProperties;

	protected int order = Ordered.LOWEST_PRECEDENCE;

	public AwsS3EnvironmentRepository(S3Client s3Client, String bucketName, ConfigServerProperties server) {
		this.s3Client = s3Client;
		this.bucketName = bucketName;
		this.serverProperties = server;
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
		final String application = !StringUtils.hasText(specifiedApplication)
				? serverProperties.getDefaultApplicationName() : specifiedApplication;
		final String profiles = !StringUtils.hasText(specifiedProfiles) ? serverProperties.getDefaultProfile()
				: specifiedProfiles;
		final String label = !StringUtils.hasText(specifiedLabel) ? serverProperties.getDefaultLabel() : specifiedLabel;

		String[] profileArray = parseProfiles(profiles);
		String[] apps = new String[] { application };
		if (application != null) {
			apps = StringUtils.commaDelimitedListToStringArray(application.replace(" ", ""));
		}

		final Environment environment = new Environment(application, profileArray);
		environment.setLabel(label);

		for (String profile : profileArray) {
			for (String app : apps) {
				S3ConfigFile s3ConfigFile = getS3ConfigFile(app, profile, label);
				if (s3ConfigFile != null) {
					environment.setVersion(s3ConfigFile.getVersion());

					final Properties config = s3ConfigFile.read();
					config.putAll(serverProperties.getOverrides());
					StringBuilder propertySourceName = new StringBuilder().append("s3:").append(app);
					if (profile != null) {
						propertySourceName.append("-").append(profile);
					}
					environment.add(new PropertySource(propertySourceName.toString(), config));
				}
			}
		}

		return environment;
	}

	private String[] parseProfiles(String profiles) {
		if (ObjectUtils.isEmpty(profiles)) {
			return new String[] { "" };
		}
		List<String> parsedProfiles = Arrays.stream(profiles.split(","))
				.collect(Collectors.collectingAndThen(Collectors.toList(), p -> {
					p.add("");
					return p;
				}));
		return parsedProfiles.toArray(new String[0]);
	}

	private S3ConfigFile getS3ConfigFile(String application, String profile, String label) {
		String objectKeyPrefix = buildObjectKeyPrefix(application, profile, label);

		return getS3ConfigFile(objectKeyPrefix);
	}

	private String buildObjectKeyPrefix(String application, String profile, String label) {
		StringBuilder objectKeyPrefix = new StringBuilder();
		if (!ObjectUtils.isEmpty(label)) {
			objectKeyPrefix.append(label).append(PATH_SEPARATOR);
		}
		objectKeyPrefix.append(application);
		if (!ObjectUtils.isEmpty(profile)) {
			objectKeyPrefix.append("-").append(profile);
		}
		return objectKeyPrefix.toString();
	}

	private S3ConfigFile getS3ConfigFile(String keyPrefix) {
		try {
			final ResponseInputStream<GetObjectResponse> responseInputStream = getObject(keyPrefix + ".properties");
			return new PropertyS3ConfigFile(responseInputStream.response().versionId(), responseInputStream);
		}
		catch (Exception eProperties) {
			try {
				final ResponseInputStream<GetObjectResponse> responseInputStream = getObject(keyPrefix + ".yml");
				return new YamlS3ConfigFile(responseInputStream.response().versionId(), responseInputStream);
			}
			catch (Exception eYml) {
				try {
					final ResponseInputStream<GetObjectResponse> responseInputStream = getObject(keyPrefix + ".yaml");
					return new YamlS3ConfigFile(responseInputStream.response().versionId(), responseInputStream);
				}
				catch (Exception eYaml) {
					try {
						final ResponseInputStream<GetObjectResponse> responseInputStream = getObject(
								keyPrefix + ".json");
						return new JsonS3ConfigFile(responseInputStream.response().versionId(), responseInputStream);
					}
					catch (Exception eJson) {
						return null;
					}
				}
			}
		}
	}

	private ResponseInputStream<GetObjectResponse> getObject(String key) throws Exception {
		return s3Client.getObject(GetObjectRequest.builder().bucket(bucketName).key(key).build());
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

}

abstract class S3ConfigFile {

	private final String version;

	protected S3ConfigFile(String version) {
		this.version = version;
	}

	String getVersion() {
		return version;
	}

	abstract Properties read();

}

class PropertyS3ConfigFile extends S3ConfigFile {

	final InputStream inputStream;

	PropertyS3ConfigFile(String version, InputStream inputStream) {
		super(version);
		this.inputStream = inputStream;
	}

	@Override
	public Properties read() {
		Properties props = new Properties();
		try (InputStream in = inputStream) {
			props.load(in);
		}
		catch (IOException e) {
			throw new IllegalStateException("Cannot load environment", e);
		}
		return props;
	}

}

class YamlS3ConfigFile extends S3ConfigFile {

	final InputStream inputStream;

	YamlS3ConfigFile(String version, InputStream inputStream) {
		super(version);
		this.inputStream = inputStream;
	}

	@Override
	public Properties read() {
		final YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
		try (InputStream in = inputStream) {
			yaml.setResources(new InputStreamResource(in));
			return yaml.getObject();
		}
		catch (IOException e) {
			throw new IllegalStateException("Cannot load environment", e);
		}
	}

}

class JsonS3ConfigFile extends YamlS3ConfigFile {

	// YAML is a superset of JSON, which means you can parse JSON with a YAML parser

	JsonS3ConfigFile(String version, InputStream inputStream) {
		super(version, inputStream);
	}

}
