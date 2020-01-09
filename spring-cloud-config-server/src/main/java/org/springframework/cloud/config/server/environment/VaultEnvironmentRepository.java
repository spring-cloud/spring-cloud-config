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

package org.springframework.cloud.config.server.environment;

import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.client.RestTemplate;

/**
 * @author Spencer Gibb
 * @author Mark Paluch
 * @author Haroun Pacquee
 * @author Haytham Mohamed
 * @author Scott Frederick
 * @deprecated Prefer
 * {@link org.springframework.cloud.config.server.environment.vault.SpringVaultEnvironmentRepository}
 * instead of this environment repository implementation. The alternative implementation
 * supports additional features including more authentication options, support for several
 * underlying HTTP client libraries, and better SSL configuration.
 */
@Validated
public class VaultEnvironmentRepository extends AbstractVaultEnvironmentRepository {

	/**
	 * Vault token header name.
	 */
	private static final String VAULT_TOKEN = "X-Vault-Token";

	/**
	 * Vault namespace header name.
	 */
	static final String VAULT_NAMESPACE = "X-Vault-Namespace";

	/** Vault host. Defaults to 127.0.0.1. */
	@NotEmpty
	private String host;

	/** Vault port. Defaults to 8200. */
	@Min(1)
	@Max(65535)
	private int port;

	/** Vault scheme. Defaults to http. */
	private String scheme;

	/** Vault backend. Defaults to secret. */
	@NotEmpty
	private String backend;

	/** Vault Namespace header value. */
	private String namespace;

	private VaultKvAccessStrategy accessStrategy;

	private final ConfigTokenProvider tokenProvider;

	public VaultEnvironmentRepository(ObjectProvider<HttpServletRequest> request,
			EnvironmentWatch watch, RestTemplate rest,
			VaultEnvironmentProperties properties) {
		this(request, watch, rest, properties,
				new HttpRequestConfigTokenProvider(request));
	}

	public VaultEnvironmentRepository(ObjectProvider<HttpServletRequest> request,
			EnvironmentWatch watch, RestTemplate rest,
			VaultEnvironmentProperties properties, ConfigTokenProvider tokenProvider) {
		super(request, watch, properties);
		this.tokenProvider = tokenProvider;
		this.backend = properties.getBackend();
		this.host = properties.getHost();
		this.port = properties.getPort();
		this.scheme = properties.getScheme();
		this.namespace = properties.getNamespace();

		String baseUrl = String.format("%s://%s:%s", this.scheme, this.host, this.port);

		this.accessStrategy = VaultKvAccessStrategyFactory.forVersion(rest, baseUrl,
				properties.getKvVersion());
	}

	/* for testing */ void setAccessStrategy(VaultKvAccessStrategy accessStrategy) {
		this.accessStrategy = accessStrategy;
	}

	@Override
	protected String read(String key) {
		HttpHeaders headers = new HttpHeaders();
		headers.add(VAULT_TOKEN, getToken());
		if (StringUtils.hasText(this.namespace)) {
			headers.add(VAULT_NAMESPACE, this.namespace);
		}

		return this.accessStrategy.getData(headers, this.backend, key);
	}

	private String getToken() {
		String token = tokenProvider.getToken();
		if (!StringUtils.hasLength(token)) {
			throw new IllegalArgumentException(
					"A Vault token must be supplied by a token provider");
		}
		return token;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public void setScheme(String scheme) {
		this.scheme = scheme;
	}

	public void setBackend(String backend) {
		this.backend = backend;
	}

	public void setNamespace(String namespace) {
		this.namespace = namespace;
	}

}
