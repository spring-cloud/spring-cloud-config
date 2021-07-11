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

package org.springframework.cloud.config.server.security;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.encrypt.TextEncryptor;

/**
 * Security meta interface for TextEncryptor.
 *
 * @author ian
 */
public interface EncryptorWithSecurity extends TextEncryptor {

	String[] getAuthorities(String method);

	@PreAuthorize("hasAnyAuthority(this.getAuthorities('encrypt'))")
	@Override
	String encrypt(String s);

	@PreAuthorize("hasAnyAuthority(this.getAuthorities('decrypt'))")
	@Override
	String decrypt(String s);

	static TextEncryptor create(TextEncryptor encryptor, String application, String profiles,
			AuthorityExtractor extractor) {
		return new EncryptorWithSecurity() {
			@Override
			public String[] getAuthorities(String method) {
				EncryptorAccessRequest request = new EncryptorAccessRequest();
				request.setType(AccessType.ENCRYPTOR);
				request.setMethod(method);
				request.setApplication(application);
				request.setProfile(profiles);
				return extractor.extract(request);
			}

			@Override
			public String encrypt(String s) {
				return encryptor.encrypt(s);
			}

			@Override
			public String decrypt(String s) {
				return encryptor.decrypt(s);
			}
		};
	}

}
