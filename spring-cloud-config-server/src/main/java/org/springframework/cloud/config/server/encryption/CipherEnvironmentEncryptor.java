package org.springframework.cloud.config.server.encryption;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

@Component
public class CipherEnvironmentEncryptor implements EnvironmentEncryptor {

    private static Log logger = LogFactory.getLog(CipherEnvironmentEncryptor.class);

    private final TextEncryptorLocator encryptorLocator;

    @Autowired
    public CipherEnvironmentEncryptor(TextEncryptorLocator encryptorLocator) {
        this.encryptorLocator = encryptorLocator;
    }

    @Override
    public Environment decrypt(Environment environment) {
        TextEncryptor encryptor = encryptorLocator.get();
        return encryptor != null ? decrypt(environment, encryptor) : environment;
    }

    private Environment decrypt(Environment environment, TextEncryptor encryptor) {
        Environment result = new Environment(environment.getName(), environment.getProfiles(), environment.getLabel());

        for (PropertySource source : environment.getPropertySources()) {
            Map<Object, Object> map = new LinkedHashMap<Object, Object>(
                    source.getSource());
            for (Map.Entry<Object,Object> entry : new LinkedHashSet<>(map.entrySet())) {
                Object key = entry.getKey();
                String name = key.toString();
                String value = entry.getValue().toString();
                if (value.startsWith("{cipher}")) {
                    map.remove(key);

                    try {
                        value = value == null ? null : encryptor.decrypt(value
                                .substring("{cipher}".length()));
                    }
                    catch (Exception e) {
                        value = "<n/a>";
                        name = "invalid." + name;
                        logger.warn("Cannot decrypt key: " + key + " ("
                                + e.getClass() + ": " + e.getMessage() + ")");
                    }
                    map.put(name, value);

                }
            }
            result.add(new PropertySource(source.getName(), map));
        }
        return result;
    }

}
