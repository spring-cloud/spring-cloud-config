
package org.springframework.platform.config.server;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.StandardServletEnvironment;

@Configuration
@ComponentScan
@EnableAutoConfiguration
@RestController
public class Application {

	@Autowired
	private ConfigurableEnvironment environment;

	private Set<String> standardSources = new HashSet<String>(Arrays.asList(
			StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME,
			StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
			StandardServletEnvironment.JNDI_PROPERTY_SOURCE_NAME,
			StandardServletEnvironment.SERVLET_CONFIG_PROPERTY_SOURCE_NAME,
			StandardServletEnvironment.SERVLET_CONTEXT_PROPERTY_SOURCE_NAME));

	@RequestMapping("/{name}/{env}")
	public List<SerializableMapPropertySource> master(@PathVariable String env) {
		return properties(env, "master");
	}

	@RequestMapping("/{name}/{env}/{label}")
	public List<SerializableMapPropertySource> properties(@PathVariable String env, @PathVariable String label) {	
		List<SerializableMapPropertySource> result = new ArrayList<Application.SerializableMapPropertySource>();
		for (PropertySource<?> source : environment.getPropertySources()) {
			String name = source.getName();
			if (!standardSources .contains(name) && source instanceof MapPropertySource) {  
				result.add(new SerializableMapPropertySource(name, (Map<?,?>)source.getSource()));
			}
		}
		return result;
	}

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}
	
	protected static class SerializableMapPropertySource {

		private String name;
		private Map<?, ?> source;

		public SerializableMapPropertySource(String name, Map<?,?> source) {
			this.name = name;
			this.source = source;
		}
		
		public String getName() {
			return name;
		}

		public Map<?, ?> getSource() {
			return source;
		}
		
	}
}
