/*
 * Copyright 2002-2009 the original author or authors.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.springframework.cloud.context.scope;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.Scope;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.cloud.context.config.BeanLifecycleDecorator;
import org.springframework.cloud.context.config.BeanLifecycleDecorator.Context;
import org.springframework.cloud.context.config.StandardBeanLifecycleDecorator;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParseException;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.util.StringUtils;

/**
 * <p>
 * A generic Scope implementation.
 * </p>
 * 
 * @author Dave Syer
 * 
 * @since 3.1
 * 
 */
public class GenericScope implements Scope, BeanFactoryPostProcessor, DisposableBean {

	private static final Log logger = LogFactory.getLog(GenericScope.class);

	public static final String SCOPED_TARGET_PREFIX = "scopedTarget.";

	private BeanLifecycleWrapperCache cache = new BeanLifecycleWrapperCache(
			new StandardScopeCache());

	private String name = "generic";

	private boolean proxyTargetClass = true;

	private ConfigurableListableBeanFactory beanFactory;

	private StandardEvaluationContext evaluationContext;

	private String id;

	private BeanLifecycleDecorator<?> lifecycle;

	/**
	 * Manual override for the serialization id that will be used to identify the bean
	 * factory. The default is a unique key based on the bean names in the bean factory.
	 * 
	 * @param id the id to set
	 */
	public void setId(String id) {
		this.id = id;
	}

	/**
	 * The name of this scope. Default "refresh".
	 * 
	 * @param name the name value to set
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * Flag to indicate that proxies should be created for the concrete type, not just the
	 * interfaces, of the scoped beans.
	 * 
	 * @param proxyTargetClass the flag value to set
	 */
	public void setProxyTargetClass(boolean proxyTargetClass) {
		this.proxyTargetClass = proxyTargetClass;
	}

	/**
	 * The cache implementation to use for bean instances in this scope.
	 * 
	 * @param cache the cache to use
	 */
	public void setScopeCache(ScopeCache cache) {
		this.cache = new BeanLifecycleWrapperCache(cache);
	}

	/**
	 * Helper to manage the creation and destruction of beans.
	 * 
	 * @param lifecycle the bean lifecycle to set
	 */
	public void setBeanLifecycleManager(BeanLifecycleDecorator<?> lifecycle) {
		this.lifecycle = lifecycle;
	}

	public void destroy() {
		List<Throwable> errors = new ArrayList<Throwable>();
		Collection<BeanLifecycleWrapper> wrappers = cache.clear();
		for (BeanLifecycleWrapper wrapper : wrappers) {
			try {
				wrapper.destroy();
			}
			catch (RuntimeException e) {
				errors.add(e);
			}
		}
		if (!errors.isEmpty()) {
			throw wrapIfNecessary(errors.get(0));
		}
	}

	protected void destroy(String name) {
		BeanLifecycleWrapper wrapper = cache.remove(name);
		if (wrapper != null) {
			wrapper.destroy();
		}
	}

	public Object get(String name, ObjectFactory<?> objectFactory) {
		if (lifecycle == null) {
			lifecycle = new StandardBeanLifecycleDecorator(proxyTargetClass);
		}
		BeanLifecycleWrapper value = cache.put(name, new BeanLifecycleWrapper(name,
				objectFactory, lifecycle));
		return value.getBean();
	}

	public String getConversationId() {
		return name;
	}

	public void registerDestructionCallback(String name, Runnable callback) {
		BeanLifecycleWrapper value = cache.get(name);
		if (value == null) {
			return;
		}
		value.setDestroyCallback(callback);
	}

	public Object remove(String name) {
		BeanLifecycleWrapper value = cache.remove(name);
		if (value == null) {
			return null;
		}
		// Someone might have added another object with the same key, but we
		// keep the method contract by removing the
		// value we found anyway
		return value.getBean();
	}

	public Object resolveContextualObject(String key) {
		Expression expression = parseExpression(key);
		return expression.getValue(evaluationContext, beanFactory);
	}

