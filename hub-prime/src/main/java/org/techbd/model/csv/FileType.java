package org.techbd.model.csv;

public enum FileType {
    DEMOGRAPHIC_DATA,
    QE_ADMIN_DATA,
    SCREENING_PROFILE_DATA,
    SCREENING_OBSERVATION_DATA;

    public static FileType fromFilename(final String filename) {
        if (filename == null) {
            throw new IllegalArgumentException("Filename cannot be null");
        }
        String upperCaseFilename = filename.toUpperCase();
        for (final FileType type : values()) {
            if (upperCaseFilename.startsWith(type.name().toUpperCase())) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown file type in filename: " + filename);
    }
}
