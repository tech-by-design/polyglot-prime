package org.techbd.service.http.hub.prime;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Date;
import java.util.Optional;

import org.apache.commons.vfs2.FileObject;
import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.VFS;
import org.ocpsoft.prettytime.PrettyTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Home Controller", description = "Dashboard APIs")
public class HomeController {
    static private final Logger LOG = LoggerFactory.getLogger(HomeController.class);

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

    @Cacheable("tenant-sftp-egress-content")
    public TenantSftpEgressContent tenantEgressContent(final @NonNull String tenantId) {
        return TenantSftpEgressContent.forTenant(tenantId);
    }

    @CacheEvict(value = "tenant-sftp-egress-content", allEntries = true)
    @Scheduled(fixedRateString = "${org.techbd.cache.tenant-sftp-egress-content.ttl:PT4H}")
    public void emptyTenantEgressCacheScheduled() {
        LOG.info("emptying tenant-sftp-egress-content (scheduled)");
    }

    @GetMapping(value = "/admin/cache/tenant-sftp-egress-content/clear")
    @CacheEvict(value = "tenant-sftp-egress-content", allEntries = true)
    public ResponseEntity<?> emptyTenantEgressCacheOnDemand() {
        LOG.info("emptying tenant-sftp-egress-content (on demand)");
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body("emptying tenant-sftp-egress-content");
    }

    @GetMapping(value = "/dashboard/stat/sftp/most-recent-egress/{tenantId}.{extension}", produces = {
            "application/json", "text/html" })
    public ResponseEntity<?> handleRequest(@PathVariable String tenantId, @PathVariable String extension) {
        final var content = tenantEgressContent(tenantId);
        final var mre = content.mostRecentEgress();

        if ("html".equalsIgnoreCase(extension)) {
            String timeAgo = mre.map(zonedDateTime -> new PrettyTime().format(zonedDateTime)).orElse("None");
            return ResponseEntity.ok().contentType(MediaType.TEXT_HTML)
                    .body(content.error == null
                            ? "<span title=\"%d sessions found, most recent %s\">%s</span>".formatted(
                                    content.directories.length,
                                    mre,
                                    timeAgo)
                            : "<span title=\"No directories found in %s\">⚠️</span>".formatted(content.sftpUri));
        } else if ("json".equalsIgnoreCase(extension)) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(mre);
        } else {
            return ResponseEntity.badRequest().build();
        }
    }
}
