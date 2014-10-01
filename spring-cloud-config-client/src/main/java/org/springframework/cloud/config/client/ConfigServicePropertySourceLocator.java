/*
 * Copyright 2013-2014 the original author or authors.
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

package org.springframework.cloud.config.client;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.cloud.config.Environment;
import org.springframework.cloud.config.PropertySource;
import org.springframework.web.client.RestTemplate;

/**
 * @author Dave Syer
 *
 */
@ConfigurationProperties("spring.cloud.config")
public class ConfigServicePropertySourceLocator implements PropertySourceLocator {

	private String env = "default";

	@Value("${spring.application.name:'application'}")
	private String name;

	private String label = "master";

	private String uri = "http://localhost:8888";

	private RestTemplate restTemplate = new RestTemplate();

	@Override
	public org.springframework.core.env.PropertySource<?> locate() {
		CompositePropertySource composite = new CompositePropertySource("configService");
		Environment result = restTemplate.exchange(uri + "/{name}/{env}/{label}",
				HttpMethod.GET, new HttpEntity<Void>((Void) null), Environment.class,
				name, env, label).getBody();
		for (PropertySource source : result.getPropertySources()) {
			@SuppressWarnings("unchecked")
			Map<String, Object> map = (Map<String, Object>) source.getSource();
			composite.addPropertySource(new MapPropertySource(source.getName(), map));
		}
		return composite;
	}

	public String getUri() {
		return uri;
	}

	public void setUri(String url) {
		this.uri = url;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getEnv() {
		return env;
	}

	public void setEnv(String env) {
		this.env = env;
	}

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

}
