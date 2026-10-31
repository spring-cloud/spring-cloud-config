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

import org.eclipse.jgit.api.TransportConfigCallback;
import org.junit.jupiter.api.Test;

import org.springframework.cloud.config.server.environment.MultipleJGitEnvironmentProperties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TransportConfigCallbackFactoryTests {

	private static final String AZURE_DEVOPS_REPO = "https://dev.azure.com/tenant/repo";

	private static final String OTHER_REPO = "https://github.com/example/repo";

	@Test
	public void usesAzureDevOpsWorkloadIdentityForAzureDevOpsRepository() {
		AzureDevOpsWorkloadIdentitySupport azureSupport = mock(AzureDevOpsWorkloadIdentitySupport.class);
		TransportConfigCallback azureCallback = mock(TransportConfigCallback.class);
		MultipleJGitEnvironmentProperties properties = mock(MultipleJGitEnvironmentProperties.class);

		when(properties.getUri()).thenReturn(AZURE_DEVOPS_REPO);
		when(azureSupport.canHandle(properties)).thenReturn(true);
		when(azureSupport.createTransportConfigCallback(properties)).thenReturn(azureCallback);

		TransportConfigCallbackFactory factory = new TransportConfigCallbackFactory(null, List.of(azureSupport));

		assertThat(factory.build(properties)).isSameAs(azureCallback);
		verify(azureSupport).createTransportConfigCallback(properties);
	}

	@Test
	public void doesNotUseAzureDevOpsWorkloadIdentityWhenDisabled() {
		AzureDevOpsWorkloadIdentitySupport azureSupport = mock(AzureDevOpsWorkloadIdentitySupport.class);
		MultipleJGitEnvironmentProperties properties = mock(MultipleJGitEnvironmentProperties.class);

		when(properties.getUri()).thenReturn(AZURE_DEVOPS_REPO);
		when(azureSupport.canHandle(properties)).thenReturn(false);

		TransportConfigCallbackFactory factory = new TransportConfigCallbackFactory(null, List.of(azureSupport));

		TransportConfigCallback result = factory.build(properties);

		assertThat(result).isNotNull();
		verify(azureSupport, never()).createTransportConfigCallback(properties);
	}

	@Test
	public void doesNotUseAzureDevOpsWorkloadIdentityForOtherRepository() {
		AzureDevOpsWorkloadIdentitySupport azureSupport = mock(AzureDevOpsWorkloadIdentitySupport.class);
		MultipleJGitEnvironmentProperties properties = mock(MultipleJGitEnvironmentProperties.class);

		when(properties.getUri()).thenReturn(OTHER_REPO);
		when(azureSupport.canHandle(properties)).thenReturn(false);

		TransportConfigCallbackFactory factory = new TransportConfigCallbackFactory(null, List.of(azureSupport));

		TransportConfigCallback result = factory.build(properties);

		assertThat(result).isNotNull();
		verify(azureSupport, never()).createTransportConfigCallback(properties);
	}

	@Test
	public void customCallbackTakesPriorityOverProviders() {
		TransportConfigCallback customCallback = mock(TransportConfigCallback.class);
		GitTransportConfigCallbackProvider provider = mock(GitTransportConfigCallbackProvider.class);
		MultipleJGitEnvironmentProperties properties = mock(MultipleJGitEnvironmentProperties.class);

		TransportConfigCallbackFactory factory = new TransportConfigCallbackFactory(customCallback, List.of(provider));

		assertThat(factory.build(properties)).isSameAs(customCallback);
		verify(provider, never()).canHandle(properties);
	}

	@Test
	public void emptyProvidersListFallsBackToSsh() {
		MultipleJGitEnvironmentProperties properties = mock(MultipleJGitEnvironmentProperties.class);
		when(properties.isIgnoreLocalSshSettings()).thenReturn(false);
		when(properties.getUri()).thenReturn(OTHER_REPO);

		TransportConfigCallbackFactory factory = new TransportConfigCallbackFactory(null, List.of());

		assertThat(factory.build(properties)).isNotNull();
	}

}
