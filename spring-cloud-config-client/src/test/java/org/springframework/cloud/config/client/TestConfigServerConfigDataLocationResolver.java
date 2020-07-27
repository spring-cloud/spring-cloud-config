/*
 * Copyright 2013-2020 the original author or authors.
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

import org.apache.commons.logging.Log;

import org.springframework.boot.context.config.ConfigDataLocationResolverContext;
import org.springframework.boot.context.config.Profiles;
import org.springframework.web.client.RestTemplate;

public class TestConfigServerConfigDataLocationResolver
		extends AbstractConfigDataLocationResolver<TestConfigServerConfigDataLocation> {

	public TestConfigServerConfigDataLocationResolver(Log log) {
		super(log);
	}

	@Override
	public int getOrder() {
		return super.getOrder() - 1;
	}

	protected RestTemplate createRestTemplate(ConfigClientProperties properties) {
		// do something custom here
		return super.createRestTemplate(properties);
	}

	@Override
	public boolean isResolvable(ConfigDataLocationResolverContext context,
			String location) {
		if (!location.startsWith(getPrefix())) {
			return false;
		}
		Boolean enabled = context.getBinder()
				.bind("spring.config.testconfigdata.enabled", Boolean.class)
				.orElse(false);
		return enabled;
	}

	@Override
	protected TestConfigServerConfigDataLocation createConfigDataLocation(
			Profiles profiles, ConfigClientProperties properties,
			RestTemplate restTemplate) {
		return new TestConfigServerConfigDataLocation(restTemplate, properties, profiles);
	}

}
