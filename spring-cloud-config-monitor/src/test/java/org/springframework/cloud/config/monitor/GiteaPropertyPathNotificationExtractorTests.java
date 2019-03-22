/*
 * Copyright 2015-2019 the original author or authors.
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

package org.springframework.cloud.config.monitor;

import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Juan Pablo Santos Rodríguez
 *
 */
public class GiteaPropertyPathNotificationExtractorTests {

	private GiteaPropertyPathNotificationExtractor extractor = new GiteaPropertyPathNotificationExtractor();

	private HttpHeaders headers = new HttpHeaders();

	@Test
	public void giteaSample() throws Exception {
		// See https://docs.gitea.io/en-us/webhooks/
		Map<String, Object> value = new ObjectMapper().readValue(
				new ClassPathResource("pathsamples/gitea.json").getInputStream(),
				new TypeReference<Map<String, Object>>() {
				});
		this.headers.set("X-Gitea-Event", "push");
		PropertyPathNotification extracted = this.extractor.extract(this.headers, value);
		assertThat(extracted).isNotNull();
		assertThat(extracted.getPaths()[0]).isEqualTo("application.yml");
	}

	@Test
	public void notAPushNotDetected() throws Exception {
		Map<String, Object> value = new ObjectMapper().readValue(
				new ClassPathResource("pathsamples/gitea.json").getInputStream(),
				new TypeReference<Map<String, Object>>() {
				});
		this.headers.set("X-Gitea-Event", "issues");
		PropertyPathNotification extracted = this.extractor.extract(this.headers, value);
		assertThat(extracted).isNull();
	}

}
