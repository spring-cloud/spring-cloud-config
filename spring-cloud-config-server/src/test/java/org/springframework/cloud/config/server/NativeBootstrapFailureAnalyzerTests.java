/*
 * Copyright 2018-2019 the original author or authors.
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

import org.junit.Rule;
import org.junit.Test;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.rule.OutputCapture;
import org.springframework.cloud.config.server.diagnostics.GitUriFailureAnalyzer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * @author Ryan Baxter
 */
public class NativeBootstrapFailureAnalyzerTests {

	@Rule
	public OutputCapture outputCapture = new OutputCapture();

	@Test
	public void contextLoads() {
		try {
			new SpringApplicationBuilder(ConfigServerApplication.class)
					.web(WebApplicationType.SERVLET)
					.properties("spring.cloud.bootstrap.name:enable-nativebootstrap")
					.profiles("test", "native").run();
			fail("Application started successfully");
		}
		catch (Exception ex) {
			assertThat(this.outputCapture.toString())
					.contains(GitUriFailureAnalyzer.ACTION);
			assertThat(this.outputCapture.toString())
					.contains(GitUriFailureAnalyzer.DESCRIPTION);
		}
	}

}
