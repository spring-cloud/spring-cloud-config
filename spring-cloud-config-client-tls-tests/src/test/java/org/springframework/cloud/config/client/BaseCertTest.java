/*
 * Copyright 2018-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.config.client;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.security.KeyStore;

import org.junit.BeforeClass;

public class BaseCertTest {
    
    protected static final String KEY_STORE_PASSWORD = "test-key-store-password";
    protected static final String KEY_PASSWORD = "test-key-password";
    protected static final String WRONG_PASSWORD = "test-wrong-password";
    
    protected static File caCert;
    protected static File wrongCaCert;
    protected static File trustStore;
    
    protected static File serverCert;
    protected static File clientCert;
    protected static File wrongClientCert;
    
    @BeforeClass
    public static void createCertificates() throws Exception {
        KeyTool tool = new KeyTool();
        
        KeyAndCert ca = tool.createCA("MyCA");
        KeyAndCert server = ca.sign("server");
        KeyAndCert client = ca.sign("client");
        
        caCert = saveCert(ca);
        trustStore = saveTrustStore(ca);
        serverCert = saveKeyAndCert(server);
        clientCert = saveKeyAndCert(client);
        
        KeyAndCert wrongCa = tool.createCA("WrongCA");
        KeyAndCert wrongClient = wrongCa.sign("client");
        
        wrongCaCert = saveCert(wrongCa);
        wrongClientCert = saveKeyAndCert(wrongClient);
        
        System.setProperty("javax.net.ssl.trustStore", trustStore.getAbsolutePath());
        System.setProperty("javax.net.ssl.trustStoreType", "JKS");
    }
    
    private static File saveKeyAndCert(KeyAndCert keyCert) throws Exception {
        return saveKeyStore(keyCert.subject(), () -> keyCert.storeKeyAndCert(KEY_PASSWORD));
    }
    
    private static File saveCert(KeyAndCert keyCert) throws Exception {
        return saveKeyStore(keyCert.subject(), () -> keyCert.storeCert());
    }
    
    private static File saveKeyStore(String prefix, KeyStoreSupplier func) throws Exception {
        File result = File.createTempFile(prefix, ".p12");
        result.deleteOnExit();
        
        try (OutputStream output = new FileOutputStream(result)) {
            KeyStore store = func.createKeyStore();
            store.store(output, KEY_STORE_PASSWORD.toCharArray());
        }
        return result;
    }
    
    private static File saveTrustStore(KeyAndCert keyCert) throws Exception {
    	File result = File.createTempFile(keyCert.subject(), "jks");
        try (OutputStream output = new FileOutputStream(result)) {
            KeyStore store = keyCert.storeCert("JKS");
            store.store(output, new char [0]);
        }
        return result;
    }
    
    interface KeyStoreSupplier {
        
        public KeyStore createKeyStore() throws Exception;
    }
}