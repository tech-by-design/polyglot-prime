package org.techbd.service.http.hub.prime.ux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;
import org.techbd.orchestrate.sftp.SftpManager;
import org.techbd.service.http.SandboxHelpers;
import org.techbd.service.http.hub.prime.AppConfig;
import org.techbd.udi.UdiPrimeJpaConfig;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.v3.core.util.ObjectMapperFactory;
import jakarta.servlet.http.HttpServletRequest;

@Service
public class Presentation {
    private static final Logger LOG = LoggerFactory.getLogger(Presentation.class.getName());

    private final Map<String, Object> ssrBaggage = new HashMap<>();
    private final ObjectMapper baggageMapper = ObjectMapperFactory.buildStrictGenericObjectMapper();
    private final AppConfig appConfig;
    private final UdiPrimeJpaConfig udiPrimeJpaConfig;
    private final SandboxHelpers sboxHelpers;

    @Value(value = "${org.techbd.service.baggage.user-agent.enable-sensitive:false}")
    private boolean userAgentSensitiveBaggageEnabled = false;

    @Value(value = "${org.techbd.service.baggage.user-agent.exposure:false}")
    private boolean userAgentBaggageExposureEnabled = false;

    @Autowired
    private Environment environment;

    public Presentation(final Environment environment, final AppConfig appConfig,
            final UdiPrimeJpaConfig udiPrimeJpaConfig,
            final SftpManager sftpManager,
            final SandboxHelpers sboxHelpers) {
        this.environment = environment;
        this.appConfig = appConfig;
        this.udiPrimeJpaConfig = udiPrimeJpaConfig;
        this.sboxHelpers = sboxHelpers;
        ssrBaggage.put("appVersion", this.appConfig.getVersion());
        ssrBaggage.put("activeSpringProfiles", List.of(this.environment.getActiveProfiles()));
    }

    protected void populateModel(final String templateName, final Model model, final HttpServletRequest request) {
        try {
            final var principal = (OAuth2User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            AuthenticatedUser authUser = null;
            if (principal != null) {
                authUser = new AuthenticatedUser(principal, "", new ArrayList<String>());
            }

            // make the request, authUser available to templates
            model.addAttribute("req", request);
            model.addAttribute("authUser", authUser);

            final var baggage = new HashMap<>(ssrBaggage);
            baggage.put("userAgentBaggageExposureEnabled", userAgentBaggageExposureEnabled);
            baggage.put("health",
                    Map.of("udiPrimaryDataSourceAlive", udiPrimeJpaConfig.udiPrimaryDataSrcHealth().isAlive()));

            if (sboxHelpers.isEditorAvailable()) {
                final var canonicalTmplName = "templates/" + templateName + ".html";
                final var targetResource = getClass().getClassLoader().getResource(canonicalTmplName);
                final var targetFsPath = targetResource != null ? targetResource.getFile() : null;
                final var srcFsPath = targetFsPath != null
                        ? targetFsPath.replace("target/classes", "src/main/resources")
                        : null;
                final var editUrl = srcFsPath != null ? sboxHelpers.getEditorUrlFromAbsolutePath(srcFsPath) : null;
                baggage.put("template",
                        Map.of("supplied", templateName, "canonical", canonicalTmplName, "targetFsPath", targetFsPath,
                                "srcFsPath", srcFsPath, "editUrl", editUrl));
            }

            // "baggage" is for typed server-side usage by templates
            // "ssrBaggageJSON" is for JavaScript client use
            model.addAttribute("baggage", baggage);
            model.addAttribute("ssrBaggageJSON", baggageMapper.writeValueAsString(baggage));
            LOG.info("Logged in user Information"
                    + SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        } catch (JsonProcessingException e) {
            LOG.error("error setting ssrBaggageJSON in populateModel", e);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AuthenticatedUser(String name, String emailPrimary, String profilePicUrl, String gitHubId,
            String tenantId, List<String> roles) {
        public AuthenticatedUser(final OAuth2User principal, String tenantId, List<String> roles) {
            this((String) principal.getAttribute("name"), (String) principal.getAttribute("email"),
                    (String) principal.getAttribute("avatar_url"), (String) principal.getAttribute("login"), tenantId,
                    roles);
        }
    }
}
