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

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author Dave Syer
 * @author Roy Clarkson
 */
@ConfigurationProperties("spring.cloud.config.server")
public class ConfigServerProperties {

	/**
	 * Flag indicating that the config server should initialize its own Environment with
	 * properties from the remote repository. Off by default because it delays startup but
	 * can be useful when embedding the server in another application.
	 */
	private boolean bootstrap;

	/**
	 * Prefix for configuration resource paths (default is empty). Useful when embedding
	 * in another application when you don't want to change the context path or servlet
	 * path.
	 */
	private String prefix;

	/**
	 * Default repository label when incoming requests do not have a specific label.
	 */
	private String defaultLabel;

	/**
	 * Extra map for a property source to be sent to all clients unconditionally.
	 */
	private Map<String, String> overrides = new LinkedHashMap<String, String>();

	/**
	 * Flag to indicate that YAML documents that are text or collections (not a map)
	 * should be returned in "native" form.
	 */
	private boolean stripDocumentFromYaml = true;

	/**
	 * Flag to indicate that If HTTP 404 needs to be sent if Application is not Found.
	 */
	private boolean acceptEmpty = true;

	/**
	 * Default application name when incoming requests do not have a specific one.
	 */
	private String defaultApplicationName = "application";

	/**
	 * Default application profile when incoming requests do not have a specific one.
	 */
	private String defaultProfile = "default";

	/**
	 * Decryption configuration for when server handles encrypted properties before
	 * sending them to clients.
	 */
	private Encrypt encrypt = new Encrypt();

	public Encrypt getEncrypt() {
		return this.encrypt;
	}

	public String getDefaultLabel() {
		return this.defaultLabel;
	}

	public void setDefaultLabel(String defaultLabel) {
		this.defaultLabel = defaultLabel;
	}

	public boolean isBootstrap() {
		return this.bootstrap;
	}

	public void setBootstrap(boolean bootstrap) {
		this.bootstrap = bootstrap;
	}

	public String getPrefix() {
		return this.prefix;
	}

	public void setPrefix(String prefix) {
		this.prefix = prefix;
	}

	public Map<String, String> getOverrides() {
		return this.overrides;
	}

	public void setOverrides(Map<String, String> overrides) {
		this.overrides = overrides;
	}

	public boolean isStripDocumentFromYaml() {
		return this.stripDocumentFromYaml;
	}

	public void setStripDocumentFromYaml(boolean stripDocumentFromYaml) {
		this.stripDocumentFromYaml = stripDocumentFromYaml;
	}

	public boolean isAcceptEmpty() {
		return this.acceptEmpty;
	}

	public void setAcceptEmpty(boolean acceptEmpty) {
		this.acceptEmpty = acceptEmpty;
	}

	public String getDefaultApplicationName() {
		return this.defaultApplicationName;
	}

	public void setDefaultApplicationName(String defaultApplicationName) {
		this.defaultApplicationName = defaultApplicationName;
	}

	public String getDefaultProfile() {
		return this.defaultProfile;
	}

	public void setDefaultProfile(String defaultProfile) {
		this.defaultProfile = defaultProfile;
	}

	/**
	 * Encryption properties.
	 */
	public static class Encrypt {

		/**
		 * Enable decryption of environment properties before sending to client.
		 */
		private boolean enabled = true;

		/**
		 * Enable decryption of environment properties served by plain text endpoint
		 * {@link org.springframework.cloud.config.server.resource.ResourceController}.
		 */
		private boolean plainTextEncrypt = false;

		public boolean isEnabled() {
			return this.enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public boolean isPlainTextEncrypt() {
			return plainTextEncrypt;
		}

		public void setPlainTextEncrypt(boolean plainTextEncrypt) {
			this.plainTextEncrypt = plainTextEncrypt;
		}

	}

}
