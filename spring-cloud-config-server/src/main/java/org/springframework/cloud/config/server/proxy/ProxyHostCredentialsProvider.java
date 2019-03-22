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

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;

/**
 * @author Dylan Roberts
 */
public class ProxyHostCredentialsProvider extends BasicCredentialsProvider {

	public ProxyHostCredentialsProvider(ProxyHostProperties... proxyHostProperties) {

		for (ProxyHostProperties proxy : proxyHostProperties) {

			if (proxy != null && proxy.getUsername() != null
					&& proxy.getPassword() != null) {
				AuthScope authscope = new AuthScope(proxy.getHost(), proxy.getPort());
				UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(
						proxy.getUsername(), proxy.getPassword());
				setCredentials(authscope, credentials);
			}
		}
	}

}
