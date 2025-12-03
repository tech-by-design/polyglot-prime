package org.techbd.ingest.service.portconfig;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.techbd.ingest.config.PortConfig;
import org.techbd.ingest.model.RequestContext;

/**
 * RouteParamResolver selects a {@link PortConfig.PortEntry} based on the
 * request's route parameters — specifically {@code sourceId} and
 * {@code msgType}.
 *
 * <p>
 * This resolver runs early in the resolver chain (Order = 1) and attempts
 * to match a port configuration entry where both:
 *
 * <ul>
 * <li>{@code entry.sourceId} equals the incoming request's
 * {@code sourceId}</li>
 * <li>{@code entry.msgType} equals the incoming request's {@code msgType}</li>
 * </ul>
 *
 * <p>
 * If a matching entry exists, it is returned; otherwise an empty
 * {@link Optional} is returned, allowing the next resolver in the chain to
 * attempt resolution.
 *
 * <p>
 * This resolver is part of the strategy chain used by
 * {@code PortConfigApplierService} to determine routing, bucket selection,
 * queue,
 * and data/metadata directory behaviors for incoming ingestion requests.
 */
@Component
@Order(1)
public class RouteParamResolver implements PortConfigResolver {

    /**
     * Attempts to resolve a {@link PortConfig.PortEntry} based on the
     * request's {@code sourceId} and {@code msgType}.
     *
     * @param portConfigList the list of configured port entries loaded from S3 or
     *                       local configuration
     * @param context        the current request context containing routing metadata
     * @return {@code Optional.of(entry)} when a matching entry is found,
     *         otherwise {@code Optional.empty()}
     */
    @Override
    public Optional<PortConfig.PortEntry> resolve(
            List<PortConfig.PortEntry> portConfigList,
            RequestContext context) {

        String sourceId = context.getSourceId();
        String msgType = context.getMsgType();

        if (sourceId == null || msgType == null) {
            return Optional.empty();
        }

        return portConfigList.stream()
                .filter(Objects::nonNull)
                .filter(entry -> matches(entry, sourceId, msgType))
                .findFirst();
    }

    /**
     * Checks whether a configuration entry matches the provided
     * {@code sourceId} and {@code msgType}.
     *
     * @param entry    the port configuration entry to check
     * @param sourceId the expected source identifier
     * @param msgType  the expected message type
     * @return {@code true} if both values match (case-insensitive), otherwise
     *         {@code false}
     */
    private boolean matches(PortConfig.PortEntry entry, String sourceId, String msgType) {
        return sourceId.equalsIgnoreCase(entry.getSourceId())
                && msgType.equalsIgnoreCase(entry.getMsgType());
    }
}
