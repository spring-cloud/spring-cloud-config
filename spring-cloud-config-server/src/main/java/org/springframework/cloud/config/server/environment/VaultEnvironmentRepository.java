/*
 * Copyright 2013-2016 the original author or authors.
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.servlet.http.HttpServletRequest;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.hibernate.validator.constraints.NotEmpty;
import org.hibernate.validator.constraints.Range;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.vault.client.VaultClient;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.client.VaultException;
import org.springframework.vault.client.VaultResponseEntity;
import org.springframework.vault.support.VaultResponseSupport;
import org.springframework.vault.support.VaultToken;
import org.springframework.web.client.RestTemplate;

import static org.springframework.cloud.config.client.ConfigClientProperties.STATE_HEADER;
import static org.springframework.cloud.config.client.ConfigClientProperties.TOKEN_HEADER;

/**
 * @author Spencer Gibb
 * @author Mark Paluch
 */
@ConfigurationProperties("spring.cloud.config.server.vault")
public class VaultEnvironmentRepository implements EnvironmentRepository, InitializingBean {

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

	private RestTemplate rest;

	//TODO: move to watchState:String on findOne?
	private HttpServletRequest request;

	private EnvironmentWatch watch;
	private VaultClient client;

	public VaultEnvironmentRepository(HttpServletRequest request, EnvironmentWatch watch, RestTemplate rest) {
		this.request = request;
		this.watch = watch;
		this.rest = rest;
	}

	@Override
	public void afterPropertiesSet() {
		this.client = new VaultClient(rest, getVaultEndpoint());
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

		String token = request.getHeader(TOKEN_HEADER);
		if (!StringUtils.hasLength(token)) {
			throw new IllegalArgumentException("Missing required header: " + TOKEN_HEADER);
		}

		VaultToken vaultToken = VaultToken.of(token);
		VaultResponseEntity<VaultResponse> response = client.exchange("{backend}/{key}", HttpMethod.GET,
				new HttpEntity<>(VaultClient.createHeaders(vaultToken)), VaultResponse.class, getUriVariables(key));

		HttpStatus status = response.getStatusCode();

		if (status == HttpStatus.OK) {

			JsonNode data = response.getBody().getData();
			return data != null ? data.toString() : null;
		}

		if (status == HttpStatus.NOT_FOUND) {
			return null;
		}

		throw new VaultException(response.getMessage());
	}

	private Map<String, String> getUriVariables(String key) {
		Map<String, String> uriVariables = new HashMap<>(2, 1);
		uriVariables.put("backend", backend);
		uriVariables.put("key", key);
		return uriVariables;
	}

	private VaultEndpoint getVaultEndpoint() {

		VaultEndpoint vaultEndpoint = new VaultEndpoint();

		vaultEndpoint.setScheme(scheme);
		vaultEndpoint.setHost(host);
		vaultEndpoint.setPort(port);

		return vaultEndpoint;
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

	@JsonIgnoreProperties(ignoreUnknown = true)
	static class VaultResponse extends VaultResponseSupport<JsonNode> {
	}
}
