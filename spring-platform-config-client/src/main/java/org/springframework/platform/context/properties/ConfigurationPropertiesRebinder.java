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
package org.springframework.platform.context.properties;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.context.properties.ConfigurationBeanFactoryMetaData;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConfigurationPropertiesBindingPostProcessor;
import org.springframework.context.ApplicationListener;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.platform.context.environment.EnvironmentChangeEvent;
import org.springframework.stereotype.Component;

/**
 * @author Dave Syer
 *
 */
@Component
@ManagedResource
public class ConfigurationPropertiesRebinder implements BeanPostProcessor, ApplicationListener<EnvironmentChangeEvent> {

	private ConfigurationBeanFactoryMetaData metaData;

	private ConfigurationPropertiesBindingPostProcessor binder;

	public ConfigurationPropertiesRebinder(
			ConfigurationPropertiesBindingPostProcessor binder) {
		this.binder = binder;
	}

	private Map<String, Object> beans = new HashMap<String, Object>();

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
