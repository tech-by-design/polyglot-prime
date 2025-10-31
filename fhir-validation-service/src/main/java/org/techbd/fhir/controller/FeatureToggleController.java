package org.techbd.fhir.controller;

import org.techbd.fhir.feature.FeatureEnum;
import org.springframework.web.bind.annotation.*;
import org.togglz.core.manager.FeatureManager;

@RestController
@RequestMapping("/api/features")
public class FeatureToggleController {

    private final FeatureManager featureManager;

    public FeatureToggleController(FeatureManager featureManager) {
        this.featureManager = featureManager;
    }

    @PostMapping("/enable")
    public String enableFeature(@RequestParam("name") String name) {
        FeatureEnum feature = FeatureEnum.valueOf(name.toUpperCase());
        featureManager.setFeatureState(new org.togglz.core.repository.FeatureState(feature, true));
        return name + " enabled";
    }

    @PostMapping("/disable")
    public String disableFeature(@RequestParam("name") String name) {
        FeatureEnum feature = FeatureEnum.valueOf(name.toUpperCase());
        featureManager.setFeatureState(new org.togglz.core.repository.FeatureState(feature, false));
        return name + " disabled";
    }

    @GetMapping("/status")
    public String isFeatureActive(@RequestParam("name") String name) {
        FeatureEnum feature = FeatureEnum.valueOf(name.toUpperCase());
        boolean active = featureManager.isActive(feature);
        return name + " is " + (active ? "enabled" : "disabled");
    }
    
    @GetMapping("/all/feature/status")
    @ResponseBody    
    public String checkFeature() {
        boolean trackingActive = featureManager.isActive(FeatureEnum.FEATURE_DATA_LEDGER_TRACKING);
        boolean diagnosticsActive = featureManager.isActive(FeatureEnum.FEATURE_DATA_LEDGER_DIAGNOSTICS);
        
        return String.format("DATA_LEDGER_TRACKING: %s, DATA_LEDGER_DIAGNOSTICS: %s", 
            trackingActive ? "ACTIVE" : "NOT ACTIVE",
            diagnosticsActive ? "ACTIVE" : "NOT ACTIVE");
    }
}