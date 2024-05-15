/*
 * Copyright 2014-2024 the original author or authors.
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

package org.springframework.cloud.config.server.aot;

import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.lang.model.element.Modifier;

import org.springframework.aot.generate.GeneratedMethod;
import org.springframework.aot.generate.GenerationContext;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.ReflectionHints;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.aot.BeanFactoryInitializationCode;
import org.springframework.beans.factory.aot.BeanRegistrationExcludeFilter;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RegisteredBean;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.cloud.config.server.composite.CompositeEnvironmentBeanFactoryPostProcessor;
import org.springframework.cloud.config.server.composite.CompositeUtils;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.cloud.config.server.environment.EnvironmentRepositoryFactory;
import org.springframework.cloud.config.server.support.EnvironmentRepositoryProperties;
import org.springframework.core.env.Environment;
import org.springframework.javapoet.MethodSpec;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

/**
 * A {@link BeanFactoryInitializationAotProcessor} implementation that generates code for
 * registering composite environment repository beans.
 *
 * @author Olga Maciaszek-Sharma
 * @since 4.1.2
 */
public class CompositeEnvironmentBeanFactoryInitializationAotProcessor
		implements BeanFactoryInitializationAotProcessor, BeanRegistrationExcludeFilter {

	@SuppressWarnings("NullableProblems")
	@Override
	public BeanFactoryInitializationAotContribution processAheadOfTime(ConfigurableListableBeanFactory beanFactory) {
		Assert.isInstanceOf(ConfigurableListableBeanFactory.class, beanFactory,
				ConfigurableListableBeanFactory.class.getSimpleName() + " instance expected.");
		Map<String, BeanDefinition> propertyBeanDefinitions = getCompositeEnvironmentBeanDefinitions(beanFactory,
				"-env-repo-properties", EnvironmentRepositoryProperties.class);
		Map<String, BeanDefinition> repoBeanDefinitions = getCompositeEnvironmentBeanDefinitions(beanFactory,
				"-env-repo", EnvironmentRepository.class);
		return new CompositeEnvironmentBeanFactoryInitializationAotContribution(propertyBeanDefinitions,
				repoBeanDefinitions, beanFactory);
	}

	private static Map<String, BeanDefinition> getCompositeEnvironmentBeanDefinitions(
			ConfigurableListableBeanFactory beanFactory, String infix, Class<?> beanClass) {
		return Arrays.stream(beanFactory.getBeanDefinitionNames()).filter(beanName -> beanName.contains(infix))
				.map(beanName -> Map.entry(beanName, beanFactory.getBeanDefinition(beanName))).filter(entry -> {
					try {
						return beanClass.isAssignableFrom(Class.forName(entry.getValue().getBeanClassName()));
					}
					catch (ClassNotFoundException e) {
						throw new RuntimeException(
								"Class " + entry.getValue().getBeanClassName() + " could not be found", e);
					}
				}).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
	}

	@Override
	public boolean isExcludedFromAotProcessing(RegisteredBean registeredBean) {
		return CompositeEnvironmentBeanFactoryPostProcessor.class.isAssignableFrom(registeredBean.getBeanClass())
				|| EnvironmentRepositoryProperties.class.isAssignableFrom(registeredBean.getBeanClass())
						&& registeredBean.getBeanName().contains("-env-repo-properties")
				|| EnvironmentRepository.class.isAssignableFrom(registeredBean.getBeanClass())
						&& registeredBean.getBeanName().contains("-env-repo");
	}

	private static final class CompositeEnvironmentBeanFactoryInitializationAotContribution
			implements BeanFactoryInitializationAotContribution {

		private final Map<String, BeanDefinition> propertyBeanDefinitions;

		private final Map<String, BeanDefinition> repoBeanDefinitions;

		private final ConfigurableListableBeanFactory beanFactory;

		private final Set<Class<?>> hintClasses = new HashSet<>();

		private CompositeEnvironmentBeanFactoryInitializationAotContribution(
				Map<String, BeanDefinition> propertyBeanDefinitions, Map<String, BeanDefinition> repoBeanDefinitions,
				ConfigurableListableBeanFactory beanFactory) {
			this.propertyBeanDefinitions = propertyBeanDefinitions;
			this.repoBeanDefinitions = repoBeanDefinitions;
			this.beanFactory = beanFactory;
		}

		@Override
		public void applyTo(GenerationContext generationContext,
				BeanFactoryInitializationCode beanFactoryInitializationCode) {
			GeneratedMethod environmentRepositoryPropertiesGeneratedMethod = beanFactoryInitializationCode.getMethods()
					.add("registerCompositeEnvironmentRepositoryPropertiesBeanDefinitions",
							this::generateRegisterBeanDefinitionsMethod);
			beanFactoryInitializationCode
					.addInitializer(environmentRepositoryPropertiesGeneratedMethod.toMethodReference());
			generateRuntimeHints(generationContext.getRuntimeHints());
		}

		private void generateRuntimeHints(RuntimeHints runtimeHints) {
			ReflectionHints hints = runtimeHints.reflection();
			for (Class<?> clazz : hintClasses) {
				hints.registerType(TypeReference.of(clazz), MemberCategory.INVOKE_PUBLIC_METHODS,
						MemberCategory.INTROSPECT_DECLARED_METHODS);
				introspectPublicMethodsOnAllInterfaces(hints, clazz);
			}
		}

		@SuppressWarnings("unchecked")
		private void generateRegisterBeanDefinitionsMethod(MethodSpec.Builder method) {
			method.addJavadoc(
					"Register composite environment repository bean definitions for composite config data sources.");
			method.addModifiers(Modifier.PUBLIC);
			method.addParameter(DefaultListableBeanFactory.class, "beanFactory");
			method.addParameter(Environment.class, "environment");
			method.addStatement("$T binder = Binder.get(environment)", Binder.class);
			Pattern findIndexPattern = Pattern.compile("(^.*)(-env-repo-properties)([0-9]+)$");
			propertyBeanDefinitions.keySet().forEach(beanName -> {
				Matcher matcher = findIndexPattern.matcher(beanName);
				if (matcher.find()) {
					String repoBeanName = beanName.replace("repo-properties", "repo");
					String factoryName = repoBeanDefinitions.get(repoBeanName).getFactoryBeanName();
					Class<? extends EnvironmentRepositoryFactory<? extends EnvironmentRepository, ? extends EnvironmentRepositoryProperties>> factoryClass = (Class<? extends EnvironmentRepositoryFactory<? extends EnvironmentRepository, ? extends EnvironmentRepositoryProperties>>) CompositeUtils
							.getFactoryClass(beanFactory, factoryName);
					Type[] environmentRepositoryFactoryTypeParams = CompositeUtils
							.getEnvironmentRepositoryFactoryTypeParams(factoryClass);
					Class<? extends EnvironmentRepositoryProperties> repoClass = (Class<? extends EnvironmentRepositoryProperties>) environmentRepositoryFactoryTypeParams[0];
					Class<? extends EnvironmentRepositoryProperties> propertiesClass = (Class<? extends EnvironmentRepositoryProperties>) environmentRepositoryFactoryTypeParams[1];
					hintClasses.addAll(Set.of(repoClass, propertiesClass, factoryClass));
					String indexString = matcher.group(3);
					int index = Integer.parseInt(indexString);
					String environmentConfigurationPropertyName = String
							.format("spring.cloud.config.server.composite[%d]", index);
					method.addStatement("$T properties$L = binder.bindOrCreate($S, $T.class)",
							EnvironmentRepositoryProperties.class, index, environmentConfigurationPropertyName,
							propertiesClass);
					method.addStatement("properties$L.setOrder($L)", index, index + 1);
					method.addStatement(
							"$T propertiesDefinition$L = "
									+ "$T.genericBeanDefinition($T.class, () -> properties$L).getBeanDefinition()",
							AbstractBeanDefinition.class, index, BeanDefinitionBuilder.class,
							EnvironmentRepositoryProperties.class, index);
					method.addStatement("beanFactory.registerBeanDefinition($S, propertiesDefinition$L)", beanName,
							index);
					method.addStatement(
							"""
									$T repoBeanDefinition$L = $T.genericBeanDefinition($T.class).setFactoryMethodOnBean("build", $S)
										.addConstructorArgValue(properties$L).getBeanDefinition()""",
							AbstractBeanDefinition.class, index, BeanDefinitionBuilder.class,
							EnvironmentRepository.class, factoryName, index);
					method.addStatement("beanFactory.registerBeanDefinition($S, repoBeanDefinition$L)", repoBeanName,
							index);
				}
			});
		}

		// TODO: switch to reflectionHints.registerForInterfaces(...) after upgrading to
		// Framework 6.2.0
		// from Spring Framework BeanRegistrationsAotContribution
		private void introspectPublicMethodsOnAllInterfaces(ReflectionHints hints, Class<?> type) {
			Class<?> currentClass = type;
			while (currentClass != null && currentClass != Object.class) {
				for (Class<?> interfaceType : currentClass.getInterfaces()) {
					if (!ClassUtils.isJavaLanguageInterface(interfaceType)) {
						hints.registerType(interfaceType, MemberCategory.INTROSPECT_PUBLIC_METHODS);
						introspectPublicMethodsOnAllInterfaces(hints, interfaceType);
					}
				}
				currentClass = currentClass.getSuperclass();
			}
		}

	}

}
