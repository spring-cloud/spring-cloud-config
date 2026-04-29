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
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;

import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Luccas Asaphe
 *
 */
public class ConfigClientRequestTemplateFactoryTests {

	@Test
	void shouldInstrumentRestTemplateWhenObservationRegistryProvided() {
		// 1. set up the factory
		ConfigClientProperties properties = new ConfigClientProperties();
		ObservationRegistry registry = ObservationRegistry.create();
		ConfigClientRequestTemplateFactory factory = new ConfigClientRequestTemplateFactory(
				LogFactory.getLog(getClass()), properties, registry);

		// 2. create the RestTemplate
		RestTemplate restTemplate = factory.create();

		// 3. verify the observation registry
		assertThat(restTemplate.getObservationRegistry()).isEqualTo(registry);
	}

	@Test
	void shouldNotInstrumentRestTemplateWhenObservationRegistryNotProvided() {
		// 1. set up the factory
		ConfigClientProperties properties = new ConfigClientProperties();
		ConfigClientRequestTemplateFactory factory = new ConfigClientRequestTemplateFactory(
				LogFactory.getLog(getClass()), properties);

		// 2. create the RestTemplate
		RestTemplate restTemplate = factory.create();

		// 3. verify the observation registry
		assertThat(restTemplate.getObservationRegistry()).isEqualTo(ObservationRegistry.NOOP);
	}

	@Test
	void shouldNotInstrumentRestTemplateWhenObservationRegistryIsNoop() {
		// 1. set up the factory
		ConfigClientProperties properties = new ConfigClientProperties();
		ObservationRegistry registry = ObservationRegistry.NOOP;
		ConfigClientRequestTemplateFactory factory = new ConfigClientRequestTemplateFactory(
				LogFactory.getLog(getClass()), properties, registry);

		// 2. create the RestTemplate
		RestTemplate restTemplate = factory.create();

		// 3. verify the observation registry
		assertThat(restTemplate.getObservationRegistry()).isEqualTo(ObservationRegistry.NOOP);
	}

}
