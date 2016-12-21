/*
 * Copyright 2013-2016 the original author or authors.
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
package org.springframework.cloud.config.server.environment;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.springframework.cloud.config.environment.Environment;
import org.springframework.cloud.config.environment.PropertySource;
import org.springframework.core.Ordered;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * @author Ryan Baxter
 */
public class CompositeEnvironmentRepositoryTests {

	private class TestOrderedEnvironmentRepository implements EnvironmentRepository, SearchPathLocator, Ordered {

		private Environment env;
		private Locations locations;
		private int order = Ordered.LOWEST_PRECEDENCE;

		public TestOrderedEnvironmentRepository(int order, Environment env, Locations locations) {
			this.order = order;
			this.env = env;
			this.locations = locations;
		}

		@Override
		public Environment findOne(String application, String profile, String label) {
			return env;
		}

		@Override
		public Locations getLocations(String application, String profile, String label) {
			return locations;
		}

		@Override
		public int getOrder() {
			return order;
		}
	}

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
		SearchPathLocator.Locations loc1 = new SearchPathLocator.Locations("app", "dev", "label", "version", new String[]{sLoc1});
		SearchPathLocator.Locations loc2 = new SearchPathLocator.Locations("app", "dev", "label", "version", new String[]{sLoc5, sLoc4});
		SearchPathLocator.Locations loc3 = new SearchPathLocator.Locations("app", "dev", "label", "version", new String[]{sLoc3, sLoc2});
		List<EnvironmentRepository> repos = new ArrayList<EnvironmentRepository>();
		repos.add(new TestOrderedEnvironmentRepository(3, e1, loc1));
		repos.add(new TestOrderedEnvironmentRepository(2, e3, loc2));
		repos.add(new TestOrderedEnvironmentRepository(1, e2, loc3));
		SearchPathCompositeEnvironmentRepository compositeRepo = new SearchPathCompositeEnvironmentRepository(repos);
		Environment compositeEnv = compositeRepo.findOne("foo", "bar", "world");
		List<PropertySource> propertySources = compositeEnv.getPropertySources();
		assertEquals(5, propertySources.size());
		assertEquals("p2", propertySources.get(0).getName());
		assertEquals("p3", propertySources.get(1).getName());
		assertEquals("p4", propertySources.get(2).getName());
		assertEquals("p1", propertySources.get(3).getName());
		assertEquals("p5", propertySources.get(4).getName());

		SearchPathLocator.Locations locations = compositeRepo.getLocations("app", "dev", "label");
		String[] locationStrings = locations.getLocations();
		assertEquals(5, locationStrings.length);
		assertEquals(sLoc3, locationStrings[0]);
		assertEquals(sLoc2, locationStrings[1]);
		assertEquals(sLoc5, locationStrings[2]);
		assertEquals(sLoc4, locationStrings[3]);
		assertEquals(sLoc1, locationStrings[4]);
	}
}
