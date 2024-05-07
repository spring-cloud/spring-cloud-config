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

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.springframework.aot.generate.GeneratedFiles;
import org.springframework.aot.hint.ReflectionHints;
import org.springframework.aot.test.generate.TestGenerationContext;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.boot.web.servlet.context.AnnotationConfigServletWebApplicationContext;
import org.springframework.cloud.config.server.environment.MultipleJGitEnvironmentProperties;
import org.springframework.cloud.config.server.environment.MultipleJGitEnvironmentRepository;
import org.springframework.cloud.config.server.environment.MultipleJGitEnvironmentRepositoryFactory;
import org.springframework.cloud.config.server.environment.SvnEnvironmentRepositoryFactory;
import org.springframework.cloud.config.server.environment.SvnKitEnvironmentProperties;
import org.springframework.cloud.config.server.environment.SvnKitEnvironmentRepository;
import org.springframework.cloud.config.server.test.TestConfigServerApplication;
import org.springframework.context.aot.ApplicationContextAotGenerator;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.javapoet.ClassName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link CompositeEnvironmentBeanFactoryInitializationAotProcessor}.
 *
 * @author Olga Maciaszek-Sharma
 */
class CompositeEnvironmentBeanFactoryInitializationAotProcessorTests {

	@Test
	void shouldCreateCompositeEnvironmentBeansRegistrationContribution() {
		Set<Class<?>> hintClasses = Set.of(MultipleJGitEnvironmentRepository.class, SvnKitEnvironmentRepository.class,
				SvnKitEnvironmentProperties.class, MultipleJGitEnvironmentRepositoryFactory.class,
				SvnEnvironmentRepositoryFactory.class, MultipleJGitEnvironmentProperties.class);
		new WebApplicationContextRunner(AnnotationConfigServletWebApplicationContext::new)
				.withUserConfiguration(TestConfigServerApplication.class)
				.withPropertyValues("spring.cloud.refresh.enabled=false",
						"spring.cloud.config.server.composite[0].uri:file:./target/repos/config-repo",
						"spring.cloud.config.server.composite[0].type:git",
						"spring.cloud.config.server.composite[1].uri:file:///./target/repos/svn-config-repo",
						"spring.cloud.config.server.composite[1].type:svn", "spring.profiles.active:test,composite")
				.prepare(context -> {
					TestGenerationContext generationContext = new TestGenerationContext(TestTarget.class);
					ClassName className = new ApplicationContextAotGenerator().processAheadOfTime(
							(GenericApplicationContext) context.getSourceApplicationContext(), generationContext);
					generationContext.writeGeneratedContent();
					Optional<String> source = getGeneratedSource(generationContext, className.simpleName());
					assertThat(source).isNotEmpty();
					assertThat(source.get()).contains(
							"beanFactory.registerBeanDefinition(\"git-env-repo-properties0\", propertiesDefinition0);",
							"beanFactory.registerBeanDefinition(\"git-env-repo0\", repoBeanDefinition0);",
							"beanFactory.registerBeanDefinition(\"svn-env-repo-properties1\", propertiesDefinition1);",
							"beanFactory.registerBeanDefinition(\"svn-env-repo1\", repoBeanDefinition1);");
					ReflectionHints hints = generationContext.getRuntimeHints().reflection();
					hintClasses.forEach(clazz -> assertThat(hints.getTypeHint(clazz)).isNotNull());
				});
	}

	private static Optional<String> getGeneratedSource(TestGenerationContext generationContext,
			String simpleClassName) {
		return generationContext.getGeneratedFiles().getGeneratedFiles(GeneratedFiles.Kind.SOURCE).values().stream()
				.map(inputStreamSource -> {
					try {
						return new String(inputStreamSource.getInputStream().readAllBytes(), Charset.defaultCharset());
					}
					catch (IOException e) {
						throw new RuntimeException(e);
					}
				}).filter(source -> source.contains(simpleClassName)).findAny();
	}

	static class TestTarget {

	}

}
