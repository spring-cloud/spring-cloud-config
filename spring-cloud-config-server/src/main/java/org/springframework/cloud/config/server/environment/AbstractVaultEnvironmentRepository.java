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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotEmpty;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.core.Ordered;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import static org.springframework.cloud.config.client.ConfigClientProperties.STATE_HEADER;

/**
 * @author Spencer Gibb
 * @author Mark Paluch
 * @author Haroun Pacquee
 * @author Haytham Mohamed
 * @author Scott Frederick
 */
public abstract class AbstractVaultEnvironmentRepository implements EnvironmentRepository, Ordered {

	private static final String DEFAULT_PROFILE = "default";

	private static final String DEFAULT_LABEL = "master";

	private static final Log log = LogFactory.getLog(AbstractVaultEnvironmentRepository.class);

	// TODO: move to watchState:String on findOne?
	protected final ObjectProvider<HttpServletRequest> request;

	protected final EnvironmentWatch watch;

	/**
	 * The key in vault shared by all applications. Defaults to application. Set to empty
	 * to disable.
	 */
	protected String defaultKey;

	/**
	 * Vault profile separator. Defaults to comma.
	 */
	@NotEmpty
	protected String profileSeparator;

	protected final boolean enableLabel;

	protected int order;

	public AbstractVaultEnvironmentRepository(ObjectProvider<HttpServletRequest> request, EnvironmentWatch watch,
			VaultEnvironmentProperties properties) {
		this.defaultKey = properties.getDefaultKey();
		this.profileSeparator = properties.getProfileSeparator();
		this.enableLabel = properties.isEnableLabel();
		this.order = properties.getOrder();
		this.request = request;
		this.watch = watch;
	}

	@Override
	public Environment findOne(String application, String profile, String label) {
		if (ObjectUtils.isEmpty(profile)) {
			profile = DEFAULT_PROFILE;
		}
		if (ObjectUtils.isEmpty(label)) {
			label = DEFAULT_LABEL;
		}

		var environment = new Environment(application, split(profile), label, null, getWatchState());

		var profiles = normalize(profile, DEFAULT_PROFILE);
		var applications = normalize(application, this.defaultKey);

		for (String prof : profiles) {
			for (String app : applications) {
				var key = vaultKey(app, prof, label);
				// read raw 'data' key from vault
				String data = read(key);
				if (data != null) {
					// data is in json format of which, yaml is a superset, so parse
					var yaml = new YamlPropertiesFactoryBean();
					yaml.setResources(new ByteArrayResource(data.getBytes()));
					var properties = yaml.getObject();

					if (properties != null && !properties.isEmpty()) {
						environment.add(new PropertySource("vault:" + key, properties));
					}
				}
			}
		}

		return environment;
	}

	protected abstract String read(String key);

	private String vaultKey(String application, String profile, String label) {
		var key = application;
		// default profile should not be included in the key.
		if (!DEFAULT_PROFILE.equals(profile)) {
			key += this.profileSeparator + profile;
		}
		// append label to the key, if flag is enabled.
		if (this.enableLabel) {
			key += this.profileSeparator + label;
		}

		return key;
	}

	private String getWatchState() {
		HttpServletRequest servletRequest = this.request.getIfAvailable();
		if (servletRequest != null) {
			try {
				String state = servletRequest.getHeader(STATE_HEADER);
				return this.watch.watch(state);
			}
			catch (IllegalStateException e) {
				log.debug("Could not get state.", e);
				return null;
			}
		}
		return null;
	}

	/**
	 * Splits the comma delimited items and returns the reversed distinct items with given
	 * default item at the end.
	 */
	private List<String> normalize(String commaDelimitedItems, String defaultItem) {
		var items = Stream.concat(Stream.of(defaultItem), Arrays.stream(split(commaDelimitedItems)))
			.distinct()
			.filter(Predicate.not(ObjectUtils::isEmpty))
			.collect(Collectors.toList());

		Collections.reverse(items);
		return items;
	}

	private String[] split(String str) {
		return StringUtils.commaDelimitedListToStringArray(str);
	}

	public void setDefaultKey(String defaultKey) {
		this.defaultKey = defaultKey;
	}

	public void setProfileSeparator(String profileSeparator) {
		this.profileSeparator = profileSeparator;
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	public void setOrder(int order) {
		this.order = order;
	}

}
