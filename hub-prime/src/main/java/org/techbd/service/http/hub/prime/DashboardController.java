package org.techbd.service.http.hub.prime;

import org.apache.commons.vfs2.FileSystemException;
import org.apache.commons.vfs2.VFS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;

@RestController
public class DashboardController {
    static private final Logger LOG = LoggerFactory.getLogger(DashboardController.class);

    @GetMapping("/hateos/dashboard/stat/sftp/most-recent-egress/{tenantId}")
    public String getMostRecentEgress(@PathVariable String tenantId) {
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

            if (directories.length > 0) {
                final var mostRecent = directories[0];
                final var creationTime = Instant.ofEpochMilli(mostRecent.getContent().getLastModifiedTime())
                        .atZone(ZoneId.systemDefault());
                final var duration = Duration.between(creationTime, ZonedDateTime.now());
                return "<span title=\"%d sessions found, most recent %s\">%s</span>".formatted(directories.length, creationTime,
                        formatDuration(duration) + " ago");
            }
        } catch (Exception e) {
            LOG.error(sftpUri, e);
        }
        return "<span title=\"No directories found in %s\">⚠️</span>".formatted(sftpUri);
    }

    protected String formatDuration(Duration duration) {
        long days = duration.toDays();
        duration = duration.minusDays(days);
        long hours = duration.toHours();
        duration = duration.minusHours(hours);
        long minutes = duration.toMinutes();

        if (days > 0) {
            return days + " day" + (days > 1 ? "s" : "");
        } else if (hours > 0) {
            return hours + " hour" + (hours > 1 ? "s" : "");
        } else {
            return minutes + " minute" + (minutes > 1 ? "s" : "");
        }
    }
}
