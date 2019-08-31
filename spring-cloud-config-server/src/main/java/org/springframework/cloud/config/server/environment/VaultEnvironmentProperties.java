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

package org.springframework.cloud.config.server.environment;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.config.server.proxy.ProxyHostProperties;
import org.springframework.cloud.config.server.support.HttpEnvironmentRepositoryProperties;
import org.springframework.core.Ordered;

/**
 * @author Dylan Roberts
 * @author Haroun Pacquee
 */
@ConfigurationProperties("spring.cloud.config.server.vault")
public class VaultEnvironmentProperties implements HttpEnvironmentRepositoryProperties {

	/** Vault host. Defaults to 127.0.0.1. */
	private String host = "127.0.0.1";

	/** Vault port. Defaults to 8200. */
	private Integer port = 8200;

	/** Vault scheme. Defaults to http. */
	private String scheme = "http";

	/** Timeout (in seconds) for obtaining HTTP connection, defaults to 5 seconds. */
	private int timeout = 5;

	/** Vault backend. Defaults to secret. */
	private String backend = "secret";

	/**
	 * The key in vault shared by all applications. Defaults to application. Set to empty
	 * to disable.
	 */
	private String defaultKey = "application";

	/** Vault profile separator. Defaults to comma. */
	private String profileSeparator = ",";

	/**
	 * Flag to indicate that SSL certificate validation should be bypassed when
	 * communicating with a repository served over an HTTPS connection.
	 */
	private boolean skipSslValidation = false;

	/**
	 * HTTP proxy configuration.
	 */
	private Map<ProxyHostProperties.ProxyForScheme, ProxyHostProperties> proxy = new HashMap<>();

	private int order = Ordered.LOWEST_PRECEDENCE;

	/**
	 * Value to indicate which version of Vault kv backend is used. Defaults to 1.
	 */
	private int kvVersion = 1;

	/**
	 * The value of the Vault X-Vault-Namespace header. Defaults to null. This a Vault
	 * Enterprise feature only.
	 */
	private String namespace;

	public String getHost() {
		return this.host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public Integer getPort() {
		return this.port;
	}

	public void setPort(Integer port) {
		this.port = port;
	}

	public String getScheme() {
		return this.scheme;
	}

	public void setScheme(String scheme) {
		this.scheme = scheme;
	}

	public String getBackend() {
		return this.backend;
	}

	public void setBackend(String backend) {
		this.backend = backend;
	}

	public String getDefaultKey() {
		return this.defaultKey;
	}

	public void setDefaultKey(String defaultKey) {
		this.defaultKey = defaultKey;
	}

	public String getProfileSeparator() {
		return this.profileSeparator;
	}

	public void setProfileSeparator(String profileSeparator) {
		this.profileSeparator = profileSeparator;
	}

	@Override
	public boolean isSkipSslValidation() {
		return this.skipSslValidation;
	}

	public void setSkipSslValidation(boolean skipSslValidation) {
		this.skipSslValidation = skipSslValidation;
	}

	@Override
	public Map<ProxyHostProperties.ProxyForScheme, ProxyHostProperties> getProxy() {
		return this.proxy;
	}

	public void setProxy(
			Map<ProxyHostProperties.ProxyForScheme, ProxyHostProperties> proxy) {
		this.proxy = proxy;
	}

	public int getOrder() {
		return this.order;
	}

	@Override
	public void setOrder(int order) {
		this.order = order;
	}

	@Override
	public int getTimeout() {
		return this.timeout;
	}

	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}

	public int getKvVersion() {
		return this.kvVersion;
	}

	public void setKvVersion(int kvVersion) {
		this.kvVersion = kvVersion;
	}

	public String getNamespace() {
		return namespace;
	}

	public void setNamespace(String namespace) {
		this.namespace = namespace;
	}

}
