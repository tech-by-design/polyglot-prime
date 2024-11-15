package org.techbd.util;

import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Organization;

public class ConversionUtil {

    public static Identifier createIdentifier(String system, String code, String value, String typeText, String assignerReference) {
        // Create the Identifier object
        Identifier identifier = new Identifier();

        // Set the type (with coding)
        CodeableConcept type = new CodeableConcept();
        type.addCoding(new Coding("http://terminology.hl7.org/CodeSystem/v2-0203", code, typeText));
        identifier.setType(type);

        // Set the system and value
        identifier.setSystem(system);
        identifier.setValue(value);

        // Set the assigner if provided
        if (assignerReference != null && !assignerReference.isEmpty()) {
           // identifier.setAssigner(new Organization().setReference(assignerReference));
        }

        return identifier;
    }
}
