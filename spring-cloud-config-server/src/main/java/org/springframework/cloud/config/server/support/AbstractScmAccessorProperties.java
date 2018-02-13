package org.springframework.cloud.config.server.support;

import java.io.File;

public class AbstractScmAccessorProperties {
    private String uri;
    private File basedir;
    private String[] searchPaths;
    private String username;
    private String password;
    private String passphrase;
    private Boolean strictHostKeyChecking;

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public File getBasedir() {
        return basedir;
    }

    public void setBasedir(File basedir) {
        this.basedir = basedir;
    }

    public String[] getSearchPaths() {
        return searchPaths;
    }

    public void setSearchPaths(String... searchPaths) {
        this.searchPaths = searchPaths;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPassphrase() {
        return passphrase;
    }

    public void setPassphrase(String passphrase) {
        this.passphrase = passphrase;
    }

    public Boolean getStrictHostKeyChecking() {
        return strictHostKeyChecking;
    }

    public void setStrictHostKeyChecking(Boolean strictHostKeyChecking) {
        this.strictHostKeyChecking = strictHostKeyChecking;
    }
}
