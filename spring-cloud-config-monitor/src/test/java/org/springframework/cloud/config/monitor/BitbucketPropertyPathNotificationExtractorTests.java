/*
 * Copyright 2015 the original author or authors.
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

package org.springframework.cloud.config.monitor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.Map;
import java.util.UUID;

import org.junit.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author Spencer Gibb
 * @author Dave Syer
 *
 */
public class BitbucketPropertyPathNotificationExtractorTests {

	private BitbucketPropertyPathNotificationExtractor extractor = new BitbucketPropertyPathNotificationExtractor();

	private HttpHeaders headers = new HttpHeaders();

	@Test
	public void bitbucketSample() throws Exception {
		// https://confluence.atlassian.com/bitbucket/event-payloads-740262817.html#EventPayloads-Push
		Map<String, Object> value = readPayload("bitbucket.json");
		setHeaders("repo:push");
		PropertyPathNotification extracted = this.extractor.extract(this.headers, value);
		assertNotNull(extracted);
		assertEquals("application.yml", extracted.getPaths()[0]);
	}

	private void setHeaders(String eventKey) {
		this.headers.set("X-Event-Key", eventKey);
		this.headers.set("X-Hook-UUID", UUID.randomUUID().toString());
	}

	@Test
	public void notAPushNotDetected() throws Exception {
		assertNotExtracted("bitbucket.json", "issue:created");
	}

	@Test
	public void gitlabNotDetected() throws Exception {
		assertNotExtracted("gitlab.json", "repo:push");
	}

	@Test
	public void githubNotDetected() throws Exception {
		assertNotExtracted("github.json", "repo:push");
	}

	private void assertNotExtracted(String path, String eventKey) throws java.io.IOException {
		Map<String, Object> value = readPayload(path);
		setHeaders(eventKey);
		PropertyPathNotification extracted = this.extractor.extract(this.headers, value);
		assertNull(extracted);
	}

	private Map<String, Object> readPayload(String path) throws java.io.IOException {
		return new ObjectMapper().readValue(
					new ClassPathResource(path).getInputStream(),
					new TypeReference<Map<String, Object>>() {
					});
	}

}
