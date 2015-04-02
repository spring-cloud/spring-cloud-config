package org.springframework.cloud.config.server.encryption;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.encrypt.TextEncryptor;

public class TextEncryptorLocator {

    private TextEncryptor encryptor;

    @Autowired(required = false)
    public void setEncryptor(TextEncryptor encryptor) {
        this.encryptor = encryptor;
    }

    public TextEncryptor get() {
        return encryptor;
    };

}
