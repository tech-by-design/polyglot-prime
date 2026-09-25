package org.techbd.service.fhir.validation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hl7.fhir.common.hapi.validation.support.PrePopulatedValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.ValueSet;
import org.techbd.util.AppLogger;
import org.techbd.util.TemplateLogger;
import org.techbd.util.fhir.ConceptReaderUtils;
import org.techbd.util.fhir.FileUtils;

import ca.uhn.fhir.context.FhirContext;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;

public class PrePopulateSupport {

    private final String referenceCodesPath = "ig-packages/reference/";
    private final Tracer tracer;
    private final TemplateLogger LOG;
    private final List<CodeSystem.ConceptDefinitionComponent> hcpcsConcepts;
    private final List<CodeSystem.ConceptDefinitionComponent> snomedConcepts;
    private final List<CodeSystem.ConceptDefinitionComponent> icd10Concepts;
    private final List<CodeSystem.ConceptDefinitionComponent> cptConcepts;
    private final List<CodeSystem.ConceptDefinitionComponent> loincConcepts;
    private final List<CodeSystem.ConceptDefinitionComponent> languageConcepts;

    public PrePopulateSupport(final Tracer tracer, final AppLogger appLogger) {
        this.tracer = tracer;
        this.LOG = appLogger.getLogger(PrePopulateSupport.class);
        this.hcpcsConcepts = ConceptReaderUtils.getCodeSystemConcepts_wCode(referenceCodesPath.concat("hcpcs.psv"));
        this.snomedConcepts = ConceptReaderUtils.getCodeSystemConcepts_wCode(referenceCodesPath.concat("snomed.psv"));
        this.icd10Concepts = ConceptReaderUtils.getCodeSystemConcepts_wCode(referenceCodesPath.concat("icd10cm.psv"));
        this.cptConcepts = ConceptReaderUtils.getCodeSystemConcepts_wCode(referenceCodesPath.concat("cpt.psv"));
        this.loincConcepts = ConceptReaderUtils.getCodeSystemConcepts_wCode(referenceCodesPath.concat("loinc.psv"));
        this.languageConcepts = ConceptReaderUtils.getCodeSystemConcepts_wCode(referenceCodesPath.concat("language-subtags.psv"));
    }

    public PrePopulatedValidationSupport build(FhirContext fhirContext) {
        Span span = tracer.spanBuilder("PrePopulateSupport.build").startSpan();
        try {
            PrePopulatedValidationSupport prePopulatedValidationSupport = new PrePopulatedValidationSupport(
                    fhirContext);
            loadValueSets(fhirContext, prePopulatedValidationSupport);
            return prePopulatedValidationSupport;
        } finally {
            span.end();
        }
    }

    public void addCodeSystems(ValidationSupportChain validationSupportChain,
            PrePopulatedValidationSupport prePopulatedValidationSupport) {
        Span span = tracer.spanBuilder("PrePopulateSupport.addCodeSystems").startSpan();
        try {
            addSnomedCodes(validationSupportChain, prePopulatedValidationSupport);
            addICD10Codes(validationSupportChain, prePopulatedValidationSupport);
            addCPTCodes(validationSupportChain, prePopulatedValidationSupport);
            addHCPCSCodes(validationSupportChain, prePopulatedValidationSupport);
            addLoincCodes(validationSupportChain, prePopulatedValidationSupport);
            addLanguageCodes(prePopulatedValidationSupport);
        } finally {
            span.end();
        }
    }

    private void addCPTCodes(ValidationSupportChain validationSupportChain,
            PrePopulatedValidationSupport prePopulatedValidationSupport) {
        LOG.info("PrePopulateSupport:addCPTCodes - BEGIN");
        String cptSystem = "http://www.ama-assn.org/go/cpt";
        CodeSystem existingCpt = (CodeSystem) validationSupportChain.fetchCodeSystem(cptSystem);
        if (existingCpt == null) {
            CodeSystem newCpt = new CodeSystem();
            newCpt.setUrl(cptSystem);
            newCpt.setConcept(new ArrayList<>(cptConcepts));
            newCpt.setContent(CodeSystem.CodeSystemContentMode.COMPLETE);
            prePopulatedValidationSupport.addCodeSystem(newCpt);
        } else {
            existingCpt.setContent(CodeSystem.CodeSystemContentMode.COMPLETE);
            if (existingCpt.getConcept().isEmpty()) {
                existingCpt.getConcept().addAll(new ArrayList<>(cptConcepts));
            }
        }
        LOG.info("PrePopulateSupport:addCPTCodes - END");
    }

