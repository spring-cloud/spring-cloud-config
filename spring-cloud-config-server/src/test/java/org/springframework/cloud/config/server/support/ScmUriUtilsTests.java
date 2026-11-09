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

package org.springframework.cloud.config.server.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ScmUriUtils}, in particular that credentials embedded in a repository
 * uri are masked before the uri is logged or reported.
 */
public class ScmUriUtilsTests {

	@Test
	public void usernameAndPasswordAreMasked() {
		assertThat(ScmUriUtils.maskUserInfo("https://ryan:secret@example.com/config-repo.git"))
			.isEqualTo("https://***:***@example.com/config-repo.git");
	}

	@Test
	public void usernameOnlyIsMasked() {
		assertThat(ScmUriUtils.maskUserInfo("https://ryan@example.com/config-repo.git"))
			.isEqualTo("https://***@example.com/config-repo.git");
	}

	@Test
	public void accessTokenInTheUsernameIsMasked() {
		assertThat(ScmUriUtils.maskUserInfo("https://ghp_AbC123@github.com/org/config-repo.git"))
			.isEqualTo("https://***@github.com/org/config-repo.git");
	}

	@Test
	public void uriWithoutCredentialsIsUnchanged() {
		assertThat(ScmUriUtils.maskUserInfo("https://example.com/config-repo.git"))
			.isEqualTo("https://example.com/config-repo.git");
	}

	@Test
	public void fileUriIsUnchanged() {
		assertThat(ScmUriUtils.maskUserInfo("file:./target/repos/config-repo"))
			.isEqualTo("file:./target/repos/config-repo");
	}

	@Test
	public void scpStyleSshUriUserIsMasked() {
		assertThat(ScmUriUtils.maskUserInfo("git@github.com:org/config-repo.git"))
			.isEqualTo("***@github.com:org/config-repo.git");
	}

	@Test
	public void scpStyleSshUriWithoutUserIsUnchanged() {
		assertThat(ScmUriUtils.maskUserInfo("github.com:org/config-repo.git"))
			.isEqualTo("github.com:org/config-repo.git");
	}

	@Test
	public void sshUriUserIsMasked() {
		assertThat(ScmUriUtils.maskUserInfo("ssh://git@github.com/org/config-repo.git"))
			.isEqualTo("ssh://***@github.com/org/config-repo.git");
	}

	@Test
	public void localPathContainingAnAtSignIsUnchanged() {
		assertThat(ScmUriUtils.maskUserInfo("/home/ryan@example.com/config-repo"))
			.isEqualTo("/home/ryan@example.com/config-repo");
	}

	@Test
	public void fileUriContainingAnAtSignIsUnchanged() {
		assertThat(ScmUriUtils.maskUserInfo("file:///home/ryan@example.com/config-repo"))
			.isEqualTo("file:///home/ryan@example.com/config-repo");
	}

	@Test
	public void atSignInThePathIsUnchanged() {
		assertThat(ScmUriUtils.maskUserInfo("https://example.com/config-repo@1.0.git"))
			.isEqualTo("https://example.com/config-repo@1.0.git");
	}

	@Test
	public void placeholdersInUriAreNotEncoded() {
		assertThat(ScmUriUtils.maskUserInfo("https://ryan:secret@example.com/{application}/config-repo.git"))
			.isEqualTo("https://***:***@example.com/{application}/config-repo.git");
	}

	@Test
	public void alreadyEncodedUriIsNotEncodedAgain() {
		assertThat(ScmUriUtils.maskUserInfo("https://ryan:secret@example.com/a%20path/config-repo.git"))
			.isEqualTo("https://***:***@example.com/a%20path/config-repo.git");
	}

	@Test
	public void portIsPreserved() {
		assertThat(ScmUriUtils.maskUserInfo("https://ryan:secret@example.com:8443/config-repo.git"))
			.isEqualTo("https://***:***@example.com:8443/config-repo.git");
	}

	@Test
	public void unencodedAtSignInPasswordIsMasked() {
		assertThat(ScmUriUtils.maskUserInfo("https://ryan:p@ss@example.com/config-repo.git"))
			.isEqualTo("https://***:***@example.com/config-repo.git");
	}

	@Test
	public void unparseableUriStillHasItsUserInfoMasked() {
		// an unparseable port makes UriComponentsBuilder throw
		assertThat(ScmUriUtils.maskUserInfo("https://ryan:secret@example.com:notaport/config-repo.git"))
			.isEqualTo("https://***:***@example.com:notaport/config-repo.git");
	}

	@Test
	public void maskingAnAlreadyMaskedUriChangesNothing() {
		assertThat(ScmUriUtils.maskUserInfo("https://***:***@example.com/config-repo.git"))
			.isEqualTo("https://***:***@example.com/config-repo.git");
		assertThat(ScmUriUtils.maskUserInfo("https://***@example.com/config-repo.git"))
			.isEqualTo("https://***@example.com/config-repo.git");
		assertThat(ScmUriUtils.maskUserInfo("***@github.com:org/config-repo.git"))
			.isEqualTo("***@github.com:org/config-repo.git");
	}

	@Test
	public void nullAndEmptyUrisAreReturnedAsIs() {
		assertThat(ScmUriUtils.maskUserInfo(null)).isNull();
		assertThat(ScmUriUtils.maskUserInfo("")).isEmpty();
	}

}
