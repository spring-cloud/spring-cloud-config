/*
 * Copyright 2015-2019 the original author or authors.
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

package org.springframework.cloud.config.server;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URISyntaxException;

import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.transport.FetchConnection;
import org.eclipse.jgit.transport.PushConnection;
import org.eclipse.jgit.transport.SshConfigStore;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.transport.sshd.SshdSessionFactory;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.config.server.environment.MultipleJGitEnvironmentProperties;
import org.springframework.cloud.config.server.environment.MultipleJGitEnvironmentRepository;
import org.springframework.cloud.config.server.ssh.FileBasedSshSessionFactory;
import org.springframework.cloud.config.server.ssh.FileBasedSshTransportConfigCallback;
import org.springframework.cloud.config.server.ssh.PropertiesBasedSshTransportConfigCallback;
import org.springframework.cloud.config.server.ssh.PropertyBasedSshSessionFactory;
import org.springframework.cloud.config.server.ssh.SshPropertyValidator;
import org.springframework.cloud.config.server.test.TestConfigServerApplication;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for property based SSH config support.
 *
 * @author Ollie Hughes
 */
public class TransportConfigurationIntegrationTests {

	public static class PropertyBasedCallbackTest {

		@SpringBootTest(classes = { TestConfigServerApplication.class, SshPropertyValidator.class },
				webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
				properties = { "spring.config.additional-location=optional:file:/ssh/,optional:classpath:/ssh/",
						"spring.config.name:ssh-private-key-block" })
		@ActiveProfiles({ "test", "git" })
		public static class StaticTest {

			@Autowired
			private MultipleJGitEnvironmentRepository jGitEnvironmentRepository;

			@Test
			public void propertyBasedTransportCallbackIsConfigured() {
				TransportConfigCallback transportConfigCallback = this.jGitEnvironmentRepository
						.getTransportConfigCallback();
				assertThat(transportConfigCallback).isInstanceOf(PropertiesBasedSshTransportConfigCallback.class);
			}

			@Test
			public void propertyBasedSessionFactoryIsUsedForSshTransports() throws Exception {
				this.jGitEnvironmentRepository.afterPropertiesSet();

				SshTransport sshTransport = DummySshTransport.newInstance();
				this.jGitEnvironmentRepository.getTransportConfigCallback().configure(sshTransport);

				assertThat(sshTransport.getSshSessionFactory()).isInstanceOf(PropertyBasedSshSessionFactory.class);
			}

		}

		@SpringBootTest(classes = { TestConfigServerApplication.class, SshPropertyValidator.class },
				webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
				properties = { "spring.config.additional-location=optional:file:/ssh/,optional:classpath:/ssh/",
						"spring.config.name:ssh-private-key-block-list" })
		@ActiveProfiles({ "test", "composite" })
		public static class ListTest {

			@Autowired
			private MultipleJGitEnvironmentRepository jGitEnvironmentRepository;

			@Test
			public void propertyBasedTransportCallbackIsConfigured() {
				TransportConfigCallback transportConfigCallback = this.jGitEnvironmentRepository
						.getTransportConfigCallback();
				assertThat(transportConfigCallback).isInstanceOf(PropertiesBasedSshTransportConfigCallback.class);
			}

			@Test
			public void propertyBasedSessionFactoryIsUsedForSshTransports() throws Exception {
				this.jGitEnvironmentRepository.afterPropertiesSet();

				SshTransport sshTransport = DummySshTransport.newInstance();
				this.jGitEnvironmentRepository.getTransportConfigCallback().configure(sshTransport);

				assertThat(sshTransport.getSshSessionFactory()).isInstanceOf(PropertyBasedSshSessionFactory.class);
			}

		}

	}

	public static class PrivateKeyPropertyWithLineBreaks {

		@SpringBootTest(classes = { TestConfigServerApplication.class, SshPropertyValidator.class },
				webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
				properties = { "spring.config.additional-location=optional:file:/ssh/,optional:classpath:/ssh/",
						"spring.config.name:ssh-private-key-newline" })
		@ActiveProfiles({ "test", "git" })
		public static class StaticTest {

			@Autowired
			private MultipleJGitEnvironmentRepository jGitEnvironmentRepository;

