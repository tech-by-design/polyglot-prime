package org.techbd.service.fhir.validation;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.ConceptValidationOptions;
import ca.uhn.fhir.context.support.IValidationSupport;
import ca.uhn.fhir.context.support.ValidationSupportContext;
import ca.uhn.fhir.context.support.ValueSetExpansionOptions;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.Enumerations;
import org.hl7.fhir.r4.model.ValueSet;

import org.techbd.util.fhir.FileUtils;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RaceEthnicityValidationSupport implements IValidationSupport {

    public static final String CDC_RACE_ETHNICITY_SYSTEM =
            "urn:oid:2.16.840.1.113883.6.238";

    public static final String NULL_FLAVOR_SYSTEM =
            "http://terminology.hl7.org/CodeSystem/v3-NullFlavor";

    public static final String OMB_RACE_VS_URL =
            "http://hl7.org/fhir/us/core/ValueSet/omb-race-category";

    public static final String OMB_ETHNICITY_VS_URL =
            "http://hl7.org/fhir/us/core/ValueSet/omb-ethnicity-category";

    public static final String DETAILED_RACE_VS_URL =
            "http://hl7.org/fhir/us/core/ValueSet/detailed-race";

    private final FhirContext fhirContext;

    private final Map<String, String> ombRaceMap = new HashMap<>();
    private final Map<String, String> ombEthnicityMap = new HashMap<>();
    private final Map<String, String> detailedRaceMap = new HashMap<>();
    private final Map<String, String> nullFlavorMap = new HashMap<>();
    private final Map<String, String> allCdcConcepts = new HashMap<>();

    private final CodeSystem codeSystemResource;

    private final ValueSet ombRaceValueSet;
    private final ValueSet ombEthnicityValueSet;
    private final ValueSet detailedRaceValueSet;

    public RaceEthnicityValidationSupport(
            FhirContext fhirContext,
            String psvFilePath) {

        this.fhirContext = fhirContext;

        this.codeSystemResource = loadConceptsFromPsv(psvFilePath);

        this.ombRaceValueSet = createExpandedValueSet(
                OMB_RACE_VS_URL,
                "omb-race-category",
                "OMB Race Categories",
                ombRaceMap,
                true);

        this.ombEthnicityValueSet = createExpandedValueSet(
                OMB_ETHNICITY_VS_URL,
                "omb-ethnicity-category",
                "OMB Ethnicity Categories",
                ombEthnicityMap,
                true);

        this.detailedRaceValueSet = createExpandedValueSet(
                DETAILED_RACE_VS_URL,
                "detailed-race",
                "Detailed Race",
                detailedRaceMap,
                false);
    }

    /**
     * Reads race.psv.
     *
     * Expected format:
     *
     * CATEGORY|CODE|DISPLAY
     *
     * Examples:
     *
     * OMB_RACE|2028-9|Asian
     * OMB_ETHNICITY|2135-2|Hispanic or Latino
     * DETAILED_RACE|1010-8|Apache
     * NULL_FLAVOR|UNK|Unknown
     */
    private CodeSystem loadConceptsFromPsv(String psvFilePath) {

        CodeSystem codeSystem = new CodeSystem();

        codeSystem.setUrl(CDC_RACE_ETHNICITY_SYSTEM);
        codeSystem.setStatus(Enumerations.PublicationStatus.ACTIVE);
        codeSystem.setContent(CodeSystem.CodeSystemContentMode.COMPLETE);

        try {
            List<String> lines = FileUtils.readFile(psvFilePath);

            if (lines == null) {
                return codeSystem;
            }

            for (String line : lines) {

                if (line == null || line.isBlank()) {
                    continue;
                }

                line = line.trim();

                // Ignore comments
                if (line.startsWith("#")) {
                    continue;
                }

                String[] parts = line.split("\\|", 3);

                if (parts.length < 2) {
                    continue;
                }

                String category = parts[0].trim();
                String code = parts[1].trim();

                if (code.isEmpty()) {
                    continue;
                }

                String display = code;

                if (parts.length >= 3 && !parts[2].trim().isEmpty()) {
                    display = parts[2].trim();
                }

                CodeSystem.ConceptDefinitionComponent concept =
                        new CodeSystem.ConceptDefinitionComponent();

                concept.setCode(code);
                concept.setDisplay(display);

                codeSystem.addConcept(concept);

                allCdcConcepts.put(code, display);

                switch (category.toUpperCase()) {

                    case "OMB_RACE":
                        ombRaceMap.put(code, display);
                        break;

                    case "OMB_ETHNICITY":
                        ombEthnicityMap.put(code, display);
                        break;

                    case "DETAILED_RACE":
                        detailedRaceMap.put(code, display);
                        break;

                    case "NULL_FLAVOR":
                        nullFlavorMap.put(code, display);
                        break;

                    default:
                        // The code is still registered in the CDC CodeSystem.
                        break;
                }
            }

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Unable to load Race/Ethnicity PSV file: " + psvFilePath,
                    e);
        }

        return codeSystem;
    }

    /**
     * Creates a fully expanded in-memory ValueSet.
     *
     * The important part here is expansion.contains.
     * HAPI uses this when validating a code against an already
     * expanded ValueSet.
     */
    private ValueSet createExpandedValueSet(
            String url,
            String id,
            String name,
            Map<String, String> concepts,
            boolean includeNullFlavor) {

        ValueSet valueSet = new ValueSet();

        valueSet.setId(id);
        valueSet.setUrl(url);
        valueSet.setVersion("7.0.0");
        valueSet.setName(name);
        valueSet.setStatus(Enumerations.PublicationStatus.ACTIVE);

        ValueSet.ValueSetExpansionComponent expansion =
                valueSet.getExpansion();

        expansion.setTimestamp(new Date());

        /*
         * CDC Race/Ethnicity concepts
         */
        ValueSet.ConceptSetComponent cdcInclude =
                new ValueSet.ConceptSetComponent();

        cdcInclude.setSystem(CDC_RACE_ETHNICITY_SYSTEM);

        for (Map.Entry<String, String> entry : concepts.entrySet()) {

            String code = entry.getKey();
            String display = entry.getValue();

            /*
             * Add to expansion.
             *
             * This is critical for:
             *
             * "Unknown code ... for in-memory expansion"
             */
            ValueSet.ValueSetExpansionContainsComponent contains =
                    expansion.addContains();

            contains.setSystem(CDC_RACE_ETHNICITY_SYSTEM);
            contains.setCode(code);
            contains.setDisplay(display);

            /*
             * Also add to compose.
             */
            cdcInclude.addConcept()
                    .setCode(code)
                    .setDisplay(display);
        }

        valueSet.getCompose().addInclude(cdcInclude);

        /*
         * OMB Race and OMB Ethnicity ValueSets support null flavors
         * in the current implementation.
         */
        if (includeNullFlavor && !nullFlavorMap.isEmpty()) {

            ValueSet.ConceptSetComponent nullFlavorInclude =
                    new ValueSet.ConceptSetComponent();

            nullFlavorInclude.setSystem(NULL_FLAVOR_SYSTEM);

            for (Map.Entry<String, String> entry : nullFlavorMap.entrySet()) {

                String code = entry.getKey();
                String display = entry.getValue();

                expansion.addContains()
                        .setSystem(NULL_FLAVOR_SYSTEM)
                        .setCode(code)
                        .setDisplay(display);

                nullFlavorInclude.addConcept()
                        .setCode(code)
                        .setDisplay(display);
            }

            valueSet.getCompose().addInclude(nullFlavorInclude);
        }

        return valueSet;
    }

    /**
     * Removes the |version portion from a canonical URL.
     *
     * Example:
     *
     * http://hl7.org/fhir/us/core/ValueSet/omb-race-category|7.0.0
     *
     * becomes:
     *
     * http://hl7.org/fhir/us/core/ValueSet/omb-race-category
     */
    private String getBaseUrl(String url) {

        if (url == null) {
            return null;
        }

        int pipeIndex = url.indexOf('|');

        if (pipeIndex >= 0) {
            return url.substring(0, pipeIndex);
        }

        return url;
    }

    private boolean isTargetValueSet(String url) {

        String baseUrl = getBaseUrl(url);

        if (baseUrl == null) {
            return false;
        }

        return OMB_RACE_VS_URL.equalsIgnoreCase(baseUrl)
                || OMB_ETHNICITY_VS_URL.equalsIgnoreCase(baseUrl)
                || DETAILED_RACE_VS_URL.equalsIgnoreCase(baseUrl);
    }

    private ValueSet getMatchingValueSet(String url) {

        String baseUrl = getBaseUrl(url);

        if (baseUrl == null) {
            return null;
        }

        if (OMB_RACE_VS_URL.equalsIgnoreCase(baseUrl)) {
            return ombRaceValueSet;
        }

        if (OMB_ETHNICITY_VS_URL.equalsIgnoreCase(baseUrl)) {
            return ombEthnicityValueSet;
        }

        if (DETAILED_RACE_VS_URL.equalsIgnoreCase(baseUrl)) {
            return detailedRaceValueSet;
        }

        return null;
    }

    @Override
    public FhirContext getFhirContext() {
        return fhirContext;
    }

    @Override
    public IBaseResource fetchCodeSystem(String theSystem) {

        if (theSystem == null) {
            return null;
        }

        if (CDC_RACE_ETHNICITY_SYSTEM.equalsIgnoreCase(theSystem)) {
            return codeSystemResource;
        }

        return null;
    }

    @Override
    public boolean isCodeSystemSupported(
            ValidationSupportContext theValidationSupportContext,
            String theSystem) {

        return theSystem != null
                && CDC_RACE_ETHNICITY_SYSTEM.equalsIgnoreCase(theSystem);
    }

    @Override
    public ValueSetExpansionOutcome expandValueSet(
            ValidationSupportContext theValidationSupportContext,
            ValueSetExpansionOptions theExpansionOptions,
            IBaseResource theValueSetToExpand) {

        if (!(theValueSetToExpand instanceof ValueSet)) {
            return null;
        }

        ValueSet valueSet = (ValueSet) theValueSetToExpand;

        if (!valueSet.hasUrl()) {
            return null;
        }

        if (!isTargetValueSet(valueSet.getUrl())) {
            return null;
        }

        ValueSet matchingValueSet =
                getMatchingValueSet(valueSet.getUrl());

        if (matchingValueSet == null) {
            return null;
        }

        /*
         * Return a copy so the caller cannot accidentally modify
         * our cached ValueSet.
         */
        ValueSet expandedValueSet =
                matchingValueSet.copy();

        return new ValueSetExpansionOutcome(expandedValueSet);
    }

    @Override
    public ValueSet fetchValueSet(String theUrl) {
        return getMatchingValueSet(theUrl);
    }

    @Override
    public boolean isValueSetSupported(
            ValidationSupportContext theValidationSupportContext,
            String theValueSetUrl) {

        return isTargetValueSet(theValueSetUrl);
    }

    @Override
    public CodeValidationResult validateCode(
            ValidationSupportContext theValidationSupportContext,
            ConceptValidationOptions theOptions,
            String theCodeSystem,
            String theCode,
            String theDisplay,
            String theValueSetUrl) {

        if (theCode == null || theCode.isBlank()) {
            return null;
        }

        /*
         * ValueSet-specific validation.
         */
        if (theValueSetUrl != null && isTargetValueSet(theValueSetUrl)) {

            String baseUrl = getBaseUrl(theValueSetUrl);

            if (OMB_RACE_VS_URL.equalsIgnoreCase(baseUrl)) {

                if (ombRaceMap.containsKey(theCode)
                        || nullFlavorMap.containsKey(theCode)) {

                    return new CodeValidationResult()
                            .setCode(theCode);
                }

                return new CodeValidationResult()
                        .setSeverity(IssueSeverity.ERROR)
                        .setMessage(
                                "Code " + theCode
                                        + " was not found in "
                                        + OMB_RACE_VS_URL);
            }

            if (OMB_ETHNICITY_VS_URL.equalsIgnoreCase(baseUrl)) {

                if (ombEthnicityMap.containsKey(theCode)
                        || nullFlavorMap.containsKey(theCode)) {

                    return new CodeValidationResult()
                            .setCode(theCode);
                }

                return new CodeValidationResult()
                        .setSeverity(IssueSeverity.ERROR)
                        .setMessage(
                                "Code " + theCode
                                        + " was not found in "
                                        + OMB_ETHNICITY_VS_URL);
            }

            if (DETAILED_RACE_VS_URL.equalsIgnoreCase(baseUrl)) {

                if (detailedRaceMap.containsKey(theCode)
                        || nullFlavorMap.containsKey(theCode)) {

                    return new CodeValidationResult()
                            .setCode(theCode);
                }

                return new CodeValidationResult()
                        .setSeverity(IssueSeverity.ERROR)
                        .setMessage(
                                "Code " + theCode
                                        + " was not found in "
                                        + DETAILED_RACE_VS_URL);
            }
        }

        /*
         * Direct CodeSystem validation.
         */
        if (CDC_RACE_ETHNICITY_SYSTEM.equalsIgnoreCase(theCodeSystem)) {

            if (allCdcConcepts.containsKey(theCode)
                    || nullFlavorMap.containsKey(theCode)) {

                return new CodeValidationResult()
                        .setCode(theCode);
            }

            return new CodeValidationResult()
                    .setSeverity(IssueSeverity.ERROR)
                    .setMessage(
                            "Unknown code " + theCode
                                    + " for system "
                                    + CDC_RACE_ETHNICITY_SYSTEM);
        }

        /*
         * This support does not handle other CodeSystems.
         */
        return null;
    }
}