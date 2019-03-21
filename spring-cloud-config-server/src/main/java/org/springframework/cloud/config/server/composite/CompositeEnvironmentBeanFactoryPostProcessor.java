/*
 * Copyright 2018-2019 the original author or authors.
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

package org.springframework.cloud.config.server.composite;

import java.lang.reflect.Type;
import java.util.List;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.cloud.config.server.support.EnvironmentRepositoryProperties;
import org.springframework.core.env.Environment;

/**
 * A {@link BeanFactoryPostProcessor} to register {@link EnvironmentRepository}
 * {@link BeanDefinition}s based on the composite list configuration.
 *
 * @author Dylan Roberts
 */
public class CompositeEnvironmentBeanFactoryPostProcessor
		implements BeanFactoryPostProcessor {

	private Environment environment;

	public CompositeEnvironmentBeanFactoryPostProcessor(Environment environment) {
		this.environment = environment;
	}

	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory)
			throws BeansException {
		List<String> typePropertyList = CompositeUtils
				.getCompositeTypeList(this.environment);
		for (int i = 0; i < typePropertyList.size(); i++) {
			String type = typePropertyList.get(i);
			String factoryName = CompositeUtils.getFactoryName(type, beanFactory);

			Type[] factoryTypes = CompositeUtils
					.getEnvironmentRepositoryFactoryTypeParams(beanFactory, factoryName);
			Class<? extends EnvironmentRepositoryProperties> propertiesClass;
			propertiesClass = (Class<? extends EnvironmentRepositoryProperties>) factoryTypes[1];
			EnvironmentRepositoryProperties properties = bindProperties(i,
					propertiesClass, this.environment);

			AbstractBeanDefinition beanDefinition = BeanDefinitionBuilder
					.genericBeanDefinition(EnvironmentRepository.class)
					.setFactoryMethodOnBean("build", factoryName)
					.addConstructorArgValue(properties).getBeanDefinition();
			String beanName = String.format("%s-env-repo%d", type, i);
			BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;
			registry.registerBeanDefinition(beanName, beanDefinition);
		}
	}

	private <P extends EnvironmentRepositoryProperties> P bindProperties(int index,
			Class<P> propertiesClass, Environment environment) {
		Binder binder = Binder.get(environment);
		String environmentConfigurationPropertyName = String
				.format("spring.cloud.config.server.composite[%d]", index);
		P properties = binder.bind(environmentConfigurationPropertyName, propertiesClass)
				.orElseCreate(propertiesClass);
		properties.setOrder(index + 1);
		return properties;
	}

}
