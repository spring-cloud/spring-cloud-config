/*
 * Copyright 2013-2024 the original author or authors.
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

package org.springframework.cloud.config.client.aot;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;
import org.springframework.boot.context.config.ConfigDataLocation;
import org.springframework.cloud.config.client.ConfigClientAutoConfiguration;
import org.springframework.cloud.config.client.RetryTemplateFactory;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.util.ClassUtils;

/**
 * The config client runtime hints to enable the client to be used for aot builds.
 *
 * @author Olga Maciaszek-Sharma
 * @author Tobias Soloschenko
 */
class ConfigClientHints implements RuntimeHintsRegistrar {

	@Override
	public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
		if (!ClassUtils.isPresent("org.springframework.cloud.config.client.ConfigServerConfigDataLoader",
				classLoader)) {
			return;
		}
		hints.reflection()
			.registerType(TypeReference.of(ConfigClientAutoConfiguration.class),
					hint -> hint.withMembers(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS))
			.registerType(TypeReference.of(ConfigDataLocation.class),
					hint -> hint.withMembers(MemberCategory.INVOKE_DECLARED_METHODS))
			.registerType(TypeReference.of("org.springframework.boot.context.config.ConfigDataProperties"),
					hint -> hint.withMembers(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
							MemberCategory.DECLARED_FIELDS, MemberCategory.INTROSPECT_DECLARED_METHODS))
			.registerType(TypeReference.of(Environment.class),
					hint -> hint.withMembers(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
							MemberCategory.INTROSPECT_DECLARED_METHODS, MemberCategory.DECLARED_FIELDS))
			.registerType(TypeReference.of(PropertySource.class),
					hint -> hint.withMembers(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
							MemberCategory.INTROSPECT_DECLARED_METHODS, MemberCategory.DECLARED_FIELDS))
			.registerType(TypeReference.of(RetryTemplateFactory.class),
					hint -> hint.withMembers(MemberCategory.INTROSPECT_DECLARED_CONSTRUCTORS,
							MemberCategory.INVOKE_DECLARED_CONSTRUCTORS, MemberCategory.DECLARED_FIELDS,
							MemberCategory.INTROSPECT_DECLARED_METHODS, MemberCategory.INVOKE_DECLARED_METHODS));
	}

}
