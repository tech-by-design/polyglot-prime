package org.techbd.ingest.feature;

import org.springframework.stereotype.Controller;
import org.togglz.core.manager.FeatureManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.techbd.ingest.util.AppLogger;
import org.techbd.ingest.util.TemplateLogger;


@Controller
public class CheckFeature {
    private final FeatureManager featureManager;
    private final TemplateLogger LOG;
    public CheckFeature(FeatureManager featureManager, AppLogger appLogger) {
        this.featureManager = featureManager;
        this.LOG = appLogger.getLogger(CheckFeature.class);
    }
    @GetMapping("/feature")
    @ResponseBody    
    public String checkFeature() {
        LOG.info("Feature is working");
        if (!featureManager.isActive(FeatureEnum.DEBUG_LOG_REQUEST_HEADERS)){
            LOG.info("Feature is not active");
            return "Feature is not active";
        }
        else{
            LOG.info("Feature is active");
            return "Feature is active";
        }

    }
    
}