			@Test
			public void privateKeyPropertyWithLineBreaks() {
				TransportConfigCallback callback = this.jGitEnvironmentRepository.getTransportConfigCallback();
				assertThat(callback).isInstanceOf(PropertiesBasedSshTransportConfigCallback.class);

				PropertiesBasedSshTransportConfigCallback configCallback = (PropertiesBasedSshTransportConfigCallback) callback;
				assertThat(configCallback.getSshUriProperties().getPrivateKey())
						.isEqualTo(TestProperties.TEST_PRIVATE_KEY_1);
			}

		}

		@SpringBootTest(classes = { TestConfigServerApplication.class, SshPropertyValidator.class },
				webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
				properties = { "spring.config.additional-location=optional:file:/ssh/,optional:classpath:/ssh/",
						"spring.config.name:ssh-private-key-newline-list" })
		@ActiveProfiles({ "test", "composite" })
		public static class ListTest {

			@Autowired
			private MultipleJGitEnvironmentRepository jGitEnvironmentRepository;

			@Test
			public void privateKeyPropertyWithLineBreaks() {
				TransportConfigCallback callback = this.jGitEnvironmentRepository.getTransportConfigCallback();
				assertThat(callback).isInstanceOf(PropertiesBasedSshTransportConfigCallback.class);

				PropertiesBasedSshTransportConfigCallback configCallback = (PropertiesBasedSshTransportConfigCallback) callback;
				assertThat(configCallback.getSshUriProperties().getPrivateKey())
						.isEqualTo(TestProperties.TEST_PRIVATE_KEY_1);
			}

		}

	}

	public static class SshPropertiesWithinNestedRepo {

		@SpringBootTest(classes = { TestConfigServerApplication.class, SshPropertyValidator.class },
				webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
				properties = { "spring.config.additional-location=optional:file:/ssh/,optional:classpath:/ssh/",
						"spring.config.name:ssh-nested-settings" })
		@ActiveProfiles({ "test", "git" })
		public static class StaticTest {

			@Autowired
			private MultipleJGitEnvironmentRepository jGitEnvironmentRepository;

			@Test
			public void sshPropertiesWithinNestedRepo() {
				TransportConfigCallback callback = this.jGitEnvironmentRepository.getTransportConfigCallback();
				assertThat(callback).isInstanceOf(PropertiesBasedSshTransportConfigCallback.class);

				PropertiesBasedSshTransportConfigCallback configCallback = (PropertiesBasedSshTransportConfigCallback) callback;
				MultipleJGitEnvironmentProperties sshUriProperties = configCallback.getSshUriProperties();
				assertThat(configCallback.getSshUriProperties().getPrivateKey())
						.isEqualTo(TestProperties.TEST_PRIVATE_KEY_1);

				assertThat(sshUriProperties.getRepos().get("repo1")).isNotNull();
				assertThat(sshUriProperties.getRepos().get("repo1").getPrivateKey())
						.isEqualTo(TestProperties.TEST_PRIVATE_KEY_2);
			}

		}

		@SpringBootTest(classes = { TestConfigServerApplication.class, SshPropertyValidator.class },
				webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
				properties = { "spring.config.additional-location=optional:file:/ssh/,optional:classpath:/ssh/",
						"spring.config.name:ssh-nested-settings-list" })
		@ActiveProfiles({ "test", "composite" })
		public static class ListTest {

			@Autowired
			private MultipleJGitEnvironmentRepository jGitEnvironmentRepository;

			@Test
			public void sshPropertiesWithinNestedRepo() {
				TransportConfigCallback callback = this.jGitEnvironmentRepository.getTransportConfigCallback();
				assertThat(callback).isInstanceOf(PropertiesBasedSshTransportConfigCallback.class);

				PropertiesBasedSshTransportConfigCallback configCallback = (PropertiesBasedSshTransportConfigCallback) callback;
				MultipleJGitEnvironmentProperties sshUriProperties = configCallback.getSshUriProperties();
				assertThat(configCallback.getSshUriProperties().getPrivateKey())
						.isEqualTo(TestProperties.TEST_PRIVATE_KEY_1);

				assertThat(sshUriProperties.getRepos().get("repo1")).isNotNull();
				assertThat(sshUriProperties.getRepos().get("repo1").getPrivateKey())
						.isEqualTo(TestProperties.TEST_PRIVATE_KEY_2);
			}

		}

	}

