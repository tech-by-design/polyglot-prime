package org.techbd.service.fhir.validation;

import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.r4.model.ValueSet;
import org.techbd.util.AppLogger;
import org.techbd.util.TemplateLogger;
import org.techbd.util.fhir.ConceptReaderUtils;

public class PostPopulateSupport {

    private final String referenceCodesPath = "ig-packages/reference/";
    private static TemplateLogger LOG;

    public PostPopulateSupport(AppLogger appLogger) {
        LOG = appLogger.getLogger(PostPopulateSupport.class);
    }

    public void update(ValidationSupportChain validationSupportChain, String profileBaseUrl) {
        addObservationLoincCodes(validationSupportChain, profileBaseUrl);
        addLanguageSubTags(validationSupportChain);
    }

    private void addObservationLoincCodes(ValidationSupportChain validationSupportChain,String profileBaseUrl) {
        LOG.info("PrePopulateSupport:addObservationLoincCodes  -BEGIN");
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
        LOG.info("PrePopulateSupport:addObservationLoincCodes  -BEGIN");
    }

    private void addLanguageSubTags(ValidationSupportChain validationSupportChain) {
        ValueSet languageVS = (ValueSet) validationSupportChain.fetchValueSet("http://hl7.org/fhir/ValueSet/languages");
        languageVS.getCompose().addInclude(new ValueSet.ConceptSetComponent()
                .setConcept(
                        ConceptReaderUtils.getValueSetConcepts_wCode(referenceCodesPath.concat("language-subtags.psv")))
                .setSystem("urn:ietf:bcp:47"));
    }

}
