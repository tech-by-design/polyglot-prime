package org.techbd.orchestrate.sftp;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
@ConfigurationProperties(prefix = "org.techbd.orchestrate.sftp.account")
public class SftpAccountsOrchctlConfig {
    private List<SftpAccount> orchctlts;

    // Default constructor
    public SftpAccountsOrchctlConfig() {
    }

    // Copy constructor
    public SftpAccountsOrchctlConfig(SftpAccountsOrchctlConfig other) {
        if (other.orchctlts != null) {
            // Since this is configuration properties object, we can make unmodifiable field
            // orchctlts.
            this.orchctlts = Collections.unmodifiableList(other.orchctlts);
        }
    }

    public List<SftpAccount> getOrchctlts() {
        return orchctlts == null ? Collections.emptyList() : Collections.unmodifiableList(orchctlts);
    }

    public void setOrchctlts(List<SftpAccount> orchctlts) {
        this.orchctlts = orchctlts == null ? null : new ArrayList<>(orchctlts);
    }

    public static class SftpAccount {
        private String tenantId;
        private String server;
        private Integer port;
        private String username;
        private String password;

        public String getTenantId() {
            return tenantId;
        }

        public void setTenantId(String tenantId) {
            this.tenantId = tenantId;
        }

        public String getServer() {
            return server;
        }

        public void setServer(String server) {
            this.server = server;
        }

        public Integer getPort() {
            return port;
        }

        public void setPort(Integer port) {
            this.port = port;
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
    }

}