	public static class FileBasedCallbackTest {

		@SpringBootTest(classes = { TestConfigServerApplication.class, SshPropertyValidator.class },
				webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
				properties = { "spring.cloud.config.server.git.uri=git@gitserver.com:team/repo.git",
						"spring.cloud.config.server.git.ignoreLocalSshSettings=false" })
		@ActiveProfiles({ "test", "git" })
		public static class StaticTest {

			@Autowired
			private MultipleJGitEnvironmentRepository jGitEnvironmentRepository;

			@Test
			public void fileBasedTransportCallbackIsConfigured() {
				TransportConfigCallback transportConfigCallback = this.jGitEnvironmentRepository
						.getTransportConfigCallback();
				assertThat(transportConfigCallback).isInstanceOf(FileBasedSshTransportConfigCallback.class);
			}

			@Test
			public void fileBasedSessionFactoryIsUsedForSshTransports() throws Exception {
				this.jGitEnvironmentRepository.afterPropertiesSet();

				SshTransport sshTransport = DummySshTransport.newInstance();
				this.jGitEnvironmentRepository.getTransportConfigCallback().configure(sshTransport);

				assertThat(sshTransport.getSshSessionFactory()).isInstanceOf(FileBasedSshSessionFactory.class);
			}

			@Test
			public void strictHostKeyCheckShouldCheck() throws Exception {
				assertThat(this.jGitEnvironmentRepository.isStrictHostKeyChecking()).isTrue();

				this.jGitEnvironmentRepository.afterPropertiesSet();
				SshTransport sshTransport = DummySshTransport.newInstance();
				this.jGitEnvironmentRepository.getTransportConfigCallback().configure(sshTransport);

				SshdSessionFactory factory = (SshdSessionFactory) sshTransport.getSshSessionFactory();
				// There's no public method that can be used to inspect the ssh
				// configuration, so we'll reflect
				// the createSshConfigStore method to allow us to check that the config
				// property is set as expected.
				Method createSshConfigStore = factory.getClass().getDeclaredMethod("createSshConfigStore", File.class,
						File.class, String.class);
				createSshConfigStore.setAccessible(true);
				SshConfigStore configStore = (SshConfigStore) createSshConfigStore.invoke(factory, new File("."),
						new File("."), "local-username");
				createSshConfigStore.setAccessible(false);
				assertThat("yes"
						.equals(configStore.lookup("gitserver.com", 22, "username").getValue("StrictHostKeyChecking")))
								.isTrue();
			}

		}

		@SpringBootTest(classes = { TestConfigServerApplication.class, SshPropertyValidator.class },
				webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
				properties = { "spring.cloud.config.server.composite[0].type=git",
						"spring.cloud.config.server.composite[0].uri=git@gitserver.com:team/repo.git",
						"spring.cloud.config.server.composite[0].ignoreLocalSshSettings=false" })
		@ActiveProfiles({ "test", "composite" })
		public static class ListTest {

			@Autowired
			private MultipleJGitEnvironmentRepository jGitEnvironmentRepository;

			@Test
			public void fileBasedTransportCallbackIsConfigured() {
				TransportConfigCallback transportConfigCallback = this.jGitEnvironmentRepository
						.getTransportConfigCallback();
				assertThat(transportConfigCallback).isInstanceOf(FileBasedSshTransportConfigCallback.class);
			}

			@Test
			public void fileBasedSessionFactoryIsUsedForSshTransports() throws Exception {
				this.jGitEnvironmentRepository.afterPropertiesSet();

				SshTransport sshTransport = DummySshTransport.newInstance();
				this.jGitEnvironmentRepository.getTransportConfigCallback().configure(sshTransport);

				assertThat(sshTransport.getSshSessionFactory()).isInstanceOf(FileBasedSshSessionFactory.class);
			}

