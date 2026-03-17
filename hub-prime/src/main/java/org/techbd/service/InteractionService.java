package org.techbd.service;


import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.jooq.DSLContext;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MimeTypeUtils;
import org.techbd.conf.Configuration;
import org.techbd.config.CoreAppConfig;
import org.techbd.service.constants.SourceType;
import org.techbd.service.http.FusionAuthUserAuthorizationFilter;
import org.techbd.service.http.Interactions.RequestResponseEncountered;
import org.techbd.service.http.InteractionsFilter;
import org.techbd.udi.auto.jooq.ingress.routines.RegisterUserInteraction;
import org.techbd.util.AppLogger;
import org.techbd.util.TemplateLogger;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class InteractionService {

    private static TemplateLogger LOG;

    public InteractionService(AppLogger appLogger) {
        LOG = appLogger.getLogger(InteractionService.class);
    }   

    @Autowired
    @Qualifier("primaryDslContext")
    private DSLContext primaryDslContext;

    @Autowired
    private CoreAppConfig coreAppConfig;

    @Value("${org.techbd.service.http.interactions.saveUserDataToInteractions:true}")
    private boolean saveUserDataToInteractions;

    @Transactional
    public void saveInteractionToDatabase(RequestResponseEncountered rre, String requestURI, 
            OffsetDateTime createdAt, String provenance, HttpServletRequest origRequest) {
       final var rihr = new RegisterUserInteraction();
            try {
                LOG.info("REGISTER State None : BEGIN for  interaction id : {} tenant id : {}",
                rre.interactionId().toString(), rre.tenant());
                final var tenant = rre.tenant();
                final var dsl = primaryDslContext.dsl();
                rihr.setPInteractionId(rre.interactionId().toString());
                rihr.setPNature((JsonNode)Configuration.objectMapper.valueToTree(
                        Map.of("nature", RequestResponseEncountered.class.getName(), "tenant_id",
                                tenant != null ? tenant.tenantId() != null ? tenant.tenantId() : "N/A" : "N/A")));
                rihr.setPContentType(MimeTypeUtils.APPLICATION_JSON_VALUE);
                rihr.setPInteractionKey(requestURI);
                rihr.setPSourceType(SourceType.FHIR.name());
                rihr.setPPayload((JsonNode) Configuration.objectMapper.valueToTree(rre));
                rihr.setPCreatedAt(createdAt); // don't let DB set this, since it might be stored out of order
                rihr.setPCreatedBy(InteractionsFilter.class.getName());
                rihr.setPTechbdVersionNumber(coreAppConfig.getVersion());
                rihr.setPProvenance(provenance);
                // User details
                var userRole = "API_ROLE";
                Object role=null;
                String roleString="N/A";
                String sql ="";
                if (saveUserDataToInteractions) {
                    var curUserName = "API_USER";
                    var fusionAuthUserId  = "N/A";
                    final var sessionId = origRequest.getRequestedSessionId();
                    final var curUser = FusionAuthUserAuthorizationFilter.getAuthenticatedUser(origRequest);
                    if (curUser.isPresent()) {
                        final var faUser = curUser.get().faUser();
                        if (null != faUser) {
                            curUserName = Optional.ofNullable(faUser.name()).orElse("NO_DATA");
                            fusionAuthUserId  = Optional.ofNullable(faUser.fusionAuthId()).orElse("NO_DATA");
                            userRole = curUser.get().principal().getAuthorities().stream()
                                    .map(GrantedAuthority::getAuthority)
                                    .collect(Collectors.joining(","));
                            LOG.info("userRole: " + userRole);
                            role = curUser.get().principal().getAttributes().get("role");
                            Object tenantsObj  = curUser.get().principal().getAttributes().get("groupNames"); 
                           if (role instanceof Collection<?> roles && !roles.isEmpty()) {
                                 roleString = roles.stream()
                                                        .map(Object::toString)
                                                        .collect(Collectors.joining(","));

                                  String tenantsString = "";
                           if (tenantsObj instanceof Collection<?> tenants && !tenants.isEmpty()) {
                                    tenantsString = tenants.stream()
                                                        .map(Object::toString)
                                                        .collect(Collectors.joining(","));
                                }
                                 sql = String.format(
                                    "SET ROLE \"%s\"; SET jwt.claims.tenants = '%s';",
                                    roleString, tenantsString
                                );
                                LOG.info("Executing SQL: " + sql);
                            }
                        }
                    }
                    rihr.setPUserName(curUserName);
                    rihr.setPUserId(fusionAuthUserId );
                    rihr.setPUserSession(sessionId);
                    rihr.setPUserRole(roleString != null ? roleString : userRole); 
                } else {
                    LOG.info("User details are not saved with Interaction as saveUserDataToInteractions: "
                            + saveUserDataToInteractions);
                }
                dsl.execute(sql);
                rihr.execute(dsl.configuration());
                LOG.info("REGISTER State None : END for  interaction id : {} tenant id : {}",
                rre.interactionId().toString(), rre.tenant());
            } catch (Exception e) {
                LOG.error("ERROR:: REGISTER State None  for  interaction id : {} tenant id : {} : CALL " + rihr.getName() + " error",  rre.interactionId().toString(), rre.tenant(),e);
        }
    }
}