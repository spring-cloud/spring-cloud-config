/*
 * Copyright 2018 the original author or authors.
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
package org.springframework.cloud.config.server.composite;

import java.lang.reflect.AnnotatedType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.cloud.config.server.environment.EnvironmentRepositoryFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.type.MethodMetadata;

/**
 * @author Dylan Roberts
 */
public class CompositeUtils {

    public static List<String> getCompositeTypeList(Environment environment) {
        List<String> repoTypes = new ArrayList<>();
        for (int i = 0; i < Integer.MAX_VALUE; i++) {
            String property = String.format("spring.cloud.config.server.composite[%d].type", i);
            String type = environment.getProperty(property);
            if (type != null) {
                repoTypes.add(type);
                continue;
            }
            break;
        }
        return repoTypes;
    }

    public static String getFactoryName(String type, ConfigurableListableBeanFactory beanFactory) {
        String[] factoryNames = BeanFactoryUtils
                .beanNamesForTypeIncludingAncestors(beanFactory, EnvironmentRepositoryFactory.class, true, false);
        return Arrays.stream(factoryNames).filter(n -> n.startsWith(type)).findFirst().orElse(null);
    }

    public static Type[] getEnvironmentRepositoryFactoryTypeParams(ConfigurableListableBeanFactory beanFactory, String factoryName) {
        MethodMetadata methodMetadata = (MethodMetadata) beanFactory.getBeanDefinition(factoryName).getSource();
        Class<?> factoryClass = null;
        try {
            factoryClass = Class.forName(methodMetadata.getReturnTypeName());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
        Optional<AnnotatedType> annotatedFactoryType = Arrays.stream(factoryClass.getAnnotatedInterfaces())
                .filter(i -> {
                    ParameterizedType parameterizedType = (ParameterizedType) i.getType();
                    return parameterizedType.getRawType().equals(EnvironmentRepositoryFactory.class);
                }).findFirst();
        ParameterizedType factoryParameterizedType = (ParameterizedType) annotatedFactoryType
                .orElse(factoryClass.getAnnotatedSuperclass()).getType();
        return factoryParameterizedType.getActualTypeArguments();
    }

}
