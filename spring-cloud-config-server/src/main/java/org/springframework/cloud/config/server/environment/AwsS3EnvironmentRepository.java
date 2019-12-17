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
import java.util.Properties;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectIdBuilder;

import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.config.server.config.ConfigServerProperties;
import org.springframework.core.Ordered;
import org.springframework.core.io.InputStreamResource;
import org.springframework.util.StringUtils;

/**
 * @author Clay McCoy
 * @author Scott Frederick
 */
public class AwsS3EnvironmentRepository
		implements EnvironmentRepository, Ordered, SearchPathLocator {

	private static final String AWS_S3_RESOURCE_SCHEME = "s3://";

	private static final String PATH_SEPARATOR = "/";

	private final AmazonS3 s3Client;

	private final String bucketName;

	private final ConfigServerProperties serverProperties;

	protected int order = Ordered.LOWEST_PRECEDENCE;

	public AwsS3EnvironmentRepository(AmazonS3 s3Client, String bucketName,
			ConfigServerProperties server) {
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
	public Environment findOne(String specifiedApplication, String specifiedProfiles,
			String specifiedLabel) {
		final String application = StringUtils.isEmpty(specifiedApplication)
				? serverProperties.getDefaultApplicationName() : specifiedApplication;
		final String profiles = StringUtils.isEmpty(specifiedProfiles)
				? serverProperties.getDefaultProfile() : specifiedProfiles;
		final String label = StringUtils.isEmpty(specifiedLabel)
				? serverProperties.getDefaultLabel() : specifiedLabel;

		String[] profileArray = parseProfiles(profiles);

		final Environment environment = new Environment(application, profileArray);
		environment.setLabel(label);

		for (String profile : profileArray) {
			S3ConfigFile s3ConfigFile = getS3ConfigFile(application, profile, label);
			if (s3ConfigFile != null) {
				environment.setVersion(s3ConfigFile.getVersion());

				final Properties config = s3ConfigFile.read();
				config.putAll(serverProperties.getOverrides());
				StringBuilder propertySourceName = new StringBuilder().append("s3:")
						.append(application);
				if (profile != null) {
					propertySourceName.append("-").append(profile);
				}
				environment
						.add(new PropertySource(propertySourceName.toString(), config));
			}
		}

		return environment;
	}

	private String[] parseProfiles(String profiles) {
		if (profiles.equals(serverProperties.getDefaultProfile())) {
			return new String[] { profiles, null };
		}
		return StringUtils.commaDelimitedListToStringArray(profiles);
	}

	private S3ConfigFile getS3ConfigFile(String application, String profile,
			String label) {
		String objectKeyPrefix = buildObjectKeyPrefix(application, profile, label);

		final S3ObjectIdBuilder s3ObjectIdBuilder = new S3ObjectIdBuilder()
				.withBucket(bucketName);

		return getS3ConfigFile(s3ObjectIdBuilder, objectKeyPrefix);
	}

	private String buildObjectKeyPrefix(String application, String profile,
			String label) {
		StringBuilder objectKeyPrefix = new StringBuilder();
		if (!StringUtils.isEmpty(label)) {
			objectKeyPrefix.append(label).append(PATH_SEPARATOR);
		}
		objectKeyPrefix.append(application);
		if (!StringUtils.isEmpty(profile)) {
			objectKeyPrefix.append("-").append(profile);
		}
		return objectKeyPrefix.toString();
	}

	private S3ConfigFile getS3ConfigFile(S3ObjectIdBuilder s3ObjectIdBuilder,
			String keyPrefix) {
		try {
			final S3Object properties = s3Client.getObject(new GetObjectRequest(
					s3ObjectIdBuilder.withKey(keyPrefix + ".properties").build()));
			return new PropertyS3ConfigFile(properties.getObjectMetadata().getVersionId(),
					properties.getObjectContent());
		}
		catch (Exception eProperties) {
			try {
				final S3Object yaml = s3Client.getObject(new GetObjectRequest(
						s3ObjectIdBuilder.withKey(keyPrefix + ".yml").build()));
				return new YamlS3ConfigFile(yaml.getObjectMetadata().getVersionId(),
						yaml.getObjectContent());
			}
			catch (Exception eYaml) {
				try {
					final S3Object json = s3Client.getObject(new GetObjectRequest(
							s3ObjectIdBuilder.withKey(keyPrefix + ".json").build()));
					return new JsonS3ConfigFile(json.getObjectMetadata().getVersionId(),
							json.getObjectContent());
				}
				catch (Exception eJson) {
					return null;
				}
			}
		}
	}

	@Override
	public Locations getLocations(String application, String profiles, String label) {
		String baseLocation = AWS_S3_RESOURCE_SCHEME + bucketName + PATH_SEPARATOR
				+ application;

		return new Locations(application, profiles, label, null,
				new String[] { baseLocation });
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
