/*
 * Copyright 2013-2017 the original author or authors.
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
package org.springframework.cloud.config.server.environment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import javax.servlet.http.HttpServletRequest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRawValue;
import com.fasterxml.jackson.databind.JsonNode;
import org.hibernate.validator.constraints.NotEmpty;
import org.hibernate.validator.constraints.Range;

import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.core.Ordered;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import static org.springframework.cloud.config.client.ConfigClientProperties.STATE_HEADER;
import static org.springframework.cloud.config.client.ConfigClientProperties.TOKEN_HEADER;

/**
 * @author Spencer Gibb
 * @author Mark Paluch
 */
@ConfigurationProperties("spring.cloud.config.server.vault")
public class VaultEnvironmentRepository implements EnvironmentRepository, Ordered {

	public static final String VAULT_TOKEN = "X-Vault-Token";

	/** Vault host. Defaults to 127.0.0.1. */
	@NotEmpty
	private String host = "127.0.0.1";

	/** Vault port. Defaults to 8200. */
	@Range(min = 1, max = 65535)
	private int port = 8200;

	/** Vault scheme. Defaults to http. */
	private String scheme = "http";

	/** Vault backend. Defaults to secret. */
	@NotEmpty
	private String backend = "secret";

	/** The key in vault shared by all applications. Defaults to application. Set to empty to disable. */
	private String defaultKey = "application";

	/** Vault profile separator. Defaults to comma. */
	@NotEmpty
	private String profileSeparator = ",";

	private int order = Ordered.LOWEST_PRECEDENCE;

	private RestTemplate rest;

	//TODO: move to watchState:String on findOne?
	private HttpServletRequest request;

	private EnvironmentWatch watch;

	public VaultEnvironmentRepository(HttpServletRequest request, EnvironmentWatch watch, RestTemplate rest) {
		this.request = request;
		this.watch = watch;
		this.rest = rest;
	}

	@Override
	public Environment findOne(String application, String profile, String label) {

		String state = request.getHeader(STATE_HEADER);
		String newState = this.watch.watch(state);

		String[] profiles = StringUtils.commaDelimitedListToStringArray(profile);
		List<String> scrubbedProfiles = scrubProfiles(profiles);

		List<String> keys = findKeys(application, scrubbedProfiles);

		Environment environment = new Environment(application, profiles, label, null, newState);

		for (String key : keys) {
			// read raw 'data' key from vault
			String data = read(key);
			if (data != null) {
				// data is in json format of which, yaml is a superset, so parse
				final YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
				yaml.setResources(new ByteArrayResource(data.getBytes()));
				Properties properties = yaml.getObject();

				if (!properties.isEmpty()) {
					environment.add(new PropertySource("vault:" + key, properties));
				}
			}
		}

		return environment;
	}

	private List<String> findKeys(String application, List<String> profiles) {
		List<String> keys = new ArrayList<>();

		if (StringUtils.hasText(this.defaultKey)) {
			keys.add(this.defaultKey);
			addProfiles(keys, this.defaultKey, profiles);
		}

		keys.add(application);
		addProfiles(keys, application, profiles);

		Collections.reverse(keys);
		return keys;
	}

	private List<String> scrubProfiles(String[] profiles) {
		List<String> scrubbedProfiles = new ArrayList<>(Arrays.asList(profiles));
		if (scrubbedProfiles.contains("default")) {
			scrubbedProfiles.remove("default");
		}
		return scrubbedProfiles;
	}

	private void addProfiles(List<String> contexts, String baseContext,
							 List<String> profiles) {
		for (String profile : profiles) {
			contexts.add(baseContext + this.profileSeparator + profile);
		}
	}

	String read(String key) {
		String url = String.format("%s://%s:%s/v1/{backend}/{key}", this.scheme, this.host, this.port);

		HttpHeaders headers = new HttpHeaders();

		String token = request.getHeader(TOKEN_HEADER);
		if (!StringUtils.hasLength(token)) {
			throw new IllegalArgumentException("Missing required header: " + TOKEN_HEADER);
		}
		headers.add(VAULT_TOKEN, token);
		try {
			ResponseEntity<VaultResponse> response = this.rest.exchange(url, HttpMethod.GET, new HttpEntity<>(headers),
					VaultResponse.class, this.backend, key);

			HttpStatus status = response.getStatusCode();
			if (status == HttpStatus.OK) {
				return response.getBody().getData();
			}
		} catch (HttpStatusCodeException e) {
			if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
				return null;
			}
			throw e;
		}

		return null;
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

	public void setDefaultKey(String defaultKey) {
		this.defaultKey = defaultKey;
	}

	public void setProfileSeparator(String profileSeparator) {
		this.profileSeparator = profileSeparator;
	}

	public void setOrder(int order) {
		this.order = order;
	}

	@Override
	public int getOrder() {
		return order;
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	static class VaultResponse {

		private String auth;

		private Object data;

		@JsonProperty("lease_duration")
		private long leaseDuration;

		@JsonProperty("lease_id")
		private String leaseId;

		private boolean renewable;

		public VaultResponse() {
		}

		public String getAuth() {
			return auth;
		}

		public void setAuth(String auth) {
			this.auth = auth;
		}

		@JsonRawValue
		public String getData() {
			return data == null ? null : data.toString();
		}

		public void setData(JsonNode data) {
			this.data = data;
		}

		public long getLeaseDuration() {
			return leaseDuration;
		}

		public void setLeaseDuration(long leaseDuration) {
			this.leaseDuration = leaseDuration;
		}

		public String getLeaseId() {
			return leaseId;
		}

		public void setLeaseId(String leaseId) {
			this.leaseId = leaseId;
		}

		public boolean isRenewable() {
			return renewable;
		}

		public void setRenewable(boolean renewable) {
			this.renewable = renewable;
		}
	}
}
