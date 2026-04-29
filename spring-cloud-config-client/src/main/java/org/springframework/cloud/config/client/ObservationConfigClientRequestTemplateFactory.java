/*
 * Copyright 2026-present the original author or authors.
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

package org.springframework.cloud.config.client;

import io.micrometer.observation.ObservationRegistry;
import org.apache.commons.logging.Log;

import org.springframework.boot.bootstrap.BootstrapContext;
import org.springframework.boot.restclient.observation.ObservationRestTemplateCustomizer;
import org.springframework.http.client.observation.DefaultClientRequestObservationConvention;
import org.springframework.web.client.RestTemplate;

public class ObservationConfigClientRequestTemplateFactory extends ConfigClientRequestTemplateFactory {

	private final ObservationRestTemplateCustomizer observationRestTemplateCustomizer;

	public ObservationConfigClientRequestTemplateFactory(Log log, ConfigClientProperties properties,
			ObservationRegistry observationRegistry) {
		super(log, properties);
		this.observationRestTemplateCustomizer = observationRegistry != ObservationRegistry.NOOP
				? new ObservationRestTemplateCustomizer(observationRegistry,
						new DefaultClientRequestObservationConvention())
				: null;
	}

	@Override
	public RestTemplate create() {
		RestTemplate template = super.create();
		if (observationRestTemplateCustomizer != null) {
			observationRestTemplateCustomizer.customize(template);
		}
		return template;
	}

	static ConfigClientRequestTemplateFactory createWithObservation(BootstrapContext context, Log log,
			ConfigClientProperties props) {
		ObservationRegistry registry = context.getOrElse(ObservationRegistry.class, ObservationRegistry.NOOP);
		return new ObservationConfigClientRequestTemplateFactory(log, props, registry);
	}

}
