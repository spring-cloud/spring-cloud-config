/*
 * Copyright 2015-present the original author or authors.
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

package org.springframework.cloud.config.monitor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import org.springframework.cloud.config.server.environment.JGitEnvironmentProperties;
import org.springframework.cloud.config.server.environment.JGitEnvironmentRepository;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.http.HttpHeaders;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Dave Syer
 *
 */
public class PropertyPathEndpointTests {

	private PropertyPathEndpoint endpoint = new PropertyPathEndpoint(
			new CompositePropertyPathNotificationExtractor(Collections.emptyList()), services -> {
			});

	@Test
	public void testNotifyByForm() {
		assertThat(this.endpoint.notifyByForm(new HttpHeaders(), new ArrayList<>())).isEmpty();
	}

	@Test
	public void testNotifySeveral() {
		List<String> request = new ArrayList<>();
		request.add("/foo/bar.properties");
		request.add("/application.properties");
		assertThat(this.endpoint.notifyByForm(new HttpHeaders(), request).toString()).isEqualTo("[bar, *]");
	}

	@Test
	public void testNotifiesAffectedServices() {
		Set<String> notifiedServices = new LinkedHashSet<>();
		PropertyPathNotifier notifier = services -> notifiedServices.addAll(services);
		PropertyPathEndpoint endpoint = new PropertyPathEndpoint(
				new CompositePropertyPathNotificationExtractor(Collections.emptyList()), notifier);

		endpoint.notifyByPath(new HttpHeaders(), Collections.singletonMap("path", "foo.yml"));

		assertThat(notifiedServices).containsExactly("foo");
	}

	@Test
	public void testExpiresGitRefreshRateBeforeNotifyingServices() {
		JGitEnvironmentRepository repository = gitRepository();
		repository.setUri("https://github.com/example/config.git");
		repository.setLastRefresh(System.currentTimeMillis());
		JGitEnvironmentRepository unrelated = gitRepository();
		unrelated.setUri("https://github.com/example/other-config.git");
		long unrelatedLastRefresh = System.currentTimeMillis();
		unrelated.setLastRefresh(unrelatedLastRefresh);
		List<Long> lastRefreshWhenNotified = new ArrayList<>();
		PropertyPathNotifier notifier = services -> lastRefreshWhenNotified.add(repository.getLastRefresh());
		PropertyPathEndpoint endpoint = new PropertyPathEndpoint(new GithubPropertyPathNotificationExtractor(),
				List.of(notifier), MonitorConfigurationProperties.DEFAULT_MAX_DASHES,
				MonitorConfigurationProperties.DEFAULT_MAX_PATHS, Collections.emptyList(),
				List.of(repository, unrelated));

		HttpHeaders headers = new HttpHeaders();
		headers.set("X-Github-Event", "push");
		endpoint.notifyByPath(headers, Map.of("repository", Map.of("clone_url", repository.getUri()), "commits",
				List.of(Map.of("modified", List.of("foo.yml")))));

		assertThat(lastRefreshWhenNotified).containsExactly(0L);
		assertThat(unrelated.getLastRefresh()).isEqualTo(unrelatedLastRefresh);
	}

	@Test
	public void testExpiryFailureStillNotifiesApplications() {
		JGitEnvironmentProperties properties = new JGitEnvironmentProperties();
		properties.setUri("https://github.com/example/config.git");
		JGitEnvironmentRepository failing = new JGitEnvironmentRepository(new StandardEnvironment(), properties,
				ObservationRegistry.NOOP) {
			@Override
			public void expireRefreshRate(String repositoryUri) {
				throw new IllegalStateException("expected");
			}
		};
		JGitEnvironmentRepository repository = gitRepository();
		repository.setUri("https://github.com/example/config.git");
		repository.setLastRefresh(System.currentTimeMillis());
		Set<String> notifiedServices = new LinkedHashSet<>();
		PropertyPathEndpoint endpoint = new PropertyPathEndpoint(new GithubPropertyPathNotificationExtractor(),
				List.of(services -> notifiedServices.addAll(services)),
				MonitorConfigurationProperties.DEFAULT_MAX_DASHES, MonitorConfigurationProperties.DEFAULT_MAX_PATHS,
				Collections.emptyList(), List.of(failing, repository));
		HttpHeaders headers = new HttpHeaders();
		headers.set("X-Github-Event", "push");

		assertThat(endpoint.notifyByPath(headers, Map.of("repository", Map.of("clone_url", repository.getUri()),
				"commits", List.of(Map.of("modified", List.of("foo.yml"))))))
			.containsExactly("foo");

		assertThat(notifiedServices).containsExactly("foo");
		assertThat(repository.getLastRefresh()).isZero();
	}

