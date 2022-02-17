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

package org.springframework.cloud.config.server.proxy;

import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.impl.conn.DefaultRoutePlanner;
import org.apache.http.protocol.HttpContext;

/**
 * @author Dylan Roberts
 */
public class SchemeBasedRoutePlanner extends DefaultRoutePlanner {

	private final HttpHost httpsProxy;

	private final HttpHost defaultSchemeProxy;

	public SchemeBasedRoutePlanner(ProxyHostProperties httpsProxy, ProxyHostProperties httpProxy) {
		super(null);
		this.httpsProxy = buildProxy(httpsProxy, "https");
		this.defaultSchemeProxy = buildProxy(httpProxy, HttpHost.DEFAULT_SCHEME_NAME);
	}

	@Override
	protected HttpHost determineProxy(HttpHost target, HttpRequest request, HttpContext context) {
		return "https".equals(target.getSchemeName()) ? determineProxy(this.httpsProxy, this.defaultSchemeProxy)
				: determineProxy(this.defaultSchemeProxy, this.httpsProxy);
	}

	private HttpHost determineProxy(HttpHost proxy, HttpHost fallbackProxy) {
		return proxy != null ? proxy : fallbackProxy;
	}

	private HttpHost buildProxy(ProxyHostProperties properties, String scheme) {
		if (properties == null) {
			return null;
		}
		return new HttpHost(properties.getHost(), properties.getPort(), scheme);
	}

}
