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
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
 * inside a CDATA section will appear with its escape sequences. Matching ciphers are
 * replaced wherever they occur in the raw text, including comments and processing
 * instructions. A decrypted value containing characters that are illegal in XML results
 * in an {@link IOException}.
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

		if (valsToDecrypt.isEmpty()) {
			return text;
		}
		Map<String, String> replacements = new HashMap<>();
		for (String value : valsToDecrypt) {
			String decryptedValue = decryptValue(value.replace(CIPHER_MARKER, ""), environment.getName(),
					environment.getProfiles());
			replacements.put(value, escape(decryptedValue));
		}
		// Replace in a single pass, trying the longest cipher first, so that a cipher
		// which is a prefix of another one cannot corrupt the longer one.
		String regex = replacements.keySet()
			.stream()
			.sorted(Comparator.comparingInt(String::length).reversed())
			.map(Pattern::quote)
			.collect(Collectors.joining("|"));
		Matcher matcher = Pattern.compile(regex).matcher(text);
		StringBuilder result = new StringBuilder(text.length());
		while (matcher.find()) {
			matcher.appendReplacement(result, Matcher.quoteReplacement(replacements.get(matcher.group())));
		}
		matcher.appendTail(result);
		return result.toString();
	}

	private void addIfEncrypted(Set<String> values, String value) {
		if (value != null && value.trim().startsWith(CIPHER_MARKER)) {
			values.add(value.trim());
		}
	}

	/**
	 * Escapes the characters that are not allowed to appear literally in element text or
	 * attribute values, so that the decrypted resource remains well-formed XML.
	 * @throws IOException if the value contains a character that is not allowed in XML
	 */
	private String escape(String value) throws IOException {
		StringBuilder escaped = new StringBuilder(value.length());
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			if (Character.isHighSurrogate(c) && i + 1 < value.length()
					&& Character.isLowSurrogate(value.charAt(i + 1))) {
				escaped.append(c).append(value.charAt(++i));
				continue;
			}
			if (!isValidXmlChar(c)) {
				throw new IOException("Decrypted value contains a character that is not allowed in XML");
			}
			switch (c) {
				case '&' -> escaped.append("&amp;");
				case '<' -> escaped.append("&lt;");
				case '>' -> escaped.append("&gt;");
				case '"' -> escaped.append("&quot;");
				case '\'' -> escaped.append("&apos;");
				case '\n' -> escaped.append("&#10;");
				case '\r' -> escaped.append("&#13;");
				case '\t' -> escaped.append("&#9;");
				default -> escaped.append(c);
			}
		}
		return escaped.toString();
	}

	private static boolean isValidXmlChar(char c) {
		return c == '\t' || c == '\n' || c == '\r' || (c >= 0x20 && c <= 0xD7FF) || (c >= 0xE000 && c <= 0xFFFD);
	}

}
