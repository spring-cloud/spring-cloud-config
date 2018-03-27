/*
 * Copyright 2013-2018 the original author or authors.
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
package org.springframework.cloud.config.server.environment.git.command;

import org.eclipse.jgit.api.TransportCommand;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.transport.CredentialsProvider;

/**
 * @author Taras Danylchuk
 */
public class JGitCommandConfigurer {

	private final int timeout;
	private final TransportConfigCallback transportConfigCallback;
	private final CredentialsProvider credentialsProvider;

	public JGitCommandConfigurer(int timeout,
	                             TransportConfigCallback transportConfigCallback,
	                             CredentialsProvider credentialsProvider) {
		this.timeout = timeout;
		this.credentialsProvider = credentialsProvider;
		this.transportConfigCallback = transportConfigCallback;
	}

	public void configureCommand(TransportCommand<?, ?> command) {
		command.setTimeout(this.timeout);
		if (this.transportConfigCallback != null) {
			command.setTransportConfigCallback(this.transportConfigCallback);
		}
		if (credentialsProvider != null) {
			command.setCredentialsProvider(credentialsProvider);
		}
	}

	public int getTimeout() {
		return timeout;
	}

	public TransportConfigCallback getTransportConfigCallback() {
		return transportConfigCallback;
	}

	public CredentialsProvider getCredentialsProvider() {
		return credentialsProvider;
	}
}
