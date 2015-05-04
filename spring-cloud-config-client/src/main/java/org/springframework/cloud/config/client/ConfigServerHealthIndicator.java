package org.springframework.cloud.config.client;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health.Builder;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;

/**
 * @author Spencer Gibb
 */
public class ConfigServerHealthIndicator extends AbstractHealthIndicator {

    private ConfigServicePropertySourceLocator locator;
	private Environment env;

    public ConfigServerHealthIndicator(ConfigServicePropertySourceLocator locator) {
        this.env = new AbstractEnvironment() {
        	@Override
        	public String[] getActiveProfiles() {
        		return new String[] {"default"};
        	}
		};
        this.locator = locator;
    }

    @Override
    protected void doHealthCheck(Builder builder) throws Exception {
		PropertySource<?> propertySource = locator.locate(this.env);
		builder.up();
		if (propertySource instanceof CompositePropertySource) {
			List<String> sources = new ArrayList<>();
			for (PropertySource<?> ps : ((CompositePropertySource) propertySource).getPropertySources()) {
				sources.add(ps.getName());
			}
			builder.withDetail("propertySources", sources);
		} else if (propertySource!=null) {
			builder.withDetail("propertySources", propertySource.toString());
		} else {
			builder.unknown().withDetail("error", "no property sources located");
		}
    }
}
