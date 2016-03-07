package org.springframework.cloud.config.server.support;

import java.util.Map;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;

/**
 * @author Spencer Gibb
 */
public class EnvironmentPropertySource extends PropertySource<Environment> {

	public static String resolvePlaceholders(Environment environment, String text) {
		StandardEnvironment standardEnvironment = new StandardEnvironment();
		standardEnvironment.getPropertySources().addAfter(
				StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME,
				new EnvironmentPropertySource(environment));
		// Mask out escaped placeholders
		text = text.replace("\\${", "$_{");
		text = standardEnvironment.resolvePlaceholders(text).replace("$_{", "${");
		return text;
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
