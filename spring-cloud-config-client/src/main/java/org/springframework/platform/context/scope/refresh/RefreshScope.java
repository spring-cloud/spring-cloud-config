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

package org.springframework.platform.context.scope.refresh;

import java.io.Serializable;

import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.platform.context.scope.GenericScope;

/**
 * <p>
 * A Scope implementation that allows for beans to be refreshed dynamically at runtime (see {@link #refresh(String)} and
 * {@link #refreshAll()}). If a bean is refreshed then the next time the bean is accessed (i.e. a method is executed) a
 * new instance is created. All lifecycle methods are applied to the bean instances, so any destruction callbacks that
 * were registered in the bean factory are called when it is refreshed, and then the initialization callbacks are
 * invoked as normal when the new instance is created. A new bean instance is created from the original bean definition,
 * so any externalized content (property placeholders or expressions in string literals) is re-evaluated when it is
 * created.
 * </p>
 * 
 * <p>
 * Note that all beans in this scope are <em>only</em> initialized when first accessed, so the scope forces lazy
 * initialization semantics. The implementation involves creating a proxy for every bean in the scope, so there is a
 * flag {@link #setProxyTargetClass(boolean) proxyTargetClass} which controls the proxy creation, defaulting to JDK
 * dynamic proxies and therefore only exposing the interfaces implemented by a bean. If callers need access to other
 * methods then the flag needs to be set (and CGLib present on the classpath). Because this scope automatically proxies
 * all its beans, there is no need to add <code>&lt;aop:auto-proxy/&gt;</code> to any bean definitions.
 * </p>
 * 
 * <p>
 * The scoped proxy approach adopted here has a side benefit that bean instances are automatically {@link Serializable},
 * and can be sent across the wire as long as the receiver has an identical application context on the other side. To
 * ensure that the two contexts agree that they are identical they have to have the same serialization id. One will be
 * generated automatically by default from the bean names, so two contexts with the same bean names are by default able
 * to exchange beans by name. If you need to override the default id then provide an explicit {@link #setId(String) id}
 * when the Scope is declared.
 * </p>
 * 
 * @author Dave Syer
 * 
 * @since 3.1
 * 
 */
@ManagedResource
public class RefreshScope extends GenericScope {

	/**
	 * Create a scope instance and give it the default name: "refresh".
	 */
	public RefreshScope() {
		super();
		super.setName("refresh");
	}

	@ManagedOperation(description = "Dispose of the current instance of bean name provided and force a refresh on next method execution.")
	public void refresh(String name) {
		if (!name.startsWith("scopedTarget.")) {
			// User wants to refresh the bean with this name but that isn't the one in the cache...
			name = "scopedTarget." + name;
		}
		// Ensure lifecycle is finished if bean was disposable
		super.destroy(name);
	}

	@ManagedOperation(description = "Dispose of the current instance of all beans in this scope and force a refresh on next method execution.")
	public void refreshAll() {
		super.destroy();
	}

}
