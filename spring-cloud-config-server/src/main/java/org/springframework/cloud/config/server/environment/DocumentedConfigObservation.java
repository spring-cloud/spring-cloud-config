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
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.docs.ObservationDocumentation;

enum DocumentedConfigObservation implements ObservationDocumentation {

	/**
	 * Observation created around an EnvironmentRepository.
	 */
	ENVIRONMENT_REPOSITORY {
		@Override
		public Class<? extends ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
			return ObservationEnvironmentRepositoryObservationConvention.class;
		}

		@Override
		public KeyName[] getLowCardinalityKeyNames() {
			return LowCardinalityTags.values();
		}

		@Override
		public String getPrefix() {
			return "spring.cloud.config.environment";
		}
	};

	enum LowCardinalityTags implements KeyName {

		/**
		 * Implementation of the EnvironmentRepository.
		 */
		ENVIRONMENT_CLASS {
			@Override
			public String asString() {
				return "spring.cloud.config.environment.class";
			}
		},

		/**
		 * Application name for which properties are being queried for.
		 */
		PROFILE {
			@Override
			public String asString() {
				return "spring.cloud.config.environment.profile";
			}
		},

		/**
		 * Label for which properties are being queried for.
		 */
		LABEL {
			@Override
			public String asString() {
				return "spring.cloud.config.environment.label";
			}
		},

		/**
		 * Application name for which properties are being queried for.
		 */
		APPLICATION {
			@Override
			public String asString() {
				return "spring.cloud.config.environment.application";
			}
		}

	}

}
