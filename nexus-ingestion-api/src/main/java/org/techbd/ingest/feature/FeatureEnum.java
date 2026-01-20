package org.techbd.ingest.feature;

import org.togglz.core.Feature;
import org.togglz.core.annotation.Label;
import org.togglz.core.context.FeatureContext;

public enum FeatureEnum implements Feature {

    @Label("Enable Debug Logging for Request Headers")
    DEBUG_LOG_REQUEST_HEADERS,
    @Label("Ignore SOAP MustUnderstand Headers")
    IGNORE_MUST_UNDERSTAND_HEADERS;

    public static boolean isEnabled(Feature feature) {
        return FeatureContext.getFeatureManager().isActive(feature);
    }
}
