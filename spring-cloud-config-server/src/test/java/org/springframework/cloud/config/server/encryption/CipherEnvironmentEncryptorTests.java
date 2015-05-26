/*
 * Copyright 2002-2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.config.server.encryption;

import java.util.Collections;

import org.junit.Test;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.context.encrypt.EncryptorFactory;
import org.springframework.security.crypto.encrypt.TextEncryptor;

import static java.util.UUID.randomUUID;
import static org.junit.Assert.assertEquals;

public class CipherEnvironmentEncryptorTests {
    TextEncryptor textEncryptor = new EncryptorFactory().create("foo");
    TextEncryptorLocator textEncryptorLocator =  new SingleTextEncryptorLocator(textEncryptor);
    EnvironmentEncryptor encryptor = new CipherEnvironmentEncryptor(textEncryptorLocator);

    @Test
    public void shouldDecryptEnvironment() {
        // given
        String secret = randomUUID().toString();

        // when
        Environment environment = new Environment("name", "profile", "label");
        environment.add(new PropertySource("a",
                Collections.<Object, Object>singletonMap(environment.getName(), "{cipher}" + textEncryptor.encrypt(secret))));

        // then
        assertEquals(secret, encryptor.decrypt(environment).getPropertySources().get(0).getSource().get(environment.getName()));
    }
}
