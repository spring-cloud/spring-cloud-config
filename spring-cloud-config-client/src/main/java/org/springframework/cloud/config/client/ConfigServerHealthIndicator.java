package org.springframework.cloud.config.client;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.BeansException;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health.Builder;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;

/**
 * @author Spencer Gibb
 * @author Marcos Barbero
 */
public class ConfigServerHealthIndicator extends AbstractHealthIndicator implements ApplicationContextAware {

	private ConfigServicePropertySourceLocator locator;
	private ConfigClientHealthProperties properties;
	private Environment environment;

	private long lastAccess = 0;

	private PropertySource<?> cached;

	private ApplicationContext applicationContext;

	public ConfigServerHealthIndicator(ConfigServicePropertySourceLocator locator,
			Environment environment, ConfigClientHealthProperties properties) {
		this.environment = environment;
		this.locator = locator;
		this.properties = properties;
	}

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    protected void doHealthCheck(Builder builder) throws Exception {
		PropertySource<?> propertySource = getPropertySource();
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

	private PropertySource<?> getPropertySource() {
		long accessTime = System.currentTimeMillis();
		if (isCacheStale(accessTime)) {
		    this.applicationContext.publishEvent(new RefreshConfigClientPropertiesEvent(this.applicationContext));
			this.lastAccess = accessTime;
			this.cached = locator.locate(this.environment);
		}
		return this.cached;
	}

	private boolean isCacheStale(long accessTime) {
		if (this.cached == null) {
			return true;
		}
		return (accessTime - this.lastAccess) >= this.properties.getTimeToLive();
	}

}
