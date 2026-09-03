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

import org.springframework.cloud.bus.event.RefreshRemoteApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;

public class BusPropertyPathNotifier implements PropertyPathNotifier {

	private final ApplicationEventPublisher applicationEventPublisher;

	private final String busId;

	public BusPropertyPathNotifier(ApplicationEventPublisher applicationEventPublisher, String busId) {
		this.applicationEventPublisher = applicationEventPublisher;
		this.busId = busId;
	}

	@Override
	public void notifyApplications(Set<String> services) {
		for (String service : services) {
			this.applicationEventPublisher.publishEvent(new RefreshRemoteApplicationEvent(this, this.busId, service));
		}
	}

}
