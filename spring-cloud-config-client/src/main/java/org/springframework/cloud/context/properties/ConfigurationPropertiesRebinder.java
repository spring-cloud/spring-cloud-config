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
package org.springframework.cloud.context.properties;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.context.properties.ConfigurationBeanFactoryMetaData;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesBindingPostProcessor;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.env.Environment;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.stereotype.Component;

/**
 * Listens for {@link EnvironmentChangeEvent} and rebinds beans that were bound to the
 * {@link Environment} using {@link ConfigurationProperties
 * <code>@ConfigurationProperties</code>}. When these beans are re-bound and
 * re-initialized the changes are available immediately to any component that is using the
 * <code>@ConfigurationProperties</code> bean.
 * 
 * @see RefreshScope for a deeper and optionally more focused refresh of bean components
 * 
 * @author Dave Syer
 *
 */
@Component
@ManagedResource
public class ConfigurationPropertiesRebinder implements BeanPostProcessor,
		ApplicationListener<EnvironmentChangeEvent>, ApplicationContextAware {

	private ConfigurationBeanFactoryMetaData metaData;

	private ConfigurationPropertiesBindingPostProcessor binder;

	public ConfigurationPropertiesRebinder(
			ConfigurationPropertiesBindingPostProcessor binder) {
		this.binder = binder;
	}

	private Map<String, Object> beans = new HashMap<String, Object>();

	private ApplicationContext applicationContext;

	@Override
	public void setApplicationContext(ApplicationContext applicationContext)
			throws BeansException {
		this.applicationContext = applicationContext;
	}

	/**
	 * @param beans the bean meta data to set
	 */
	public void setBeanMetaDataStore(ConfigurationBeanFactoryMetaData beans) {
		this.metaData = beans;
	}

	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName)
			throws BeansException {
		ConfigurationProperties annotation = AnnotationUtils.findAnnotation(
				bean.getClass(), ConfigurationProperties.class);
		if (annotation != null) {
			beans.put(beanName, bean);
		}
		else if (metaData != null) {
			annotation = this.metaData.findFactoryAnnotation(beanName,
					ConfigurationProperties.class);
			if (annotation != null) {
				beans.put(beanName, bean);
			}
		}
		return bean;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName)
			throws BeansException {
		return bean;
	}

	@ManagedOperation
	public void rebind() {
		for (String name : beans.keySet()) {
			rebind(name);
		}
	}

	@ManagedOperation
	public void rebind(String name) {
		binder.postProcessBeforeInitialization(beans.get(name), name);
		if (applicationContext != null) {
			applicationContext.getAutowireCapableBeanFactory().initializeBean(
					beans.get(name), name);
		}
	}

	@ManagedAttribute
	public Set<String> getBeanNames() {
		return new HashSet<String>(beans.keySet());
	}

	@Override
	public void onApplicationEvent(EnvironmentChangeEvent event) {
		rebind();
	}

}