	@ParameterizedTest
	@MethodSource("requestsWithoutRepositoryIdentity")
	public void testMissingRepositoryIdentityDoesNotExpireRefreshRate(Map<String, Object> request) {
		JGitEnvironmentRepository repository = gitRepository();
		repository.setUri("https://github.com/example/config.git");
		long lastRefresh = System.currentTimeMillis();
		repository.setLastRefresh(lastRefresh);
		Set<String> notifiedServices = new LinkedHashSet<>();
		PropertyPathEndpoint endpoint = new PropertyPathEndpoint(
				new CompositePropertyPathNotificationExtractor(Collections.emptyList()),
				List.of(services -> notifiedServices.addAll(services)),
				MonitorConfigurationProperties.DEFAULT_MAX_DASHES, MonitorConfigurationProperties.DEFAULT_MAX_PATHS,
				Collections.emptyList(), List.of(repository));

		assertThat(endpoint.notifyByPath(new HttpHeaders(), request)).containsExactly("foo");

		assertThat(repository.getLastRefresh()).isEqualTo(lastRefresh);
		assertThat(notifiedServices).containsExactly("foo");
	}

	static Stream<Map<String, Object>> requestsWithoutRepositoryIdentity() {
		return Stream.of(Map.of("path", "foo.yml"), Map.of("path", "foo.yml", "repository", "not a map"),
				Map.of("path", "foo.yml", "repository", Map.of("clone_url", 42, "ssh_url", " ")),
				Map.of("path", "foo.yml", "repository", Map.of("links", Map.of("clone", "not a list"))),
				Map.of("path", "foo.yml", "repository",
						Map.of("links", Map.of("clone", List.of("not a map", Map.of("href", 42))))),
				Map.of("path", "foo.yml", "repository", Map.of("url", "https://github.com/example/config.git")));
	}

	@ParameterizedTest
	@MethodSource("repositoryNotifications")
	public void testExpiresOnlyTheRepositoryIdentifiedByProviderPayload(HttpHeaders headers,
			Map<String, Object> request, String repositoryUri) {
		JGitEnvironmentRepository repository = gitRepository();
		repository.setUri(repositoryUri);
		repository.setLastRefresh(System.currentTimeMillis());
		JGitEnvironmentRepository unrelated = gitRepository();
		unrelated.setUri("https://example.com/unrelated/config.git");
		long unrelatedLastRefresh = System.currentTimeMillis();
		unrelated.setLastRefresh(unrelatedLastRefresh);
		Set<String> notifiedServices = new LinkedHashSet<>();
		PropertyPathEndpoint endpoint = new PropertyPathEndpoint(
				new CompositePropertyPathNotificationExtractor(List.of(new GithubPropertyPathNotificationExtractor(),
						new GitlabPropertyPathNotificationExtractor(),
						new BitbucketPropertyPathNotificationExtractor())),
				List.of(services -> notifiedServices.addAll(services)),
				MonitorConfigurationProperties.DEFAULT_MAX_DASHES, MonitorConfigurationProperties.DEFAULT_MAX_PATHS,
				Collections.emptyList(), List.of(repository, unrelated));

		assertThat(endpoint.notifyByPath(headers, request)).isNotEmpty();

		assertThat(repository.getLastRefresh()).isZero();
		assertThat(unrelated.getLastRefresh()).isEqualTo(unrelatedLastRefresh);
		assertThat(notifiedServices).isNotEmpty();
	}

