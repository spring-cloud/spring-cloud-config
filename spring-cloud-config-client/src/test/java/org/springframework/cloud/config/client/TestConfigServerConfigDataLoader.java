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

import java.io.IOException;
import java.util.function.Function;

import org.apache.commons.logging.Log;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.config.ConfigData;
import org.springframework.boot.context.config.ConfigDataLoaderContext;
import org.springframework.util.ReflectionUtils;

public class TestConfigServerConfigDataLoader
		extends AbstractConfigDataLoader<TestConfigServerConfigDataLocation> {

	public TestConfigServerConfigDataLoader(Log logger) {
		super(logger);
	}

	@Override
	public int getOrder() {
		return super.getOrder() - 1;
	}

	@Override
	public ConfigData load(ConfigDataLoaderContext context,
			TestConfigServerConfigDataLocation location) throws IOException {
		// This could be a RetryTemplate
		Function<TestConfigServerConfigDataLocation, ConfigData> fn = dataLocation -> {
			try {
				return super.load(context, dataLocation);
			}
			catch (IOException e) {
				ReflectionUtils.rethrowRuntimeException(e);
			}
			return null; // will never happen
		};
		return fn.apply(location);
	}

	public static void main(String[] args) {
		new SpringApplicationBuilder(TestConfig.class)
				.properties("spring.config.testconfigdata.enabled=true",
						"spring.application.name=foo",
						"spring.config.import=configserver:")
				.run(args);
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	protected static class TestConfig {

	}

}