    private void addHCPCSCodes(ValidationSupportChain validationSupportChain,
            PrePopulatedValidationSupport prePopulatedValidationSupport) {
        LOG.info("PrePopulateSupport:addHCPCSCodes - BEGIN");

        String newHcpcsSystem = "http://www.cms.gov/Medicare/Coding/HCPCSReleaseCodeSets";

        String oldHcpcsSystem = "urn:oid:2.16.840.1.113883.6.285";
        // New HCPCS system
        CodeSystem existingNewHCPCS = (CodeSystem) validationSupportChain.fetchCodeSystem(newHcpcsSystem);

        if (existingNewHCPCS == null) {
            CodeSystem newHCPCS = new CodeSystem();
            newHCPCS.setUrl(newHcpcsSystem);
            newHCPCS.setConcept(new ArrayList<>(hcpcsConcepts));
            newHCPCS.setContent(CodeSystem.CodeSystemContentMode.COMPLETE);
            prePopulatedValidationSupport.addCodeSystem(newHCPCS);
        } else {
            existingNewHCPCS.setContent(CodeSystem.CodeSystemContentMode.COMPLETE);
            if (existingNewHCPCS.getConcept().isEmpty()) {
                existingNewHCPCS.getConcept().addAll(
                        new ArrayList<>(hcpcsConcepts));
            }
        }
        // Old HCPCS OID - backward compatibility
        CodeSystem existingOldHCPCS = (CodeSystem) validationSupportChain.fetchCodeSystem(oldHcpcsSystem);

        if (existingOldHCPCS == null) {
            CodeSystem oldHCPCS = new CodeSystem();
            oldHCPCS.setUrl(oldHcpcsSystem);
            oldHCPCS.setConcept(new ArrayList<>(hcpcsConcepts));
            oldHCPCS.setContent(CodeSystem.CodeSystemContentMode.COMPLETE);

            prePopulatedValidationSupport.addCodeSystem(oldHCPCS);
        } else {
            existingOldHCPCS.setContent(CodeSystem.CodeSystemContentMode.COMPLETE);
            if (existingOldHCPCS.getConcept().isEmpty()) {
                existingOldHCPCS.getConcept().addAll(
                        new ArrayList<>(hcpcsConcepts));
            }
        }
        LOG.info("PrePopulateSupport:addHCPCSCodes - END");
    }

    private void addICD10Codes(ValidationSupportChain validationSupportChain,
            PrePopulatedValidationSupport prePopulatedValidationSupport) {
        LOG.info("PrePopulateSupport:addICD10Codes - BEGIN");
        Span span = tracer.spanBuilder("PrePopulateSupport.addICD10Codes")
                .startSpan();
        try {
            String icd10System = "http://hl7.org/fhir/sid/icd-10-cm";
            CodeSystem existingIcd10 = (CodeSystem) validationSupportChain
                    .fetchCodeSystem(icd10System);
            if (existingIcd10 == null) {
                CodeSystem newIcd10 = new CodeSystem();
                newIcd10.setUrl(icd10System);
                newIcd10.setConcept(new ArrayList<>(icd10Concepts));
                newIcd10.setContent(CodeSystem.CodeSystemContentMode.COMPLETE);
                prePopulatedValidationSupport.addCodeSystem(newIcd10);
            } else {
                if (existingIcd10.getConcept().isEmpty()) {
                    existingIcd10.getConcept().addAll(
                            new ArrayList<>(icd10Concepts));
                }
                existingIcd10.setContent(CodeSystem.CodeSystemContentMode.COMPLETE);
            }
        } finally {
            span.end();
        }
        LOG.info("PrePopulateSupport:addICD10Codes - END");
    }

    private void addSnomedCodes(ValidationSupportChain validationSupportChain,
            PrePopulatedValidationSupport prePopulatedValidationSupport) {
        LOG.info("PrePopulateSupport:addSnomedCodes - BEGIN");
        Span span = tracer.spanBuilder("PrePopulateSupport.addSnomedCodes").startSpan();
        try {
            String snomedSystem = "http://snomed.info/sct";
            CodeSystem existingSnomed = (CodeSystem) validationSupportChain
                    .fetchCodeSystem(snomedSystem);
            if (existingSnomed == null) {
                CodeSystem newSnomed = new CodeSystem();
                newSnomed.setUrl(snomedSystem);
                newSnomed.setConcept(new ArrayList<>(snomedConcepts));
                newSnomed.setContent(CodeSystem.CodeSystemContentMode.COMPLETE);
                prePopulatedValidationSupport.addCodeSystem(newSnomed);
            } else {
                if (existingSnomed.getConcept().isEmpty()) {
                    existingSnomed.getConcept().addAll(
                            new ArrayList<>(snomedConcepts));
                }
                existingSnomed.setContent(CodeSystem.CodeSystemContentMode.COMPLETE);
            }
        } finally {
            span.end();
        }
        LOG.info("PrePopulateSupport:addSnomedCodes - END");
    }

