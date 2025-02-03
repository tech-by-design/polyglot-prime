package org.techbd.orchestrate.fhir;

import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.r4.model.ValueSet;
import org.techbd.orchestrate.fhir.util.ConceptReaderUtils;

// import io.opentelemetry.api.trace.Span;
// import io.opentelemetry.api.trace.Tracer;

class PostPopulateSupport {

    private final String referenceCodesPath = "ig-packages/reference/";
    // private final Tracer tracer;

    // public PostPopulateSupport(final Tracer tracer) {
    //     this.tracer = tracer;
    // }

    void update(ValidationSupportChain validationSupportChain) {
        // Span span = tracer.spanBuilder("PostPopulateSupport.update").startSpan();
        // try {
            addObservationLoincCodes(validationSupportChain);
        // } finally {
        //     span.end();
        // }
    }

    private void addObservationLoincCodes(ValidationSupportChain validationSupportChain) {
        // Span span = tracer.spanBuilder("PostPopulateSupport.addObservationLoincCodes").startSpan();
        // try {
            ValueSet loinc_valueSet = (ValueSet) validationSupportChain
                    .fetchValueSet("http://hl7.org/fhir/ValueSet/observation-codes");
            try {
                loinc_valueSet.getCompose().addInclude(new ValueSet.ConceptSetComponent()
                        .setConcept(
                                ConceptReaderUtils.getValueSetConcepts_wCode(referenceCodesPath.concat("loinc.psv")))
                        .addConcept(new ValueSet.ConceptReferenceComponent().setCode("95614-4"))
                        .addConcept(new ValueSet.ConceptReferenceComponent().setCode("77594-0"))
                        .addConcept(new ValueSet.ConceptReferenceComponent().setCode("71969-0"))
                        .setSystem("http://loinc.org"));
            } finally {
                loinc_valueSet = null;
            }
        // } finally {
        //     span.end();
        // }
    }

}
