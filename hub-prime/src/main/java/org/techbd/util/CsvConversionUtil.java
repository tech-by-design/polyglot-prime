package org.techbd.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import org.techbd.model.csv.DemographicData;
import org.techbd.model.csv.QeAdminData;
import org.techbd.model.csv.ScreeningData;

import com.opencsv.bean.CsvToBeanBuilder;

public class CsvConversionUtil {

    
    /**
     * Converts a CSV string to a list of objects of the specified type.
     *
     * @param csvData The CSV string containing the data.
     * @param clazz   The class type to which the data should be converted.
     * @param separator The separator used in the CSV string (e.g., '|').
     * @param <T> The type of the object to convert the CSV to (DemographicData, ScreeningData, etc.).
     * @return List of objects of the specified type.
     * @throws IOException If an I/O error occurs during CSV reading.
     */
    private static <T> List<T> convertCsvStringToObjectList(String csvData, Class<T> clazz, char separator) throws IOException {
        List<T> dataList = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new StringReader(csvData))) {
            dataList = new CsvToBeanBuilder<T>(reader)
                    .withType(clazz)
                    .withSeparator(separator) // Specify the separator
                    .withIgnoreLeadingWhiteSpace(true)
                    .build()
                    .parse();
        }
        return dataList;
    }

    /**
     * Converts a CSV string to a list of DemographicData objects.
     *
     * @param csvData The CSV string containing the demographic data.
     * @return List of DemographicData objects.
     * @throws IOException If an I/O error occurs during CSV reading.
     */
    public static List<DemographicData> convertCsvStringToDemographicData(String csvData) throws IOException {
        return convertCsvStringToObjectList(csvData, DemographicData.class, '|');
    }

    /**
     * Converts a CSV string to a list of ScreeningData objects.
     *
     * @param csvData The CSV string containing the screening data.
     * @return List of ScreeningData objects.
     * @throws IOException If an I/O error occurs during CSV reading.
     */
    public static List<ScreeningData> convertCsvStringToScreeningData(String csvData) throws IOException {
        return convertCsvStringToObjectList(csvData, ScreeningData.class, '|');
    }

    /**
     * Converts a CSV string to a list of QeAdminData objects.
     *
     * @param csvData The CSV string containing the QeAdmin data.
     * @return List of QeAdminData objects.
     * @throws IOException If an I/O error occurs during CSV reading.
     */
    public static List<QeAdminData> convertCsvStringToQeAdminData(String csvData) throws IOException {
        return convertCsvStringToObjectList(csvData, QeAdminData.class, '|');
    }


}