			@Test
			public void strictHostKeyCheckShouldCheck() throws Exception {
				assertThat(this.jGitEnvironmentRepository.isStrictHostKeyChecking()).isTrue();

				this.jGitEnvironmentRepository.afterPropertiesSet();
				SshTransport sshTransport = DummySshTransport.newInstance();
				this.jGitEnvironmentRepository.getTransportConfigCallback().configure(sshTransport);

				SshdSessionFactory factory = (SshdSessionFactory) sshTransport.getSshSessionFactory();
				// There's no public method that can be used to inspect the ssh
				// configuration, so we'll reflect
				// the createSshConfigStore method to allow us to check that the config
				// property is set as expected.
				Method createSshConfigStore = factory.getClass().getDeclaredMethod("createSshConfigStore", File.class,
						File.class, String.class);
				createSshConfigStore.setAccessible(true);
				SshConfigStore configStore = (SshConfigStore) createSshConfigStore.invoke(factory, new File("."),
						new File("."), "local-username");
				createSshConfigStore.setAccessible(false);
				assertThat("yes"
						.equals(configStore.lookup("gitserver.com", 22, "username").getValue("StrictHostKeyChecking")))
								.isTrue();
			}

		}

	}

	public static class CallbackWithHttpUrlsOnly {

		@SpringBootTest(classes = { TestConfigServerApplication.class, SshPropertyValidator.class },
				webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
				properties = { "spring.cloud.config.server.git.uri=https://gitserver.com/team/repo.git" })
		@ActiveProfiles({ "test", "git" })
		public static class StaticTest {

			@Autowired
			private MultipleJGitEnvironmentRepository jGitEnvironmentRepository;

			@Test
			public void sshTransportCallbackIsConfigured() {
				TransportConfigCallback transportConfigCallback = this.jGitEnvironmentRepository
						.getTransportConfigCallback();
				assertThat(transportConfigCallback).isNotNull();
			}

			@Test
			public void noSessionFactoryIsConfiguredForSshTransports() throws Exception {
				this.jGitEnvironmentRepository.afterPropertiesSet();

				SshTransport sshTransport = DummySshTransport.newInstance();
				SshSessionFactory defaultSessionFactory = sshTransport.getSshSessionFactory();
				this.jGitEnvironmentRepository.getTransportConfigCallback().configure(sshTransport);

				assertThat(sshTransport.getSshSessionFactory()).isSameAs(defaultSessionFactory);
			}

		}

		@SpringBootTest(classes = { TestConfigServerApplication.class, SshPropertyValidator.class },
				webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
				properties = { "spring.cloud.config.server.composite[0].type=git",
						"spring.cloud.config.server.composite[0].uri=https://gitserver.com/team/repo.git" })
		@ActiveProfiles({ "test", "composite" })
		public static class ListTest {

			@Autowired
			private MultipleJGitEnvironmentRepository jGitEnvironmentRepository;

			@Test
			public void sshTransportCallbackIsConfigured() {
				TransportConfigCallback transportConfigCallback = this.jGitEnvironmentRepository
						.getTransportConfigCallback();
				assertThat(transportConfigCallback).isNotNull();
			}

			@Test
			public void noSessionFactoryIsConfiguredForSshTransports() throws Exception {
				this.jGitEnvironmentRepository.afterPropertiesSet();

				SshTransport sshTransport = DummySshTransport.newInstance();
				SshSessionFactory defaultSessionFactory = sshTransport.getSshSessionFactory();
				this.jGitEnvironmentRepository.getTransportConfigCallback().configure(sshTransport);

				assertThat(sshTransport.getSshSessionFactory()).isSameAs(defaultSessionFactory);
			}

		}

	}

	private static class DummySshTransport extends SshTransport {

		DummySshTransport(String uri) throws URISyntaxException {
			super(new URIish(uri));
		}

		@Override
		public FetchConnection openFetch() {
			return null;
		}

		@Override
		public PushConnection openPush() {
			return null;
		}

		public static SshTransport newInstance() {
			try {
				return new DummySshTransport("git+ssh://git@uri");
			}
			catch (URISyntaxException e) {
				throw new IllegalStateException("Not expected", e);
			}
		}

	}

	private static class TestProperties {

