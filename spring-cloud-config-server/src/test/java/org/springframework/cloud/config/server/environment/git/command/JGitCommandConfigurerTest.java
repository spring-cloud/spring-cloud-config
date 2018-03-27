package org.springframework.cloud.config.server.environment.git.command;

import org.eclipse.jgit.api.TransportCommand;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * @author Taras Danylchuk
 */
public class JGitCommandConfigurerTest {

	@Test
	public void shouldConfigureCommand() {
		TransportCommand transportCommand = mock(TransportCommand.class);
		int timeout = 4;
		TransportConfigCallback transportConfigCallback = mock(TransportConfigCallback.class);
		CredentialsProvider credentialsProvider = mock(CredentialsProvider.class);
		JGitCommandConfigurer configurer = new JGitCommandConfigurer(timeout, transportConfigCallback, credentialsProvider);

		//test
		configurer.configureCommand(transportCommand);

		verify(transportCommand).setTimeout(timeout);
		verify(transportCommand).setTransportConfigCallback(transportConfigCallback);
		verify(transportCommand).setCredentialsProvider(credentialsProvider);
		verifyNoMoreInteractions(transportCommand);
	}

	@Test
	public void shouldNotConfigureNullValues() {
		TransportCommand transportCommand = mock(TransportCommand.class);
		int timeout = 4;
		JGitCommandConfigurer configurer = new JGitCommandConfigurer(timeout, null, null);

		//test
		configurer.configureCommand(transportCommand);

		verify(transportCommand).setTimeout(timeout);
		verifyNoMoreInteractions(transportCommand);
	}
}