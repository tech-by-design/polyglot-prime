package org.techbd.ingest.feature;

import org.springframework.stereotype.Controller;
import org.togglz.core.manager.FeatureManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;


@Controller
public class CheckFeature {
    private final FeatureManager featureManager;

    public CheckFeature(FeatureManager featureManager) {
        this.featureManager = featureManager;
    }
    @GetMapping("/feature")
    @ResponseBody    
    public String checkFeature() {
        System.out.println("Feature is working");
        if (!featureManager.isActive(FeatureEnum.DEBUG_LOG_REQUEST_HEADERS)){
            System.out.println("Feature is not active");
            return "Feature is not active";
        }
        else{
            System.out.println("Feature is active");
            return "Feature is active";
        }

    }
    
}