		private static final String TEST_PRIVATE_KEY_1 = "-----BEGIN RSA PRIVATE KEY-----\n"
				+ "MIIEpAIBAAKCAQEAoqyz6YaYMTr7L8GLPSQpAQXaM04gRx4CCsGK2kfLQdw4BlqI\n"
				+ "yyxp38YcuZG9cUDBAxby+K2TKmwHaC1R61QTwbPuCRdIPrDwRz+FLoegm3iDLCmn\n"
				+ "uP6rjZDneYsqfU1KSdrOwIbCnONfDdvYL/vnZC/o8DDMlk5Orw2SfHkT3pq0o8km\n"
				+ "ayBwN4Sf3bpyWTY0oZcmNeSCCoIdE59k8Pa7/t9bwY9caLj05C3DEsjucc7Ei/Eq\n"
				+ "TOyGyobtXwaya5CqKLUHes74Poz1aEP/yVFdUud91uezd8ZK1P1t5/ZKA3R6aHir\n"
				+ "+diDJ2/GQ2tD511FW46yw+EtBUJTO6ADVv4UnQIDAQABAoIBAF+5qwEfX82QfKFk\n"
				+ "jfADqFFexUDtl1biFKeJrpC2MKhn01wByH9uejrhFKQqW8UaKroLthyZ34DWIyGt\n"
				+ "lDnHGv0gSVF2LuAdNLdobJGt49e4+c9yD61vxzm97Eh8mRs08SM2q/VlF35E2fmI\n"
				+ "xdWusUImYzd8L9e+6tRd8zZl9UhG5vR5XIstKqxC6S0g79aAt0hasE4Gw1FKOf2V\n"
				+ "4mlL15atjQSKCPdOicuyc4zpjAtU1A9AfF51iG8oOUuJebPW8tCftfOQxaeGFgMG\n"
				+ "7M9aai1KzXR6M5IBAKEv31yBvz/SHTneP7oZXNLeC1GIR420PKybmeZdNK8BbEAu\n"
				+ "3reKgm0CgYEA03Sx8JoF5UBsIvFPpP1fjSlTgKryM5EJR6KQtj5e4YfyxccJepN8\n"
				+ "q4MrqDfNKleG/a1acEtDMhBNovU7Usp2QIP7zpAeioHBOhmE5WSieZGc3icOGWWq\n"
				+ "mRkdulSONruqWKv76ZoluxftekE03bDhZDNlcCgmrslEKB/ufHd2oc8CgYEAxPFa\n"
				+ "lKOdSeiYFV5CtvO8Ro8em6rGpSsVz4qkPxbeBqUDCb9KXHhq6YrhRxOIfQJKfT7M\n"
				+ "ZFCn8ArJXKgOGu+KsvwIErFHF9g2jJMG4DOUTpkQgi2yveihFxcmz/AltyVXgrnv\n"
				+ "ZWQbAerH77pdKKhNivLGgEv72GYawdYjYNjemdMCgYA2kEMmMahZyrDcp2YEzfit\n"
				+ "BT/t0K6kzcUWPgWXcSqsiZcEn+J7RbmCzFskkhmX1nQX23adyV3yejB+X0dKisHO\n"
				+ "zf/ZAmlPFkJVCqa3RquCMSfIT02dEhXeYZPBM/Zqeyxuqxpa4hLgX0FBLbhFiFHw\n"
				+ "uC5xrXql2XuD2xF//peXEwKBgQC+pa28Cg7vRxxCQzduB9CQtWc55j3aEjVQ7bNF\n"
				+ "54sS/5ZLT0Ra8677WZfuyDfuW9NkHvCZg4Ku2qJG8eCFrrGjxlrCTZ62tHVJ6+JS\n"
				+ "E1xUIdRbUIWhVZrr0VufG6hG/P0T7Y6Tpi6G0pKtvMkF3LcD9TS3adboix8H2ZXx\n"
				+ "4L7MRQKBgQC0OO3qqNXOjIVYWOoqXLybOY/Wqu9lxCAgGyCYaMcstnBI7W0MZTBr\n"
				+ "/syluvGsaFc1sE7MMGOOzKi1tF4YvDmSnzA/R1nmaPguuD9fOA+w7Pwkv5vLvuJq\n"
				+ "2U7EeNwxq1I1L3Ag6E7wH4BHLHd4TKaZR6agFkn8oomz71yZPGjuZQ==\n" + "-----END RSA PRIVATE KEY-----";

