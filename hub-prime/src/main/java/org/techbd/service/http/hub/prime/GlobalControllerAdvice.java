package org.techbd.service.http.hub.prime;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.swagger.v3.core.util.ObjectMapperFactory;
import jakarta.servlet.http.HttpServletRequest;

@ControllerAdvice
public class GlobalControllerAdvice {
    private static final Logger LOG = LoggerFactory.getLogger(GlobalControllerAdvice.class.getName());
    private static final ObjectMapper baggageMapper = ObjectMapperFactory.buildStrictGenericObjectMapper();

    public record UserAgentControllerBaggage(String appVersion, String[] activeSpringProfiles) {
        UserAgentControllerBaggage(final Environment environment, final AppConfig appConfig) {
            this(appConfig.getVersion(), environment.getActiveProfiles());
        }
    }

    public record AuthenticatedUser(String fullName, String emailPrimary, String profilePicUrl) {
        AuthenticatedUser(final OAuth2User principal) {
            this((String) principal.getAttribute("name"), (String) principal.getAttribute("email"),
                    (String) principal.getAttribute("avatar_url"));
        }
    }

    @Value(value = "${org.techbd.service.baggage.user-agent.enable-sensitive:false}")
    private boolean userAgentSensitiveBaggageEnabled = false;

    @Value(value = "${org.techbd.service.baggage.user-agent.exposure:false}")
    private boolean userAgentBaggageExposureEnabled = false;

    private final UserAgentControllerBaggage uacBaggage;

    public GlobalControllerAdvice(final Environment environment, final AppConfig appConfig) {
        this.uacBaggage = new UserAgentControllerBaggage(environment, appConfig);
    }

    @ModelAttribute
    public void injectTypicalModelAttrs(final HttpServletRequest request,
            final @AuthenticationPrincipal OAuth2User principal, final Model model) {
        AuthenticatedUser authUser = null;
        if (principal != null) {
            authUser = new AuthenticatedUser(principal);
        }

        // make the request, authUser available to templates
        model.addAttribute("req", request);
        model.addAttribute("authUser", authUser);

        // Everything in uaBaggage will be "carried" into the user agent (browser) so
        // the content can be available to JavaScript (be careful not to expose secrets)
        // - model."uaBaggage" is for typed server-side usage by templates
        // - model."uaBaggageJSON" is for JavaScript client use
        final var uaBaggage = Map.of("userAgentBaggageExposureEnabled", userAgentBaggageExposureEnabled, "controller",
                uacBaggage, "authUser", authUser);
        model.addAttribute("baggage", uaBaggage);

        try {
            model.addAttribute("ssrBaggageJSON", baggageMapper.writeValueAsString(uaBaggage));
        } catch (JsonProcessingException e) {
            LOG.error("error setting ssrBaggageJSON in GlobalControllerAdvice.injectTypicalModelAttrs", e);
        }
    }
}
