package org.techbd.service.fhir.validation;
import java.util.ArrayList;
import java.util.List;

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
    private final TemplateLogger LOG;

    /*
     * Load each PSV once and reuse the parsed concepts.
     */
    private final List<ValueSet.ConceptReferenceComponent> loincConcepts;
    private final List<ValueSet.ConceptReferenceComponent> snomedConcepts;
    private final List<ValueSet.ConceptReferenceComponent> hcpcsConcepts;
    private final List<ValueSet.ConceptReferenceComponent> icd10Concepts;
    private final List<ValueSet.ConceptReferenceComponent> customSystemConcepts;
    private final List<ValueSet.ConceptReferenceComponent> languageConcepts;

    public PostPopulateSupport(final Tracer tracer,final AppLogger appLogger) {
        this.tracer = tracer;
        LOG = appLogger.getLogger(PostPopulateSupport.class);
        /*
         * Load each PSV only once.
         */
        this.loincConcepts = ConceptReaderUtils.getValueSetConcepts_wCode(referenceCodesPath.concat("loinc.psv"));
        this.snomedConcepts =  ConceptReaderUtils.getValueSetConcepts_wCode(referenceCodesPath.concat("snomed.psv"));
        this.hcpcsConcepts = ConceptReaderUtils.getValueSetConcepts_wCode(referenceCodesPath.concat("hcpcs.psv"));
        this.icd10Concepts = ConceptReaderUtils.getValueSetConcepts_wCode(referenceCodesPath.concat("icd10cm.psv"));
        this.customSystemConcepts =ConceptReaderUtils.getValueSetConcepts_wCode(referenceCodesPath.concat("custom-system-code.psv"));
        this.languageConcepts = ConceptReaderUtils.getValueSetConcepts_wCode(referenceCodesPath.concat("language-subtags.psv"));
    }

    public void update(final ValidationSupportChain validationSupportChain,final String profileBaseUrl) {
        Span span = tracer.spanBuilder("PostPopulateSupport.update").startSpan();
        try {
            addObservationLoincCodes(validationSupportChain, profileBaseUrl);
            addUsCoreSurveyCodes(validationSupportChain, profileBaseUrl);
            addUsCoreConditionCodes(validationSupportChain);
            addUsCoreProcedureCodes(validationSupportChain);
            addLanguageSubTags(validationSupportChain);
        } finally {
            span.end();
        }
    }

    private void addObservationLoincCodes(final ValidationSupportChain validationSupportChain, final String profileBaseUrl) {
        LOG.info("PostPopulateSupport:addObservationLoincCodes - BEGIN");
        Span span =tracer.spanBuilder("PostPopulateSupport.addObservationLoincCodes").startSpan();
        try {
            ValueSet loincValueSet = (ValueSet) validationSupportChain.fetchValueSet(
                            "http://hl7.org/fhir/ValueSet/observation-codes");
            if (loincValueSet != null) {
                // LOINC codes
                loincValueSet.getCompose().addInclude(
                        new ValueSet.ConceptSetComponent()
                                .setConcept(new ArrayList<>(loincConcepts))
                                .setSystem("http://loinc.org"));

                // New CodeSystem URL
                loincValueSet.getCompose().addInclude(
                        new ValueSet.ConceptSetComponent()
                                .setConcept(new ArrayList<>(customSystemConcepts))
                                .setSystem(
                                        profileBaseUrl
                                                + "/CodeSystem/NYSHRSNQuestionnaire"));
                // Old CodeSystem URL - backward compatibility
                loincValueSet.getCompose().addInclude(
                        new ValueSet.ConceptSetComponent()
                                .setConcept(
                                        new ArrayList<>(customSystemConcepts))
                                .setSystem(profileBaseUrl
                                                + "/CodeSystem/NYS-HRSN-Questionnaire"));
            }
        } finally {
            span.end();
        }
        LOG.info("PostPopulateSupport:addObservationLoincCodes - END");
    }

    private void addUsCoreSurveyCodes(final ValidationSupportChain validationSupportChain,
            final String profileBaseUrl) {

        ValueSet surveyValueSet = (ValueSet) validationSupportChain.fetchValueSet(
                        "http://hl7.org/fhir/us/core/ValueSet/us-core-survey-codes");

        if (surveyValueSet != null) {

            // LOINC codes
            surveyValueSet.getCompose().addInclude(
                    new ValueSet.ConceptSetComponent()
                            .setConcept(new ArrayList<>(loincConcepts))
                            .setSystem("http://loinc.org"));

            // New CodeSystem URL
            surveyValueSet.getCompose().addInclude(
                    new ValueSet.ConceptSetComponent()
                            .setConcept(new ArrayList<>(customSystemConcepts))
                            .setSystem(
                                    profileBaseUrl
                                            + "/CodeSystem/NYSHRSNQuestionnaire"));

            // Old CodeSystem URL - backward compatibility
            surveyValueSet.getCompose().addInclude(
                    new ValueSet.ConceptSetComponent()
                            .setConcept(new ArrayList<>(customSystemConcepts))
                            .setSystem(
                                    profileBaseUrl
                                            + "/CodeSystem/NYS-HRSN-Questionnaire"));
        }
    }

    private void addUsCoreConditionCodes(
            final ValidationSupportChain validationSupportChain) {

        ValueSet conditionValueSet = (ValueSet) validationSupportChain.fetchValueSet(
                        "http://hl7.org/fhir/us/core/ValueSet/us-core-condition-code");

        if (conditionValueSet != null) {

            conditionValueSet.getCompose().addInclude(
                    new ValueSet.ConceptSetComponent()
                            .setConcept(new ArrayList<>(snomedConcepts))
                            .setSystem("http://snomed.info/sct"));
        }
    }

    private void addUsCoreProcedureCodes(final ValidationSupportChain validationSupportChain) {

        ValueSet procedureValueSet = (ValueSet) validationSupportChain.fetchValueSet(
                        "http://hl7.org/fhir/us/core/ValueSet/us-core-procedure-code");

        if (procedureValueSet != null) {

            // New HCPCS CodeSystem URL
            procedureValueSet.getCompose().addInclude(
                    new ValueSet.ConceptSetComponent()
                            .setConcept(new ArrayList<>(hcpcsConcepts))
                            .setSystem(
                                    "http://www.cms.gov/Medicare/Coding/HCPCSReleaseCodeSets"));

            // Old HCPCS OID - backward compatibility
            procedureValueSet.getCompose().addInclude(
                    new ValueSet.ConceptSetComponent()
                            .setConcept(new ArrayList<>(hcpcsConcepts))
                            .setSystem("urn:oid:2.16.840.1.113883.6.285"));

            // ICD-10
            procedureValueSet.getCompose().addInclude(
                    new ValueSet.ConceptSetComponent()
                            .setConcept(new ArrayList<>(icd10Concepts))
                            .setSystem(
                                    "http://www.cms.gov/Medicare/Coding/ICD10"));

            // SNOMED
            procedureValueSet.getCompose().addInclude(
                    new ValueSet.ConceptSetComponent()
                            .setConcept(new ArrayList<>(snomedConcepts))
                            .setSystem("http://snomed.info/sct"));

            // LOINC
            procedureValueSet.getCompose().addInclude(
                    new ValueSet.ConceptSetComponent()
                            .setConcept(new ArrayList<>(loincConcepts))
                            .setSystem("http://loinc.org"));
        }
    }

    private void addLanguageSubTags(final ValidationSupportChain validationSupportChain) {
        ValueSet languageValueSet = (ValueSet) validationSupportChain.fetchValueSet("http://hl7.org/fhir/ValueSet/languages");
        if (languageValueSet != null) {
            languageValueSet.getCompose().addInclude(new ValueSet.ConceptSetComponent()
                            .setConcept(new ArrayList<>(languageConcepts))
                            .setSystem("urn:ietf:bcp:47"));
        }
    }
}