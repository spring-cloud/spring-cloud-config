package org.springframework.cloud.config.server;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.rule.OutputCapture;
import org.springframework.cloud.config.server.diagnostics.GitUriFailureAnalyzer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;

/**
 * @author Ryan Baxter
 */
public class NativeBootstrapFailureAnalyzerTests {

	@Rule
	public OutputCapture outputCapture = new OutputCapture();
	
	@Test
	public void contextLoads(){
		try {
			new SpringApplicationBuilder(ConfigServerApplication.class)
					.web(WebApplicationType.SERVLET).properties("spring.cloud.bootstrap.name:enable-nativebootstrap").profiles("test","native").run();
			fail("Application started successfully");
		}
		catch (Exception ex) {
			assertThat(this.outputCapture.toString())
					.contains(GitUriFailureAnalyzer.ACTION);
			assertThat(this.outputCapture.toString()).contains(GitUriFailureAnalyzer.DESCRIPTION);
		}
	}
}
