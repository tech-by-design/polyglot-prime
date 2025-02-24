package org.techbd.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.lang.reflect.Field;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.techbd.model.csv.DemographicData;
import org.techbd.model.csv.QeAdminData;
import org.techbd.model.csv.ScreeningObservationData;
import org.techbd.model.csv.ScreeningProfileData;

import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.exceptions.CsvException;

/**
 * Utility class for converting CSV data into domain-specific models grouped by
 * patient MR ID.
 */
public class CsvConversionUtil {
    private static final Logger LOG = LoggerFactory.getLogger(CsvConversionUtil.class.getName());
    private static final char SEPARATOR = ',';

    /**
     * Converts a CSV string into a map of demographic data grouped by
     * `patientMrIdValue`.
     *
     * @param csvData the CSV data as a string
     * @return a map where the key is the patient MR ID value and the value is the
     *         list of demographic data with that ID
     * @throws IOException if an error occurs during reading or parsing the CSV
     */
    public static Map<String, List<DemographicData>> convertCsvStringToDemographicData(String csvData) {
        try {
            return convertCsvStringToObjectMap(csvData, DemographicData.class, SEPARATOR, "patientMrIdValue");
        } catch (IOException e) {
            //This will be a foreign key error in frictionless and hence the csv validation will fail and need not be converted to bundle.
            LOG.error(
                    "Error converting CSV data to DemographicData: Field 'patientMrIdValue' does not exist or is incorrect. Details: "
                            + e.getMessage());
            return new HashMap<>();
        } catch (Exception ex) {
            // Catching any other unexpected exceptions
            LOG.error("Unexpected error while parsing Demographic Details: " + ex.getMessage(), ex);
            return new HashMap<>();
        }
    }

    /**
     * Converts a CSV string into a map of screening observation data grouped by
     * `encounterId`.
     *
     * @param csvData the CSV data as a string
     * @return a map where the key is the encounter ID value and the value is the
     *         list of screening observation data with that ID
     * @throws IOException if an error occurs during reading or parsing the CSV
     */
    public static Map<String, List<ScreeningObservationData>> convertCsvStringToScreeningObservationData(String csvData) {
        try {
            return convertCsvStringToObjectMap(csvData, ScreeningObservationData.class, SEPARATOR, "encounterId");
        } catch (IOException e) {
            //This will be a foreign key error in frictionless and hence the csv validation will fail and need not be converted to bundle.
            LOG.error("Error converting CSV data to ScreeningObservationData: Field 'encounterId' does not exist or is incorrect. Details: " + e.getMessage());
            return new HashMap<>();
        } catch (Exception ex) {
            // Catching any other unexpected exceptions
            LOG.error("Unexpected error while parsing Screening Observation Details: " + ex.getMessage(), ex);
            return new HashMap<>();
        }
    }
    

    /**
     * Converts a CSV string into a map of QE admin data grouped by
     * `patientMrIdValue`.
     *
     * @param csvData the CSV data as a string
     * @return a map where the key is the patient MR ID value and the value is the
     *         list of QE admin data with that ID
     * @throws IOException if an error occurs during reading or parsing the CSV
     */
    public static Map<String, List<QeAdminData>> convertCsvStringToQeAdminData(String csvData) {
        try {
            return convertCsvStringToObjectMap(csvData, QeAdminData.class, SEPARATOR, "patientMrIdValue");
        } catch (IOException e) {
             //This will be a foreign key error in frictionless and hence the csv validation will fail and need not be converted to bundle.
            LOG.error("Error converting CSV data to QeAdminData: Field 'patientMrIdValue' does not exist or is incorrect. Details: " + e.getMessage());
            return new HashMap<>();
        } catch (Exception ex) {
            // Catching any other unexpected exceptions
            LOG.error("Unexpected error while parsing Qe Admin Details: " + ex.getMessage(), ex);
            return new HashMap<>();
        }
    }
    

    /**
     * Converts a CSV string into a map of screening profile data grouped by
     * `patientMrIdValue`.
     *
     * @param csvData the CSV data as a string
     * @return a map where the key is the patient MR ID value and the value is the
     *         list of screening profile data with that ID
     * @throws IOException if an error occurs during reading or parsing the CSV
     */
    public static Map<String, List<ScreeningProfileData>> convertCsvStringToScreeningProfileData(String csvData) {
        try {
            return convertCsvStringToObjectMap(csvData, ScreeningProfileData.class, SEPARATOR, "encounterId");
        } catch (IOException e) {
            //This will be a foreign key error in frictionless and hence the csv validation will fail and need not be converted to bundle.
            LOG.error("Error converting CSV data to ScreeningProfileData: Field 'encounterId' does not exist or is incorrect. Details: " + e.getMessage());
            return new HashMap<>();
        } catch (Exception ex) {
            // Catching any other unexpected exceptions
            LOG.error("Unexpected error while parsing Screening Profile Details: " + ex.getMessage(), ex);
            return new HashMap<>();
        }
    }
    

       public static <T> Map<String, List<T>> convertCsvStringToObjectMap(String csvData, Class<T> clazz, char separator, String fieldName) throws IOException {
        List<String[]> errorRows = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new StringReader(csvData))) {
            CsvToBean<T> csvToBean = new CsvToBeanBuilder<T>(reader)
                    .withType(clazz)
                    .withSeparator(separator)
                    .withIgnoreLeadingWhiteSpace(true)
                    .withIgnoreQuotations(true)
                    .withThrowExceptions(false) // Do not fail on bad rows
                    .build();

            List<T> dataList = csvToBean.parse();
            List<CsvException> errors = csvToBean.getCapturedExceptions();
            for (T obj : dataList) {
                if (obj instanceof DemographicData) {
                    printDemographicData((DemographicData) obj);
                }
            }
            for (CsvException error : errors) {
                LOG.warn("Malformed CSV row skipped: {}", error.getLineNumber(), error);
                errorRows.add(error.getLine()); // Store bad rows for debugging
            }

            // Grouping valid rows by fieldName
            return dataList.stream()
                    .collect(Collectors.groupingBy(obj -> {
                        String fieldValue = getFieldValue(obj, fieldName);
                        if (fieldValue == null) {
                            LOG.error("Null value encountered for field '{}' in object: {}", fieldName, obj);
                            throw new IllegalArgumentException("Field '" + fieldName + "' has a null value in object: " + obj);
                        }
                        return fieldValue;
                    }));
        } catch (Exception ex) {
            LOG.error("Error processing CSV data. Field '{}': {}", fieldName, ex.getMessage(), ex);
            throw ex;
        }
    }
    private static void printDemographicData(DemographicData data) {
        StringBuilder sb = new StringBuilder("\nDemographicData:\n");

        try {
            for (Field field : DemographicData.class.getDeclaredFields()) {
                field.setAccessible(true);
                sb.append(field.getName()).append(": ").append(field.get(data)).append("\n");
            }
        } catch (IllegalAccessException e) {
            LOG.error("Error accessing DemographicData fields", e);
        }

        LOG.info(sb.toString());
        System.out.println(sb.toString()); // Print to console
    }
    /**
     * Extracts the value of a specified field from an object using reflection.
     *
     * @param obj       the object to extract the value from
     * @param fieldName the name of the field to extract (e.g., "patientMrIdValue"
     *                  or "encounterId")
     * @return the field value as a string
     */
    private static String getFieldValue(Object obj, String fieldName) {
        try {
            var field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return (String) field.get(obj);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalArgumentException(
                    "The class " + obj.getClass().getName() + " does not have a field named '" + fieldName + "'.", e);
        }
    }

    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("Error generating SHA-256 hash", e);
        }
    }

}
