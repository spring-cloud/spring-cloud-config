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
		TransportConfigCallback customCallback = null;
		GoogleCloudSourceSupport googleCloudSourceSupport = null;
		AzureDevOpsWorkloadIdentitySupport azureSupport = mock(AzureDevOpsWorkloadIdentitySupport.class);
		TransportConfigCallback azureCallback = mock(TransportConfigCallback.class);
		MultipleJGitEnvironmentProperties properties = mock(MultipleJGitEnvironmentProperties.class);

		when(properties.getUri()).thenReturn(AZURE_DEVOPS_REPO);
		when(properties.isManagedIdentityEnabled()).thenReturn(true);
		when(properties.getClientId()).thenReturn("test-client-id");
		when(azureSupport.canHandle(AZURE_DEVOPS_REPO)).thenReturn(true);
		when(azureSupport.createTransportConfigCallback("test-client-id")).thenReturn(azureCallback);

		TransportConfigCallbackFactory factory = new TransportConfigCallbackFactory(customCallback,
				googleCloudSourceSupport, azureSupport);

		assertThat(factory.build(properties)).isSameAs(azureCallback);
		verify(azureSupport).createTransportConfigCallback("test-client-id");
	}

	@Test
	public void doesNotUseAzureDevOpsWorkloadIdentityWhenDisabled() {
		TransportConfigCallback customCallback = null;
		GoogleCloudSourceSupport googleCloudSourceSupport = null;
		AzureDevOpsWorkloadIdentitySupport azureSupport = mock(AzureDevOpsWorkloadIdentitySupport.class);
		MultipleJGitEnvironmentProperties properties = mock(MultipleJGitEnvironmentProperties.class);

		when(properties.getUri()).thenReturn(AZURE_DEVOPS_REPO);
		when(properties.isManagedIdentityEnabled()).thenReturn(false);
		when(azureSupport.canHandle(AZURE_DEVOPS_REPO)).thenReturn(true);

		TransportConfigCallbackFactory factory = new TransportConfigCallbackFactory(customCallback,
				googleCloudSourceSupport, azureSupport);

		TransportConfigCallback result = factory.build(properties);

		assertThat(result).isNotNull();
		verify(azureSupport, never()).createTransportConfigCallback("test-client-id");
	}

	@Test
	public void doesNotUseAzureDevOpsWorkloadIdentityForOtherRepository() {
		TransportConfigCallback customCallback = null;
		GoogleCloudSourceSupport googleCloudSourceSupport = null;
		AzureDevOpsWorkloadIdentitySupport azureSupport = mock(AzureDevOpsWorkloadIdentitySupport.class);
		MultipleJGitEnvironmentProperties properties = mock(MultipleJGitEnvironmentProperties.class);

		when(properties.getUri()).thenReturn(OTHER_REPO);
		when(properties.isManagedIdentityEnabled()).thenReturn(true);
		when(azureSupport.canHandle(OTHER_REPO)).thenReturn(false);

		TransportConfigCallbackFactory factory = new TransportConfigCallbackFactory(customCallback,
				googleCloudSourceSupport, azureSupport);

		TransportConfigCallback result = factory.build(properties);

		assertThat(result).isNotNull();
		verify(azureSupport, never()).createTransportConfigCallback("test-client-id");
	}

}
