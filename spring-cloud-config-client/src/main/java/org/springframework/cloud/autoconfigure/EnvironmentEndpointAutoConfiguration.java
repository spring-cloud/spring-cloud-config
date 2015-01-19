/*
 * Copyright 2013-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.cloud.autoconfigure;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.EndpointAutoConfiguration;
import org.springframework.boot.actuate.endpoint.EnvironmentEndpoint;
import org.springframework.boot.actuate.endpoint.mvc.MvcEndpoint;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.web.WebMvcAutoConfiguration;
import org.springframework.cloud.config.client.RefreshEndpoint;
import org.springframework.cloud.context.environment.EnvironmentManager;
import org.springframework.cloud.context.environment.EnvironmentManagerMvcEndpoint;
import org.springframework.cloud.context.restart.RestartEndpoint;
import org.springframework.cloud.context.restart.RestartMvcEndpoint;
import org.springframework.cloud.endpoint.GenericPostableMvcEndpoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author Dave Syer
 *
 */
@Configuration
@ConditionalOnClass(EnvironmentEndpoint.class)
@ConditionalOnProperty(value = "endpoints.env.enabled", matchIfMissing = true)
@ConditionalOnWebApplication
@ConditionalOnBean(RestartEndpoint.class)
@AutoConfigureAfter({ WebMvcAutoConfiguration.class, EndpointAutoConfiguration.class, RefreshAutoConfiguration.class })
public class EnvironmentEndpointAutoConfiguration {

	@Autowired
	private RestartEndpoint restartEndpoint;

	@Bean
	@ConditionalOnBean(EnvironmentEndpoint.class)
	public EnvironmentManagerMvcEndpoint environmentManagerEndpoint(
			EnvironmentEndpoint delegate, EnvironmentManager environment) {
		return new EnvironmentManagerMvcEndpoint(delegate, environment);
	}

	@Bean
	@ConditionalOnBean(RefreshEndpoint.class)
	public MvcEndpoint refreshMvcEndpoint(RefreshEndpoint endpoint) {
		return new GenericPostableMvcEndpoint(endpoint);
	}

	@Bean
	public RestartMvcEndpoint restartMvcEndpoint() {
		return new RestartMvcEndpoint(restartEndpoint);
	}

	@Bean
	public MvcEndpoint pauseMvcEndpoint(RestartMvcEndpoint restartEndpoint) {
		return restartEndpoint.getPauseEndpoint();
	}

	@Bean
	public MvcEndpoint resumeMvcEndpoint(RestartMvcEndpoint restartEndpoint) {
		return restartEndpoint.getResumeEndpoint();
	}

}
