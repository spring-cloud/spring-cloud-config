package org.springframework.cloud.config.client;

import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health.Builder;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
                CompositePropertySource composite = CompositePropertySource.class.cast(propertySource);
                Field field = ReflectionUtils.findField(CompositePropertySource.class,
                        "propertySources");
                field.setAccessible(true);
                @SuppressWarnings("unchecked")
				Set<PropertySource<?>> propertySources = (Set<PropertySource<?>>) field.get(composite);
                List<String> sources = new ArrayList<>();
                for (PropertySource<?> ps : propertySources) {
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
