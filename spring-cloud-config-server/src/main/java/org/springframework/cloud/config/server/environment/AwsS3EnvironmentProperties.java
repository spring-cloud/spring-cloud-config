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

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.config.server.support.EnvironmentRepositoryProperties;

/**
 * @author Clay McCoy
 * @author Geonwook Ham
 */
@ConfigurationProperties("spring.cloud.config.server.awss3")
public class AwsS3EnvironmentProperties implements EnvironmentRepositoryProperties {

	/**
	 * AWS region that contains config.
	 */
	private String region;

	/**
	 * Adds the ability to override the baseUrl of the s3 client.
	 */
	private String endpoint;

	/**
	 * Name of the S3 bucket that contains config.
	 */
	private String bucket;

	/**
	 * Use application name as intermediate directory. Analogous to `searchPaths:
	 * {application}` from Git backend.
	 */
	private boolean useDirectoryLayout;

	private int order = DEFAULT_ORDER;

	private List<String> searchPaths = new ArrayList<>();

	public List<String> getSearchPaths() {
		return searchPaths;
	}

	public void setSearchPaths(List<String> searchPaths) {
		this.searchPaths = searchPaths;
	}

	public String getRegion() {
		return region;
	}

	public void setRegion(String region) {
		this.region = region;
	}

	public String getEndpoint() {
		return endpoint;
	}

	public void setEndpoint(String endpoint) {
		this.endpoint = endpoint;
	}

	public String getBucket() {
		return bucket;
	}

	public void setBucket(String bucket) {
		this.bucket = bucket;
	}

	public boolean isUseDirectoryLayout() {
		return useDirectoryLayout;
	}

	public void setUseDirectoryLayout(boolean useDirectoryLayout) {
		this.useDirectoryLayout = useDirectoryLayout;
	}

	public int getOrder() {
		return order;
	}

	@Override
	public void setOrder(int order) {
		this.order = order;
	}

}
