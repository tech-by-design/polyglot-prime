package org.techbd.service.http.hub.prime.ux;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.techbd.orchestrate.sftp.SftpManager;
import org.techbd.service.http.SandboxHelpers;
import org.techbd.service.http.hub.prime.route.RouteMapping;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;



@Controller
@Tag(name = "Need Attention of Tenant")
public class NeedAttentionController {
    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(DataQualityController.class.getName());

    private final Presentation presentation;

    public NeedAttentionController(final Presentation presentation,
            @SuppressWarnings("PMD.UnusedFormalParameter") final SftpManager sftpManager,
            @SuppressWarnings("PMD.UnusedFormalParameter") final SandboxHelpers sboxHelpers) {
        this.presentation = presentation;
    }

    public List<String> getValuesForField(String field) {
        List<String> values = Arrays.asList(field);
        return values;
    }

    @GetMapping("/needs-attention") 
    @RouteMapping(label = "Needs Attention", title = "Needs Attention", siblingOrder = 100)
    public String diagnosticsFhirNeedsAttention() {
        return "redirect:/data-quality/needs-attention";
    } 

    @GetMapping("/needs-attention/techbd-processing-failures")
    @RouteMapping(label = "Tech by Design – Processing Failures", title = "Tech by Design – Processing Failures", siblingOrder = 5)
    public String diagnosticsFhirNeedsAttentionScnToQe(final Model model, final HttpServletRequest request) {
 
    model.addAttribute("qeName", request.getParameter("qeName"));
    String templateName = "page/diagnostics/techbd-processing-failures"; 
    return presentation.populateModel(templateName, model, request);

    }    

    @GetMapping("/needs-attention/techbd-validation-failures")
    @RouteMapping(label = "Tech by Design – Validation Failures", title = "Tech by Design – Validation Failures", siblingOrder = 6)
    public String diagnosticsFhirNeedsAttentionTechbdToScoringEngine(final Model model, final HttpServletRequest request) {
 
    model.addAttribute("qeName", request.getParameter("qeName"));
    String templateName = "page/diagnostics/techbd-validation-failures"; 
    return presentation.populateModel(templateName, model, request);

    }    

    @GetMapping("/needs-attention/shinny-datalake-failed-submissions")
    @RouteMapping(label = "SHIN-NY Data Lake – Failed Submissions", title = "SHIN-NY Data Lake – Failed Submissions", siblingOrder = 7)
    public String diagnosticsFhirNeedsAttentionQeToTechbd(final Model model, final HttpServletRequest request) {
 
    model.addAttribute("qeName", request.getParameter("qeName"));
    String templateName = "page/diagnostics/shinny-datalake-failed-submissions"; 
    return presentation.populateModel(templateName, model, request);

    }    
}
