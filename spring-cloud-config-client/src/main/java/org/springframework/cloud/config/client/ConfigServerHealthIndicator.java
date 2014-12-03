package org.springframework.cloud.config.client;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health.Builder;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;

/**
 * @author Spencer Gibb
 */
public class ConfigServerHealthIndicator extends AbstractHealthIndicator {

    private Environment env;
    private ConfigServicePropertySourceLocator locator;

    public ConfigServerHealthIndicator(Environment env, ConfigServicePropertySourceLocator locator) {
        this.env = env;
        this.locator = locator;
    }

    @Override
    protected void doHealthCheck(Builder builder) throws Exception {
        try {
            PropertySource<?> propertySource = locator.locate(env);
            builder.up();
            if (propertySource instanceof CompositePropertySource) {
                List<String> sources = new ArrayList<>();
                for (PropertySource<?> ps : ((CompositePropertySource) propertySource).getPropertySources()) {
                    sources.add(ps.getName());
                }
                builder.withDetail("propertySources", sources);
            } else {
                builder.withDetail("propertySources", propertySource.toString());
            }
        } catch (Exception e) {
            builder.down(e);
        }
    }
}
