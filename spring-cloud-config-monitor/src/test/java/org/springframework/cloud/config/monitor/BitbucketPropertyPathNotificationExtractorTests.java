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

import org.junit.Before;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author Spencer Gibb
 * @author Dave Syer
 * @author Greg Jacobs
 *
 */
public class BitbucketPropertyPathNotificationExtractorTests {

	private BitbucketPropertyPathNotificationExtractor extractor = new BitbucketPropertyPathNotificationExtractor();

	private HttpHeaders headers;

	@Before
	public void setup(){
			headers = new HttpHeaders();
	}

	@Test
	public void bitbucketSample() throws Exception {
		// https://confluence.atlassian.com/bitbucket/event-payloads-740262817.html#EventPayloads-Push
		Map<String, Object> value = readPayload("bitbucket.json");
		setHeaders("repo:push");
		PropertyPathNotification extracted = this.extractor.extract(this.headers, value);
		assertNotNull(extracted);
		assertEquals("application.yml", extracted.getPaths()[0]);
	}
        
        @Test
	public void bitbucketPullRequestFulfillmentDetected() throws Exception {
		// https://confluence.atlassian.com/bitbucket/event-payloads-740262817.html#EventPayloads-Merged
		Map<String, Object> value = readPayload("bitbucket.json");
		setHeaders("pullrequest:fulfilled");
		PropertyPathNotification extracted = this.extractor.extract(this.headers, value);
		assertNotNull(extracted);
		assertEquals("application.yml", extracted.getPaths()[0]);
	}

	private void setHeaders(String eventKey) {
		this.headers.set("X-Event-Key", eventKey);
		this.headers.set("X-Hook-UUID", UUID.randomUUID().toString());
	}

	@Test
	public void notAPushOrPullRequestNotDetected() throws Exception {
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
	
	@Test
	public void missingUuidHeader() throws Exception {
		// https://confluence.atlassian.com/bitbucket/event-payloads-740262817.html#EventPayloads-Push
		Map<String, Object> value = readPayload("bitbucket.json");
		this.headers.set("X-Event-Key", "repo:push");
		PropertyPathNotification extracted = this.extractor.extract(this.headers, value);
		assertNull(extracted);
	}
	
	@Test
	public void missingChanges() throws Exception {
		// https://confluence.atlassian.com/bitbucket/event-payloads-740262817.html#EventPayloads-Push
		Map<String, Object> value = readPayload("bitbucket-invalid.json");
		setHeaders("repo:push");
		PropertyPathNotification extracted = this.extractor.extract(this.headers, value);
		assertNull(extracted);
	}

	private void assertNotExtracted(String path, String eventKey) throws java.io.IOException {
		Map<String, Object> value = readPayload(path);
		setHeaders(eventKey);
		PropertyPathNotification extracted = this.extractor.extract(this.headers, value);
		assertNull(extracted);
	}
	
	@Test
	public void bitbucketServerSample() throws Exception {
		// https://confluence.atlassian.com/bitbucketserver/event-payload-938025882.html
		Map<String, Object> value = readPayload("bitbucketserver.json");
		setServerHeaders("repo:refs_changed");
		PropertyPathNotification extracted = this.extractor.extract(this.headers, value);
		assertNotNull(extracted);
		assertEquals("application.yml", extracted.getPaths()[0]);
	}
        
    @Test
	public void bitbucketServerSamplePullRequest() throws Exception {
		// https://confluence.atlassian.com/bitbucketserver/event-payload-938025882.html
		Map<String, Object> value = readPayload("bitbucketserver.json");
		setServerHeaders("pr:merged");
		PropertyPathNotification extracted = this.extractor.extract(this.headers, value);
		assertNotNull(extracted);
		assertEquals("application.yml", extracted.getPaths()[0]);
	}

	private void setServerHeaders(String eventKey) {
		this.headers.set("X-Event-Key", eventKey);
		this.headers.set("X-Request-Id", UUID.randomUUID().toString());
	}

	@Test
	public void notAPushOrPullRequestServer() throws Exception {
		assertNotExtractedServer("bitbucketserver.json", "repo:comment:added");
	}
	
	@Test
	public void missingUuidHeaderServer() throws Exception {
		// https://confluence.atlassian.com/bitbucketserver/event-payload-938025882.html
		Map<String, Object> value = readPayload("bitbucketserver.json");
		this.headers.set("X-Event-Key", "repo:refs_changed");
		PropertyPathNotification extracted = this.extractor.extract(this.headers, value);
		assertNull(extracted);
	}
	
	@Test
	public void missingChangesServer() throws Exception {
		// https://confluence.atlassian.com/bitbucketserver/event-payload-938025882.html
		Map<String, Object> value = readPayload("bitbucketserver-invalid.json");
		setServerHeaders("repo:refs_changed");
		PropertyPathNotification extracted = this.extractor.extract(this.headers, value);
		assertNull(extracted);
	}
	
	private void assertNotExtractedServer(String path, String eventKey) throws java.io.IOException {
		Map<String, Object> value = readPayload(path);
		setServerHeaders(eventKey);
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
