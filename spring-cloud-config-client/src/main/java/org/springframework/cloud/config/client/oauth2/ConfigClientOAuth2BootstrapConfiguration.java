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

package org.springframework.cloud.config.client.oauth2;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientProperties;
import org.springframework.cloud.config.client.oauth2.ConfigClientOAuth2Support.OAuth2InterceptorRegistrar;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor.ClientRegistrationIdResolver;

/**
 * Bootstrap configuration for OAuth2 support in the legacy bootstrap flow.
 *
 * <p>
 * Spring Security's own auto-configuration does not run inside the Spring Cloud bootstrap
 * context, so {@link OAuth2ClientProperties} binding is enabled here explicitly in order
 * to build the default {@link ClientRegistrationRepository} from
 * {@code spring.security.oauth2.client.*}.
 * </p>
 *
 * <p>
 * Each layer of the chain is a {@link ConditionalOnMissingBean} so a user may override
 * any piece (the {@link ClientRegistrationRepository}, the
 * {@link OAuth2AuthorizedClientManager}, the {@link ClientRegistrationIdResolver}, or the
 * {@link #configClientOAuth2Interceptor} bean itself) without rewriting the rest.
 * </p>
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({ OAuth2ClientHttpRequestInterceptor.class, ClientRegistrationRepository.class })
@ConditionalOnProperty(name = ConfigClientOAuth2Properties.PREFIX + ".enabled")
@EnableConfigurationProperties({ ConfigClientOAuth2Properties.class, OAuth2ClientProperties.class })
public class ConfigClientOAuth2BootstrapConfiguration {

	/**
	 * Bean name of the OAuth2 {@link ClientHttpRequestInterceptor} consumed by the legacy
	 * bootstrap flow.
	 */
	public static final String OAUTH2_INTERCEPTOR_BEAN_NAME = "configClientOAuth2Interceptor";

	@Bean
	@ConditionalOnMissingBean
	public ClientRegistrationRepository configClientClientRegistrationRepository(OAuth2ClientProperties properties) {
		return OAuth2InterceptorRegistrar.buildClientRegistrationRepository(properties);
	}

	@Bean
	@ConditionalOnMissingBean
	public OAuth2AuthorizedClientManager configClientAuthorizedClientManager(
			ClientRegistrationRepository clientRegistrationRepository) {
		return OAuth2InterceptorRegistrar.buildAuthorizedClientManager(clientRegistrationRepository);
	}

	@Bean
	@ConditionalOnMissingBean
	public ClientRegistrationIdResolver configClientRegistrationIdResolver(ConfigClientOAuth2Properties properties) {
		String clientRegistrationId = OAuth2InterceptorRegistrar
			.requireClientRegistrationId(properties.getClientRegistrationId());
		return request -> clientRegistrationId;
	}

	@Bean(name = OAUTH2_INTERCEPTOR_BEAN_NAME)
	@ConditionalOnMissingBean(name = OAUTH2_INTERCEPTOR_BEAN_NAME)
	public ClientHttpRequestInterceptor configClientOAuth2Interceptor(
			OAuth2AuthorizedClientManager authorizedClientManager,
			ClientRegistrationIdResolver clientRegistrationIdResolver) {
		return OAuth2InterceptorRegistrar.buildInterceptor(authorizedClientManager, clientRegistrationIdResolver);
	}

}
