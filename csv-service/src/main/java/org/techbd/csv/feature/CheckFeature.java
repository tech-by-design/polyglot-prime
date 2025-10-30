package org.techbd.csv.feature;

import org.springframework.stereotype.Controller;
import org.togglz.core.manager.FeatureManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.techbd.csv.feature.FeatureEnum;
//import org.techbd.corelib.feature.DataLedgerFeatures;

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

        return String.format("FEATURE_DATA_LEDGER_TRACKING: %s, FEATURE_DATA_LEDGER_DIAGNOSTICS: %s",
            trackingActive ? "ACTIVE" : "NOT ACTIVE",
            diagnosticsActive ? "ACTIVE" : "NOT ACTIVE");
    }

    @GetMapping("/csv-service/features")
    @ResponseBody
    public String checkCsvServiceFeatures() {
        boolean trackingEnabled = FeatureEnum.isEnabled(FeatureEnum.FEATURE_DATA_LEDGER_TRACKING);
        boolean diagnosticsEnabled = FeatureEnum.isEnabled(FeatureEnum.FEATURE_DATA_LEDGER_DIAGNOSTICS);

        return String.format(
            "CSV Service Features - TRACKING: %s, DIAGNOSTICS: %s",
            trackingEnabled ? "ENABLED" : "DISABLED",
            diagnosticsEnabled ? "ENABLED" : "DISABLED"
        );
    }
}