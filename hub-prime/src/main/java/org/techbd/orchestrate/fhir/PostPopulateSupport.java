package org.techbd.orchestrate.fhir;

import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.r4.model.ValueSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.techbd.orchestrate.fhir.util.ConceptReaderUtils;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;

class PostPopulateSupport {

    private final String referenceCodesPath = "ig-packages/reference/";
    private final Tracer tracer;
    private static final Logger LOG = LoggerFactory.getLogger(PostPopulateSupport.class);

    public PostPopulateSupport(final Tracer tracer) {
        this.tracer = tracer;
    }

    void update(ValidationSupportChain validationSupportChain) {
        Span span = tracer.spanBuilder("PostPopulateSupport.update").startSpan();
        try {
            addObservationLoincCodes(validationSupportChain);
        } finally {
            span.end();
        }
    }

    private void addObservationLoincCodes(ValidationSupportChain validationSupportChain) {
        LOG.info("PrePopulateSupport:addObservationLoincCodes  -BEGIN");
        Span span = tracer.spanBuilder("PostPopulateSupport.addObservationLoincCodes").startSpan();
        try {
            ValueSet loinc_valueSet = (ValueSet) validationSupportChain
                    .fetchValueSet("http://hl7.org/fhir/ValueSet/observation-codes");
            try {
                loinc_valueSet.getCompose().addInclude(new ValueSet.ConceptSetComponent()
                .setConcept(ConceptReaderUtils.getValueSetConcepts_wCode(referenceCodesPath.concat("loinc.psv")))
                .setSystem("http://loinc.org"));
            } finally {
                loinc_valueSet = null;
            }
        } finally {
            span.end();
        }
        LOG.info("PrePopulateSupport:addObservationLoincCodes  -BEGIN");
    }

}
