package org.techbd.ingest.controller;


import org.techbd.ingest.feature.FeatureEnum;
import org.springframework.web.bind.annotation.*;
import org.togglz.core.manager.FeatureManager;

@RestController
@RequestMapping("/api/features")
public class FeatureToggleController {

    private final FeatureManager featureManager;

    public FeatureToggleController(FeatureManager featureManager) {
        this.featureManager = featureManager;
    }

    @PostMapping("/{featureName}/enable")
    public String enableFeature(@PathVariable String featureName) {
        FeatureEnum feature = FeatureEnum.valueOf(featureName.toUpperCase());
        featureManager.setFeatureState(new org.togglz.core.repository.FeatureState(feature, true));
        return featureName + " enabled";
    }

    @PostMapping("/{featureName}/disable")
    public String disableFeature(@PathVariable String featureName) {
        FeatureEnum feature = FeatureEnum.valueOf(featureName.toUpperCase());
        featureManager.setFeatureState(new org.togglz.core.repository.FeatureState(feature, false));
        return featureName + " disabled";
    }

    @GetMapping("/{featureName}")
    public String isFeatureActive(@PathVariable String featureName) {
        FeatureEnum feature = FeatureEnum.valueOf(featureName.toUpperCase());
        boolean active = featureManager.isActive(feature);
        return featureName + " is " + (active ? "enabled" : "disabled");
    }
}
 