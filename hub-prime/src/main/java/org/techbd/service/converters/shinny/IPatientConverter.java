package org.techbd.service.converters.shinny;

import org.hl7.fhir.r4.model.Extension;
public interface IPatientConverter {
    Extension getRaceOmbExtension(String url, String system, String code, String display);

    Extension getRaceDetailedExtension(String url, String system, String code, String display);
    
    Extension getEthnicityOmbExtension(String url, String system, String code, String display);
    
    Extension getEthnicityDetailedExtension(String url, String system, String code, String display);
    
    Extension getSexAtBirthExtension(String url, String system, String code, String display);

    Extension getShinnyPersonalPronounsExtension(String url, String system, String code, String display);

    Extension getShinnyGenderIdentityExtension(String url, String system, String code, String display);

    // Extension getSexualOrientationExtension(String code,String description,String system); //TODO -current not shown in shinny examples
}
