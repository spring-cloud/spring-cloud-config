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
import org.springframework.cloud.config.environment.PropertySource;

/**
 * @author Dave Syer
 * @author Michael Prankl
 */
public class EnvironmentCleaner {

	public Environment clean(Environment value, String workingDir, String uri) {
		Environment result = new Environment(value);
		for (PropertySource source : value.getPropertySources()) {
			String name = source.getName().replace(workingDir, "");
			name = name.replace("applicationConfig: [", "");
			name = uri + "/" + name.replace("]", "");
			result.add(new PropertySource(name, source.getSource()));
		}
		return result;
	}

}
