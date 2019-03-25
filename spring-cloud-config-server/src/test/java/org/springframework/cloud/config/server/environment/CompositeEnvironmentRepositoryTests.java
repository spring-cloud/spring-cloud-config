/*
 * Copyright 2013-2019 the original author or authors.
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

package org.springframework.cloud.config.server.environment;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.cloud.config.server.config.CompositeConfiguration;
import org.springframework.cloud.config.server.config.ConfigServerHealthIndicator;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * @author Ryan Baxter
 */
public class CompositeEnvironmentRepositoryTests {

	@Test
	public void testOrder() {
		PropertySource p1 = mock(PropertySource.class);
		doReturn("p1").when(p1).getName();
		PropertySource p2 = mock(PropertySource.class);
		doReturn("p2").when(p2).getName();
		PropertySource p3 = mock(PropertySource.class);
		doReturn("p3").when(p3).getName();
		PropertySource p4 = mock(PropertySource.class);
		doReturn("p4").when(p4).getName();
		PropertySource p5 = mock(PropertySource.class);
		doReturn("p5").when(p5).getName();
		String sLoc1 = "loc1";
		String sLoc2 = "loc2";
		String sLoc3 = "loc3";
		String sLoc4 = "loc4";
		String sLoc5 = "loc5";
		Environment e1 = new Environment("app", "dev");
		e1.add(p1);
		e1.add(p5);
		Environment e2 = new Environment("app", "dev");
		e2.add(p2);
		Environment e3 = new Environment("app", "dev");
		e3.add(p3);
		e3.add(p4);
		SearchPathLocator.Locations loc1 = new SearchPathLocator.Locations("app", "dev",
				"label", "version", new String[] { sLoc1 });
		SearchPathLocator.Locations loc2 = new SearchPathLocator.Locations("app", "dev",
				"label", "version", new String[] { sLoc5, sLoc4 });
		SearchPathLocator.Locations loc3 = new SearchPathLocator.Locations("app", "dev",
				"label", "version", new String[] { sLoc3, sLoc2 });
		List<EnvironmentRepository> repos = new ArrayList<EnvironmentRepository>();
		repos.add(new TestOrderedEnvironmentRepository(3, e1, loc1));
		repos.add(new TestOrderedEnvironmentRepository(2, e3, loc2));
		repos.add(new TestOrderedEnvironmentRepository(1, e2, loc3));
		SearchPathCompositeEnvironmentRepository compositeRepo = new SearchPathCompositeEnvironmentRepository(
				repos);
		Environment compositeEnv = compositeRepo.findOne("foo", "bar", "world");
		List<PropertySource> propertySources = compositeEnv.getPropertySources();
		assertThat(propertySources.size()).isEqualTo(5);
		assertThat(propertySources.get(0).getName()).isEqualTo("p2");
		assertThat(propertySources.get(1).getName()).isEqualTo("p3");
		assertThat(propertySources.get(2).getName()).isEqualTo("p4");
		assertThat(propertySources.get(3).getName()).isEqualTo("p1");
		assertThat(propertySources.get(4).getName()).isEqualTo("p5");

		SearchPathLocator.Locations locations = compositeRepo.getLocations("app", "dev",
				"label");
		String[] locationStrings = locations.getLocations();
		assertThat(locationStrings.length).isEqualTo(5);
		assertThat(locationStrings[0]).isEqualTo(sLoc3);
		assertThat(locationStrings[1]).isEqualTo(sLoc2);
		assertThat(locationStrings[2]).isEqualTo(sLoc5);
		assertThat(locationStrings[3]).isEqualTo(sLoc4);
		assertThat(locationStrings[4]).isEqualTo(sLoc1);
	}

	@Test
	public void testVersion() {
		PropertySource p1 = mock(PropertySource.class);
		doReturn("p1").when(p1).getName();
		PropertySource p2 = mock(PropertySource.class);
		doReturn("p2").when(p2).getName();
		String sLoc1 = "loc1";
		String sLoc2 = "loc2";
		Environment e1 = new Environment("app", "dev");
		e1.add(p1);
		e1.setVersion("1");
		e1.setState("state");
		Environment e2 = new Environment("app", "dev");
		e2.add(p2);
		e2.setVersion("2");
		e2.setState("state2");
		SearchPathLocator.Locations loc1 = new SearchPathLocator.Locations("app", "dev",
				"label", "version", new String[] { sLoc1 });
		SearchPathLocator.Locations loc2 = new SearchPathLocator.Locations("app", "dev",
				"label", "version", new String[] { sLoc1, sLoc2 });
		List<EnvironmentRepository> repos = new ArrayList<EnvironmentRepository>();
		repos.add(new TestOrderedEnvironmentRepository(3, e1, loc1));
		List<EnvironmentRepository> repos2 = new ArrayList<EnvironmentRepository>();
		repos2.add(new TestOrderedEnvironmentRepository(3, e1, loc1));
		repos2.add(new TestOrderedEnvironmentRepository(3, e2, loc2));
		SearchPathCompositeEnvironmentRepository compositeRepo = new SearchPathCompositeEnvironmentRepository(
				repos);
		SearchPathCompositeEnvironmentRepository multiCompositeRepo = new SearchPathCompositeEnvironmentRepository(
				repos2);
		Environment env = compositeRepo.findOne("app", "dev", "label");
		assertThat(env.getVersion()).isEqualTo("1");
		assertThat(env.getState()).isEqualTo("state");
		Environment multiEnv = multiCompositeRepo.findOne("app", "dev", "label");
		assertThat(multiEnv.getVersion()).isEqualTo(null);
		assertThat(multiEnv.getState()).isEqualTo(null);

	}

	@Test
	public void overridingCompositeEnvRepo_contextLoads() {
		try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
			context.register(OverrideCompositeConfig.class, CompositeConfiguration.class,
					ConfigServerHealthIndicator.class);
			context.refresh();
		}
	}

	private static class TestOrderedEnvironmentRepository
			implements EnvironmentRepository, SearchPathLocator, Ordered {

		private Environment env;

		private Locations locations;

		private int order = Ordered.LOWEST_PRECEDENCE;

		TestOrderedEnvironmentRepository(int order, Environment env,
				Locations locations) {
			this.order = order;
			this.env = env;
			this.locations = locations;
		}

		@Override
		public Environment findOne(String application, String profile, String label) {
			return this.env;
		}

		@Override
		public Locations getLocations(String application, String profile, String label) {
			return this.locations;
		}

		@Override
		public int getOrder() {
			return this.order;
		}

	}

	@Configuration
	static class OverrideCompositeConfig {

		@Bean
		@Primary
		CompositeEnvironmentRepository customCompositeEnvironmentRepository() {
			return new CompositeEnvironmentRepository(Arrays
					.<EnvironmentRepository>asList(new TestOrderedEnvironmentRepository(1,
							new Environment("app", "dev"), null)));
		}

	}

}