	static Stream<Arguments> repositoryNotifications() {
		HttpHeaders githubHeaders = new HttpHeaders();
		githubHeaders.set("X-Github-Event", "push");
		HttpHeaders gitlabHeaders = new HttpHeaders();
		gitlabHeaders.set("X-Gitlab-Event", "Push Hook");
		HttpHeaders bitbucketCloudHeaders = new HttpHeaders();
		bitbucketCloudHeaders.set("X-Hook-UUID", "hook-id");
		bitbucketCloudHeaders.set("X-Event-Key", "repo:push");
		HttpHeaders bitbucketServerHeaders = new HttpHeaders();
		bitbucketServerHeaders.set("X-Request-Id", "request-id");
		bitbucketServerHeaders.set("X-Event-Key", "repo:refs_changed");
		HttpHeaders bitbucketMergedHeaders = new HttpHeaders();
		bitbucketMergedHeaders.set("X-Request-Id", "request-id");
		bitbucketMergedHeaders.set("X-Event-Key", "pr:merged");
		List<Map<String, Object>> commits = List.of(Map.of("modified", List.of("foo.yml")));
		return Stream.of(
				Arguments.of(githubHeaders,
						Map.of("repository", Map.of("ssh_url", "git@github.com:example/config.git"), "commits",
								commits),
						"git@github.com:example/config.git"),
				Arguments.of(githubHeaders,
						Map.of("repository", Map.of("git_url", "git://github.com/example/config.git"), "commits",
								commits),
						"git://github.com/example/config.git"),
				Arguments.of(gitlabHeaders,
						Map.of("project", Map.of("git_http_url", "https://gitlab.com/example/config.git"), "commits",
								commits),
						"https://gitlab.com/example/config.git"),
				Arguments.of(gitlabHeaders,
						Map.of("project", Map.of("git_ssh_url", "git@gitlab.com:example/config.git"), "commits",
								commits),
						"git@gitlab.com:example/config.git"),
				Arguments.of(gitlabHeaders,
						Map.of("repository", Map.of("url", "git@gitlab.com:example/config.git"), "commits", commits),
						"git@gitlab.com:example/config.git"),
				Arguments.of(bitbucketCloudHeaders,
						Map.of("repository", Map.of("links", Map.of("html",
								Map.of("href", "https://bitbucket.org/example/config"))), "push", Map.of("changes",
										List.of())),
						"https://bitbucket.org/example/config.git"),
				Arguments.of(bitbucketServerHeaders, Map.of("repository", Map.of("links", Map.of("clone",
						List.of(Map.of("name", "ssh", "href", "ssh://git@example.com:7999/example/config.git")))),
						"changes", List.of()), "ssh://git@example.com:7999/example/config.git"),
				Arguments.of(bitbucketMergedHeaders, Map.of("pullRequest",
						Map.of("toRef",
								Map.of("repository", Map.of("links", Map.of("clone",
										List.of(Map.of("href", "https://example.com/scm/example/config.git"))))),
								"fromRef",
								Map.of("repository", Map.of("clone_url", "https://example.com/unrelated/config.git")))),
						"https://example.com/scm/example/config.git"));
	}

	@Test
	public void testBitbucketWithoutCloneUrlsStillNotifiesApplications() {
		JGitEnvironmentRepository repository = gitRepository();
		repository.setUri("https://example.com/scm/example/config.git");
		long lastRefresh = System.currentTimeMillis();
		repository.setLastRefresh(lastRefresh);
		Set<String> notifiedServices = new LinkedHashSet<>();
		PropertyPathEndpoint endpoint = new PropertyPathEndpoint(new BitbucketPropertyPathNotificationExtractor(),
				List.of(services -> notifiedServices.addAll(services)),
				MonitorConfigurationProperties.DEFAULT_MAX_DASHES, MonitorConfigurationProperties.DEFAULT_MAX_PATHS,
				Collections.emptyList(), List.of(repository));
		HttpHeaders headers = new HttpHeaders();
		headers.set("X-Request-Id", "request-id");
		headers.set("X-Event-Key", "repo:refs_changed");

		assertThat(endpoint.notifyByPath(headers, Map.of("repository",
				Map.of("slug", "config", "project", Map.of("key", "EXAMPLE")), "changes", List.of())))
			.containsExactly("*");

		assertThat(repository.getLastRefresh()).isEqualTo(lastRefresh);
		assertThat(notifiedServices).containsExactly("*");
	}

	@Test
	public void testUnrecognizedWebhookDoesNotExpireGitRefreshRate() {
		JGitEnvironmentRepository repository = gitRepository();
		repository.setUri("https://github.com/example/config.git");
		long lastRefresh = System.currentTimeMillis();
		repository.setLastRefresh(lastRefresh);
		PropertyPathEndpoint endpoint = new PropertyPathEndpoint(new GithubPropertyPathNotificationExtractor(),
				List.of(services -> {
					throw new AssertionError("Unrecognized webhook must not notify applications");
				}), MonitorConfigurationProperties.DEFAULT_MAX_DASHES, MonitorConfigurationProperties.DEFAULT_MAX_PATHS,
				Collections.emptyList(), List.of(repository));

		assertThat(endpoint.notifyByPath(new HttpHeaders(), Map.of("repository",
				Map.of("clone_url", repository.getUri()), "commits", List.of(Map.of("modified", List.of("foo.yml"))))))
			.isEmpty();

		assertThat(repository.getLastRefresh()).isEqualTo(lastRefresh);
	}

