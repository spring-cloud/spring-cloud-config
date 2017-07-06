/*
 * Copyright 2015 the original author or authors.
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

package org.springframework.cloud.config.server.ssh;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.KeyPair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import javax.annotation.PostConstruct;
import java.util.*;

import static java.lang.String.format;
import static org.springframework.util.StringUtils.hasText;

/**
 * Validate SSH related properties
 *
 * @author Ollie Hughes
 */
@Component
@EnableConfigurationProperties(SshUriProperties.class)
public class SshPropertyValidator {

    private final SshUriProperties sshUriProperties;
	private final JSch jsch = new JSch();
	private static final Set<String> VALID_HOST_KEY_ALGORITHMS = new LinkedHashSet<>(Arrays.asList(
			"ssh-dss","ssh-rsa","ecdsa-sha2-nistp256","ecdsa-sha2-nistp384","ecdsa-sha2-nistp521"));
	private static final String GIT_PROPERTY_PREFIX = "spring.cloud.config.server.git.";

	@Autowired
    public SshPropertyValidator(SshUriProperties sshUriProperties) {
        this.sshUriProperties = sshUriProperties;
    }

	static boolean isSshUri(Object uri) {
		return uri != null && (uri.toString().startsWith("ssh") || uri.toString().startsWith("git"));
	}

	@PostConstruct
    public void validateSshConfigurationProperties() {
		List<SshUriProperties> allRepoProperties = new ArrayList<>();
		allRepoProperties.add(sshUriProperties);
		Map<String, SshUriProperties> repos = sshUriProperties.getRepos();
		if (repos != null) {
			allRepoProperties.addAll(repos.values());
		}
		for (SshUriProperties repoProperties : allRepoProperties) {
			if(isSshUri(repoProperties.getUri()) && sshUriProperties.isIgnoreLocalSshSettings()){
				validatePrivateKeyPresent();
				validatePrivateKeyFormat();
				validateAlgorithmSpecifiedWhenHostKeySet();
				validateHostKeySpecifiedWhenAlgorithmSet();
				validateHostKeyAlgorithmSupported();
			}
		}
	}

	protected void validatePrivateKeyFormat() {
		try {
			KeyPair.load(jsch, sshUriProperties.getPrivateKey().getBytes(), null);
		} catch (JSchException e) {
			throw new IllegalStateException(format("Property '%sprivateKey' contains an invalid value", GIT_PROPERTY_PREFIX));
		}
	}

	protected void validateHostKeyAlgorithmSupported() {
		if (hasText(sshUriProperties.getHostKeyAlgorithm())) {
			Assert.state(VALID_HOST_KEY_ALGORITHMS.contains(sshUriProperties.getHostKeyAlgorithm()),
					format("Property '%shostKeyAlgorithm' must be one of %s", GIT_PROPERTY_PREFIX, VALID_HOST_KEY_ALGORITHMS));
		}
	}

	protected void validatePrivateKeyPresent() {
		Assert.state(sshUriProperties.getPrivateKey() != null,
				format("Property '%sprivateKey' must be set when '%signoreLocalSshSettings' is set to 'true'", GIT_PROPERTY_PREFIX, GIT_PROPERTY_PREFIX));
	}

	protected void validateHostKeySpecifiedWhenAlgorithmSet() {
		if (hasText(sshUriProperties.getHostKeyAlgorithm())) {
			Assert.state(hasText(sshUriProperties.getHostKey()),
					format("Property '%shostKey' must be set when 'hostKeyAlgorithm' is specified", GIT_PROPERTY_PREFIX));
		}
	}

	protected void validateAlgorithmSpecifiedWhenHostKeySet() {
		if (hasText(sshUriProperties.getHostKey())) {
			Assert.state(hasText(sshUriProperties.getHostKeyAlgorithm()),
					format("Property '%shostKeyAlgorithm' must be set when 'hostKey' is specified", GIT_PROPERTY_PREFIX));
		}
	}

}
