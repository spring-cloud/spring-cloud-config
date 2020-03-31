package org.springframework.cloud.config.server.environment.secretManager;

import java.util.Comparator;

import com.google.cloud.secretmanager.v1.SecretVersion;

public class GoogleSecretComparatorByVersion implements Comparator<SecretVersion> {

	@Override
	public int compare(SecretVersion leftVersion, SecretVersion rightVersion) {
		if (rightVersion == null) {
			return 1;
		}
		if (leftVersion == null) {
			return -1;
		}
		return leftVersion.getName().compareTo(rightVersion.getName());
	}
}
