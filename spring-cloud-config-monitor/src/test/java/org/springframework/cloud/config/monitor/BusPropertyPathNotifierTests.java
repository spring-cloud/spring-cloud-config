/*
 * Copyright 2015-present the original author or authors.
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

import java.util.Set;

import org.junit.jupiter.api.Test;

import org.springframework.cloud.bus.event.RefreshRemoteApplicationEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;

public class BusPropertyPathNotifierTests {

	@Test
	public void publishesRefreshEventsForAffectedServices() {
		RecordingApplicationEventPublisher publisher = new RecordingApplicationEventPublisher();
		BusPropertyPathNotifier notifier = new BusPropertyPathNotifier(publisher, "abc1");

		notifier.notifyApplications(Set.of("foo", "bar"));

		assertThat(publisher.getEvents()).hasSize(2);
		assertThat(publisher.getEvents())
			.allSatisfy(event -> assertThat(event).isInstanceOf(RefreshRemoteApplicationEvent.class));
	}

	private static final class RecordingApplicationEventPublisher implements ApplicationEventPublisher {

		private final java.util.List<ApplicationEvent> events = new java.util.ArrayList<>();

		@Override
		public void publishEvent(ApplicationEvent event) {
			this.events.add(event);
		}

		@Override
		public void publishEvent(Object event) {
			this.events.add((ApplicationEvent) event);
		}

		java.util.List<ApplicationEvent> getEvents() {
			return this.events;
		}

	}

}
