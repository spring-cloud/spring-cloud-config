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

package org.springframework.cloud.config.client.tls;

import java.io.File;

public class TlsConfigClientRunner extends AppRunner {

	public TlsConfigClientRunner(Class<?> appClass, AppRunner server) {
		this(appClass, server, "spring.config.use-legacy-processing", "true");
	}

	public TlsConfigClientRunner(Class<?> appClass, AppRunner server, String importKey, String importValue) {
		super(appClass);

		property("spring.cloud.config.uri", server.root());
		property("spring.cloud.config.enabled", "true");
		property(importKey, importValue);
	}

	public void enableTls() {
		property("spring.cloud.config.tls.enabled", "true");
	}

	public void disableTls() {
		property("spring.cloud.config.tls.enabled", "false");
	}

	public void setKeyStore(File keyStore, String keyStorePassword, String keyPassword) {
		property("spring.cloud.config.tls.key-store", pathOf(keyStore));
		property("spring.cloud.config.tls.key-store-password", keyStorePassword);
		property("spring.cloud.config.tls.key-password", keyPassword);
	}

	public void setKeyStore(File keyStore) {
		property("spring.cloud.config.tls.key-store", pathOf(keyStore));
	}

	public void setTrustStore(File trustStore, String password) {
		property("spring.cloud.config.tls.trust-store", pathOf(trustStore));
		property("spring.cloud.config.tls.trust-store-password", password);
	}

	public void setTrustStore(File trustStore) {
		property("spring.cloud.config.tls.trust-store", pathOf(trustStore));
	}

	private String pathOf(File file) {
		return String.format("file:%s", file.getAbsolutePath());
	}

}
