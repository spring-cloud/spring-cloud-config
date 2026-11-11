/*
 * Copyright 2013-present the original author or authors.
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


package org.springframework.cloud.config.server.encryption;

import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.stereotype.Component;

/**
 * {@link org.springframework.cloud.config.server.encryption.ResourceEncryptor}
 * implementation that can decrypt element text and attribute values prefixed with
 * {cipher} marker in an XML file. Decrypted values are XML-escaped, so a value placed
 * inside a CDATA section will appear with its escape sequences.
 *
 * @author Asish Kumar
 */
@Component
public class CipherResourceXmlEncryptor extends AbstractCipherResourceEncryptor implements ResourceEncryptor {

	private static final List<String> SUPPORTED_EXTENSIONS = Arrays.asList("xml");

	private final XMLInputFactory factory;

	public CipherResourceXmlEncryptor(TextEncryptorLocator encryptor) {
		super(encryptor);
		this.factory = XMLInputFactory.newFactory();
		// The resources are untrusted input, do not resolve DTDs or external entities.
		this.factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
		this.factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
		// Report adjacent text, CDATA and entity references as a single event.
		this.factory.setProperty(XMLInputFactory.IS_COALESCING, true);
	}

	@Override
	public List<String> getSupportedExtensions() {
		return SUPPORTED_EXTENSIONS;
	}

	@Override
	public String decrypt(String text, Environment environment) throws IOException {
		Set<String> valsToDecrypt = new LinkedHashSet<>();
		XMLStreamReader reader = null;
		try {
			reader = this.factory.createXMLStreamReader(new StringReader(text));
			while (reader.hasNext()) {
				int event = reader.next();
				if (event == XMLStreamConstants.START_ELEMENT) {
					for (int i = 0; i < reader.getAttributeCount(); i++) {
						addIfEncrypted(valsToDecrypt, reader.getAttributeValue(i));
					}
				}
				else if (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA) {
					addIfEncrypted(valsToDecrypt, reader.getText());
				}
			}
		}
		catch (XMLStreamException e) {
			throw new IOException("Unable to parse XML resource", e);
		}
		finally {
			if (reader != null) {
				try {
					reader.close();
				}
				catch (XMLStreamException ignored) {
					// nothing to do
				}
			}
		}

		for (String value : valsToDecrypt) {
			String decryptedValue = decryptValue(value.replace(CIPHER_MARKER, ""), environment.getName(),
					environment.getProfiles());
			text = text.replace(value, escape(decryptedValue));
		}
		return text;
	}

	private void addIfEncrypted(Set<String> values, String value) {
		if (value != null && value.startsWith(CIPHER_MARKER)) {
			values.add(value.trim());
		}
	}

	/**
	 * Escapes the characters that are not allowed to appear literally in element text or
	 * attribute values, so that the decrypted resource remains well-formed XML.
	 */
	private String escape(String value) {
		StringBuilder escaped = new StringBuilder(value.length());
		for (char c : value.toCharArray()) {
			switch (c) {
				case '&' -> escaped.append("&amp;");
				case '<' -> escaped.append("&lt;");
				case '>' -> escaped.append("&gt;");
				case '"' -> escaped.append("&quot;");
				case '\'' -> escaped.append("&apos;");
				default -> escaped.append(c);
			}
		}
		return escaped.toString();
	}

}
