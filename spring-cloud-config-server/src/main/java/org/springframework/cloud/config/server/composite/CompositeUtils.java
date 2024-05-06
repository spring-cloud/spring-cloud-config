/*
 * Copyright 2018-2024 the original author or authors.
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

import java.lang.reflect.AnnotatedType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.cloud.config.server.environment.EnvironmentRepositoryFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.type.MethodMetadata;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * @author Dylan Roberts
 * @author Olga Maciaszek-Sharma
 */
public final class CompositeUtils {

	private CompositeUtils() {
		throw new IllegalStateException("Can't instantiate a utility class");
	}

	/**
	 * Returns list of values of the `type` field from the
	 * `spring.cloud.config.server.composite` collection.
	 * @param environment Spring Environment
	 * @return list of matching types
	 */
	public static List<String> getCompositeTypeList(Environment environment) {
		return Binder.get(environment).bind("spring.cloud.config.server", CompositeConfig.class).get().getComposite()
				.stream().map(map -> (String) map.get("type")).collect(Collectors.toList());
	}

	/**
	 * Given a type of EnvironmentRepository (git, svn, native, etc...) returns the name
	 * of the factory bean. See {@link #getCompositeTypeList(Environment)}
	 * @param type type of a repository
	 * @param beanFactory Spring Bean Factory
	 * @return name of the factory bean
	 */
	public static String getFactoryName(String type, ConfigurableListableBeanFactory beanFactory) {
		String[] factoryNames = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(beanFactory,
				EnvironmentRepositoryFactory.class, true, false);
		return Arrays.stream(factoryNames).filter(n -> StringUtils.startsWithIgnoreCase(n, type)).findFirst()
				.orElse(null);
	}

	/**
	 * Given a Factory Name, return the generic type parameters of the factory (The actual
	 * repository class, and its properties class).
	 * @param beanFactory Spring Bean Factory
	 * @param factoryName name of the factory
	 * @return generic type params of the factory
	 */
	public static Type[] getEnvironmentRepositoryFactoryTypeParams(ConfigurableListableBeanFactory beanFactory,
			String factoryName) {
		Class<?> factoryClass = getFactoryClass(beanFactory, factoryName);
		return getEnvironmentRepositoryFactoryTypeParams(factoryClass);
	}

	/**
	 * Given a Factory {@link Class}, return the generic type parameters of the factory
	 * (The actual repository class, and its properties class).
	 * @param factoryClass Factory {@link Class}
	 * @return generic type params of the factory
	 */
	public static Type[] getEnvironmentRepositoryFactoryTypeParams(Class<?> factoryClass) {
		Optional<AnnotatedType> annotatedFactoryType = Arrays.stream(factoryClass.getAnnotatedInterfaces())
				.filter(i -> {
					ParameterizedType parameterizedType = (ParameterizedType) i.getType();
					return parameterizedType.getRawType().equals(EnvironmentRepositoryFactory.class);
				}).findFirst();
		ParameterizedType factoryParameterizedType = (ParameterizedType) annotatedFactoryType
				.orElse(factoryClass.getAnnotatedSuperclass()).getType();
		return factoryParameterizedType.getActualTypeArguments();
	}

	/**
	 * Given a Factory Name, return the Factory {@link Class}.
	 * @param beanFactory Spring Bean Factory
	 * @param factoryName name of the factory
	 * @return factory {@link Class}
	 */
	public static Class<?> getFactoryClass(ConfigurableListableBeanFactory beanFactory, String factoryName) {
		MethodMetadata methodMetadata = (MethodMetadata) beanFactory.getBeanDefinition(factoryName).getSource();
		Assert.notNull(methodMetadata, "Factory MethodMetadata cannot be null.");
		Class<?> factoryClass;
		try {
			factoryClass = Class.forName(methodMetadata.getReturnTypeName());
		}
		catch (ClassNotFoundException e) {
			throw new IllegalStateException(e);
		}
		return factoryClass;
	}

	static class CompositeConfig {

		List<Map<String, Object>> composite;

		public List<Map<String, Object>> getComposite() {
			return this.composite;
		}

		public void setComposite(List<Map<String, Object>> composite) {
			this.composite = composite;
		}

	}

}
