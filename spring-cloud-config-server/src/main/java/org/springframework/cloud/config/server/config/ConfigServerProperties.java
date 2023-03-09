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
import org.springframework.core.style.ToStringCreator;

/**
 * @author Dave Syer
 * @author Roy Clarkson
 */
@ConfigurationProperties(ConfigServerProperties.PREFIX)
public class ConfigServerProperties {

	/**
	 * Config Server properties prefix.
	 */
	public static final String PREFIX = "spring.cloud.config.server";

	/**
	 * Flag indicating config server is enabled.
	 */
	private boolean enabled = true;

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
	 * Flag indicating that if there are any errors reading properties from a subordinate
	 * environment repository in a composite environment repository, then the entire
	 * composite read should fail. Useful when set to false when a Vault repository is in
	 * the composite to allow clients to still read properties from other repositories
	 * without providing a valid Vault token.
	 *
	 * Defaults to true, resulting in a failure on any error.
	 */
	private boolean failOnCompositeError = true;

	/**
	 * By default the location order we use in GenericResourceRepository is the order in
	 * which they are listed. Prior to Hoxton.SR11 the order used to be reverse. If this
	 * property is set to true then we will reverse ther order like it used to be prior to
	 * Hoxton.SR11.
	 */
	private boolean reverseLocationOrder = false;

	/**
	 * Decryption configuration for when server handles encrypted properties before
	 * sending them to clients.
	 */
	private Encrypt encrypt = new Encrypt();

	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

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

	public boolean isFailOnCompositeError() {
		return failOnCompositeError;
	}

	public void setFailOnCompositeError(boolean failOnCompositeError) {
		this.failOnCompositeError = failOnCompositeError;
	}

	public boolean isReverseLocationOrder() {
		return reverseLocationOrder;
	}

	public void setReverseLocationOrder(boolean reverseLocationOrder) {
		this.reverseLocationOrder = reverseLocationOrder;
	}

	@Override
	public String toString() {
		return new ToStringCreator(this).append("enabled", enabled).append("bootstrap", bootstrap)
				.append("prefix", prefix).append("defaultLabel", defaultLabel).append("overrides", overrides)
				.append("stripDocumentFromYaml", stripDocumentFromYaml).append("acceptEmpty", acceptEmpty)
				.append("defaultApplicationName", defaultApplicationName).append("defaultProfile", defaultProfile)
				.append("failOnCompositeError", failOnCompositeError).append("encrypt", encrypt)
				.append("reverseLocationOrder", reverseLocationOrder).toString();

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

		@Override
		public String toString() {
			return new ToStringCreator(this).append("enabled", enabled).append("plainTextEncrypt", plainTextEncrypt)
					.toString();

		}

	}

}
