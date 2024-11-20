package org.techbd.service.converters.shinny;

import org.hl7.fhir.r4.model.Extension;
public interface IPatientConverter {
    Extension getRaceOmbExtension(String code,String description,String system);
    Extension getRaceDetailedExtension(String code,String description,String system);
    Extension getEthinicityOmbExtension(String code,String description,String system);
    Extension getEthinicityDetailedExtension(String code,String description,String system);
    Extension getSexAtBirthExtension(String code,String description,String system);
    // Extension getSexualOrientationExtension(String code,String description,String system); //TODO -current not shown in shinny examples
}
