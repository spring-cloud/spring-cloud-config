package org.springframework.cloud.config.server.config;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.config.server.environment.EnvironmentRepository;
import org.springframework.cloud.config.server.environment.FileResolvingEnvironmentRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Autoconfiguration for {@link FileResolvingEnvironmentRepository}.
 * Wraps the existing EnvironmentRepository to support file content resolution.
 *
 * @author Johny Cho
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter(EnvironmentRepositoryConfiguration.class)
public class FileResolvingEnvironmentRepositoryConfiguration {

	@Bean
	@Primary
	@ConditionalOnBean(EnvironmentRepository.class)
	@ConditionalOnProperty(value = "spring.cloud.config.server.file-resolving.enabled", matchIfMissing = true)
	public FileResolvingEnvironmentRepository fileResolvingEnvironmentRepository(EnvironmentRepository environmentRepository) {
		return new FileResolvingEnvironmentRepository(environmentRepository);
	}

}
