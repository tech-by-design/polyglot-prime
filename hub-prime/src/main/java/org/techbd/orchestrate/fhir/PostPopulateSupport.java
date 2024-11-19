package org.techbd.orchestrate.fhir;

import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.r4.model.ValueSet;
import org.techbd.orchestrate.fhir.util.ConceptReaderUtils;

class PostPopulateSupport {

    private final String referenceCodesPath = "ig-packages/reference/";

    void update(ValidationSupportChain validationSupportChain) {
        addObservationLoincCodes(validationSupportChain);
    }

    private void addObservationLoincCodes(ValidationSupportChain validationSupportChain) {
        ValueSet loinc_valueSet = (ValueSet) validationSupportChain
                .fetchValueSet("http://hl7.org/fhir/ValueSet/observation-codes");
        try {
            loinc_valueSet.getCompose().addInclude(new ValueSet.ConceptSetComponent()
                    .setConcept(ConceptReaderUtils.getValueSetConcepts_wCode(referenceCodesPath.concat("loinc.psv")))
                    .addConcept(new ValueSet.ConceptReferenceComponent().setCode("95614-4"))
                    .addConcept(new ValueSet.ConceptReferenceComponent().setCode("77594-0"))
                    .addConcept(new ValueSet.ConceptReferenceComponent().setCode("71969-0"))
                    .setSystem("http://loinc.org"));
        } finally {
            loinc_valueSet = null;
        }
    }

}
