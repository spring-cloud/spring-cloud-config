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

package org.springframework.cloud.config.monitor;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("spring.cloud.config.server.monitor")
public class MonitorConfigurationProperties {

	/**
	 * Default maximum number of dash-separated segments considered when guessing a
	 * service name from a notification path.
	 */
	public static final int DEFAULT_MAX_DASHES = 20;

	private Endpoint endpoint = new Endpoint();

	/**
	 * Upper bound on the number of dash-separated segments processed when guessing a
	 * service name from a notification path. Prevents a long, heavily dashed path from
	 * producing an unbounded number of candidate service names (and refresh events).
	 */
	private int maxDashes = DEFAULT_MAX_DASHES;

	private GitHub github = new GitHub();

	private Gogs gogs = new Gogs();

	private GitLab gitlab = new GitLab();

	private Gitee gitee = new Gitee();

	private Gitea gitea = new Gitea();

	private Bitbucket bitbucket = new Bitbucket();

	public GitHub getGithub() {
		return github;
	}

	public void setGithub(GitHub github) {
		this.github = github;
	}

	public Gogs getGogs() {
		return gogs;
	}

	public void setGogs(Gogs gogs) {
		this.gogs = gogs;
	}

	public GitLab getGitlab() {
		return gitlab;
	}

	public void setGitlab(GitLab gitlab) {
		this.gitlab = gitlab;
	}

	public Gitee getGitee() {
		return gitee;
	}

	public void setGitee(Gitee gitee) {
		this.gitee = gitee;
	}

	public Gitea getGitea() {
		return gitea;
	}

	public void setGitea(Gitea gitea) {
		this.gitea = gitea;
	}

	public Bitbucket getBitbucket() {
		return bitbucket;
	}

	public void setBitbucket(Bitbucket bitbucket) {
		this.bitbucket = bitbucket;
	}

	public Endpoint getEndpoint() {
		return endpoint;
	}

	public void setEndpoint(Endpoint endpoint) {
		this.endpoint = endpoint;
	}

	public int getMaxDashes() {
		return maxDashes;
	}

	public void setMaxDashes(int maxDashes) {
		this.maxDashes = maxDashes;
	}

	public static class Endpoint {

		private String path = "/monitor";

		public String getPath() {
			return path;
		}

		public void setPath(String path) {
			this.path = path;
		}

	}

	public static class GitHub {

		private String webhookSecret;

		private boolean enabled = true;

		private boolean validationEnabled = true;

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public boolean isValidationEnabled() {
			return validationEnabled;
		}

		public void setValidationEnabled(boolean validationEnabled) {
			this.validationEnabled = validationEnabled;
		}

		public String getWebhookSecret() {
			return webhookSecret;
		}

		public void setWebhookSecret(String webhookSecret) {
			this.webhookSecret = webhookSecret;
		}

	}

	public static class Gogs {

		private String webhookSecret;

		private boolean enabled = true;

		private boolean validationEnabled = true;

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public boolean isValidationEnabled() {
			return validationEnabled;
		}

		public void setValidationEnabled(boolean validationEnabled) {
			this.validationEnabled = validationEnabled;
		}

		public String getWebhookSecret() {
			return webhookSecret;
		}

		public void setWebhookSecret(String webhookSecret) {
			this.webhookSecret = webhookSecret;
		}

	}

	public static class GitLab {

		private String webhookSecret;

		private boolean enabled = true;

		private boolean validationEnabled = true;

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public boolean isValidationEnabled() {
			return validationEnabled;
		}

		public void setValidationEnabled(boolean validationEnabled) {
			this.validationEnabled = validationEnabled;
		}

		public String getWebhookSecret() {
			return webhookSecret;
		}

		public void setWebhookSecret(String webhookSecret) {
			this.webhookSecret = webhookSecret;
		}

	}

	public static class Gitee {

		private String webhookSecret;

		private boolean enabled = true;

		private boolean validationEnabled = true;

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public boolean isValidationEnabled() {
			return validationEnabled;
		}

		public void setValidationEnabled(boolean validationEnabled) {
			this.validationEnabled = validationEnabled;
		}

		public String getWebhookSecret() {
			return webhookSecret;
		}

		public void setWebhookSecret(String webhookSecret) {
			this.webhookSecret = webhookSecret;
		}

	}

	public static class Gitea {

		private String webhookSecret;

		private boolean enabled = true;

		private boolean validationEnabled = true;

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public boolean isValidationEnabled() {
			return validationEnabled;
		}

		public void setValidationEnabled(boolean validationEnabled) {
			this.validationEnabled = validationEnabled;
		}

		public String getWebhookSecret() {
			return webhookSecret;
		}

		public void setWebhookSecret(String webhookSecret) {
			this.webhookSecret = webhookSecret;
		}

	}

	public static class Bitbucket {

		private String webhookSecret;

		private boolean enabled = true;

		private boolean validationEnabled = true;

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public boolean isValidationEnabled() {
			return validationEnabled;
		}

		public void setValidationEnabled(boolean validationEnabled) {
			this.validationEnabled = validationEnabled;
		}

		public String getWebhookSecret() {
			return webhookSecret;
		}

		public void setWebhookSecret(String webhookSecret) {
			this.webhookSecret = webhookSecret;
		}

	}

}
