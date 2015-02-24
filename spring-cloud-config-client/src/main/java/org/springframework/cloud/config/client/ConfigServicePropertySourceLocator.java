/*
 * Copyright 2013-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.config.client;

import org.springframework.core.annotation.Order;
import org.springframework.core.env.CompositePropertySource;

/**
 * @author Dave Syer
 *
 */
@Order(0)
public class ConfigServicePropertySourceLocator extends AbstractConfigServicePropertyLocator {

	public ConfigServicePropertySourceLocator(ConfigClientProperties defaults) {
		super(defaults);
	}

	@Override
	protected org.springframework.core.env.PropertySource<?> tryLocating(ConfigClientProperties client, CompositePropertySource composite, org.springframework.core.env.Environment environment) throws Exception {
		return exchange(client, composite);
	}
}
