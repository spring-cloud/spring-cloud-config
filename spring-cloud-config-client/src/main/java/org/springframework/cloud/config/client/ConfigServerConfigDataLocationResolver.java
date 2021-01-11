/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.config.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;

import org.springframework.boot.BootstrapRegistry.InstanceSupplier;
import org.springframework.boot.ConfigurableBootstrapContext;
import org.springframework.boot.context.config.ConfigDataLocation;
import org.springframework.boot.context.config.ConfigDataLocationNotFoundException;
import org.springframework.boot.context.config.ConfigDataLocationResolver;
import org.springframework.boot.context.config.ConfigDataLocationResolverContext;
import org.springframework.boot.context.config.ConfigDataResourceNotFoundException;
import org.springframework.boot.context.config.Profiles;
import org.springframework.boot.context.properties.bind.BindHandler;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.core.Ordered;
import org.springframework.core.log.LogMessage;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import static org.springframework.cloud.config.client.ConfigClientProperties.AUTHORIZATION;
import static org.springframework.cloud.config.client.ConfigClientProperties.CONFIG_DISCOVERY_ENABLED;

public class ConfigServerConfigDataLocationResolver
		implements ConfigDataLocationResolver<ConfigServerConfigDataResource>, Ordered {

	/**
	 * Prefix for Config Server imports.
	 */
	public static final String PREFIX = "configserver:";

	private final Log log;

	public ConfigServerConfigDataLocationResolver(Log log) {
		this.log = log;
	}

	@Override
	public int getOrder() {
		return -1;
	}

	protected ConfigClientProperties loadProperties(ConfigDataLocationResolverContext context) {
		Binder binder = context.getBinder();
		BindHandler bindHandler = getBindHandler(context);
		ConfigClientProperties configClientProperties = binder
				.bind(ConfigClientProperties.PREFIX, Bindable.of(ConfigClientProperties.class), bindHandler)
				.orElseGet(ConfigClientProperties::new);
		if (!StringUtils.hasText(configClientProperties.getName())) {
			// default to spring.application.name if name isn't set
			String applicationName = binder.bind("spring.application.name", Bindable.of(String.class), bindHandler)
					.orElse("application");
			configClientProperties.setName(applicationName);
		}
		return configClientProperties;
	}

	private BindHandler getBindHandler(ConfigDataLocationResolverContext context) {
		return context.getBootstrapContext().getOrElse(BindHandler.class, null);
	}

	protected RestTemplate createRestTemplate(ConfigClientProperties properties) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		if (properties.getRequestReadTimeout() < 0) {
			throw new IllegalStateException("Invalid Value for Read Timeout set.");
		}
		if (properties.getRequestConnectTimeout() < 0) {
			throw new IllegalStateException("Invalid Value for Connect Timeout set.");
		}
		requestFactory.setReadTimeout(properties.getRequestReadTimeout());
		requestFactory.setConnectTimeout(properties.getRequestConnectTimeout());
		RestTemplate template = new RestTemplate(requestFactory);
		Map<String, String> headers = new HashMap<>(properties.getHeaders());
		if (headers.containsKey(AUTHORIZATION)) {
			headers.remove(AUTHORIZATION); // To avoid redundant addition of header
		}
		if (!headers.isEmpty()) {
			template.setInterceptors(Collections
					.singletonList(new ConfigServicePropertySourceLocator.GenericRequestHeaderInterceptor(headers)));
		}

		return template;
	}

	protected Log getLog() {
		return this.log;
	}

	@Override
	public boolean isResolvable(ConfigDataLocationResolverContext context, ConfigDataLocation location) {
		if (!location.hasPrefix(getPrefix())) {
			return false;
		}
		return context.getBinder().bind(ConfigClientProperties.PREFIX + ".enabled", Boolean.class).orElse(true);
	}

	protected String getPrefix() {
		return PREFIX;
	}

	@Override
	public List<ConfigServerConfigDataResource> resolve(ConfigDataLocationResolverContext context,
			ConfigDataLocation location)
			throws ConfigDataLocationNotFoundException, ConfigDataResourceNotFoundException {
		return Collections.emptyList();
	}

	@Override
	public List<ConfigServerConfigDataResource> resolveProfileSpecific(
			ConfigDataLocationResolverContext resolverContext, ConfigDataLocation location, Profiles profiles)
			throws ConfigDataLocationNotFoundException {
		ConfigClientProperties properties = loadProperties(resolverContext);
		String uris = location.getNonPrefixedValue(getPrefix());

		if (StringUtils.hasText(uris)) {
			String[] uri = StringUtils.commaDelimitedListToStringArray(uris);
			properties.setUri(uri);
		}

		ConfigurableBootstrapContext bootstrapContext = resolverContext.getBootstrapContext();
		bootstrapContext.registerIfAbsent(ConfigClientProperties.class, InstanceSupplier.of(properties));
		bootstrapContext.addCloseListener(event -> event.getApplicationContext().getBeanFactory().registerSingleton(
				"configDataConfigClientProperties", event.getBootstrapContext().get(ConfigClientProperties.class)));

		bootstrapContext.registerIfAbsent(RestTemplate.class, context -> {
			ConfigClientProperties props = context.get(ConfigClientProperties.class);
			return createRestTemplate(props);
		});

		boolean discoveryEnabled = resolverContext.getBinder()
				.bind(CONFIG_DISCOVERY_ENABLED, Bindable.of(Boolean.class), getBindHandler(resolverContext))
				.orElse(false);

		boolean retryEnabled = resolverContext.getBinder().bind(ConfigClientProperties.PREFIX + ".fail-fast",
				Bindable.of(Boolean.class), getBindHandler(resolverContext)).orElse(false);

		if (discoveryEnabled) {
			log.debug(LogMessage.format("discovery enabled"));
			// register ConfigServerInstanceMonitor
			bootstrapContext.registerIfAbsent(ConfigServerInstanceMonitor.class, context -> {
				ConfigServerInstanceProvider.Function function = context
						.get(ConfigServerInstanceProvider.Function.class);

				ConfigServerInstanceProvider instanceProvider;
				if (ConfigClientRetryBootstrapper.RETRY_IS_PRESENT && retryEnabled) {
					log.debug(LogMessage.format("discovery plus retry enabled"));
					RetryTemplate retryTemplate = context.get(RetryTemplate.class);
					instanceProvider = new ConfigServerInstanceProvider(function) {
						@Override
						public List<ServiceInstance> getConfigServerInstances(String serviceId) {
							return retryTemplate.execute(retryContext -> super.getConfigServerInstances(serviceId));
						}
					};
				}
				else {
					instanceProvider = new ConfigServerInstanceProvider(function);
				}
				instanceProvider.setLog(log);

				ConfigClientProperties clientProperties = context.get(ConfigClientProperties.class);
				ConfigServerInstanceMonitor instanceMonitor = new ConfigServerInstanceMonitor(log, clientProperties,
						instanceProvider);
				instanceMonitor.setRefreshOnStartup(false);
				instanceMonitor.refresh();
				return instanceMonitor;
			});
			// promote ConfigServerInstanceMonitor to bean so updates can be made to
			// config client uri
			bootstrapContext.addCloseListener(event -> {
				ConfigServerInstanceMonitor configServerInstanceMonitor = event.getBootstrapContext()
						.get(ConfigServerInstanceMonitor.class);
				event.getApplicationContext().getBeanFactory().registerSingleton("configServerInstanceMonitor",
						configServerInstanceMonitor);
			});
		}

		List<ConfigServerConfigDataResource> locations = new ArrayList<>();
		locations.add(new ConfigServerConfigDataResource(properties, location.isOptional(), profiles));

		return locations;
	}

}
