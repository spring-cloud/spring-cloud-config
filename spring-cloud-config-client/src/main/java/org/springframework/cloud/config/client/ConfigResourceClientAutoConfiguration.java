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

package org.springframework.cloud.config.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

/**
 * Auto-configuration for a {@link ConfigResourceClient} that reads plain text and binary
 * files from the config server with the {@link RestClient} that the
 * {@code spring.config.import} of the config server registers as a bean.
 *
 * @author Daniel Lavoie
 * @author Roy Clarkson
 * @author Dylan Roberts
 * @author Sharang Gupta
 * @since 5.1.0
 * @see ConfigServerConfigDataLocationResolver#REST_CLIENT_BEAN_NAME
 */
@AutoConfiguration(after = ConfigClientAutoConfiguration.class)
@ConditionalOnProperty(name = ConfigClientProperties.PREFIX + ".enabled", matchIfMissing = true)
public class ConfigResourceClientAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean(ConfigResourceClient.class)
	@ConditionalOnBean(name = ConfigServerConfigDataLocationResolver.REST_CLIENT_BEAN_NAME)
	public ConfigResourceClient configResourceClient(
			@Qualifier(ConfigServerConfigDataLocationResolver.REST_CLIENT_BEAN_NAME) RestClient restClient,
			ConfigClientProperties properties) {
		return new DefaultConfigResourceClient(restClient, properties);
	}

}
