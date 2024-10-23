package org.techbd.service.converters.shinny;

import org.hl7.fhir.r4.model.ResourceType;
import org.springframework.stereotype.Component;

@Component
public class QuestionnaireResponseConverter  extends BaseConverter {
    @Override
    public ResourceType getResourceType() {
        return ResourceType.QuestionnaireResponse;
    }
}
