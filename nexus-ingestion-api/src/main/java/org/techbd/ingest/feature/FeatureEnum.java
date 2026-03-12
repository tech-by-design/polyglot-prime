package org.techbd.ingest.feature;

import org.togglz.core.Feature;
import org.togglz.core.annotation.Label;
import org.togglz.core.context.FeatureContext;

public enum FeatureEnum implements Feature {

    @Label("Enable Debug Logging for Request Headers")
    DEBUG_LOG_REQUEST_HEADERS,
    @Label("Ignore SOAP MustUnderstand Headers")
    IGNORE_MUST_UNDERSTAND_HEADERS,
    @Label("Log Incoming Message")
    LOG_INCOMING_MESSAGE,
    @Label("Include NTE Segment in HL7 ACK Response")
    ADD_NTE_SEGMENT_TO_HL7_ACK,
    @Label("Send HL7 ACK/NACK When Idle Timeout Occurs")
    SEND_HL7_ACK_ON_IDLE_TIMEOUT;
    public static boolean isEnabled(Feature feature) {
        return FeatureContext.getFeatureManager().isActive(feature);
    }
}
