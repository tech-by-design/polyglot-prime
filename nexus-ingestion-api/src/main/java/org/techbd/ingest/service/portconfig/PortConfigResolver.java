package org.techbd.ingest.service.portconfig;

import java.util.List;
import java.util.Optional;

import org.techbd.ingest.config.PortConfig;
import org.techbd.ingest.model.RequestContext;

/**
 * Resolves the appropriate {@link PortConfig.PortEntry} for a given request.
 * <p>
 * Implementations of this interface determine how a request maps to a
 * PortConfig entry, based on fields such as {@code sourceId}, {@code msgType},
 * and optional route headers.
 * </p>
 * <p>
 * This abstraction allows multiple resolution strategies (e.g., route-based,
 * port-based) without coupling the caller to specific logic.
 * </p>
 */
public interface PortConfigResolver {

    /**
     * Attempts to resolve the correct {@link PortConfig.PortEntry} for the incoming request.
     *
     * @param portConfigList the list of PortConfig entries loaded from configuration
     * @param context        contextual request information such as sourceId, msgType,
     *                       HTTP headers, AWS identifiers, timestamps, etc.
     * @return an {@link Optional} containing the resolved PortEntry if a match is found,
     *         or an empty Optional if no matching configuration is applicable
     */
    Optional<PortConfig.PortEntry> resolve(
            List<PortConfig.PortEntry> portConfigList,
            RequestContext context
    );
}
