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

    public PrePopulateSupport(final Tracer tracer, final AppLogger appLogger) {
        this.tracer = tracer;
        this.LOG = appLogger.getLogger(PrePopulateSupport.class);
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
        } finally {
            span.end();
        }
    }

    private void addCPTCodes(ValidationSupportChain validationSupportChain,
            PrePopulatedValidationSupport prePopulatedValidationSupport) {
        LOG.info("PrePopulateSupport:addCPTCodes  -BEGIN");
        CodeSystem existCpt = (CodeSystem) validationSupportChain.fetchCodeSystem("http://www.ama-assn.org/go/cpt");
        if (existCpt == null) {
            CodeSystem newCpt = new CodeSystem();
            newCpt.setUrl("http://www.ama-assn.org/go/cpt");
            newCpt.setConcept(ConceptReaderUtils.getCodeSystemConcepts_wCode(referenceCodesPath.concat("cpt.psv")));
            newCpt.setContent(CodeSystem.CodeSystemContentMode.COMPLETE);
            prePopulatedValidationSupport.addCodeSystem(newCpt);
        } else {
            existCpt.setContent(CodeSystem.CodeSystemContentMode.COMPLETE);
            existCpt.getConcept()
                    .addAll(ConceptReaderUtils.getCodeSystemConcepts_wCode(referenceCodesPath.concat("cpt.psv")));
        }
        LOG.info("PrePopulateSupport:addCPTCodes  -END");
    }

    private void addHCPCSCodes(ValidationSupportChain validationSupportChain,
            PrePopulatedValidationSupport prePopulatedValidationSupport) {
        LOG.info("PrePopulateSupport:addHCPCSCodes  -BEGIN");
        CodeSystem existHCPCS = (CodeSystem) validationSupportChain.fetchCodeSystem("urn:oid:2.16.840.1.113883.6.285");
        // CodeSystem existHCPCS = (CodeSystem)
        // validationSupportChain.fetchCodeSystem("https://www.cms.gov/Medicare/Coding/HCPCSReleaseCodeSets");
        if (existHCPCS == null) {
            CodeSystem newHCPCS = new CodeSystem();
            // newHCPCS.setUrl("https://www.cms.gov/Medicare/Coding/HCPCSReleaseCodeSets");
            newHCPCS.setUrl("urn:oid:2.16.840.1.113883.6.285");
            newHCPCS.setConcept(ConceptReaderUtils.getCodeSystemConcepts_wCode(referenceCodesPath.concat("hcpcs.psv")));
            newHCPCS.setContent(CodeSystem.CodeSystemContentMode.COMPLETE);
            prePopulatedValidationSupport.addCodeSystem(newHCPCS);
        } else {
            existHCPCS.setContent(CodeSystem.CodeSystemContentMode.COMPLETE);
            existHCPCS.getConcept()
                    .addAll(ConceptReaderUtils.getCodeSystemConcepts_wCode(referenceCodesPath.concat("hcpcs.psv")));
        }
        LOG.info("PrePopulateSupport:addHCPCSCodes  -END");
    }

    private void addICD10Codes(ValidationSupportChain validationSupportChain,
            PrePopulatedValidationSupport prePopulatedValidationSupport) {
        LOG.info("PrePopulateSupport:addICD10Codes  -BEGIN");
        Span span = tracer.spanBuilder("PrePopulateSupport.addICD10Codes").startSpan();
        try {
            CodeSystem existingIcd10 = (CodeSystem) validationSupportChain
                    .fetchCodeSystem("http://hl7.org/fhir/sid/icd-10-cm");
            if (existingIcd10 == null) {
                CodeSystem newIcd10 = new CodeSystem();
                newIcd10.setUrl("http://hl7.org/fhir/sid/icd-10-cm");
                newIcd10.setConcept(ConceptReaderUtils.getCodeSystemConcepts_wCode(referenceCodesPath + "icd10cm.psv"));
                newIcd10.setContent(CodeSystem.CodeSystemContentMode.COMPLETE);
                prePopulatedValidationSupport.addCodeSystem(newIcd10);
            } else {
                if (existingIcd10.getConcept().isEmpty()) {
                    existingIcd10.getConcept()
                            .addAll(ConceptReaderUtils.getCodeSystemConcepts_wCode(referenceCodesPath + "icd10cm.psv"));
                }
                existingIcd10.setContent(CodeSystem.CodeSystemContentMode.COMPLETE);
            }
        } finally {
            span.end();
        }
        LOG.info("PrePopulateSupport:addICD10Codes  -END");
    }

    private void addSnomedCodes(ValidationSupportChain validationSupportChain,
            PrePopulatedValidationSupport prePopulatedValidationSupport) {
        LOG.info("PrePopulateSupport:addSnomedCodes  -BEGIN");
        Span span = tracer.spanBuilder("PrePopulateSupport.addSnomedCodes").startSpan();
        try {
            CodeSystem existingSnomed = (CodeSystem) validationSupportChain.fetchCodeSystem("http://snomed.info/sct");
            if (existingSnomed == null) {
                CodeSystem newSnomed = new CodeSystem();
                newSnomed.setUrl("http://snomed.info/sct");
                newSnomed.setConcept(ConceptReaderUtils.getCodeSystemConcepts_wCode(referenceCodesPath + "snomed.psv"));
                newSnomed.setContent(CodeSystem.CodeSystemContentMode.COMPLETE);
                prePopulatedValidationSupport.addCodeSystem(newSnomed);
            } else {
                if (existingSnomed.getConcept().isEmpty()) {
                    existingSnomed.getConcept()
                            .addAll(ConceptReaderUtils.getCodeSystemConcepts_wCode(referenceCodesPath + "snomed.psv"));
                }
                existingSnomed.setContent(CodeSystem.CodeSystemContentMode.COMPLETE);
            }
        } finally {
            span.end();
        }
        LOG.info("PrePopulateSupport:addSnomedCodes  -END");
    }

    public void loadValueSets(FhirContext fhirContext, PrePopulatedValidationSupport prePopulatedValidationSupport) {
        LOG.info("PrePopulateSupport:loadValueSets  -BEGIN");
        Span span = tracer.spanBuilder("PrePopulateSupport.loadValueSets").startSpan();
        try {
            loadValueSet("ig-packages/vs/2.16.840.1.113762.1.4.1021.32.json", fhirContext,
                    prePopulatedValidationSupport);
            loadValueSet("ig-packages/vs/2.16.840.1.113762.1.4.1240.11.json", fhirContext,
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
        LOG.info("PrePopulateSupport:addLoincCodes  -BEGIN");
        Span span = tracer.spanBuilder("PrePopulateSupport.addLoincCodes").startSpan();
        try {
            CodeSystem existingLoinc = (CodeSystem) validationSupportChain.fetchCodeSystem("http://loinc.org");
            if (existingLoinc == null) {
                CodeSystem newLoinc = new CodeSystem();
                newLoinc.setUrl("http://loinc.org");
                newLoinc.setVersion("2.81"); // or whatever version your .psv represents
                newLoinc.setName("LOINC");
                newLoinc.setStatus(Enumerations.PublicationStatus.ACTIVE);
                newLoinc.setConcept(ConceptReaderUtils.getCodeSystemConcepts_wCode(referenceCodesPath + "loinc.psv"));
                newLoinc.setContent(CodeSystem.CodeSystemContentMode.COMPLETE);
                prePopulatedValidationSupport.addCodeSystem(newLoinc);
            } else {
                if (existingLoinc.getConcept().isEmpty()) {
                    existingLoinc.getConcept()
                            .addAll(ConceptReaderUtils.getCodeSystemConcepts_wCode(referenceCodesPath + "loinc.psv"));
                }
                existingLoinc.setContent(CodeSystem.CodeSystemContentMode.COMPLETE);
            }
        } finally {
            span.end();
        }
        LOG.info("PrePopulateSupport:addLoincCodes  -END");
    }

}
