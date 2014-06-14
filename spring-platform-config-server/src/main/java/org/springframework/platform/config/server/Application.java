
package org.springframework.platform.config.server;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.platform.bootstrap.config.Environment;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Configuration
@ComponentScan
@EnableAutoConfiguration
@RestController
public class Application {

	@Autowired
	private EnvironmentRepository repository;

	@RequestMapping("/{name}/{env}")
	public Environment master(@PathVariable String name, @PathVariable String env) {
		return properties(name, env, "master");
	}

	@RequestMapping("/{name}/{env}/{label}")
	public Environment properties(@PathVariable String name, @PathVariable String env, @PathVariable String label) {	
		return repository.findOne(name, env, label);
	}

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
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
		@Bean
		@ConfigurationProperties("spring.platform.config.server")
		public JGitEnvironmentRepository repository() {
			return new JGitEnvironmentRepository();
		}
	}
}
