package org.techbd.service.converters.shinny;

import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.hl7.fhir.r4.model.CanonicalType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.techbd.model.csv.ScreeningData;
import org.techbd.util.DateUtil;
import org.techbd.util.YamlUtil;

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
}
