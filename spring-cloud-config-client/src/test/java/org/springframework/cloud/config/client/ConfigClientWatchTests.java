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

package org.springframework.cloud.config.client;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Spencer Gibb
 */
public class ConfigClientWatchTests {

	@Test
	public void stateChangedWorks() {
		ConfigClientWatch watch = new ConfigClientWatch(null);
		assertThat(watch.stateChanged(null, "1")).isTrue();
		assertThat(watch.stateChanged("1", "2")).isTrue();
		assertThat(watch.stateChanged("1", null)).isTrue();
		assertThat(watch.stateChanged("1", "1")).isFalse();
		watch.close();
	}

}
