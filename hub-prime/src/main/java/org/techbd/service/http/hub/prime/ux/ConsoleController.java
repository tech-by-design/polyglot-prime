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
@Tag(name = "Console API")
public class ConsoleController {
    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(ConsoleController.class.getName());
    public static final ObjectMapper headersOM = JsonMapper.builder()
            .findAndAddModules()
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .build();

    private final Presentation presentation;

    public ConsoleController(final Presentation presentation) throws Exception {
        this.presentation = presentation;
    }

    @RouteMapping(label = "Console", siblingOrder = 80)
    @GetMapping("/console")
    public String docs() {
        return "redirect:/console/health-info";
    }

     @RouteMapping(label = "Health Information", title = "Health Information", siblingOrder = 30)
    @GetMapping("/console/health-info")
    public String healthInformation(final Model model, final HttpServletRequest request) {
        return presentation.populateModel("page/console/health-info", model, request);
    }

    @RouteMapping(label = "Project", title = "Project", siblingOrder = 40)
    @GetMapping("/console/project")
    public String projects(final Model model, final HttpServletRequest request) {
        return presentation.populateModel("page/console/project", model, request);
    }

    @RouteMapping(label = "Schema", title = "Schema", siblingOrder = 50)
    @GetMapping("/console/schema")
    public String schemaSpy(final Model model, final HttpServletRequest request) {
        return presentation.populateModel("page/console/schema", model, request);
    }

    @RouteMapping(label = "ISLM Migration", title = "ISLM", siblingOrder = 60)
    @GetMapping("/console/islm")
    public String islm(final Model model, final HttpServletRequest request) {
        return presentation.populateModel("page/console/islm", model, request);
    }

    // @RouteMapping(label = "Cron Jobs", title = "Cron Jobs", siblingOrder = 70)
    // @GetMapping("/console/cron-job")
    // public String cronjob(final Model model, final HttpServletRequest request) {
    //     return presentation.populateModel("page/console/cron-job", model, request);
    // }

    // @RouteMapping(label = "CloudWatch Dashoard", title = "CloudWatch Dashoard", siblingOrder = 80)
    // @GetMapping("/console/cloudwatch-dashoard")
    // public String cloudwatchDashoard(final Model model, final HttpServletRequest request) {
    //     return presentation.populateModel("page/console/cloudwatch-dashoard", model, request);
    // }

}
