/*
 * Copyright 2013-2014 the original author or authors.
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
package org.springframework.cloud.config.server;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author Dave Syer
 *
 */
@ConfigurationProperties("spring.cloud.config.server")
public class ConfigServerProperties {
	
	public static final String MASTER = "master";
	
	private boolean bootstrap;
	private String prefix;
	private String defaultLabel = ConfigServerProperties.MASTER;
	public String getDefaultLabel() {
		return defaultLabel;
	}
	public void setDefaultLabel(String defaultLabel) {
		this.defaultLabel = defaultLabel;
	}
	public boolean isBootstrap() {
		return bootstrap;
	}
	public void setBootstrap(boolean bootstrap) {
		this.bootstrap = bootstrap;
	}
	public String getPrefix() {
		return prefix;
	}
	public void setPrefix(String prefix) {
		this.prefix = prefix;
	}

}
