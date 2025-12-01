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
import org.techbd.udi.UdiPrimeJpaConfig;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

@Controller
@Tag(name = "Tech by Design Hub Data Quality UX API")
public class DataQualityController {
    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(DataQualityController.class.getName());

    private final Presentation presentation;

    public DataQualityController(final Presentation presentation,
            @SuppressWarnings("PMD.UnusedFormalParameter") final UdiPrimeJpaConfig udiPrimeJpaConfig,
            @SuppressWarnings("PMD.UnusedFormalParameter") final SftpManager sftpManager,
            @SuppressWarnings("PMD.UnusedFormalParameter") final SandboxHelpers sboxHelpers) {
        this.presentation = presentation;
    }

    public List<String> getValuesForField(String field) {
        List<String> values = Arrays.asList(field);
        return values;
    }

    @GetMapping("/data-quality")
    @RouteMapping(label = "Data Quality", siblingOrder = 10)
    public String adminDiagnostics() {
        return "redirect:/data-quality/needs-attention";
    }

    @GetMapping("/data-quality/needs-attention")
    @RouteMapping(label = "Needs Attention", title = "Needs Attention", siblingOrder = 5)
    public String diagnosticsFhirNeedsAttention(final Model model, final HttpServletRequest request) {
        String templateName = "page/diagnostics/needs-attention";
        if (null != request.getParameter("qeName")) {
            model.addAttribute("qeName", request.getParameter("qeName"));
            templateName = "page/diagnostics/techbd-to-scoring-engine";
        }
        return presentation.populateModel(templateName, model, request);
    }
 

    @GetMapping("/data-quality/fhir-validation-issues")
    @RouteMapping(label = "FHIR Data Quality", title = "FHIR Data Quality", siblingOrder = 30)
    public String diagnosticsFhirValidationIssues(final Model model, final HttpServletRequest request) {
        return presentation.populateModel("page/diagnostics/fhir-validation-issues", model, request);
    }

    @GetMapping("/data-quality/csv-validations") 
    @RouteMapping(label = "CSV Data Quality", title = "CSV Data Quality", siblingOrder = 40)    
    public String diagnosticsHttpsViaCsvValidationIssues(final Model model, final HttpServletRequest request) {
        return "redirect:/data-quality/csv-validations/csv-issues-summary";
    }   
    
    // @GetMapping("/data-quality/csv-validations/file-not-processed")
    // @RouteMapping(label = "Files Not Processed", title = "Files Not Processed", siblingOrder = 40)
    // public String diagnosticsFileNotProcessedIssues(final Model model, final HttpServletRequest request) {
    //     return presentation.populateModel("page/diagnostics/file-not-processed", model, request);
    // }       
    
    // @GetMapping("/data-quality/csv-validations/incomplete-groups")
    // @RouteMapping(label = "Incomplete Groups", title = "Incomplete Groups", siblingOrder = 50)
    // public String diagnosticsIncompleteGroupsIssues(final Model model, final HttpServletRequest request) {
    //     return presentation.populateModel("page/diagnostics/incomplete-groups", model, request);
    // }   
    
    // @GetMapping("/data-quality/csv-validations/data-integrity")
    // @RouteMapping(label = "Data Integrity Errors", title = "Data Integrity Errors", siblingOrder = 60)
    // public String diagnosticsDataIntegrityIssues(final Model model, final HttpServletRequest request) {
    //     return presentation.populateModel("page/diagnostics/data-integrity", model, request);
    // }       
    
    // @GetMapping("/data-quality/csv-validations/processing-errors")
    // @RouteMapping(label = "Processing Errors", title = "Processing Errors", siblingOrder = 40)
    // public String diagnosticsProcessingErrors(final Model model, final HttpServletRequest request) {
    //     return presentation.populateModel("page/diagnostics/processing-errors", model, request);
    // }
    
    @GetMapping("/data-quality/csv-validations/csv-issues-summary")
    @RouteMapping(label = "CSV Issues Summary", title = "CSV Issues Summary", siblingOrder = 10)
    public String csvErrorSummary(final Model model, final HttpServletRequest request) {        
        return presentation.populateModel("page/diagnostics/csv-issues-summary", model, request);
    }    

    @GetMapping("/data-quality/csv-validations/csv-issue-details")
    @RouteMapping(label = "CSV Issues", title = "CSV Issues", siblingOrder = 20)
    public String csvAllErrorSummary(final Model model, final HttpServletRequest request) {        
        return presentation.populateModel("page/diagnostics/csv-issue-details", model, request);
    }       

    @GetMapping("/data-quality/ccda-validations-issues") 
    @RouteMapping(label = "CCDA Data Quality", title = "CCDA Data Quality", siblingOrder = 50)    
    public String diagnosticsHttpsViaCCDAValidationIssues(final Model model, final HttpServletRequest request) {
        return presentation.populateModel("page/diagnostics/ccda-validations-issues", model, request);
    }   
    @GetMapping("/data-quality/hl7v2-validations-issues") 
    @RouteMapping(label = "HL7v2 Data Quality", title = "HL7v2 Data Quality", siblingOrder = 60)    
    public String diagnosticsHttpsViaHL7v2ValidationIssues(final Model model, final HttpServletRequest request) {
    return presentation.populateModel("page/diagnostics/hl7v2-validations-issues", model, request);
    }   
    @GetMapping("/data-quality/ig-publication-issues")
    @RouteMapping(label = "IG Publication Issues", title = "IG Publication Issues", siblingOrder = 70)
    public String igPublicationIssues(final Model model, final HttpServletRequest request) {
        return presentation.populateModel("page/diagnostics/ig-publication-issues", model, request);
    }

    @GetMapping("/data-quality/fhir-rules")
    @RouteMapping(label = "FHIR Rules", title = "FHIR Rules", siblingOrder = 80)
    public String fhirRules(final Model model, final HttpServletRequest request) {
        return presentation.populateModel("page/diagnostics/fhir-rules", model, request);
    }

}