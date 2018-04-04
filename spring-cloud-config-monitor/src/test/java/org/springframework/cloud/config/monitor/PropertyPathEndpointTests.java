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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.http.HttpHeaders;

/**
 * @author Dave Syer
 *
 */
public class PropertyPathEndpointTests {

	private PropertyPathEndpoint endpoint = new PropertyPathEndpoint(
			new CompositePropertyPathNotificationExtractor(
					Collections.emptyList()), "abc1");

	@Before
	public void init() {
		StaticApplicationContext publisher = new StaticApplicationContext();
		this.endpoint.setApplicationEventPublisher(publisher);
		publisher.refresh();
	}

    @Test
    public void testBusId() {
        assertEquals("abc1", this.endpoint.getBusId());
    }

	@Test
	public void testNotifyByForm() {
		assertEquals(0, this.endpoint
				.notifyByForm(new HttpHeaders(), new ArrayList<>()).size());
	}

	@Test
	public void testNotifySeveral() {
		List<String> request = new ArrayList<>();
		request.add("/foo/bar.properties");
		request.add("/application.properties");
		assertEquals("[bar, *]",
				this.endpoint.notifyByForm(new HttpHeaders(), request).toString());
	}

	@Test
	public void testNotifyAll() {
		assertEquals("[*]",
				this.endpoint
						.notifyByPath(new HttpHeaders(), Collections
								.singletonMap("path", "application.yml"))
				.toString());
	}

	@Test
	public void testNotifyAllWithProfile() {
		assertEquals("[*:local]",
				this.endpoint
						.notifyByPath(new HttpHeaders(), Collections
								.singletonMap("path", "application-local.yml"))
				.toString());
	}

	@Test
	public void testNotifyOne() {
		assertEquals("[foo]",
				this.endpoint
						.notifyByPath(new HttpHeaders(), Collections
								.singletonMap("path", "foo.yml"))
				.toString());
	}

	@Test
	public void testNotifyOneWithWindowsPath() {
		assertEquals("[foo]",
				this.endpoint
						.notifyByPath(new HttpHeaders(), Collections
								.singletonMap("path", "C:\\config\\foo.yml"))
				.toString());
	}

	@Test
	public void testNotifyOneWithProfile() {
		assertEquals("[foo:local, foo-local]",
				this.endpoint
						.notifyByPath(new HttpHeaders(), Collections
								.singletonMap("path", "foo-local.yml"))
				.toString());
	}

	@Test
	public void testNotifyMultiDash() {
		assertEquals("[foo:local-dev, foo-local:dev, foo-local-dev]",
				this.endpoint
						.notifyByPath(new HttpHeaders(), Collections
								.singletonMap("path", "foo-local-dev.yml"))
				.toString());
	}

}
