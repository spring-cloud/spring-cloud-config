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
 * Configuration properties for the Google Cloud Storage environment repository.
 */
@ConfigurationProperties("spring.cloud.config.server.gcs")
public class GcsEnvironmentProperties implements EnvironmentRepositoryProperties {

	private String bucket;

	private String projectId;

	private boolean useDirectoryLayout;

	private List<String> searchPaths = new ArrayList<>();

	private int order = DEFAULT_ORDER;

	public String getBucket() {
		return bucket;
	}

	public void setBucket(String bucket) {
		this.bucket = bucket;
	}

	public String getProjectId() {
		return projectId;
	}

	public void setProjectId(String projectId) {
		this.projectId = projectId;
	}

	public boolean isUseDirectoryLayout() {
		return useDirectoryLayout;
	}

	public void setUseDirectoryLayout(boolean useDirectoryLayout) {
		this.useDirectoryLayout = useDirectoryLayout;
	}

	public List<String> getSearchPaths() {
		return searchPaths;
	}

	public void setSearchPaths(List<String> searchPaths) {
		this.searchPaths = searchPaths != null ? searchPaths : new ArrayList<>();
	}

	public int getOrder() {
		return order;
	}

	@Override
	public void setOrder(int order) {
		this.order = order;
	}

}
