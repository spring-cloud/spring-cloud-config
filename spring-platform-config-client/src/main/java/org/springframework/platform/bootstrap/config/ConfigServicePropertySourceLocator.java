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

package org.springframework.platform.bootstrap.config;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author Dave Syer
 *
 */
public class ConfigServicePropertySourceLocator implements PropertySourceLocator {

	private String env = "development";

	@Value("${spring.application.name:'application'}")
	private String name;

	private String label = "master";

	private String url = "http://localhost:8888";

	private RestTemplate restTemplate = new RestTemplate();

	@Override
	public PropertySource<?> locate() {
		CompositePropertySource composite = new CompositePropertySource("configService");
		List<SerializableMapPropertySource> result = restTemplate.exchange(
				url + "/{name}/{env}/{label}", HttpMethod.GET,
				new HttpEntity<Void>((Void) null),
				new ParameterizedTypeReference<List<SerializableMapPropertySource>>() {
				}, name, env, label).getBody();
		for (SerializableMapPropertySource source : result) {
			@SuppressWarnings("unchecked")
			Map<String, Object> map = (Map<String, Object>) source.getSource();
			composite.addPropertySource(new MapPropertySource(source.getName(), map));
		}
		return composite;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
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

	protected static class SerializableMapPropertySource {

		private String name;

		private Map<?, ?> source;

		@JsonCreator
		public SerializableMapPropertySource(@JsonProperty("name") String name,
				@JsonProperty("source") Map<?, ?> source) {
			this.name = name;
			this.source = source;
		}

		public String getName() {
			return name;
		}

		public Map<?, ?> getSource() {
			return source;
		}

	}
}
