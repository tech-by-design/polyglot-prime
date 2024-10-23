package org.techbd.service.converters.shinny;

import org.hl7.fhir.r4.model.ResourceType;
import org.springframework.stereotype.Component;

@Component
public class PatientConverter  extends BaseConverter {

    public ResourceType getResourceType(){
        return ResourceType.Patient;
    }
}
