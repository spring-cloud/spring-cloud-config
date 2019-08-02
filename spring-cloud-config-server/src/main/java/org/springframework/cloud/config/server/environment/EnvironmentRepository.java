/*
 * Copyright 2013-2019 the original author or authors.
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

import org.springframework.cloud.config.environment.Environment;

/**
 * @author Dave Syer
 * @author Roy Clarkson
 */
public interface EnvironmentRepository {

	Environment findOne(String application, String profile, String label);

	default Environment findOne(String application, String profile, String label,
			boolean includeOrigin) {
		return findOne(application, profile, label);
	}

}
