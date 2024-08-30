package org.techbd.service.http.hub.prime.ux;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

@Controller
@Tag(name = "Tech by Design Hub Experiments API")
public class ExperimentsController {
    private final Presentation presentation;

    public ExperimentsController(final Presentation presentation) {
        this.presentation = presentation;
    }

    @GetMapping(value = "/experiment/{page}.html")
    @Profile(value = "sandbox")
    public String navPrimeDebug(@PathVariable String page, final Model model, final HttpServletRequest request) {
        return presentation.populateModel("page/experiments/%s".formatted(page), model, request);
    }
}