		private static final String TEST_PRIVATE_KEY_2 = "-----BEGIN RSA PRIVATE KEY-----\n"
				+ "MIIEpgIBAAKCAQEAx4UbaDzY5xjW6hc9jwN0mX33XpTDVW9WqHp5AKaRbtAC3DqX\n"
				+ "IXFMPgw3K45jxRb93f8tv9vL3rD9CUG1Gv4FM+o7ds7FRES5RTjv2RT/JVNJCoqF\n"
				+ "ol8+ngLqRZCyBtQN7zYByWMRirPGoDUqdPYrj2yq+ObBBNhg5N+hOwKjjpzdj2Ud\n"
				+ "1l7R+wxIqmJo1IYyy16xS8WsjyQuyC0lL456qkd5BDZ0Ag8j2X9H9D5220Ln7s9i\n"
				+ "oezTipXipS7p7Jekf3Ywx6abJwOmB0rX79dV4qiNcGgzATnG1PkXxqt76VhcGa0W\n"
				+ "DDVHEEYGbSQ6hIGSh0I7BQun0aLRZojfE3gqHQIDAQABAoIBAQCZmGrk8BK6tXCd\n"
				+ "fY6yTiKxFzwb38IQP0ojIUWNrq0+9Xt+NsypviLHkXfXXCKKU4zUHeIGVRq5MN9b\n"
				+ "BO56/RrcQHHOoJdUWuOV2qMqJvPUtC0CpGkD+valhfD75MxoXU7s3FK7yjxy3rsG\n"
				+ "EmfA6tHV8/4a5umo5TqSd2YTm5B19AhRqiuUVI1wTB41DjULUGiMYrnYrhzQlVvj\n"
				+ "5MjnKTlYu3V8PoYDfv1GmxPPh6vlpafXEeEYN8VB97e5x3DGHjZ5UrurAmTLTdO8\n"
				+ "+AahyoKsIY612TkkQthJlt7FJAwnCGMgY6podzzvzICLFmmTXYiZ/28I4BX/mOSe\n"
				+ "pZVnfRixAoGBAO6Uiwt40/PKs53mCEWngslSCsh9oGAaLTf/XdvMns5VmuyyAyKG\n"
				+ "ti8Ol5wqBMi4GIUzjbgUvSUt+IowIrG3f5tN85wpjQ1UGVcpTnl5Qo9xaS1PFScQ\n"
				+ "xrtWZ9eNj2TsIAMp/svJsyGG3OibxfnuAIpSXNQiJPwRlW3irzpGgVx/AoGBANYW\n"
				+ "dnhshUcEHMJi3aXwR12OTDnaLoanVGLwLnkqLSYUZA7ZegpKq90UAuBdcEfgdpyi\n"
				+ "PhKpeaeIiAaNnFo8m9aoTKr+7I6/uMTlwrVnfrsVTZv3orxjwQV20YIBCVRKD1uX\n"
				+ "VhE0ozPZxwwKSPAFocpyWpGHGreGF1AIYBE9UBtjAoGBAI8bfPgJpyFyMiGBjO6z\n"
				+ "FwlJc/xlFqDusrcHL7abW5qq0L4v3R+FrJw3ZYufzLTVcKfdj6GelwJJO+8wBm+R\n"
				+ "gTKYJItEhT48duLIfTDyIpHGVm9+I1MGhh5zKuCqIhxIYr9jHloBB7kRm0rPvYY4\n"
				+ "VAykcNgyDvtAVODP+4m6JvhjAoGBALbtTqErKN47V0+JJpapLnF0KxGrqeGIjIRV\n"
				+ "cYA6V4WYGr7NeIfesecfOC356PyhgPfpcVyEztwlvwTKb3RzIT1TZN8fH4YBr6Ee\n"
				+ "KTbTjefRFhVUjQqnucAvfGi29f+9oE3Ei9f7wA+H35ocF6JvTYUsHNMIO/3gZ38N\n"
				+ "CPjyCMa9AoGBAMhsITNe3QcbsXAbdUR00dDsIFVROzyFJ2m40i4KCRM35bC/BIBs\n"
				+ "q0TY3we+ERB40U8Z2BvU61QuwaunJ2+uGadHo58VSVdggqAo0BSkH58innKKt96J\n"
				+ "69pcVH/4rmLbXdcmNYGm6iu+MlPQk4BUZknHSmVHIFdJ0EPupVaQ8RHT\n" + "-----END RSA PRIVATE KEY-----\n";

	}

}
