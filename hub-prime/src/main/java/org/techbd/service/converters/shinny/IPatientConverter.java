package org.techbd.service.converters.shinny;

import org.hl7.fhir.r4.model.Identifier;

public interface IPatientConverter {

    String getId();
    String getFirstName();
    String getLastName();
    String getMiddleName();
    Identifier getMRN(String system,String value,String assigner);
    Identifier getMPIID(String system,String value,String assigner);
    Identifier getSSN(String system,String value,String assigner);


}
