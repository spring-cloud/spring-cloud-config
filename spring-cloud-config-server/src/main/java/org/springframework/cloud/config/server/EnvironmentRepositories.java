package org.springframework.cloud.config.server;

import javax.annotation.PostConstruct;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 
 * @author iceycake
 *
 */
@ConfigurationProperties(prefix = "spring.cloud.config.server.git.repos")
public class EnvironmentRepositories {
	
	private String uri;
	private String name;
	
    @PostConstruct
    public void init() throws Exception {
    	
    }
    
	public EnvironmentRepositories() {
		super();
	}
}
