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

import io.micrometer.core.tck.TestObservationRegistry;
import io.micrometer.core.tck.TestObservationRegistryAssert;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.reflect.SourceLocation;
import org.aspectj.runtime.internal.AroundClosure;
import org.junit.jupiter.api.Test;

class ObservationEnvironmentRepositoryAspectTests {

	@Test
	void should_collect_metrics() throws Throwable {
		TestObservationRegistry registry = TestObservationRegistry.create();
		ObservationEnvironmentRepositoryAspect aspect = new ObservationEnvironmentRepositoryAspect(registry);

		aspect.observationFindEnvironment(pjp(aspect));

		TestObservationRegistryAssert.assertThat(registry).hasSingleObservationThat().hasNameEqualTo("find")
				.hasBeenStarted().hasBeenStopped()
				.hasLowCardinalityKeyValue("spring.config.environment.class",
						"org.springframework.cloud.config.server.environment.ObservationEnvironmentRepositoryAspect")
				.hasLowCardinalityKeyValue("spring.config.environment.method", "mySignature");
	}

	private ProceedingJoinPoint pjp(Object target) {
		return new ProceedingJoinPoint() {
			@Override
			public void set$AroundClosure(AroundClosure aroundClosure) {

			}

			@Override
			public Object proceed() throws Throwable {
				return null;
			}

			@Override
			public Object proceed(Object[] objects) throws Throwable {
				return null;
			}

			@Override
			public String toShortString() {
				return null;
			}

			@Override
			public String toLongString() {
				return null;
			}

			@Override
			public Object getThis() {
				return target;
			}

			@Override
			public Object getTarget() {
				return target;
			}

			@Override
			public Object[] getArgs() {
				return new Object[0];
			}

			@Override
			public Signature getSignature() {
				return new Signature() {
					@Override
					public String toShortString() {
						return null;
					}

					@Override
					public String toLongString() {
						return null;
					}

					@Override
					public String getName() {
						return "mySignature";
					}

					@Override
					public int getModifiers() {
						return 0;
					}

					@Override
					public Class getDeclaringType() {
						return target.getClass();
					}

					@Override
					public String getDeclaringTypeName() {
						return null;
					}
				};
			}

			@Override
			public SourceLocation getSourceLocation() {
				return null;
			}

			@Override
			public String getKind() {
				return null;
			}

			@Override
			public StaticPart getStaticPart() {
				return null;
			}
		};
	}

}
