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

package org.springframework.cloud.config.server.config;

import com.google.auth.oauth2.GoogleCredentials;
import org.eclipse.jgit.api.TransportConfigCallback;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.cloud.config.server.support.GoogleCloudSourceSupport;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Adds Google Cloud Source OAuth2 support, if
 * com.google.auth:google-auth-library-oauth2-http library is on classpath.
 *
 * @author Eduard Wirch
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({ GoogleCredentials.class, TransportConfigCallback.class })
public class GoogleCloudSourceConfiguration {

	@Bean
	public GoogleCloudSourceSupport createGoogleCloudSourceSupport() {
		return new GoogleCloudSourceSupport();
	}

}
