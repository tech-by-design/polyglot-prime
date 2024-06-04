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
    static private final Logger LOG = LoggerFactory.getLogger(SftpManager.class);
    static public final String TENANT_EGRESS_CONTENT_CACHE_KEY = "tenant-sftp-egress-content";
    static public final String TENANT_EGRESS_SESSIONS_CACHE_KEY = "tenant-sftp-egress-sessions";

    public record TenantSftpEgressSession(String tenantId, String sessionId, Date cachedAt,
            String sessionJsonPath, String sessionJson, Date sessionFinalizedAt,
            Exception error) {
    }

    public record TenantSftpEgressContent(String tenantId, String sftpUri, Date cachedAt, FileObject home,
            FileObject[] directories,
            Exception error) {

        static private final Logger LOG = LoggerFactory.getLogger(TenantSftpEgressContent.class);

        public Optional<ZonedDateTime> mostRecentEgress() {
            try {
                if (directories.length > 0) {
                    final var mostRecent = directories[0];
                    return Optional.of(Instant.ofEpochMilli(mostRecent.getContent().getLastModifiedTime())
                            .atZone(ZoneId.systemDefault()));
                }
            } catch (FileSystemException e) {
                LOG.error("Unable to obtain most recent egress for %s".formatted(tenantId), e);
            }
            return Optional.empty();
        }

        static TenantSftpEgressContent forTenant(final @NonNull String tenantId) {
            final var sftpUri = "sftp://%s:%s@synthetic.sftp.techbd.org:22/egress".formatted(tenantId, "pass");
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
                return new TenantSftpEgressContent(tenantId, sftpUri, Date.from(Instant.now()), sftpDir, directories,
                        null);
            } catch (Exception e) {
                LOG.error(sftpUri, e);
                return new TenantSftpEgressContent(tenantId, sftpUri, Date.from(Instant.now()), null, null, e);
            }
        }
    }

    @Cacheable(TENANT_EGRESS_CONTENT_CACHE_KEY)
    public TenantSftpEgressContent tenantEgressContent(final @NonNull String tenantId) {
        return TenantSftpEgressContent.forTenant(tenantId);
    }

    @Cacheable(TENANT_EGRESS_SESSIONS_CACHE_KEY)
    public List<TenantSftpEgressSession> tenantEgressSessions() {
        final var result = new ArrayList<TenantSftpEgressSession>();
        final var tecs = List.of(tenantEgressContent("healthelink"));
        for (var tec : tecs) {
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
                    final var sessionJson = sessionJsonFile.getContent();
                    result.add(new TenantSftpEgressSession(tec.tenantId(), egressSessionDir.getName().getBaseName(),
                            tec.cachedAt(),
                            sessionJsonFile.getPublicURIString(),
                            sessionJson.getString(Charset.defaultCharset()),
                            Date.from(Instant.ofEpochMilli(sessionJson.getLastModifiedTime())), null));
                }
            } catch (Exception e) {
                result.add(new TenantSftpEgressSession(tec.tenantId(), null, tec.cachedAt(),
                        tec.home().getPublicURIString(),
                        null, null, e));
            }

        }
        return result;
    }

    @CacheEvict(value = { TENANT_EGRESS_CONTENT_CACHE_KEY, TENANT_EGRESS_SESSIONS_CACHE_KEY }, allEntries = true)
    @Scheduled(fixedRateString = "${org.techbd.cache.tenant-sftp-egress-content.ttl:PT4H}")
    public void emptyTenantEgressCacheScheduled() {
        LOG.info("emptying " + TENANT_EGRESS_CONTENT_CACHE_KEY + " (scheduled)");
    }
}
