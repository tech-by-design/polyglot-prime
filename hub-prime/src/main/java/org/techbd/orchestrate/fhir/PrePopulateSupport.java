package org.techbd.orchestrate.fhir;

import ca.uhn.fhir.context.FhirContext;
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
    public PrePopulateSupport() {
    }

    public PrePopulatedValidationSupport build(FhirContext fhirContext) {
        PrePopulatedValidationSupport prePopulatedValidationSupport = new PrePopulatedValidationSupport(fhirContext);
        loadValueSets(fhirContext, prePopulatedValidationSupport);
        return prePopulatedValidationSupport;
    }
    
    public void addCodeSystems(ValidationSupportChain validationSupportChain,
            PrePopulatedValidationSupport prePopulatedValidationSupport) {
        addSnomedCodes(validationSupportChain, prePopulatedValidationSupport);
        addICD10Codes(validationSupportChain, prePopulatedValidationSupport);
    }

    private void addICD10Codes(ValidationSupportChain validationSupportChain,
            PrePopulatedValidationSupport prePopulatedValidationSupport) {
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
    }

    private void addSnomedCodes(ValidationSupportChain validationSupportChain,
            PrePopulatedValidationSupport prePopulatedValidationSupport) {
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
    }

    public void loadValueSets(FhirContext fhirContext, PrePopulatedValidationSupport prePopulatedValidationSupport) {
        loadValueSet("ig-packages/vs/2.16.840.1.113762.1.4.1021.32.json", fhirContext, prePopulatedValidationSupport);
        loadValueSet("ig-packages/vs/2.16.840.1.113762.1.4.1240.11.json", fhirContext, prePopulatedValidationSupport);
        System.out.println("Completed PrePopulatedValidationSupport value set build");
    }

    private void loadValueSet(String filePath, FhirContext fhirContext,
            PrePopulatedValidationSupport prePopulatedValidationSupport) {
        ValueSet valueSet = fhirContext.newJsonParser().parseResource(ValueSet.class, FileUtils.readFile1(filePath));
        addExpansionToInclude(valueSet);
        prePopulatedValidationSupport.addValueSet(valueSet);
        valueSet = null;
    }

    private void addExpansionToInclude(ValueSet valueSet) {
        if (valueSet == null || valueSet.getExpansion().getContains().size()==0 ||  valueSet.getExpansion().getContains().isEmpty())
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
    }
}