	@Test
	public void testDoesNotExpireGitRefreshRateWhenNoServiceIsAffected() {
		JGitEnvironmentRepository repository = gitRepository();
		repository.setUri("https://github.com/example/config.git");
		long lastRefresh = System.currentTimeMillis();
		repository.setLastRefresh(lastRefresh);
		PropertyPathEndpoint endpoint = new PropertyPathEndpoint(
				new CompositePropertyPathNotificationExtractor(Collections.emptyList()), List.of(services -> {
				}), MonitorConfigurationProperties.DEFAULT_MAX_DASHES, MonitorConfigurationProperties.DEFAULT_MAX_PATHS,
				List.of("foo\\.yml"), List.of(repository));

		endpoint.notifyByPath(new HttpHeaders(),
				Map.of("path", "foo.yml", "repository", Map.of("clone_url", repository.getUri())));

		assertThat(repository.getLastRefresh()).isEqualTo(lastRefresh);
	}

	@Test
	public void testNotifyAll() {
		assertThat(this.endpoint.notifyByPath(new HttpHeaders(), Collections.singletonMap("path", "application.yml"))
			.toString()).isEqualTo("[*]");
	}

	@Test
	public void testNotifyAllWithProfile() {
		assertThat(
				this.endpoint.notifyByPath(new HttpHeaders(), Collections.singletonMap("path", "application-local.yml"))
					.toString())
			.isEqualTo("[application-local, *]");
	}

	@Test
	public void testNotifyOne() {
		assertThat(
				this.endpoint.notifyByPath(new HttpHeaders(), Collections.singletonMap("path", "foo.yml")).toString())
			.isEqualTo("[foo]");
	}

	@Test
	public void testNotifyOneWithWindowsPath() {
		assertThat(
				this.endpoint.notifyByPath(new HttpHeaders(), Collections.singletonMap("path", "C:\\config\\foo.yml"))
					.toString())
			.isEqualTo("[foo]");
	}

	@Test
	public void testNotifyOneWithProfile() {
		assertThat(this.endpoint.notifyByPath(new HttpHeaders(), Collections.singletonMap("path", "foo-local.yml"))
			.toString()).isEqualTo("[foo-local, foo]");
	}

	@Test
	public void testNotifyMultiDash() {
		assertThat(this.endpoint.notifyByPath(new HttpHeaders(), Collections.singletonMap("path", "foo-local-dev.yml"))
			.toString()).isEqualTo("[foo-local-dev, foo-local, foo]");
	}

	@Test
	public void testNotifyLimitsDashes() {
		PropertyPathEndpoint limitedEndpoint = new PropertyPathEndpoint(
				new CompositePropertyPathNotificationExtractor(Collections.emptyList()), services -> {
				}, 2);
		StringBuilder path = new StringBuilder();
		for (int i = 0; i < 1000; i++) {
			path.append("a-");
		}
		path.append("a.yml");
		assertThat(limitedEndpoint.notifyByPath(new HttpHeaders(), Collections.singletonMap("path", path.toString())))
			.hasSize(2);
	}

	@Test
	public void testNotifyIgnoresConfiguredPaths() {
		PropertyPathEndpoint ignoredEndpoint = new PropertyPathEndpoint(
				new CompositePropertyPathNotificationExtractor(Collections.emptyList()), List.of(services -> {
				}), 20, 20, Collections.singletonList("docker-compose/.*"));

		List<String> request = new ArrayList<>();
		request.add("docker-compose/docker-compose.yml");
		request.add("foo.yml");

		assertThat(ignoredEndpoint.notifyByForm(new HttpHeaders(), request).toString()).isEqualTo("[foo]");
	}

	@Test
	public void testNotifyLimitsPaths() {
		PropertyPathEndpoint limitedEndpoint = new PropertyPathEndpoint(
				new CompositePropertyPathNotificationExtractor(Collections.emptyList()), List.of(services -> {
				}), 20, 2, Collections.emptyList());

		List<String> request = new ArrayList<>();
		request.add("foo.yml");
		request.add("bar.yml");
		request.add("baz.yml");

		assertThat(limitedEndpoint.notifyByForm(new HttpHeaders(), request).toString()).isEqualTo("[foo, bar]");
	}

	private static JGitEnvironmentRepository gitRepository() {
		JGitEnvironmentProperties properties = new JGitEnvironmentProperties();
		properties.setRefreshRate(60);
		return new JGitEnvironmentRepository(new StandardEnvironment(), properties, ObservationRegistry.NOOP);
	}

}
