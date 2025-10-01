package org.techbd.fhir.service.validation;

import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.r4.model.ValueSet;
import org.techbd.corelib.util.AppLogger;
import org.techbd.corelib.util.TemplateLogger;
import org.techbd.fhir.util.ConceptReaderUtils;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;

public class PostPopulateSupport {

    private final String referenceCodesPath = "ig-packages/reference/";
    private final Tracer tracer;
    private static TemplateLogger LOG;

    public PostPopulateSupport(final Tracer tracer, AppLogger appLogger) {
        this.tracer = tracer;
        LOG = appLogger.getLogger(PostPopulateSupport.class);
    }

    public void update(ValidationSupportChain validationSupportChain, String profileBaseUrl) {
        Span span = tracer.spanBuilder("PostPopulateSupport.update").startSpan();
        try {
            addObservationLoincCodes(validationSupportChain,profileBaseUrl);
        } finally {
            span.end();
        }
    }

    private void addObservationLoincCodes(ValidationSupportChain validationSupportChain,String profileBaseUrl) {
        LOG.info("PrePopulateSupport:addObservationLoincCodes  -BEGIN");
        Span span = tracer.spanBuilder("PostPopulateSupport.addObservationLoincCodes").startSpan();
        try {
            ValueSet loinc_valueSet = (ValueSet) validationSupportChain
                    .fetchValueSet("http://hl7.org/fhir/ValueSet/observation-codes");
            try {
                loinc_valueSet.getCompose().addInclude(new ValueSet.ConceptSetComponent()
                        .setConcept(
                                ConceptReaderUtils.getValueSetConcepts_wCode(referenceCodesPath.concat("loinc.psv")))
                        .setSystem("http://loinc.org"));

                loinc_valueSet.getCompose().addInclude(new ValueSet.ConceptSetComponent()
                        .setConcept(ConceptReaderUtils
                                .getValueSetConcepts_wCode(referenceCodesPath.concat("custom-system-code.psv")))
                        .setSystem(profileBaseUrl + "/CodeSystem/NYS-HRSN-Questionnaire"));
            } finally {
                loinc_valueSet = null;
            }
        } finally {
            span.end();
        }
        LOG.info("PrePopulateSupport:addObservationLoincCodes  -BEGIN");
    }

}
