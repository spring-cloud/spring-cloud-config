/*
 * Copyright 2013-2021 the original author or authors.
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

package org.springframework.cloud.config.server.environment;

import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.docs.DocumentedObservation;

enum DocumentedConfigObservation implements DocumentedObservation {

	/**
	 * Observation created around an EnvironmentRepository.
	 */
	CONFIG_OBSERVATION {
		@Override
		public String getName() {
			return "find";
		}

		@Override
		public KeyName[] getHighCardinalityKeyNames() {
			return Tags.values();
		}

		@Override
		public String getPrefix() {
			return "spring.cloud.config";
		}
	};

	enum Tags implements KeyName {

		/**
		 * Implementation of the EnvironmentRepository.
		 */
		ENVIRONMENT_CLASS {
			@Override
			public String getKeyName() {
				return "spring.cloud.config.environment.class";
			}
		}

	}

}
