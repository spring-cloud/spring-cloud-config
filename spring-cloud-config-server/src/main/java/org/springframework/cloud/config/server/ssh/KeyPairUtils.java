/*
 * Copyright 2015-2022 the original author or authors.
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

package org.springframework.cloud.config.server.ssh;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.util.Collection;

import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.config.keys.loader.KeyPairResourceLoader;
import org.apache.sshd.common.session.SessionContext;
import org.apache.sshd.common.util.io.resource.AbstractIoResource;
import org.apache.sshd.common.util.security.SecurityUtils;

import org.springframework.util.StringUtils;

final class KeyPairUtils {

	private static final KeyPairResourceLoader loader = SecurityUtils.getKeyPairResourceParser();

	private KeyPairUtils() {

	}

	static Collection<KeyPair> load(SessionContext session, String privateKey, String passphrase)
			throws IOException, GeneralSecurityException {

		FilePasswordProvider passwordProvider = StringUtils.hasText(passphrase) ? FilePasswordProvider.of(passphrase)
				: FilePasswordProvider.EMPTY;

		return loader.loadKeyPairs(session, new StringResource(privateKey), passwordProvider);
	}

	static boolean isValid(String privateKey, String passphrase) {
		try {
			return !KeyPairUtils.load(null, privateKey, passphrase).isEmpty();
		}
		catch (IOException | GeneralSecurityException ignored) {
			return false;
		}
	}

	private static class StringResource extends AbstractIoResource<String> {

		protected StringResource(String resourceValue) {
			super(String.class, resourceValue);
		}

		@Override
		public InputStream openInputStream() {
			return new ByteArrayInputStream(this.getResourceValue().getBytes());
		}

	}

}
