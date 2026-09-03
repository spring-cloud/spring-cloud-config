/*
 * Copyright 2015-present the original author or authors.
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

package org.springframework.cloud.config.monitor;

import java.net.URI;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.web.client.RestClient;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HttpPropertyPathNotifierTests {

	@Test
	void shouldDiscoverInstancesForAffectedService() {
		DiscoveryClient discoveryClient = mock(DiscoveryClient.class);
		RestClient restClient = mock(RestClient.class);
		MonitorConfigurationProperties monitorProperties = new MonitorConfigurationProperties();
		HttpPropertyPathNotifier notifier = new HttpPropertyPathNotifier(discoveryClient, restClient,
				monitorProperties);
		notifier.notifyApplications(Set.of("foo"));
		verify(discoveryClient).getInstances("foo");
	}

	@Test
	void shouldContinueNotifyingInstancesWhenNotificationFails() {
		DiscoveryClient discoveryClient = mock(DiscoveryClient.class);
		RestClient restClient = mock(RestClient.class);
		RestClient.RequestBodyUriSpec requestSpec = mock(RestClient.RequestBodyUriSpec.class);
		RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);
		ServiceInstance firstInstance = mock(ServiceInstance.class);
		ServiceInstance secondInstance = mock(ServiceInstance.class);
		when(firstInstance.getUri()).thenReturn(URI.create("http://localhost:8080"));
		when(secondInstance.getUri()).thenReturn(URI.create("http://localhost:8081"));
		when(discoveryClient.getInstances("foo")).thenReturn(List.of(firstInstance, secondInstance));
		when(restClient.post()).thenReturn(requestSpec);
		when(requestSpec.uri(any(URI.class))).thenReturn(requestSpec);
		when(requestSpec.retrieve()).thenReturn(responseSpec);
		when(responseSpec.toBodilessEntity()).thenThrow(new RuntimeException("Connection failed")).thenReturn(null);
		MonitorConfigurationProperties monitorProperties = new MonitorConfigurationProperties();
		HttpPropertyPathNotifier notifier = new HttpPropertyPathNotifier(discoveryClient, restClient,
				monitorProperties);
		notifier.notifyApplications(Set.of("foo"));
		verify(restClient, times(2)).post();
		verify(responseSpec, times(2)).toBodilessEntity();
	}

}
