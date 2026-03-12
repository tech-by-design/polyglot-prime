package org.techbd.fhir.feature;

import org.togglz.core.Feature;
import org.togglz.core.annotation.Label;
import org.togglz.core.context.FeatureContext;

public enum FeatureEnum implements Feature {

    @Label("Enable Data Ledger Tracking")
    FEATURE_DATA_LEDGER_TRACKING,

    @Label("Enable Data Ledger Diagnostics")
    FEATURE_DATA_LEDGER_DIAGNOSTICS;

    public static boolean isEnabled(Feature feature) {
        return FeatureContext.getFeatureManager().isActive(feature);
    }
}