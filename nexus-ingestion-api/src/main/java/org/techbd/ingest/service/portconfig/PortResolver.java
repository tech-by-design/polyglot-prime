package org.techbd.ingest.service.portconfig;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.techbd.ingest.config.PortConfig;
import org.techbd.ingest.model.RequestContext;

/**
 * Resolves a {@link PortConfig.PortEntry} based on the destination port 
 * provided in the {@link RequestContext}.
 *
 * <p>This resolver is ordered second in the resolution chain
 * (see {@link Order @Order(2)}). It attempts to match the incoming request's
 * {@code destinationPort} against the {@code port} value defined in each
 * {@link PortConfig.PortEntry}.
 *
 * <p>Resolution succeeds when:
 * <ul>
 *   <li>The request includes a non-null destination port, and</li>
 *   <li>Any entry in {@code portConfigList} has a port value equal to this 
 *       destination port.</li>
 * </ul>
 *
 * <p>If the destination port is missing or no match is found, an empty 
 * {@link Optional} is returned, allowing the next resolver in the chain
 * to attempt resolution.
 */
@Component
@Order(2)
public class PortResolver implements PortConfigResolver {

    @Override
    public Optional<PortConfig.PortEntry> resolve(
            List<PortConfig.PortEntry> portConfigList,
            RequestContext context) {

        String destPort = context.getDestinationPort();
        if (destPort == null) {
            return Optional.empty();
        }

        return portConfigList.stream()
                .filter(Objects::nonNull)
                .filter(entry -> String.valueOf(entry.getPort()).equals(destPort))
                .findFirst();
    }
}
