package org.techbd.corelib.service.tenant;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.techbd.udi.auto.jooq.ingress.tables.GetActiveTenants;
import org.techbd.udi.auto.jooq.ingress.tables.records.GetActiveTenantsRecord;

@Service
public class TenantService {

    private static final Logger LOG = LoggerFactory.getLogger(TenantService.class);

    private final DSLContext dslContext;

    public TenantService(final DSLContext dslContext) {
        this.dslContext = dslContext;
    }

    public List<Map<String, Object>> getActiveTenants(final String interactionId) {
        if (interactionId == null || interactionId.isBlank()) {
            throw new IllegalArgumentException("interactionId must not be null or blank");
        }

        LOG.info("TENANT-SERVICE Fetching active tenants | interactionId={}", interactionId);

        try {
            final List<GetActiveTenantsRecord> records = dslContext
                    .selectFrom(GetActiveTenants.GET_ACTIVE_TENANTS)
                    .fetchInto(GetActiveTenantsRecord.class);

            if (records == null || records.isEmpty()) {
                LOG.warn("TENANT-SERVICE No active tenants found | interactionId={}", interactionId);
                return List.of();
            }

            final List<Map<String, Object>> tenants = records.stream()
                    .filter(r -> r.getPTenantId() != null && !r.getPTenantId().isBlank())
                    .map(r -> {
                        final Map<String, Object> tenant = new LinkedHashMap<>();
                        tenant.put("tenantId", r.getPTenantId());
                        tenant.put("tenantName", r.getPTenantName());
                        tenant.put("tenantType", r.getPTenantType());
                        return tenant;
                    })
                    .collect(Collectors.toList());

            LOG.info("TENANT-SERVICE Found {} active tenants | interactionId={}", tenants.size(), interactionId);
            return tenants;

        } catch (Exception e) {
            LOG.error("TENANT-SERVICE Error fetching active tenants | interactionId={} : {}",
                    interactionId, e.getMessage(), e);
            throw new RuntimeException("Failed to fetch active tenants", e);
        }
    }
}