    public void loadValueSets(FhirContext fhirContext, PrePopulatedValidationSupport prePopulatedValidationSupport) {
        LOG.info("PrePopulateSupport:loadValueSets  -BEGIN");
        Span span = tracer.spanBuilder("PrePopulateSupport.loadValueSets").startSpan();
        try {
            loadValueSet("ig-packages/vs/2.16.840.1.113762.1.4.1021.32.json", fhirContext,
                    prePopulatedValidationSupport);
            loadValueSet("ig-packages/vs/2.16.840.1.113762.1.4.1240.11.json", fhirContext,
                    prePopulatedValidationSupport);
            loadValueSet("ig-packages/vs/2.16.840.1.113762.1.4.1021.24.json", fhirContext,
                    prePopulatedValidationSupport);
        } finally {
            span.end();
        }
        LOG.info("PrePopulateSupport:loadValueSets  -END");
    }

    private void loadValueSet(String filePath, FhirContext fhirContext,
            PrePopulatedValidationSupport prePopulatedValidationSupport) {
        Span span = tracer.spanBuilder("PrePopulateSupport.loadValueSet").startSpan();
        try {
            ValueSet valueSet = fhirContext.newJsonParser().parseResource(ValueSet.class,
                    FileUtils.readFile1(filePath));
            try {
                addExpansionToInclude(valueSet);
                prePopulatedValidationSupport.addValueSet(valueSet);
            } finally {
                valueSet = null;
            }
        } finally {
            span.end();
        }
    }

    private void addExpansionToInclude(ValueSet valueSet) {
        if (valueSet == null || valueSet.getExpansion().getContains().size() == 0)
            return;

        Map<String, List<ValueSet.ConceptReferenceComponent>> concepts = new HashMap<>();
        valueSet.getExpansion().getContains().forEach(it -> {
            List<ValueSet.ConceptReferenceComponent> include_concepts = concepts.getOrDefault(it.getSystem(),
                    new ArrayList<>());
            include_concepts.add(
                    new ValueSet.ConceptReferenceComponent().setCode(it.getCode()).setDisplay(it.getDisplay()));

            concepts.putIfAbsent(it.getSystem(), include_concepts);
        });

        concepts.forEach((k, v) -> valueSet.getCompose().getInclude().add(
                new ValueSet.ConceptSetComponent().setSystem(k).setConcept(v)));
    }

    private void addLoincCodes(ValidationSupportChain validationSupportChain,
            PrePopulatedValidationSupport prePopulatedValidationSupport) {
        LOG.info("PrePopulateSupport:addLoincCodes - BEGIN");
        Span span = tracer.spanBuilder("PrePopulateSupport.addLoincCodes").startSpan();
        try {
            String loincSystem = "http://loinc.org";
            CodeSystem existingLoinc = (CodeSystem) validationSupportChain.fetchCodeSystem(loincSystem);
            if (existingLoinc == null) {
                CodeSystem newLoinc = new CodeSystem();
                newLoinc.setUrl(loincSystem);
                newLoinc.setVersion("2.81");
                newLoinc.setName("LOINC");
                newLoinc.setStatus(Enumerations.PublicationStatus.ACTIVE);
                newLoinc.setConcept(new ArrayList<>(loincConcepts));
                newLoinc.setContent(CodeSystem.CodeSystemContentMode.COMPLETE);
                prePopulatedValidationSupport.addCodeSystem(newLoinc);
            } else {
                if (existingLoinc.getConcept().isEmpty()) {
                    existingLoinc.getConcept().addAll(
                            new ArrayList<>(loincConcepts));
                }
                existingLoinc.setContent(CodeSystem.CodeSystemContentMode.COMPLETE);
            }
        } finally {
            span.end();
        }
        LOG.info("PrePopulateSupport:addLoincCodes - END");
    }

    private void addLanguageCodes(
            PrePopulatedValidationSupport prePopulatedValidationSupport) {

        CodeSystem languageCodeSystem = new CodeSystem()
                .setUrl("urn:ietf:bcp:47")
                .setName("BCP47LanguageCodes")
                .setContent(CodeSystem.CodeSystemContentMode.COMPLETE);

        languageCodeSystem.setConcept(new ArrayList<>(languageConcepts));

        prePopulatedValidationSupport.addCodeSystem(languageCodeSystem);
    }
}
