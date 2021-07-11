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

package org.springframework.cloud.config.server.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.config.server.security.AuthorityExtractor;
import org.springframework.cloud.config.server.security.ConfigServerSecurityProperties;
import org.springframework.cloud.config.server.security.DefaultAuthorityExtractor;
import org.springframework.cloud.config.server.security.DefaultSecurityEnhancer;
import org.springframework.cloud.config.server.security.SecurityEnhancer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.intercept.aopalliance.MethodSecurityInterceptor;

/**
 * Config Server Security Configuration.
 *
 * @author ian
 */
@Configuration
@EnableConfigurationProperties(ConfigServerSecurityProperties.class)
@ConditionalOnProperty(prefix = ConfigServerProperties.PREFIX, value = { "security.enabled" }, havingValue = "true")
public class ConfigServerSecurityConfiguration {

	@Autowired(required = false)
	private MethodSecurityInterceptor methodSecurityInterceptor;

	@Bean
	@ConditionalOnMissingBean(SecurityEnhancer.class)
	public SecurityEnhancer defaultSecurityEnhancer(AuthorityExtractor authorityExtractor) {
		if (methodSecurityInterceptor == null) {
			return SecurityEnhancer.DUMMY;
		}
		return new DefaultSecurityEnhancer(methodSecurityInterceptor, authorityExtractor);
	}

	@Bean
	@ConditionalOnMissingBean(AuthorityExtractor.class)
	public AuthorityExtractor defaultAuthorityExtractor() {
		return new DefaultAuthorityExtractor();
	}

}
