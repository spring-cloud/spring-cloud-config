package org.springframework.cloud.config.client;

import static org.junit.Assert.assertFalse;

import org.junit.Test;
import org.springframework.cloud.config.client.ConfigClientProperties.Credentials;

public class CredentialsTests {

	@Test(expected = NullPointerException.class)
	public void emptyUsernameFails() {
		new ConfigClientProperties.Credentials(null, "x");
	}

	@Test(expected = NullPointerException.class)
	public void emptyPasswordFails() {
		new ConfigClientProperties.Credentials("x", null);
	}

	@Test
	public void passwordDoesNotAppearInToString() {
		Credentials credentials = new ConfigClientProperties.Credentials("foo", "bar");
		assertFalse(credentials.toString().contains("bar"));
	}

}
