/*
 * Copyright 2013-present the original author or authors.
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

package org.springframework.cloud.config.server.environment.compliance;

import java.util.Map;

/**
 * A single document within a {@link ConfigFile}. Files containing more than one document
 * model multi-document YAML, where later documents can be activated by
 * {@code spring.config.activate.on-profile}.
 *
 * @param activateOnProfile the value of {@code spring.config.activate.on-profile}, or
 * {@code null} if the document is always active
 * @param properties the properties contained in the document
 * @author Ryan Baxter
 */
public record ConfigDocument(String activateOnProfile, Map<String, Object> properties) {

}
