/*
 * Copyright 2015 the original author or authors.
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
package org.springframework.cloud.config.server.environment;

/**
 * Signals that the scm could not be updated and provides an older version that can be used instead.
 *
 * @author Mark Bonnekessel
 */
public class ScmUpdateFailedException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	/**
	 * Specifies the version that can be used if the current repository cannot be fetched!
	 */
	private String existingVersion = "";

	public ScmUpdateFailedException(String msg) {
		super(msg);
	}
	public ScmUpdateFailedException(String msg, String existingVersion) {
		super(msg);
		this.existingVersion = existingVersion;
	}

	public ScmUpdateFailedException(String msg, Throwable cause) {
		super(msg, cause);
	}
	public ScmUpdateFailedException(String msg, String existingVersion, Throwable cause) {
		super(msg, cause);
		this.existingVersion = existingVersion;
	}

	public String getExistingVersion() {
		return existingVersion;
	}
}
