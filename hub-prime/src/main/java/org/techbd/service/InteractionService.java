package org.techbd.service;


import java.time.OffsetDateTime;
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
import org.techbd.service.http.GitHubUserAuthorizationFilter;
import org.techbd.service.http.Interactions.RequestResponseEncountered;
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

    @Value("${AUTH_PROVIDER:fusionauth}")
    private String authProvider;

    @Transactional
    public void saveInteractionToDatabase(RequestResponseEncountered rre, String requestURI, 
            OffsetDateTime createdAt, String provenance, HttpServletRequest origRequest) {
       final var rihr = new RegisterUserInteraction();
            try {
                LOG.info("REGISTER State None : BEGIN for  interaction id : {} tenant id : {}",
                rre.interactionId().toString(), rre.tenant());
                final var tenant = rre.tenant();
                rihr.setPInteractionId(rre.interactionId().toString());
                rihr.setPNature((JsonNode)Configuration.objectMapper.valueToTree(
                        Map.of("nature", RequestResponseEncountered.class.getName(), "tenant_id",
                                tenant != null ? tenant.tenantId() != null ? tenant.tenantId() : "N/A" : "N/A")));
                rihr.setPContentType(MimeTypeUtils.APPLICATION_JSON_VALUE);
                rihr.setPInteractionKey(requestURI);
                rihr.setPSourceType(SourceType.FHIR.name());
                rihr.setPPayload((JsonNode) Configuration.objectMapper.valueToTree(rre));
                rihr.setPCreatedAt(createdAt); // don't let DB set this, since it might be stored out of order
                rihr.setPCreatedBy(InteractionService.class.getName());
                rihr.setPTechbdVersionNumber(coreAppConfig.getVersion());
                rihr.setPProvenance(provenance);
                // User details
                if (saveUserDataToInteractions) {
                    var curUserName = "API_USER";
                    var userId = "N/A";
                    final var sessionId = origRequest.getRequestedSessionId();
                    var userRole = "API_ROLE";

                    Optional<?> curUser = Optional.empty();

                    if ("fusionauth".equalsIgnoreCase(authProvider)) {
                        curUser = FusionAuthUserAuthorizationFilter.getAuthenticatedUser(origRequest);
                    } else if ("github".equalsIgnoreCase(authProvider)) {
                        curUser = GitHubUserAuthorizationFilter.getAuthenticatedUser(origRequest);
                    }

                    if (curUser.isPresent()) {
                        Object user = curUser.get();

                        if ("fusionauth".equalsIgnoreCase(authProvider)) {
                            final var faUser = ((FusionAuthUserAuthorizationFilter.AuthenticatedUser) user).faUser();
                            if (faUser != null) {
                                curUserName = Optional.ofNullable(faUser.name()).orElse("NO_DATA");
                                userId = Optional.ofNullable(faUser.fusionAuthId()).orElse("NO_DATA");
                            }

                            userRole = ((FusionAuthUserAuthorizationFilter.AuthenticatedUser) user)
                                    .principal()
                                    .getAuthorities()
                                    .stream()
                                    .map(GrantedAuthority::getAuthority)
                                    .collect(Collectors.joining(","));

                        } else if ("github".equalsIgnoreCase(authProvider)) {
                            final var ghUser = ((GitHubUserAuthorizationFilter.AuthenticatedUser) user).ghUser();
                            if (ghUser != null) {
                                curUserName = Optional.ofNullable(ghUser.name()).orElse("NO_DATA");
                                userId = Optional.ofNullable(ghUser.gitHubId()).orElse("NO_DATA");
                            }

                            userRole = ((GitHubUserAuthorizationFilter.AuthenticatedUser) user)
                                    .principal()
                                    .getAuthorities()
                                    .stream()
                                    .map(GrantedAuthority::getAuthority)
                                    .collect(Collectors.joining(","));
                        }

                        LOG.info("userRole: " + userRole);
                        userRole = "DEFAULT_ROLE"; // TODO: remove when real role mapping is implemented
                    }
                    rihr.setPUserName(curUserName);
                    rihr.setPUserId(userId);
                    rihr.setPUserSession(sessionId);
                    rihr.setPUserRole(userRole);
                } else {
                    LOG.info("User details are not saved with Interaction as saveUserDataToInteractions: "
                            + saveUserDataToInteractions);
                }

                rihr.execute(primaryDslContext.configuration());
                LOG.info("REGISTER State None : END for  interaction id : {} tenant id : {}",
                rre.interactionId().toString(), rre.tenant());
            } catch (Exception e) {
                LOG.error("ERROR:: REGISTER State None  for  interaction id : {} tenant id : {} : CALL " + rihr.getName() + " error",  rre.interactionId().toString(), rre.tenant(),e);
        }
    }
}
