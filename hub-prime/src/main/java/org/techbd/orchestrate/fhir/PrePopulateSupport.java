package org.techbd.orchestrate.fhir;

import ca.uhn.fhir.context.FhirContext;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;

import org.hl7.fhir.common.hapi.validation.support.PrePopulatedValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.ValueSet;
import org.techbd.orchestrate.fhir.util.ConceptReaderUtils;
import org.techbd.orchestrate.fhir.util.FileUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PrePopulateSupport {

    private final String referenceCodesPath = "ig-packages/reference/";
    private final Tracer tracer;

    public PrePopulateSupport(final Tracer tracer) {
        this.tracer = tracer;
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
        } finally {
            span.end();
        }
    }

    private void addICD10Codes(ValidationSupportChain validationSupportChain,
            PrePopulatedValidationSupport prePopulatedValidationSupport) {
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
    }

    private void addSnomedCodes(ValidationSupportChain validationSupportChain,
            PrePopulatedValidationSupport prePopulatedValidationSupport) {
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
    }

    public void loadValueSets(FhirContext fhirContext, PrePopulatedValidationSupport prePopulatedValidationSupport) {
        Span span = tracer.spanBuilder("PrePopulateSupport.loadValueSets").startSpan();
        try {
            loadValueSet("ig-packages/vs/2.16.840.1.113762.1.4.1021.32.json", fhirContext,
                    prePopulatedValidationSupport);
            loadValueSet("ig-packages/vs/2.16.840.1.113762.1.4.1240.11.json", fhirContext,
                    prePopulatedValidationSupport);
        } finally {
            span.end();
        }
    }

    private void loadValueSet(String filePath, FhirContext fhirContext,
            PrePopulatedValidationSupport prePopulatedValidationSupport) {
        Span span = tracer.spanBuilder("PrePopulateSupport.loadValueSet").startSpan();
        try {
            ValueSet valueSet = fhirContext.newJsonParser().parseResource(ValueSet.class,
                    FileUtils.readFile1(filePath));
            addExpansionToInclude(valueSet);
            prePopulatedValidationSupport.addValueSet(valueSet);
            valueSet = null;
        } finally {
            span.end();
        }
    }

    private void addExpansionToInclude(ValueSet valueSet) {
        Span span = tracer.spanBuilder("PrePopulateSupport.addExpansionToInclude").startSpan();
        try {
            if (valueSet == null || valueSet.getExpansion().getContains().size() == 0
                    || valueSet.getExpansion().getContains().isEmpty())
                return;

            Map<String, List<ValueSet.ConceptReferenceComponent>> concepts = new HashMap<>();
            valueSet.getExpansion().getContains().forEach(item -> {
                concepts.computeIfAbsent(item.getSystem(), k -> new ArrayList<>())
                        .add(new ValueSet.ConceptReferenceComponent().setCode(item.getCode())
                                .setDisplay(item.getDisplay()));
            });

            valueSet.getCompose().getInclude().clear();
            concepts.forEach((system, conceptList) -> valueSet.getCompose().getInclude()
                    .add(new ValueSet.ConceptSetComponent().setSystem(system).setConcept(conceptList)));
        } finally {
            span.end();
        }
    }
}
