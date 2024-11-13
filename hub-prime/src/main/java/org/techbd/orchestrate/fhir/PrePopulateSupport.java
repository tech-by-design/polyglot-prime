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

    private final String reference_codes_path = "ig-packages/reference/";
    public PrePopulateSupport() {
    }

    public void addCodeSystems(ValidationSupportChain validationSupportChain, PrePopulatedValidationSupport prePopulatedValidationSupport) {
        addSnomedCodes(validationSupportChain, prePopulatedValidationSupport);
        addICD10Codes(validationSupportChain, prePopulatedValidationSupport);
    }

    private void addICD10Codes(ValidationSupportChain validationSupportChain, PrePopulatedValidationSupport prePopulatedValidationSupport) {
        CodeSystem existIcd10 = (CodeSystem) validationSupportChain.fetchCodeSystem("http://hl7.org/fhir/sid/icd-10-cm");
        if (existIcd10==null) {
            CodeSystem newIcd10 = new CodeSystem();
            newIcd10.setUrl("http://hl7.org/fhir/sid/icd-10-cm");
            newIcd10.setConcept(ConceptReaderUtils.getCodeSystemConcepts_wCode(reference_codes_path.concat("icd10cm.psv")));
            newIcd10.setContent(CodeSystem.CodeSystemContentMode.COMPLETE);
            prePopulatedValidationSupport.addCodeSystem(newIcd10);
        } else {
            existIcd10.setContent(CodeSystem.CodeSystemContentMode.COMPLETE);
            existIcd10.getConcept().addAll(ConceptReaderUtils.getCodeSystemConcepts_wCode(reference_codes_path.concat("icd10cm.psv")));
        }
    }
    private void addSnomedCodes(ValidationSupportChain validationSupportChain, PrePopulatedValidationSupport prePopulatedValidationSupport) {
        CodeSystem existSnomed = (CodeSystem) validationSupportChain.fetchCodeSystem("http://snomed.info/sct");
        if (existSnomed==null) {
            CodeSystem newSnomed = new CodeSystem();
            newSnomed.setUrl("http://snomed.info/sct");
            newSnomed.setConcept(ConceptReaderUtils.getCodeSystemConcepts_wCode(reference_codes_path.concat("snomed.psv")));
            newSnomed.setContent(CodeSystem.CodeSystemContentMode.COMPLETE);
            prePopulatedValidationSupport.addCodeSystem(newSnomed);
        } else {
            existSnomed.setContent(CodeSystem.CodeSystemContentMode.COMPLETE);
            existSnomed.getConcept().addAll(ConceptReaderUtils.getCodeSystemConcepts_wCode(reference_codes_path.concat("snomed.psv")));
        }
    }

    public PrePopulatedValidationSupport build(FhirContext fhirContext) {
        PrePopulatedValidationSupport prePopulatedValidationSupport = new PrePopulatedValidationSupport(fhirContext);
        load_vs(fhirContext, prePopulatedValidationSupport);
        return prePopulatedValidationSupport;
    }

    public void load_vs(FhirContext fhirContext, PrePopulatedValidationSupport prePopulatedValidationSupport) {
        ValueSet vs = fhirContext.newJsonParser().parseResource(ValueSet.class,
                FileUtils.readFile1("ig-packages/vs/2.16.840.1.113762.1.4.1021.32.json")
                );
        addExpansionToInclude(vs);
        prePopulatedValidationSupport.addValueSet(vs);

        ValueSet vs1 = fhirContext.newJsonParser().parseResource(ValueSet.class,
                FileUtils.readFile1("ig-packages/vs/2.16.840.1.113762.1.4.1240.11.json")
                );
        addExpansionToInclude(vs1);
        prePopulatedValidationSupport.addValueSet(vs1);

        System.out.println("Completed PrePopulatedValidationSupport value set build ");
    }

    private void addExpansionToInclude(ValueSet valueSet) {
        if (valueSet==null || valueSet.getExpansion().getContains().size()==0)
            return;

        Map<String, List<ValueSet.ConceptReferenceComponent>> concepts = new HashMap<>();
        valueSet.getExpansion().getContains().forEach(it -> {
            List<ValueSet.ConceptReferenceComponent> include_concepts =
                    concepts.getOrDefault(it.getSystem(), new ArrayList<>());
            include_concepts.add(
                    new ValueSet.ConceptReferenceComponent().setCode(it.getCode()).setDisplay(it.getDisplay())
            );

            concepts.putIfAbsent(it.getSystem(), include_concepts);
        });


        concepts.forEach( (k,v) -> valueSet.getCompose().getInclude().add(
                new ValueSet.ConceptSetComponent().setSystem(k).setConcept(v)
        ));
    }

}
