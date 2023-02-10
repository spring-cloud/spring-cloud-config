/*
 * Copyright 2013-2020 the original author or authors.
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

package org.springframework.cloud.config.server.environment.secretmanager;

import com.google.cloud.secretmanager.v1.SecretVersion;

import java.util.Comparator;

public class GoogleSecretComparatorByVersion implements Comparator<SecretVersion> {

	@Override
	public int compare(SecretVersion leftVersion, SecretVersion rightVersion) {
		if (rightVersion == null) {
			return 1;
		}
		if (leftVersion == null) {
			return -1;
		}
		String leftVersionName = leftVersion.getName();
		String rightVersionName = rightVersion.getName();
		Integer leftVersionNumber = Integer.valueOf(leftVersionName.substring(leftVersionName.lastIndexOf("/") + 1));
		Integer rightVersionNumber = Integer.valueOf(rightVersionName.substring(rightVersionName.lastIndexOf("/") + 1));
		return leftVersionNumber.compareTo(rightVersionNumber);
	}

}
