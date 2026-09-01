package org.techbd.service.fhir.validation;

import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.r4.model.ValueSet;
import org.techbd.util.AppLogger;
import org.techbd.util.TemplateLogger;
import org.techbd.util.fhir.ConceptReaderUtils;

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
            addUsCoreSurveyCodes(validationSupportChain, profileBaseUrl);
            addUsCoreConditionCodes(validationSupportChain);
            addUsCoreProcedureCodes(validationSupportChain);
            addLanguageSubTags(validationSupportChain);
        } finally {
            span.end();
        }
    }

    private void addObservationLoincCodes(ValidationSupportChain validationSupportChain,String profileBaseUrl) {
        LOG.info("PrePopulateSupport:addObservationLoincCodes  -BEGIN");
        Span span = tracer.spanBuilder("PostPopulateSupport.addObservationLoincCodes").startSpan();
        try {
            ValueSet loincValueSet = (ValueSet) validationSupportChain
                    .fetchValueSet("http://hl7.org/fhir/ValueSet/observation-codes");
            if (loincValueSet != null) {
                // LOINC codes
                loincValueSet.getCompose().addInclude(
                        new ValueSet.ConceptSetComponent()
                                .setConcept(
                                        ConceptReaderUtils.getValueSetConcepts_wCode(
                                                referenceCodesPath.concat("loinc.psv")))
                                .setSystem("http://loinc.org"));

                // New CodeSystem URL
                loincValueSet.getCompose().addInclude(
                        new ValueSet.ConceptSetComponent()
                                .setConcept(
                                        ConceptReaderUtils.getValueSetConcepts_wCode(
                                                referenceCodesPath.concat("custom-system-code.psv")))
                                .setSystem(
                                        profileBaseUrl +
                                                "/CodeSystem/NYSHRSNQuestionnaire"));
                // Old CodeSystem URL - backward compatibility
                loincValueSet.getCompose().addInclude(
                        new ValueSet.ConceptSetComponent()
                                .setConcept(
                                        ConceptReaderUtils.getValueSetConcepts_wCode(
                                                referenceCodesPath.concat("custom-system-code.psv")))
                                .setSystem(
                                        profileBaseUrl +
                                                "/CodeSystem/NYS-HRSN-Questionnaire"));
            }
        } finally {
            span.end();
        }
        LOG.info("PrePopulateSupport:addObservationLoincCodes - END");
    }

    private void addUsCoreSurveyCodes(
            ValidationSupportChain validationSupportChain,
            String profileBaseUrl) {

        ValueSet surveyValueSet = (ValueSet) validationSupportChain
                .fetchValueSet(
                        "http://hl7.org/fhir/us/core/ValueSet/us-core-survey-codes");

        if (surveyValueSet != null) {

            // LOINC codes
            surveyValueSet.getCompose().addInclude(
                    new ValueSet.ConceptSetComponent()
                            .setConcept(
                                    ConceptReaderUtils.getValueSetConcepts_wCode(
                                            referenceCodesPath.concat("loinc.psv")))
                            .setSystem("http://loinc.org"));

            // New CodeSystem URL
            surveyValueSet.getCompose().addInclude(
                    new ValueSet.ConceptSetComponent()
                            .setConcept(
                                    ConceptReaderUtils.getValueSetConcepts_wCode(
                                            referenceCodesPath.concat("custom-system-code.psv")))
                            .setSystem(
                                    profileBaseUrl +
                                            "/CodeSystem/NYSHRSNQuestionnaire"));

            // Old CodeSystem URL - backward compatibility
            surveyValueSet.getCompose().addInclude(
                    new ValueSet.ConceptSetComponent()
                            .setConcept(
                                    ConceptReaderUtils.getValueSetConcepts_wCode(
                                            referenceCodesPath.concat("custom-system-code.psv")))
                            .setSystem(
                                    profileBaseUrl +
                                            "/CodeSystem/NYS-HRSN-Questionnaire"));
        }
    }

    private void addUsCoreConditionCodes(
            ValidationSupportChain validationSupportChain) {

        ValueSet conditionValueSet = (ValueSet) validationSupportChain
                .fetchValueSet(
                        "http://hl7.org/fhir/us/core/ValueSet/us-core-condition-code");

        if (conditionValueSet != null) {

            conditionValueSet.getCompose().addInclude(
                    new ValueSet.ConceptSetComponent()
                            .setConcept(
                                    ConceptReaderUtils.getValueSetConcepts_wCode(
                                            referenceCodesPath.concat("snomed.psv")))
                            .setSystem("http://snomed.info/sct"));
        }
    }

    private void addUsCoreProcedureCodes(
            ValidationSupportChain validationSupportChain) {

        ValueSet procedureValueSet = (ValueSet) validationSupportChain
                .fetchValueSet(
                        "http://hl7.org/fhir/us/core/ValueSet/us-core-procedure-code");

        if (procedureValueSet != null) {

            procedureValueSet.getCompose().addInclude(
                    new ValueSet.ConceptSetComponent()
                            .setConcept(
                                    ConceptReaderUtils.getValueSetConcepts_wCode(
                                            referenceCodesPath.concat("hcpcs.psv")))
                            .setSystem(
                                    "http://www.cms.gov/Medicare/Coding/HCPCSReleaseCodeSets"));

            procedureValueSet.getCompose().addInclude(
                    new ValueSet.ConceptSetComponent()
                            .setConcept(
                                    ConceptReaderUtils.getValueSetConcepts_wCode(
                                            referenceCodesPath.concat("snomed.psv")))
                            .setSystem("http://snomed.info/sct"));
        }
    }

    private void addLanguageSubTags(ValidationSupportChain validationSupportChain) {
        ValueSet languageVS = (ValueSet) validationSupportChain.fetchValueSet("http://hl7.org/fhir/ValueSet/languages");
        languageVS.getCompose().addInclude(new ValueSet.ConceptSetComponent()
                .setConcept(
                        ConceptReaderUtils.getValueSetConcepts_wCode(referenceCodesPath.concat("language-subtags.psv")))
                .setSystem("urn:ietf:bcp:47"));
    }

}
