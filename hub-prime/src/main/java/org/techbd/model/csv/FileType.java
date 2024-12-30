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
        StringBuilder errorMessage = new StringBuilder("Invalid file prefix: ")
                .append(filename)
                .append(". Filenames must start with one of the following prefixes: ");
        for (FileType type : values()) {
            errorMessage.append(type.name()).append(", ");
        }
        errorMessage.setLength(errorMessage.length() - 2);
        throw new IllegalArgumentException(errorMessage.toString());
    }
}