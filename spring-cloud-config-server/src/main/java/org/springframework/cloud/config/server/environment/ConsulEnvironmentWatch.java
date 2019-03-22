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

import java.util.ArrayList;
import java.util.List;

import javax.validation.constraints.NotNull;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

/**
 * @author Spencer Gibb
 */
@ConfigurationProperties("spring.cloud.config.server.consul.watch")
public class ConsulEnvironmentWatch implements EnvironmentWatch {

	/**
	 * Consul index header name.
	 */
	public static final String CONSUL_INDEX = "X-Consul-Index";

	/**
	 * Consul token header name.
	 */
	public static final String CONSUL_TOKEN = "X-Consul-Token";

	/**
	 * Response types.
	 */
	public static final ParameterizedTypeReference<List<String>> RESPONSE_TYPE = new ParameterizedTypeReference<List<String>>() {
	};

	private static final String WATCH_URL = "{scheme}://{host}:{port}/v1/kv/{path}?keys&recurse&wait={wait}&index={index}";

	private static Log LOG = LogFactory.getLog(ConsulEnvironmentWatch.class);

	private RestTemplate restTemplate = new RestTemplate();

	/** Consul agent scheme. Defaults to 'http'. */
	@NotNull
	private String scheme = "http";

	/** Consul agent hostname. Defaults to 'localhost'. */
	@NotNull
	private String host = "localhost";

	/** Consul agent port. Defaults to '8500'. */
	@NotNull
	private int port = 8500;

	/** Path to watch in consul key/value store. */
	@NotNull
	private String path;

	/** Consul wait value (eg, 3m or 30s). */
	@NotNull
	private String wait = "3m";

	/** Consul ACL token. */
	private String token;

	@Override
	public String watch(String state) {
		ArrayList<String> params = new ArrayList<>();
		params.add(this.scheme);
		params.add(this.host);
		params.add(String.valueOf(this.port));
		params.add(this.path);
		params.add(this.wait);
		params.add(StringUtils.hasText(state) ? state : "");

		try {
			HttpHeaders headers = new HttpHeaders();
			if (StringUtils.hasText(this.token)) {
				headers.add(CONSUL_TOKEN, this.token);
			}
			HttpEntity<Object> request = new HttpEntity<>(headers);
			ResponseEntity<List<String>> response = this.restTemplate.exchange(WATCH_URL,
					HttpMethod.GET, request, RESPONSE_TYPE, params.toArray());

			if (response.getStatusCode().is2xxSuccessful()) {
				String consulIndex = response.getHeaders().getFirst(CONSUL_INDEX);
				return consulIndex;
			}
		}
		catch (HttpStatusCodeException e) {
			if (!e.getStatusCode().equals(HttpStatus.NOT_FOUND)) {
				LOG.error("Unable to watch consul path " + this.path, e);
				return null;
			}
		}
		// TODO: error handling?

		return null;
	}

	public void setRestTemplate(RestTemplate restTemplate) {
		this.restTemplate = restTemplate;
	}

	public void setScheme(String scheme) {
		this.scheme = scheme;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public void setPath(String path) {
		this.path = path;
		if (this.path.startsWith("/")) {
			this.path = this.path.substring(1);
		}
	}

	public void setWait(String wait) {
		this.wait = wait;
	}

	public void setToken(String token) {
		this.token = token;
	}

}
