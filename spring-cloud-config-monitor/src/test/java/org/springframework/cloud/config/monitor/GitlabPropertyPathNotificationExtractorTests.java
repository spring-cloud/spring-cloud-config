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
 * @author Dave Syer
 *
 */
public class GitlabPropertyPathNotificationExtractorTests {

	private GitlabPropertyPathNotificationExtractor extractor = new GitlabPropertyPathNotificationExtractor();

	private HttpHeaders headers = new HttpHeaders();

	@Test
	public void pushEvent() throws Exception {
		// See https://docs.gitlab.com/ee/web_hooks/web_hooks.html#push-events
		Map<String, Object> value = new ObjectMapper().readValue(
				new ClassPathResource("pathsamples/gitlab.json").getInputStream(),
				new TypeReference<Map<String, Object>>() {
				});
		this.headers.set("X-Gitlab-Event", "Push Hook");
		PropertyPathNotification extracted = this.extractor.extract(this.headers, value);
		assertThat(extracted).isNotNull();
		String[] paths = extracted.getPaths();
		assertThat(paths).as("paths was wrong").contains("oldapp.yml",
				"newapp.properties", "application.yml");
	}

	@Test
	public void nonPushEventNotDetected() throws Exception {
		// See https://docs.gitlab.com/ee/web_hooks/web_hooks.html#push-events
		Map<String, Object> value = new ObjectMapper().readValue(
				new ClassPathResource("pathsamples/gitlab.json").getInputStream(),
				new TypeReference<Map<String, Object>>() {
				});
		this.headers.set("X-Gitlab-Event", "Issue Hook");
		PropertyPathNotification extracted = this.extractor.extract(this.headers, value);
		assertThat(extracted).isNull();
	}

}
