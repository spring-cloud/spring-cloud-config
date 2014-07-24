
package org.springframework.platform.config.server;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.ConfigurableEnvironment;

@Configuration
@ComponentScan
@EnableAutoConfiguration
public class Application {

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	@Configuration
	@ConfigurationProperties("encrypt")
	protected static class KeyConfiguration {
		@Autowired
		private EncryptionController controller;
		
		private String key;
		
		public String getKey() {
			return key;
		}

		public void setKey(String key) {
			this.key = key;
		}

		@PostConstruct
		public void init() {
			if (key!=null) {
				controller.uploadKey(key);
			}
		}
	}

	@Configuration
	@Profile("native")
	protected static class NativeRepositoryConfiguration {
		@Autowired
		private ConfigurableEnvironment environment;
		
		@Bean
		public NativeEnvironmentRepository repository() {
			return new NativeEnvironmentRepository(environment);
		}
	}

	@Configuration
	@Profile("!native")
	protected static class GitRepositoryConfiguration {
		@Autowired
		private ConfigurableEnvironment environment;

		@Bean
		@ConfigurationProperties("spring.platform.config.server")
		public JGitEnvironmentRepository repository() {
			return new JGitEnvironmentRepository(environment);
		}
	}
}
