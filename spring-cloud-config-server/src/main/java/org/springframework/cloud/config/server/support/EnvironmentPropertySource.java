package org.springframework.cloud.config.server.support;

import java.util.Map;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;

/**
 * @author Spencer Gibb
 */
public class EnvironmentPropertySource extends PropertySource<Environment> {

	public static StandardEnvironment prepareEnvironment(Environment environment) {
		StandardEnvironment standardEnvironment = new StandardEnvironment();
		standardEnvironment.getPropertySources()
				.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
		standardEnvironment.getPropertySources()
				.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
		standardEnvironment.getPropertySources()
				.addFirst(new EnvironmentPropertySource(environment));
		return standardEnvironment;
	}

	public static String resolvePlaceholders(StandardEnvironment preparedEnvironment,
			String text) {
		// Mask out escaped placeholders
		text = text.replace("\\${", "$_{");
		return preparedEnvironment.resolvePlaceholders(text).replace("$_{", "${");
	}

	public EnvironmentPropertySource(Environment sources) {
		super("cloudEnvironment", sources);
	}

	@Override
	public Object getProperty(String name) {
		for (org.springframework.cloud.config.environment.PropertySource source : getSource()
				.getPropertySources()) {
			Map<?, ?> map = source.getSource();
			if (map.containsKey(name)) {
				return map.get(name);
			}
		}
		return null;
	}

}
