package org.springframework.cloud.config.server.diagnostics;

import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.cloud.config.server.environment.JGitEnvironmentRepository;

/**
 * @author Ryan Baxter
 */
public class GitUriFailureAnalyzer extends AbstractFailureAnalyzer<IllegalStateException> {

	public static final String DESCRIPTION = "Invalid config server configuration.";
	public static final String ACTION = "If you are using the git profile, you need to set a Git URI in your " +
			"configuration.  If you are using a native profile and have spring.cloud.config.server.bootstrap=true, " +
			"you need to use a composite configuration.";

	@Override
	protected FailureAnalysis analyze(Throwable rootFailure, IllegalStateException cause) {
		if(JGitEnvironmentRepository.MESSAGE.equalsIgnoreCase(cause.getMessage())) {
			return new FailureAnalysis(DESCRIPTION, ACTION, cause);
		}
		return null;
	}

}
