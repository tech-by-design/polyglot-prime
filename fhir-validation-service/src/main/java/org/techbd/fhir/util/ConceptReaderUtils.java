package org.techbd.fhir.util;

import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.ValueSet;

import java.util.ArrayList;
import java.util.List;

public class ConceptReaderUtils {

    public static List<CodeSystem.ConceptDefinitionComponent> getCodeSystemConcepts_wCode(String file) {
        List<CodeSystem.ConceptDefinitionComponent> concepts = new ArrayList<>();
        FileUtils.readFile(file).stream().distinct().forEach(
                l -> {
                    CodeSystem.ConceptDefinitionComponent conceptDef = new CodeSystem.ConceptDefinitionComponent();
                    conceptDef.setCode(l);
                    concepts.add(conceptDef);
                });
        return concepts;
    }

    public static List<ValueSet.ConceptReferenceComponent> getValueSetConcepts_wCode(String file) {
        List<ValueSet.ConceptReferenceComponent> concepts = new ArrayList<>();
        FileUtils.readFile(file).stream().distinct().forEach(
                l -> {
                    ValueSet.ConceptReferenceComponent conceptDef = new ValueSet.ConceptReferenceComponent();
                    conceptDef.setCode(l);
                    concepts.add(conceptDef);
                });
        return concepts;
    }
}
