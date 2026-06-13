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

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.bootstrap.ConfigurableBootstrapContext;
import org.springframework.boot.context.properties.bind.BindHandler;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientProperties;
import org.springframework.boot.security.oauth2.client.autoconfigure.OAuth2ClientPropertiesMapper;
import org.springframework.cloud.config.client.ConfigClientRequestTemplateFactory;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProvider;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor;
import org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor.ClientRegistrationIdResolver;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * Wires the OAuth2 {@link ClientHttpRequestInterceptor} into a
 * {@link ConfigClientRequestTemplateFactory} for the Spring Cloud Config Data flow, when
 * Spring Security OAuth2 client classes are on the classpath and
 * {@code spring.cloud.config.oauth2.enabled=true}.
 *
 * <p>
 * Every collaborator that gets built ({@link ClientRegistrationRepository},
 * {@link OAuth2AuthorizedClientManager}, {@link ClientRegistrationIdResolver}, and the
 * interceptor itself) is looked up in the bootstrap context first via
 * {@link ConfigurableBootstrapContext#getOrElseSupply}, so users can plug in custom
 * implementations by registering them in the bootstrap registry before this support class
 * runs.
 * </p>
 *
 * <p>
 * All Spring Security references are isolated in the nested
 * {@link OAuth2InterceptorRegistrar}, which is only loaded after the
 * {@link #OAUTH2_CLIENT_IS_PRESENT} guard passes, so config clients without Spring
 * Security OAuth2 on the classpath never trigger class loading of those types.
 * </p>
 */
public final class ConfigClientOAuth2Support {

	/**
	 * Fully-qualified class name of the OAuth2 request interceptor used as the classpath
	 * probe.
	 */
	public static final String OAUTH2_INTERCEPTOR_CLASS = "org.springframework.security.oauth2.client.web.client.OAuth2ClientHttpRequestInterceptor";

	/**
	 * Whether Spring Security OAuth2 client classes are on the classpath.
	 */
	public static final boolean OAUTH2_CLIENT_IS_PRESENT = ClassUtils.isPresent(OAUTH2_INTERCEPTOR_CLASS, null);

	private ConfigClientOAuth2Support() {
	}

	/**
	 * Adds an OAuth2 interceptor to the given factory if Spring Security OAuth2 client is
	 * available and {@code spring.cloud.config.oauth2.enabled=true}. Otherwise this is a
	 * no-op.
	 * @param bootstrapContext the bootstrap context used to look up overridable
	 * collaborators
	 * @param binder the binder used to read OAuth2 properties
	 * @param bindHandler the bind handler (may be {@code null}) so encrypted or
	 * placeholder values resolve consistently with the rest of the config client
	 * @param factory the factory to add the interceptor to
	 */
	public static void registerInterceptor(ConfigurableBootstrapContext bootstrapContext, Binder binder,
			BindHandler bindHandler, ConfigClientRequestTemplateFactory factory) {
		if (!OAUTH2_CLIENT_IS_PRESENT) {
			return;
		}
		OAuth2InterceptorRegistrar.register(bootstrapContext, binder, bindHandler, factory);
	}

	/**
	 * Holds every Spring Security reference. Loaded only after the
	 * {@link #OAUTH2_CLIENT_IS_PRESENT} guard in {@link #registerInterceptor}, or from
	 * the {@code @ConditionalOnClass}-guarded bootstrap configuration, so config clients
	 * without Spring Security OAuth2 on the classpath never trigger class loading here.
	 */
	static final class OAuth2InterceptorRegistrar {

		private OAuth2InterceptorRegistrar() {
		}

		static void register(ConfigurableBootstrapContext bootstrapContext, Binder binder, BindHandler bindHandler,
				ConfigClientRequestTemplateFactory factory) {
			ConfigClientOAuth2Properties oauth2Properties = binder
				.bind(ConfigClientOAuth2Properties.PREFIX, Bindable.of(ConfigClientOAuth2Properties.class), bindHandler)
				.orElseGet(ConfigClientOAuth2Properties::new);
			if (!oauth2Properties.isEnabled()) {
				return;
			}
			String clientRegistrationId = requireClientRegistrationId(oauth2Properties.getClientRegistrationId());

			// Resolve the manager first. The default ClientRegistrationRepository is
			// built
			// lazily inside the manager supplier, so a user-supplied manager makes the
			// default repository unnecessary.
			OAuth2AuthorizedClientManager authorizedClientManager = bootstrapContext.getOrElseSupply(
					OAuth2AuthorizedClientManager.class,
					() -> buildAuthorizedClientManager(
							bootstrapContext.getOrElseSupply(ClientRegistrationRepository.class,
									() -> buildClientRegistrationRepository(binder, bindHandler))));

			ClientRegistrationIdResolver registrationIdResolver = bootstrapContext
				.getOrElseSupply(ClientRegistrationIdResolver.class, () -> request -> clientRegistrationId);

			ClientHttpRequestInterceptor interceptor = bootstrapContext.getOrElseSupply(
					ClientHttpRequestInterceptor.class,
					() -> buildInterceptor(authorizedClientManager, registrationIdResolver));

			factory.addInterceptor(interceptor);
		}

		static String requireClientRegistrationId(String clientRegistrationId) {
			if (!StringUtils.hasText(clientRegistrationId)) {
				throw new IllegalStateException("'" + ConfigClientOAuth2Properties.PREFIX + ".enabled' is true but '"
						+ ConfigClientOAuth2Properties.PREFIX + ".client-registration-id' is not set");
			}
			return clientRegistrationId;
		}

		static ClientRegistrationRepository buildClientRegistrationRepository(Binder binder, BindHandler bindHandler) {
			OAuth2ClientProperties oauth2ClientProperties = binder
				.bind("spring.security.oauth2.client", Bindable.of(OAuth2ClientProperties.class), bindHandler)
				.orElseGet(OAuth2ClientProperties::new);
			return buildClientRegistrationRepository(oauth2ClientProperties);
		}

		static ClientRegistrationRepository buildClientRegistrationRepository(
				OAuth2ClientProperties oauth2ClientProperties) {
			oauth2ClientProperties.afterPropertiesSet();
			List<ClientRegistration> registrations = new ArrayList<>(
					new OAuth2ClientPropertiesMapper(oauth2ClientProperties).asClientRegistrations().values());
			return new InMemoryClientRegistrationRepository(registrations);
		}

		static OAuth2AuthorizedClientManager buildAuthorizedClientManager(
				ClientRegistrationRepository registrationRepository) {
			OAuth2AuthorizedClientProvider authorizedClientProvider = OAuth2AuthorizedClientProviderBuilder.builder()
				.clientCredentials()
				.refreshToken()
				.build();
			OAuth2AuthorizedClientService authorizedClientService = new InMemoryOAuth2AuthorizedClientService(
					registrationRepository);
			AuthorizedClientServiceOAuth2AuthorizedClientManager authorizedClientManager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(
					registrationRepository, authorizedClientService);
			authorizedClientManager.setAuthorizedClientProvider(authorizedClientProvider);
			return authorizedClientManager;
		}

		static ClientHttpRequestInterceptor buildInterceptor(OAuth2AuthorizedClientManager manager,
				ClientRegistrationIdResolver resolver) {
			OAuth2ClientHttpRequestInterceptor interceptor = new OAuth2ClientHttpRequestInterceptor(manager);
			interceptor.setClientRegistrationIdResolver(resolver);
			return interceptor;
		}

	}

}
