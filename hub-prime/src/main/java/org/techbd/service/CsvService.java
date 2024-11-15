package org.techbd.service;


import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class CsvService {

    public void processZipFile(MultipartFile file) throws Exception {
        try (InputStream is = file.getInputStream();
             ZipInputStream zis = new ZipInputStream(is)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().endsWith("QE_ADMIN_DATA.csv")) {
                    handleQeAdminData(zis);
                } else if (entry.getName().endsWith("DEMOGRAPHIC_DATA.csv")) {
                    handleDemographicData(zis);
                } else if (entry.getName().endsWith("SCREENING_DATA.csv")) {
                    handleScreeningData(zis);
                }
                zis.closeEntry();
            }
        }
    }

    private void handleQeAdminData(InputStream is) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Logic to process each line of QE_ADMIN_DATA.csv
                System.out.println("Processing QE_ADMIN_DATA: " + line);
            }
        }
    }

    private void handleDemographicData(InputStream is) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Logic to process each line of DEMOGRAPHIC_DATA.csv
                System.out.println("Processing DEMOGRAPHIC_DATA: " + line);
            }
        }
    }

    private void handleScreeningData(InputStream is) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Logic to process each line of SCREENING_DATA.csv
                System.out.println("Processing SCREENING_DATA: " + line);
            }
        }
    }
}