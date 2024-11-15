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
import org.techbd.model.csv.ScreeningData;

@Component
public class PractitionerConverter extends BaseConverter {
    private static final Logger LOG = LoggerFactory.getLogger(PractitionerConverter.class.getName());

    @Override
    public ResourceType getResourceType() {
        return ResourceType.Practitioner;
    }

    @Override
    public BundleEntryComponent convert(Bundle bundle, DemographicData demographicData,
            List<ScreeningData> screeningDataList,
            QeAdminData qrAdminData,String interactionId) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'convert'");
    }
}
