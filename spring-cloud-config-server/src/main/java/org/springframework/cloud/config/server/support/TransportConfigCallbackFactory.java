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

package org.springframework.cloud.config.server.support;

import java.util.List;

import javax.annotation.Nullable;

import org.eclipse.jgit.api.TransportConfigCallback;

import org.springframework.cloud.config.server.environment.JGitEnvironmentProperties;
import org.springframework.cloud.config.server.ssh.FileBasedSshTransportConfigCallback;
import org.springframework.cloud.config.server.ssh.PropertiesBasedSshTransportConfigCallback;

/** Factory for creating a JGit {@link TransportConfigCallback}. */
public class TransportConfigCallbackFactory {

	@Nullable
	private final TransportConfigCallback customTransportConfigCallback;

	/** Ordered list of cloud-provider transport callback providers. */
	private final List<GitTransportConfigCallbackProvider> providers;

	/**
	 * Creates a new factory.
	 * @param customTransportConfigCallback optional custom callback (highest priority)
	 * @param callbackProviders ordered list of callback providers
	 */
	public TransportConfigCallbackFactory(@Nullable final TransportConfigCallback customTransportConfigCallback,
			final List<GitTransportConfigCallbackProvider> callbackProviders) {
		this.customTransportConfigCallback = customTransportConfigCallback;
		this.providers = callbackProviders != null ? callbackProviders : List.of();
	}

	/**
	 * Builds a {@link TransportConfigCallback} for the given repository properties.
	 * @param environmentProperties the JGit environment properties
	 * @return the appropriate {@link TransportConfigCallback}
	 */
	public final TransportConfigCallback build(final JGitEnvironmentProperties environmentProperties) {

		// customTransportConfigCallback has the highest priority. If someone put
		// a TransportConfigCallback bean in to the Spring context, we use it for
		// all repositories.
		if (this.customTransportConfigCallback != null) {
			return this.customTransportConfigCallback;
		}

		// Delegate to the first provider that can handle this repository.
		for (final GitTransportConfigCallbackProvider provider : this.providers) {
			if (provider.canHandle(environmentProperties)) {
				return provider.createTransportConfigCallback(environmentProperties);
			}
		}

		// Otherwise - legacy behaviour - use SshTransportConfigCallback for all
		// repositories.
		return buildSshTransportConfigCallback(environmentProperties);
	}

	private TransportConfigCallback buildSshTransportConfigCallback(
			final JGitEnvironmentProperties gitEnvironmentProperties) {

		if (gitEnvironmentProperties.isIgnoreLocalSshSettings()) {
			return new PropertiesBasedSshTransportConfigCallback(gitEnvironmentProperties);
		}
		return new FileBasedSshTransportConfigCallback(gitEnvironmentProperties);
	}

}
