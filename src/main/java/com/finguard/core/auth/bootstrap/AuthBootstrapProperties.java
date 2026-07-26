package com.finguard.core.auth.bootstrap;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "finguard.auth.bootstrap")
public class AuthBootstrapProperties {

    private boolean enabled;
    private Credential admin = new Credential();
    private Credential reviewer = new Credential();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Credential getAdmin() {
        return admin;
    }

    public void setAdmin(Credential admin) {
        this.admin = admin;
    }

    public Credential getReviewer() {
        return reviewer;
    }

    public void setReviewer(Credential reviewer) {
        this.reviewer = reviewer;
    }

    public static class Credential {

        private String username;
        private String password;

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
    }
}
