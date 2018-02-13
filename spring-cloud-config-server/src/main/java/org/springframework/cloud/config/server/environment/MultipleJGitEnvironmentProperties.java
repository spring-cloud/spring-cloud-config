package org.springframework.cloud.config.server.environment;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties("spring.cloud.config.server.git")
public class MultipleJGitEnvironmentProperties extends JGitEnvironmentProperties {
    private Map<String, MultipleJGitEnvironmentRepository.PatternMatchingJGitEnvironmentRepository> repos;

    public Map<String, MultipleJGitEnvironmentRepository.PatternMatchingJGitEnvironmentRepository> getRepos() {
        return repos;
    }

    public void setRepos(Map<String, MultipleJGitEnvironmentRepository.PatternMatchingJGitEnvironmentRepository> repos) {
        this.repos = repos;
    }
}
