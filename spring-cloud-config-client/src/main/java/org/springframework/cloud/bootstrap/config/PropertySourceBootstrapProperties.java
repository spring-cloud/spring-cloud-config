package org.springframework.cloud.bootstrap.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("spring.cloud.config")
public class PropertySourceBootstrapProperties {

	/**
	 * Flag to indicate that the external properties should override system properties.
	 * Default true.
	 */
	private boolean systemPropertiesOverride = true;

	/**
	 * Flag to indicate that {@link #isSystemPropertiesOverride()
	 * systemPropertiesOverride} can be used. Set to false to prevent users from changing
	 * the default accidentally. Default true.
	 */
	private boolean allowOverride = true;

	public boolean isSystemPropertiesOverride() {
		return systemPropertiesOverride;
	}

	public void setSystemPropertiesOverride(boolean systemPropertiesOverride) {
		this.systemPropertiesOverride = systemPropertiesOverride;
	}

	public boolean isAllowOverride() {
		return allowOverride;
	}

	public void setAllowOverride(boolean allowOverride) {
		this.allowOverride = allowOverride;
	}

}
