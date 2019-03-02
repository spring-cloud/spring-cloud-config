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

package org.springframework.cloud.config.server.support;

import javax.annotation.Nullable;

import org.eclipse.jgit.api.TransportConfigCallback;

import org.springframework.cloud.config.server.environment.MultipleJGitEnvironmentProperties;
import org.springframework.cloud.config.server.ssh.FileBasedSshTransportConfigCallback;
import org.springframework.cloud.config.server.ssh.PropertiesBasedSshTransportConfigCallback;

public class TransportConfigCallbackFactory {

	@Nullable
	private final TransportConfigCallback customTransportConfigCallback;

	@Nullable
	private final GoogleCloudSourceSupport googleCloudSourceSupport;

	public TransportConfigCallbackFactory(
			TransportConfigCallback customTransportConfigCallback,
			GoogleCloudSourceSupport googleCloudSourceSupport) {

		this.customTransportConfigCallback = customTransportConfigCallback;
		this.googleCloudSourceSupport = googleCloudSourceSupport;
	}

	public TransportConfigCallback build(
			MultipleJGitEnvironmentProperties environmentProperties) {

		// customTransportConfigCallback has the highest priority. If someone put
		// a TransportConfigCallback bean in to the Spring context, we use it for
		// all repositories.
		if (customTransportConfigCallback != null) {
			return customTransportConfigCallback;
		}

		// If the currently configured repository is a Google Cloud Source repository
		// we use GoogleCloudSourceSupport.
		if (googleCloudSourceSupport != null) {
			final String uri = environmentProperties.getUri();
			if (googleCloudSourceSupport.canHandle(uri)) {
				return googleCloudSourceSupport.createTransportConfigCallback();
			}
		}

		// Otherwise - legacy behaviour - use SshTransportConfigCallback for all
		// repositories.
		return buildSshTransportConfigCallback(environmentProperties);
	}

	private TransportConfigCallback buildSshTransportConfigCallback(
			MultipleJGitEnvironmentProperties gitEnvironmentProperties) {

		if (gitEnvironmentProperties.isIgnoreLocalSshSettings()) {
			return new PropertiesBasedSshTransportConfigCallback(
					gitEnvironmentProperties);
		}
		return new FileBasedSshTransportConfigCallback(gitEnvironmentProperties);
	}

}
