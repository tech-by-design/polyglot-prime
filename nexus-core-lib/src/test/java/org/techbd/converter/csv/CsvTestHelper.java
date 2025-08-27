package org.techbd.converter.csv;

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
import org.techbd.util.csv.CsvConversionUtil;

public class CsvTestHelper {
    public static final String BASE_FHIR_URL="http://test.shinny.org/us/ny/hrsn";
   // Base path for all CSV files
   private static final String BASE_CSV_PATH = "src/test/resources/org/techbd/csv/data/latestResources/";
    
   // File names for each type of data
   private static final String SCREENING_PROFILE_FILE = "SDOH_ScreeningProf_CareRidgeSCN_testcase1_20250312040214.csv";
   private static final String DEMOGRAPHIC_DATA_FILE = "SDOH_PtInfo_CareRidgeSCN_testcase1_20250312040214.csv";
   private static final String SCREENING_OBSERVATION_FILE = "SDOH_ScreeningObs_CareRidgeSCN_testcase1_20250312040214.csv";
   private static final String QE_ADMIN_DATA_FILE = "SDOH_QEadmin_CareRidgeSCN_testcase1_20250312040214.csv";
    
   public static ScreeningProfileData createScreeningProfileData() throws IOException {
        String csvContent = Files.readString(Path.of(BASE_CSV_PATH + SCREENING_PROFILE_FILE));
        return CsvConversionUtil.convertCsvStringToScreeningProfileData(csvContent,"test","0.667.0").get("EncounterExample").get(0);
    }

    public static DemographicData createDemographicData() throws IOException {
        String csvContent = Files.readString(Path.of(BASE_CSV_PATH + DEMOGRAPHIC_DATA_FILE));
        return CsvConversionUtil.convertCsvStringToDemographicData(csvContent,"test","0.667.0").get("11223344").get(0);
    }

    public static List<ScreeningObservationData> createScreeningObservationData() throws IOException {
        String csvContent = Files.readString(Path.of(BASE_CSV_PATH + SCREENING_OBSERVATION_FILE));
        return CsvConversionUtil.convertCsvStringToScreeningObservationData(csvContent,"test","0.667.0").get("EncounterExample");
    }

    public static QeAdminData createQeAdminData() throws IOException {
        String csvContent = Files.readString(Path.of(BASE_CSV_PATH + QE_ADMIN_DATA_FILE));
        return CsvConversionUtil.convertCsvStringToQeAdminData(csvContent,"test","0.667.0").get("11223344").get(0);
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
