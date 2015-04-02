package org.springframework.cloud.config.server.encryption;

import org.junit.Test;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.context.encrypt.EncryptorFactory;
import org.springframework.security.crypto.encrypt.TextEncryptor;

import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class CipherEnvironmentEncryptorTests {


    TextEncryptor textEncryptor = new EncryptorFactory().create("foo");

    TextEncryptorLocator textEncryptorLocator =  new TextEncryptorLocator();

    EnvironmentEncryptor encryptor = new CipherEnvironmentEncryptor(textEncryptorLocator);


    @Test
    public void shouldDecryptEnvironment() {
        // given
        textEncryptorLocator.setEncryptor(textEncryptor);

        Environment environment = new Environment("name", "profile");
        String secret = environment.toString();

        // when
        String encrypted = textEncryptor.encrypt(secret);
        Environment clone = new Environment(environment.getName(), environment.getProfiles(), environment.getLabel());
        clone.add(new PropertySource("a",
                Collections.<Object, Object>singletonMap(environment.getName(), "{cipher}" + encrypted)));

        // then
        assertEquals(secret, encryptor.decrypt(clone).getPropertySources().get(0).getSource().get(environment.getName()));
    }
}
