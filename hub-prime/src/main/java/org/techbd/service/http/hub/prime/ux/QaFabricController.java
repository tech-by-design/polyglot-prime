package org.techbd.service.http.hub.prime.ux;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.techbd.service.http.hub.prime.route.RouteMapping;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

@Controller
@Tag(name = "QA Fabric API")
public class QaFabricController {
    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(QaFabricController.class.getName());
    public static final ObjectMapper headersOM = JsonMapper.builder()
            .findAndAddModules()
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .build();

    private final Presentation presentation;

    public QaFabricController(final Presentation presentation) throws Exception {
        this.presentation = presentation;
    }

    @RouteMapping(label = "QA Fabric", siblingOrder = 60)
    @GetMapping("/qa-fabric")
    public String docs() {
        return "redirect:/qa-fabric/hrsn-viewer-app";
    }

    @RouteMapping(label = "Certification Engine Scorecard", title = "Certification Engine Scorecard", siblingOrder = 10)
    @GetMapping("/qa-fabric/cert-engine-scorecard")
    public String certEngineScorecard(final Model model, final HttpServletRequest request) {
        return presentation.populateModel("page/qa-fabric/cert-engine-scorecard", model, request);
    }

    @RouteMapping(label = "HRSN Viewer", title = "HRSN Viewer App", siblingOrder = 20)
    @GetMapping("/qa-fabric/hrsn-viewer-app")
    public String hrsnViewerApp(final Model model, final HttpServletRequest request) {
        return presentation.populateModel("page/qa-fabric/hrsn-viewer-app", model, request);
    }
}
