/*
 * Copyright 2002-2011 the original author or authors.
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

package org.springframework.cloud.context.scope.thread;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.cloud.context.scope.ScopeCache;

/**
 * @author Dave Syer
 * 
 */
public class ThreadLocalScopeCache implements ScopeCache {

	private ThreadLocal<ConcurrentMap<String, Object>> data = new ThreadLocal<ConcurrentMap<String, Object>>() {
		protected ConcurrentMap<String, Object> initialValue() {
			return new ConcurrentHashMap<String, Object>();
		}
	};

	public Object remove(String name) {
		return data.get().remove(name);
	}

	public Collection<Object> clear() {
		ConcurrentMap<String, Object> map = data.get();
		Collection<Object> values = new ArrayList<Object>(map.values());
		map.clear();
		return values;
	}

	public Object get(String name) {
		return data.get().get(name);
	}

	public Object put(String name, Object value) {
		Object result = data.get().putIfAbsent(name, value);
		if (result!=null) {
			return result;
		}
		return value;
	}

}
