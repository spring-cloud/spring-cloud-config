/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.config.server.security;

import java.util.function.BiFunction;

import org.springframework.aop.framework.ProxyFactory;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.cloud.config.server.resource.ResourceRepository;
import org.springframework.security.access.intercept.aopalliance.MethodSecurityInterceptor;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.util.ClassUtils;

/**
 * Default security enhancer implementation.
 *
 * @author ian
 */
public class DefaultSecurityEnhancer implements SecurityEnhancer {

	private final MethodSecurityInterceptor methodSecurityInterceptor;

	private final AuthorityExtractor authorityExtractor;

	public DefaultSecurityEnhancer(MethodSecurityInterceptor methodSecurityInterceptor,
			AuthorityExtractor authorityExtractor) {
		this.methodSecurityInterceptor = methodSecurityInterceptor;
		this.authorityExtractor = authorityExtractor;
	}

	@SuppressWarnings("unchecked")
	private <T> T wrap(T repository, BiFunction<T, AuthorityExtractor, T> wrapFn) {
		T wrapper = wrapFn.apply(repository, authorityExtractor);
		ProxyFactory proxyFactory = new ProxyFactory(wrapper);
		for (Class<?> intf : ClassUtils.getAllInterfaces(repository)) {
			proxyFactory.addInterface(intf);
		}
		proxyFactory.addAdvice(methodSecurityInterceptor);
		return (T) proxyFactory.getProxy();
	}

	@Override
	public EnvironmentRepository secure(EnvironmentRepository repository) {
		return wrap(repository, EnvRepoWithSecurity::create);
	}

	@Override
	public ResourceRepository secure(ResourceRepository repository) {
		return wrap(repository, ResRepoWithSecurity::create);
	}

	@Override
	public TextEncryptor secure(TextEncryptor encryptor, String application, String profiles) {
		return wrap(encryptor, (delegate, authorityExtractor) -> EncryptorWithSecurity.create(delegate, application,
				profiles, authorityExtractor));
	}

}