	private Expression parseExpression(String input) {
		if (StringUtils.hasText(input)) {
			ExpressionParser parser = new SpelExpressionParser();
			try {
				return parser.parseExpression(input);
			}
			catch (ParseException e) {
				throw new IllegalArgumentException("Cannot parse expression: " + input, e);
			}

		}
		else {
			return null;
		}
	}

	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory)
			throws BeansException {
		beanFactory.registerScope(name, this);
		setSerializationId(beanFactory);
	}

	/**
	 * If the bean factory is a DefaultListableBeanFactory then it can serialize scoped
	 * beans and deserialize them in another context (even in another JVM), as long as the
	 * ids of the bean factories match. This method sets up the serialization id to be
	 * either the id provided to the scope instance, or if that is null, a hash of all the
	 * bean names.
	 * 
	 * @param beanFactory the bean factory to configure
	 */
	private void setSerializationId(ConfigurableListableBeanFactory beanFactory) {

		if (beanFactory instanceof DefaultListableBeanFactory) {

			String id = this.id;
			if (id == null) {
				String names = Arrays.asList(beanFactory.getBeanDefinitionNames())
						.toString();
				logger.debug("Generating bean factory id from names: " + names);
				id = UUID.nameUUIDFromBytes(names.getBytes()).toString();
			}

			logger.info("BeanFactory id=" + id);
			((DefaultListableBeanFactory) beanFactory).setSerializationId(id);

		}
		else {
			logger.warn("BeanFactory was not a DefaultListableBeanFactory, so RefreshScope beans "
					+ "cannot be serialized reliably and passed to a remote JVM.");
		}

	}

	static RuntimeException wrapIfNecessary(Throwable throwable) {
		if (throwable instanceof RuntimeException) {
			return (RuntimeException) throwable;
		}
		if (throwable instanceof Error) {
			throw (Error) throwable;
		}
		return new IllegalStateException(throwable);
	}

	private static class BeanLifecycleWrapperCache {

		private final ScopeCache cache;

		public BeanLifecycleWrapperCache(ScopeCache cache) {
			this.cache = cache;
		}

		public BeanLifecycleWrapper remove(String name) {
			return (BeanLifecycleWrapper) cache.remove(name);
		}

		public Collection<BeanLifecycleWrapper> clear() {
			Collection<Object> values = cache.clear();
			Collection<BeanLifecycleWrapper> wrappers = new LinkedHashSet<BeanLifecycleWrapper>();
			for (Object object : values) {
				wrappers.add((BeanLifecycleWrapper) object);
			}
			return wrappers;
		}

		public BeanLifecycleWrapper get(String name) {
			return (BeanLifecycleWrapper) cache.get(name);
		}

		public BeanLifecycleWrapper put(String name, BeanLifecycleWrapper value) {
			return (BeanLifecycleWrapper) cache.put(name, (Object) value);
		}

	}

	/**
	 * Wrapper for a bean instance and any destruction callback (DisposableBean etc.) that
	 * is registered for it. Also decorates the bean to optionally guard it from
	 * concurrent access (for instance).
	 * 
	 * @author Dave Syer
	 * 
	 */
	private static class BeanLifecycleWrapper {

		private Object bean;

		private Context<?> context;

		private final String name;

		@SuppressWarnings("rawtypes")
		private final BeanLifecycleDecorator lifecycle;

		private final ObjectFactory<?> objectFactory;

		@SuppressWarnings("rawtypes")
		public BeanLifecycleWrapper(String name, ObjectFactory<?> objectFactory,
				BeanLifecycleDecorator lifecycle) {
			this.name = name;
			this.objectFactory = objectFactory;
			this.lifecycle = lifecycle;
		}

		public void setDestroyCallback(Runnable callback) {
			this.context = lifecycle.decorateDestructionCallback(callback);
		}

		@SuppressWarnings("unchecked")
		public Object getBean() {
			if (bean == null) {
				bean = lifecycle.decorateBean(objectFactory.getObject(), context);
			}
			return bean;
		}

		public void destroy() {
			if (context == null) {
				return;
			}
			Runnable callback = context.getCallback();
			if (callback != null) {
				callback.run();
			}
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((name == null) ? 0 : name.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			BeanLifecycleWrapper other = (BeanLifecycleWrapper) obj;
			if (name == null) {
				if (other.name != null)
					return false;
			}
			else if (!name.equals(other.name))
				return false;
			return true;
		}

	}

}
