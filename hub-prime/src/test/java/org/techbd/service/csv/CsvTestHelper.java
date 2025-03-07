package org.techbd.service.csv;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.techbd.model.csv.DemographicData;
import org.techbd.model.csv.QeAdminData;
import org.techbd.model.csv.ScreeningObservationData;
import org.techbd.model.csv.ScreeningProfileData;
import org.techbd.util.CsvConversionUtil;

public class CsvTestHelper {
    public static final String BASE_FHIR_URL="http://test.shinny.org/us/ny/hrsn";
   // Base path for all CSV files
   private static final String BASE_CSV_PATH = "src/test/resources/org/techbd/csv/data/latestResources/";
    
   // File names for each type of data
   private static final String SCREENING_PROFILE_FILE = "SCREENING_PROFILE_DATA_Care_Ridge_SCN_ScreeningProf_20240223102001.csv";
   private static final String DEMOGRAPHIC_DATA_FILE = "DEMOGRAPHIC_DATA_Care_Ridge_SCN_SDOH_PtInfo_20240223102001.csv";
   private static final String SCREENING_OBSERVATION_FILE = "SCREENING_OBSERVATION_DATA_Care_Ridge_SCN_SDOH_ScreeningObs_20240223102001.csv";
   private static final String QE_ADMIN_DATA_FILE = "QE_ADMIN_DATA_Care_Ridge_SCN_SDOH_QEadmin_20240223102001.csv";
    
   public static ScreeningProfileData createScreeningProfileData() throws IOException {
        String csvContent = Files.readString(Path.of(BASE_CSV_PATH + SCREENING_PROFILE_FILE));
        return CsvConversionUtil.convertCsvStringToScreeningProfileData(csvContent).get("EncounterExample").get(0);
    }

    public static DemographicData createDemographicData() throws IOException {
        String csvContent = Files.readString(Path.of(BASE_CSV_PATH + DEMOGRAPHIC_DATA_FILE));
        return CsvConversionUtil.convertCsvStringToDemographicData(csvContent).get("11223344").get(0);
    }

    public static List<ScreeningObservationData> createScreeningObservationData() throws IOException {
        String csvContent = Files.readString(Path.of(BASE_CSV_PATH + SCREENING_OBSERVATION_FILE));
        return CsvConversionUtil.convertCsvStringToScreeningObservationData(csvContent).get("EncounterExample");
    }

    public static QeAdminData createQeAdminData() throws IOException {
        String csvContent = Files.readString(Path.of(BASE_CSV_PATH + QE_ADMIN_DATA_FILE));
        return CsvConversionUtil.convertCsvStringToQeAdminData(csvContent).get("11223344").get(0);
    }
    
    public static Map<String, String> getProfileMap() {
            Map<String, String> profileMap = new HashMap<>();
            profileMap.put("bundle", "/StructureDefinition/SHINNYBundleProfile");
            profileMap.put("patient", "/StructureDefinition/shinny-patient");
            profileMap.put("consent", "/StructureDefinition/shinny-Consent");
            profileMap.put("encounter", "/StructureDefinition/shinny-encounter");
            profileMap.put("organization", "/StructureDefinition/shin-ny-organization");
            profileMap.put("observation", "/StructureDefinition/shinny-observation-screening-response");
            profileMap.put("questionnaire", "/StructureDefinition/shinny-questionnaire");
            profileMap.put("practitioner", "/StructureDefinition/shin-ny-practitioner");
            profileMap.put("questionnaireResponse", "/StructureDefinition/shinny-questionnaire");
            profileMap.put("observationSexualOrientation",
                            "/StructureDefinition/shinny-observation-sexual-orientation");
            return profileMap;
    }

}
