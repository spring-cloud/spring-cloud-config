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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import org.springframework.context.support.StaticApplicationContext;
import org.springframework.http.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 *
 */
public class PropertyPathEndpointTests {

	private PropertyPathEndpoint endpoint = new PropertyPathEndpoint(
			new CompositePropertyPathNotificationExtractor(Collections.emptyList()),
			"abc1");

	@Before
	public void init() {
		StaticApplicationContext publisher = new StaticApplicationContext();
		this.endpoint.setApplicationEventPublisher(publisher);
		publisher.refresh();
	}

	@Test
	public void testBusId() {
		assertThat(this.endpoint.getBusId()).isEqualTo("abc1");
	}

	@Test
	public void testNotifyByForm() {
		assertThat(
				this.endpoint.notifyByForm(new HttpHeaders(), new ArrayList<>()).size())
						.isEqualTo(0);
	}

	@Test
	public void testNotifySeveral() {
		List<String> request = new ArrayList<>();
		request.add("/foo/bar.properties");
		request.add("/application.properties");
		assertThat(this.endpoint.notifyByForm(new HttpHeaders(), request).toString())
				.isEqualTo("[bar, *]");
	}

	@Test
	public void testNotifyAll() {
		assertThat(
				this.endpoint
						.notifyByPath(new HttpHeaders(),
								Collections.singletonMap("path", "application.yml"))
						.toString()).isEqualTo("[*]");
	}

	@Test
	public void testNotifyAllWithProfile() {
		assertThat(this.endpoint
				.notifyByPath(new HttpHeaders(),
						Collections.singletonMap("path", "application-local.yml"))
				.toString()).isEqualTo("[*:local]");
	}

	@Test
	public void testNotifyOne() {
		assertThat(this.endpoint.notifyByPath(new HttpHeaders(),
				Collections.singletonMap("path", "foo.yml")).toString())
						.isEqualTo("[foo]");
	}

	@Test
	public void testNotifyOneWithWindowsPath() {
		assertThat(this.endpoint
				.notifyByPath(new HttpHeaders(),
						Collections.singletonMap("path", "C:\\config\\foo.yml"))
				.toString()).isEqualTo("[foo]");
	}

	@Test
	public void testNotifyOneWithProfile() {
		assertThat(
				this.endpoint
						.notifyByPath(new HttpHeaders(),
								Collections.singletonMap("path", "foo-local.yml"))
						.toString()).isEqualTo("[foo:local, foo-local]");
	}

	@Test
	public void testNotifyMultiDash() {
		assertThat(
				this.endpoint
						.notifyByPath(new HttpHeaders(),
								Collections.singletonMap("path", "foo-local-dev.yml"))
						.toString()).isEqualTo(
								"[foo:local-dev, foo-local:dev, foo-local-dev]");
	}

}
