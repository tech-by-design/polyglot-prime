package org.techbd.service.converters.shinny;

import java.util.List;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.ResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.techbd.model.csv.DemographicData;
import org.techbd.model.csv.QeAdminData;
import org.techbd.model.csv.ScreeningObservationData;
import org.techbd.model.csv.ScreeningProfileData;

@Component
public class QuestionnaireResponseConverter extends BaseConverter {
    private static final Logger LOG = LoggerFactory.getLogger(QuestionnaireResponseConverter.class.getName());

    @Override
    public ResourceType getResourceType() {
        return ResourceType.QuestionnaireResponse;
    }

    @Override
    public BundleEntryComponent  convert(Bundle bundle,DemographicData demographicData,QeAdminData qeAdminData ,
    ScreeningProfileData screeningProfileData ,List<ScreeningObservationData> screeningObservationData,String interactionId) {
        throw new UnsupportedOperationException("Unimplemented method 'convert'");
    }
}
