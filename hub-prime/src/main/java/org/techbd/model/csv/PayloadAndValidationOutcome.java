package org.techbd.model.csv;

import java.util.List;

public record PayloadAndValidationOutcome(List<FileDetail> fileDetails, boolean isValid) {
}
