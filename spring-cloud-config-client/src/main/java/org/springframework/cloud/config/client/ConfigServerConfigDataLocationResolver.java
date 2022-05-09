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
import java.util.List;
import java.util.Properties;

import org.apache.commons.logging.Log;

import org.springframework.beans.BeanUtils;
import org.springframework.boot.BootstrapRegistry.InstanceSupplier;
import org.springframework.boot.ConfigurableBootstrapContext;
import org.springframework.boot.context.config.ConfigDataLocation;
import org.springframework.boot.context.config.ConfigDataLocationNotFoundException;
import org.springframework.boot.context.config.ConfigDataLocationResolver;
import org.springframework.boot.context.config.ConfigDataLocationResolverContext;
import org.springframework.boot.context.config.ConfigDataResourceNotFoundException;
import org.springframework.boot.context.config.Profiles;
import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.boot.context.properties.bind.BindHandler;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.core.Ordered;
import org.springframework.core.log.LogMessage;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import static org.springframework.cloud.config.client.ConfigClientProperties.CONFIG_DISCOVERY_ENABLED;

public class ConfigServerConfigDataLocationResolver
		implements ConfigDataLocationResolver<ConfigServerConfigDataResource>, Ordered {

	/**
	 * Prefix for Config Server imports.
	 */
	public static final String PREFIX = "configserver:";

	private final Log log;

	public ConfigServerConfigDataLocationResolver(DeferredLogFactory factory) {
		this.log = factory.getLog(ConfigServerConfigDataLocationResolver.class);
	}

	@Override
	public int getOrder() {
		return -1;
	}

	protected PropertyHolder loadProperties(ConfigDataLocationResolverContext context, String uris) {
		Binder binder = context.getBinder();
		BindHandler bindHandler = getBindHandler(context);

		ConfigClientProperties configClientProperties;
		if (context.getBootstrapContext().isRegistered(ConfigClientProperties.class)) {
			configClientProperties = new ConfigClientProperties();
			BeanUtils.copyProperties(context.getBootstrapContext().get(ConfigClientProperties.class),
					configClientProperties);
		}
		else {
			configClientProperties = binder
					.bind(ConfigClientProperties.PREFIX, Bindable.of(ConfigClientProperties.class), bindHandler)
					.orElseGet(ConfigClientProperties::new);
		}
		if (!StringUtils.hasText(configClientProperties.getName())) {
			// default to spring.application.name if name isn't set
			String applicationName = binder.bind("spring.application.name", Bindable.of(String.class), bindHandler)
					.orElse("application");
			configClientProperties.setName(applicationName);
		}

		PropertyHolder holder = new PropertyHolder();
		holder.properties = configClientProperties;
		// bind retry, override later
		holder.retryProperties = binder.bind(RetryProperties.PREFIX, RetryProperties.class)
				.orElseGet(RetryProperties::new);

		if (StringUtils.hasText(uris)) {
			String[] uri = StringUtils.commaDelimitedListToStringArray(uris);
			String paramStr = null;
			for (int i = 0; i < uri.length; i++) {
				int paramIdx = uri[i].indexOf('?');
				if (paramIdx > 0) {
					if (i == 0) {
						// only gather params from first uri
						paramStr = uri[i].substring(paramIdx + 1);
					}
					uri[i] = uri[i].substring(0, paramIdx);
				}
			}
			if (StringUtils.hasText(paramStr)) {
				Properties properties = StringUtils
						.splitArrayElementsIntoProperties(StringUtils.delimitedListToStringArray(paramStr, "&"), "=");
				if (properties != null) {
					PropertyMapper map = PropertyMapper.get().alwaysApplyingWhenNonNull();
					map.from(() -> properties.getProperty("fail-fast")).as(Boolean::valueOf)
							.to(configClientProperties::setFailFast);
					map.from(() -> properties.getProperty("max-attempts")).as(Integer::valueOf)
							.to(holder.retryProperties::setMaxAttempts);
					map.from(() -> properties.getProperty("max-interval")).as(Long::valueOf)
							.to(holder.retryProperties::setMaxInterval);
					map.from(() -> properties.getProperty("multiplier")).as(Double::valueOf)
							.to(holder.retryProperties::setMultiplier);
					map.from(() -> properties.getProperty("initial-interval")).as(Long::valueOf)
							.to(holder.retryProperties::setInitialInterval);
				}
			}
			configClientProperties.setUri(uri);
		}

		return holder;
	}

	private BindHandler getBindHandler(ConfigDataLocationResolverContext context) {
		return context.getBootstrapContext().getOrElse(BindHandler.class, null);
	}

	@Deprecated
	protected RestTemplate createRestTemplate(ConfigClientProperties properties) {
		return null;
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
		String uris = location.getNonPrefixedValue(getPrefix());
		PropertyHolder propertyHolder = loadProperties(resolverContext, uris);
		ConfigClientProperties properties = propertyHolder.properties;

		ConfigurableBootstrapContext bootstrapContext = resolverContext.getBootstrapContext();
		bootstrapContext.registerIfAbsent(ConfigClientProperties.class, InstanceSupplier.of(properties));
		bootstrapContext.addCloseListener(event -> event.getApplicationContext().getBeanFactory().registerSingleton(
				"configDataConfigClientProperties", event.getBootstrapContext().get(ConfigClientProperties.class)));

		bootstrapContext.registerIfAbsent(ConfigClientRequestTemplateFactory.class,
				context -> new ConfigClientRequestTemplateFactory(log, context.get(ConfigClientProperties.class)));

		bootstrapContext.registerIfAbsent(RestTemplate.class, context -> {
			ConfigClientRequestTemplateFactory factory = context.get(ConfigClientRequestTemplateFactory.class);
			RestTemplate restTemplate = createRestTemplate(factory.getProperties());
			if (restTemplate != null) {
				// shouldn't normally happen
				return restTemplate;
			}
			return factory.create();
		});

		ConfigServerConfigDataResource resource = new ConfigServerConfigDataResource(properties, location.isOptional(),
				profiles);
		resource.setLog(log);
		resource.setRetryProperties(propertyHolder.retryProperties);

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
					RetryTemplate retryTemplate = RetryTemplateFactory.create(propertyHolder.retryProperties, log);
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
		locations.add(resource);

		return locations;
	}

	private class PropertyHolder {

		ConfigClientProperties properties;

		RetryProperties retryProperties;

	}

}
