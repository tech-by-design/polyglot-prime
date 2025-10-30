package org.techbd.csv.controller;

import org.techbd.csv.feature.FeatureEnum;
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
}