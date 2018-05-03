package org.springframework.cloud.config.client;

import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ApplicationContextEvent;

/**
 * @author dyc87112
 *
 */
public class RefreshConfigClientPropertiesEvent extends ApplicationContextEvent {

    public RefreshConfigClientPropertiesEvent(ApplicationContext source) {
        super(source);
    }
}
