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
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionMessage;
import org.springframework.boot.autoconfigure.condition.ConditionOutcome;
import org.springframework.boot.autoconfigure.condition.SpringBootCondition;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.cloud.config.server.environment.SearchPathLocator;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * @author Dylan Roberts
 */
public class OnSearchPathLocatorPresent extends SpringBootCondition {

	@Override
	public ConditionOutcome getMatchOutcome(ConditionContext context,
			AnnotatedTypeMetadata metadata) {
		ConfigurableListableBeanFactory beanFactory = context.getBeanFactory();
		List<String> types = CompositeUtils
				.getCompositeTypeList(context.getEnvironment());

		// get EnvironmentRepository types from registered factories
		List<Class<? extends EnvironmentRepository>> repositoryTypes = new ArrayList<>();
		for (String type : types) {
			String factoryName = CompositeUtils.getFactoryName(type, beanFactory);
			Type[] actualTypeArguments = CompositeUtils
					.getEnvironmentRepositoryFactoryTypeParams(beanFactory, factoryName);
			Class<? extends EnvironmentRepository> repositoryType;
			repositoryType = (Class<? extends EnvironmentRepository>) actualTypeArguments[0];
			repositoryTypes.add(repositoryType);
		}

		boolean required = metadata
				.isAnnotated(ConditionalOnSearchPathLocator.class.getName());
		boolean foundSearchPathLocator = repositoryTypes.stream()
				.anyMatch(SearchPathLocator.class::isAssignableFrom);
		if (required && !foundSearchPathLocator) {
			return ConditionOutcome.noMatch(
					ConditionMessage.forCondition(ConditionalOnSearchPathLocator.class)
							.notAvailable(SearchPathLocator.class.getTypeName()));
		}
		if (!required && foundSearchPathLocator) {
			return ConditionOutcome.noMatch(ConditionMessage
					.forCondition(ConditionalOnMissingSearchPathLocator.class)
					.available(SearchPathLocator.class.getTypeName()));
		}
		return ConditionOutcome.match();
	}

}
