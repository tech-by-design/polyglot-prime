package org.techbd.orchestrate.sftp;

import java.nio.charset.Charset;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import com.fasterxml.jackson.databind.JsonNode;
import org.techbd.conf.Configuration;

import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.VFS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SftpManager {
    private static final Logger LOG = LoggerFactory.getLogger(SftpManager.class);
    public static final String TENANT_EGRESS_CONTENT_CACHE_KEY = "tenant-sftp-egress-content";
    public static final String TENANT_EGRESS_SESSIONS_CACHE_KEY = "tenant-sftp-egress-sessions";

    private final SftpAccountsOrchctlConfig configuredTenants;

    public SftpManager(final SftpAccountsOrchctlConfig saoc) {
        this.configuredTenants = new SftpAccountsOrchctlConfig(saoc);
    }

    public SftpAccountsOrchctlConfig configuredTenants() {
        return new SftpAccountsOrchctlConfig(this.configuredTenants);
    }

    public Optional<SftpAccountsOrchctlConfig.SftpAccount> configuredTenant(final String tenantId) {
        return configuredTenants().getOrchctlts().stream().filter(a -> a.getTenantId().equals(tenantId)).findFirst();
    }

    public record TenantSftpEgressSession(String tenantId, String sessionId, Date cachedAt,
            String sessionJsonPath, String sessionJson, Date sessionFinalizedAt,
            Exception error) {
        public String getSessionId() {
            return sessionId;
        }

        public Integer getFhirCount() {
            try {
                JsonNode jsonNode = Configuration.objectMapper.readTree(sessionJson);
                JsonNode publishFhirResultNode = jsonNode.path("publishFhirResult");
                if (publishFhirResultNode.isArray()) {
                    return publishFhirResultNode.size();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return 0;
        }
    }

    public record IndividualTenantSftpEgressSession(String tenantId, String sessionId, Date cachedAt,
            String sessionJsonPath, String sessionJson, Date sessionFinalizedAt,
            Exception error) {
    }

    public record TenantSftpEgressContent(String tenantId, String sftpUri, Date cachedAt, FileObject home,
            FileObject[] directories,
            Exception error) {

        private static final Logger LOG = LoggerFactory.getLogger(TenantSftpEgressContent.class);

        public Optional<ZonedDateTime> mostRecentEgress() {
            if (error != null) {
                LOG.error("Unable to obtain most recent egress for %s".formatted(tenantId), error);
                return Optional.empty();
            }

            try {
                if (directories.length > 0) {
                    final var mostRecent = directories[0];
                    return Optional.of(Instant.ofEpochMilli(mostRecent.getContent().getLastModifiedTime())
                            .atZone(ZoneId.systemDefault()));
                }
            } catch (Exception e) {
                LOG.error("Unable to obtain most recent egress for %s".formatted(tenantId), e);
            }
            return Optional.empty();
        }

        static TenantSftpEgressContent forTenant(final @NonNull SftpAccountsOrchctlConfig.SftpAccount account,
                int limitMostRecent) {
            final var sftpUri = "sftp://%s:%s@%s:%d/egress".formatted(account.getUsername(),
                    account.getPassword(), account.getServer(), account.getPort());
            try {
                final var sftpDir = VFS.getManager().resolveFile(sftpUri);
                final var directories = sftpDir.getChildren();
                Arrays.sort(directories, (a, b) -> {
                    try {
                        return Long.compare(b.getContent().getLastModifiedTime(), a.getContent().getLastModifiedTime());
                    } catch (FileSystemException e) {
                        throw new RuntimeException(e);
                    }
                });
                return new TenantSftpEgressContent(account.getTenantId(), sftpUri, Date.from(Instant.now()), sftpDir,
                        Arrays.copyOf(directories, Math.min(directories.length, limitMostRecent)),
                        null);
            } catch (Exception e) {
                LOG.error(sftpUri, e);
                return new TenantSftpEgressContent(account.getTenantId(), sftpUri, Date.from(Instant.now()), null, null,
                        e);
            }
        }
    }

    @Cacheable(TENANT_EGRESS_CONTENT_CACHE_KEY)
    public TenantSftpEgressContent tenantEgressContent(final @NonNull SftpAccountsOrchctlConfig.SftpAccount account,
            int limitMostRecent) {
        return TenantSftpEgressContent.forTenant(account, limitMostRecent);
    }

    @Cacheable(TENANT_EGRESS_SESSIONS_CACHE_KEY)
    public Optional<IndividualTenantSftpEgressSession> getTenantEgressSession(String tenantId, String interactionId,
            int limitMostRecent) {
        final var configuredAccounts = configuredTenants.getOrchctlts();
        if (configuredAccounts != null) {
            for (var a : configuredAccounts) {
                final var tec = tenantEgressContent(a, limitMostRecent);

                if (tec.error() == null) {
                    if (tec.tenantId.equals(tenantId)) {
                        try {
                            for (var egressSessionDir : tec.directories()) {
                                if (egressSessionDir.getName().getPath().contains(interactionId)) {
                                    FileObject sessionJsonFile;
                                    try {
                                        sessionJsonFile = egressSessionDir.resolveFile("session.json");
                                    } catch (Exception e) {
                                        // this usually means that the SFTP directory is not a session path
                                        // so this is not an error
                                        continue;
                                    }
                                    try {
                                        var sessionJson = sessionJsonFile.getContent();
                                        // JSONObject sessionJsonObject = (JSONObject) sessionJson;
                                        return Optional.of(new IndividualTenantSftpEgressSession(tec.tenantId(),
                                                egressSessionDir.getName().getBaseName(),
                                                tec.cachedAt(),
                                                sessionJsonFile.getPublicURIString(),
                                                sessionJson.getString(Charset.defaultCharset()),
                                                Date.from(Instant.ofEpochMilli(sessionJson.getLastModifiedTime())),
                                                null));
                                    } catch (Exception e) {
                                        return Optional.of(new IndividualTenantSftpEgressSession(tec.tenantId(),
                                                egressSessionDir.getName().getBaseName(), tec.cachedAt(),
                                                sessionJsonFile.getPublicURIString(),
                                                null,
                                                Date.from(Instant
                                                        .ofEpochMilli(
                                                                egressSessionDir.getContent().getLastModifiedTime())),
                                                new RuntimeException("Unable to read session.json from %s"
                                                        .formatted(sessionJsonFile.getPublicURIString()), e)));
                                    }
                                }
                            }
                        } catch (Exception e) {
                            return Optional
                                    .of(new IndividualTenantSftpEgressSession(tec.tenantId(), null, tec.cachedAt(),
                                            tec.home().getPublicURIString(),
                                            null, null, e));
                        }

                    }
                } else {
                    return Optional.of(new IndividualTenantSftpEgressSession(tenantId, null, tec.cachedAt(), null,
                            null, null,
                            new RuntimeException("Invalid SFTP account %s".formatted(tenantId), tec.error())));
                }
            }
        }
        return Optional.empty();
    }

    @Cacheable(TENANT_EGRESS_SESSIONS_CACHE_KEY)
    public List<TenantSftpEgressSession> tenantEgressSessions(int limitMostRecent) {
        final var result = new ArrayList<TenantSftpEgressSession>();
        final var configuredAccounts = configuredTenants.getOrchctlts();
        if (configuredAccounts != null) {
            for (var a : configuredAccounts) {
                final var tec = tenantEgressContent(a, limitMostRecent);
                if (tec.error() == null) {
                    try {
                        for (var egressSessionDir : tec.directories()) {
                            FileObject sessionJsonFile;
                            try {
                                sessionJsonFile = egressSessionDir.resolveFile("session.json");
                            } catch (Exception e) {
                                // this usually means that the SFTP directory is not a session path
                                // so this is not an error
                                continue;
                            }
                            try {
                                final var sessionJson = sessionJsonFile.getContent();
                                result.add(new TenantSftpEgressSession(tec.tenantId(),
                                        egressSessionDir.getName().getBaseName(),
                                        tec.cachedAt(),
                                        sessionJsonFile.getPublicURIString(),
                                        sessionJson.getString(Charset.defaultCharset()),
                                        Date.from(Instant.ofEpochMilli(sessionJson.getLastModifiedTime())), null));
                            } catch (Exception e) {
                                result.add(new TenantSftpEgressSession(tec.tenantId(),
                                        egressSessionDir.getName().getBaseName(), tec.cachedAt(),
                                        sessionJsonFile.getPublicURIString(),
                                        null,
                                        Date.from(Instant
                                                .ofEpochMilli(egressSessionDir.getContent().getLastModifiedTime())),
                                        new RuntimeException("Unable to read session.json from %s"
                                                .formatted(sessionJsonFile.getPublicURIString()), e)));
                            }
                        }
                    } catch (Exception e) {
                        result.add(new TenantSftpEgressSession(tec.tenantId(), null, tec.cachedAt(),
                                tec.home().getPublicURIString(),
                                null, null, e));
                    }
                } else {
                    result.add(new TenantSftpEgressSession(tec.tenantId(), null, tec.cachedAt(), null,
                            null, null,
                            new RuntimeException("Invalid SFTP account %s".formatted(tec.tenantId()), tec.error())));
                }
            }
        }
        return result;
    }

    @CacheEvict(value = { TENANT_EGRESS_CONTENT_CACHE_KEY, TENANT_EGRESS_SESSIONS_CACHE_KEY }, allEntries = true)
    @Scheduled(fixedRateString = "${org.techbd.cache.tenant-sftp-egress-content.ttl:PT5M}")
    public void emptyTenantEgressCacheScheduled() {
        LOG.info("emptying " + TENANT_EGRESS_CONTENT_CACHE_KEY + " (scheduled PT5M)");
    }
}
