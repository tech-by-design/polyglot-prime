package org.techbd.fhir.feature;

import org.springframework.stereotype.Controller;
import org.togglz.core.manager.FeatureManager;

import net.sf.saxon.lib.Feature;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.techbd.fhir.feature.FeatureEnum;

@Controller
public class CheckFeature {
    private final FeatureManager featureManager;

    public CheckFeature(FeatureManager featureManager) {
        this.featureManager = featureManager;
    }

    @GetMapping("/feature")
    @ResponseBody    
    public String checkFeature() {
        boolean trackingActive = featureManager.isActive(FeatureEnum.FEATURE_DATA_LEDGER_TRACKING);
        boolean diagnosticsActive = featureManager.isActive(FeatureEnum.FEATURE_DATA_LEDGER_DIAGNOSTICS);
        
        return String.format("DATA_LEDGER_TRACKING: %s, DATA_LEDGER_DIAGNOSTICS: %s", 
            trackingActive ? "ACTIVE" : "NOT ACTIVE",
            diagnosticsActive ? "ACTIVE" : "NOT ACTIVE");
    }

    @GetMapping("/core-lib/features")
    @ResponseBody
    public String checkCoreLibFeatures() {
        boolean trackingEnabled = FeatureEnum.isEnabled(FeatureEnum.FEATURE_DATA_LEDGER_TRACKING);
        boolean diagnosticsEnabled = FeatureEnum.isEnabled(FeatureEnum.FEATURE_DATA_LEDGER_DIAGNOSTICS);

        return String.format(
            "Core-Lib Features - TRACKING: %s, DIAGNOSTICS: %s", 
            trackingEnabled ? "ENABLED" : "DISABLED",
            diagnosticsEnabled ? "ENABLED" : "DISABLED"
        );
    }
}