package org.techbd.csv.model;

import java.util.List;
import java.util.Map;

public record PayloadAndValidationOutcome(List<FileDetail> fileDetails, boolean isValid,String groupInteractionId,Map<String,Object> provenance,Map<String,Object> validationResults) {
}
