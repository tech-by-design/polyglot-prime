package org.techbd.service.converters.shinny;

import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.StringType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.techbd.model.csv.ScreeningData;
import org.techbd.util.DateUtil;
import org.techbd.util.YamlUtil;

import org.apache.commons.lang3.StringUtils;
public abstract class BaseConverter implements IConverter {

    private static final Logger LOG = LoggerFactory.getLogger(BaseConverter.class.getName());
    public static Map<String, String> PROFILE_MAP = getProfileUrlMap();


    public static Map<String, String> getProfileUrlMap() {
        return YamlUtil.getYamlResourceAsMap("src/main/resources/shinny/shinny-artifacts/profile.yml");
    }

    public CanonicalType getProfileUrl() {
        return new CanonicalType(PROFILE_MAP.get(getResourceType().name().toLowerCase()));
    }
    /**
     * Returns the maximum lastUpdatedDate from a list of ScreeningData objects as a
     * java.util.Date.
     * If no valid date is found, returns null.
     *
     * @param screeningDataList List of ScreeningData objects.
     * @return Maximum lastUpdatedDate as a java.util.Date or null if no valid dates
     *         are found.
     */
    public static Date getMaxLastUpdatedDate(List<ScreeningData> screeningDataList) {
        return screeningDataList.stream()
                .map(ScreeningData::getEncounterLastUpdated)
                .filter(dateStr -> dateStr != null && !dateStr.isEmpty())
                .map(DateUtil::convertStringToDate)
                .filter(date -> date != null)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }


    public static Extension createExtension(String url,String value, String system, String code, String display) {
        if (StringUtils.isEmpty(url)) {
            throw new IllegalArgumentException("Extension URL cannot be null or empty");
        }
        Extension extension = new Extension();
        extension.setUrl(url);
        if (StringUtils.isNotEmpty(system) || StringUtils.isNotEmpty(code) || StringUtils.isNotEmpty(display)) {
            Coding coding = new Coding();
            if (StringUtils.isNotEmpty(system)) {
                coding.setSystem(system);
            }
            if (StringUtils.isNotEmpty(code)) {
                coding.setCode(code);
            }
            if (StringUtils.isNotEmpty(display)) {
                coding.setDisplay(display);
            }
            CodeableConcept codeableConcept = new CodeableConcept();
            codeableConcept.addCoding(coding);
            extension.setValue(codeableConcept);
        }
        if (StringUtils.isNotEmpty(value)){
            extension.setValue(new StringType(value));
        }
        return extension;
    }
    
}
