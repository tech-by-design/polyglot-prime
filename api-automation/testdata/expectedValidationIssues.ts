export const expectedValidationIssues = {
    testcase1: [
        {
            severity: "error",
            messageContains:
                "Constraint failed: SHINNY-Bundle-Encounter-RI: 'Checks for RI between all resources & Encounter'"
        },
        {
            severity: "error",
            messageContains:
                "Constraint failed: SHINNY-Bundle-Obs-Patient-RI: 'Checks for RI between Obs & Patient'"
        },
        {
            severity: "error",
            messageContains:
                "Constraint failed: SHINNY-Bundle-Obs-Encounter-RI: 'Checks for RI between Screening Obs & Encounter'"
        }
    ],

    testcase2: [
        {
            severity: "error",
            messageContains: "Constraint failed: SHINNY-Resources-Profile-Check: 'SHINNY-Resources-Profile-Check'"
        },
        {
            severity: "error",
            messageContains: "Profile reference 'http://test/StructureDefinition/shinny-patient' has not been checked because it could not be found"
        }
    ],

    testcase3: [
        {
            severity: "error",
            messageContains: "Constraint failed: SHINNY-Screening-Consent: 'All screening bundles must include a consent'"
        }
    ],

    testcase4: [
        {
            severity: "error",
            messageContains: "Constraint failed: SHINNY-Patient-MRN: 'Every bundle should always have only 1 MR (i.e MRN) Object within patient.identifier, system.contains('facility') and value (MRN) is provided'"
        }
    ],

    testcase5: [
        {
            severity: "error",
            messageContains: "Constraint failed: SHINNY-Bundle-Required-Resources: 'Every bundle should always have one and only one 'Patient' & 'Encounter' and atleast one Org'"
        }
    ],

    testcase6: [
        {
            severity: "error",
            messageContains: "Constraint failed: SHINNY-Bundle-Required-Resources: 'Every bundle should always have one and only one 'Patient' & 'Encounter' and atleast one Org'"
        }
    ],

    testcase7: [
        {
            severity: "error",
            messageContains: "Constraint failed: Observation-PerformerOrganizationScreening: 'All grouper screening observations must include a performer that references an organization.'"
        }
    ],

    testcase8: [
        {
            severity: "error",
            messageContains: "Constraint failed: Observation-PerformerOrganizationScreening: 'All grouper screening observations must include a performer that references an organization."
        }
    ],

    testcase9: [
        {
            severity: "error",
            messageContains: "Constraint failed: SHINNY-Obs-Category: 'Ensure that all Observation categories have an SDOH code'"
        },
        {
            severity: "error",
            messageContains: "Observation.category: minimum required = 3, but only found 2"
        }
    ],

    testcase10: [
        {
            severity: "error",
            messageContains: "Observation.encounter: minimum required = 1, but only found 0 "
        }
    ],

    testcase11: [
        {
            severity: "error",
            messageContains: "Observation.subject: minimum required = 1, but only found 0"
        }
    ],

    testcase12: [
        {
            severity: "error",
            messageContains: "Constraint failed: Organization-ID: 'Ensure that all Organization resources include a value for one of the following id types: NPI, TAX, or MA.'"
        }
    ],

    testcase13: [
        {
            severity: "fatal",
            messageContains: "[element=\"birthDate\"] Invalid attribute value \"16-07-1981\": Invalid date/time format: \"16-07-1981\""
        }
    ],

    testcase14: [
        {
            severity: "error",
            messageContains: "Patient.gender: minimum required = 1, but only found 0"
        }
    ],

    testcase15: [
        {
            severity: "error",
            messageContains: "Patient.identifier:CMS: max allowed = 1, but found 2"
        },
        {
            severity: "error",
            messageContains: "Every bundle should always have only 1 Medicaid CIN (i.e MA) Object within patient.identifier AND the CIN is always in the format of 2 letters, 5 numbers, and 1 letter"
        }
    ],

    testcase16: [
        {
            severity: "error",
            messageContains: "Every bundle should always have only 1 Medicaid CIN (i.e MA) Object within patient.identifier AND the CIN is always in the format of 2 letters, 5 numbers, and 1 letter"
        }
    ],

    testcase17: [
        {
            severity: "error",
            messageContains: "Patient.identifier:MR: max allowed = 1, but found 2"
        },
        {
            severity: "error",
            messageContains: "Constraint failed: SHINNY-Patient-MRN: 'Every bundle should always have only 1 MR (i.e MRN) Object within patient.identifier, system.contains('facility') and value (MRN) is provided'"
        }
    ],

    testcase18: [
        {
            severity: "error",
            messageContains: ""
        }
    ],

    testcase19: [
        {
            severity: "error",
            messageContains: "Constraint failed: SHINNY-Bundle-Obs-derivedFrom-RI: 'Checks for RI between Observation & child Observation'"
        }
    ],

    testcase20: [
        {
            severity: "error",
            messageContains: "Constraint failed: SHINNY-Bundle-Obs-hasMember-RI: 'Checks for RI between Observation & child Observations'"
        }
    ],

    testcase21: [
        {
            severity: "error",
            messageContains: "Constraint failed: SHINNY-Bundle-Obs-Patient-RI: 'Checks for RI between Obs & Patient'"
        }
    ],

    testcase22: [
        {
            severity: "error",
            messageContains: "Constraint failed: SHINNYScreeningHousingComponent: 'All housing adequacy questions with code 96778-6, with a response value, require values as components.'"
        }
    ],

    testcase23: [
        {
            severity: "error",
            messageContains: "All FHIR elements must have a @value or children"
        },
        {
            severity: "error",
            messageContains: "value cannot be empty"
        }
    ],

    testcase24: [
        {
            severity: "error",
            messageContains: "Constraint failed: Observation-PerformerOrganizationAssessment: 'All assessment observations must include a performer that references an organization.'"
        }
    ],

    testcase25: [
        {
            severity: "error",
            messageContains: "Constraint failed: Observation-PerformerOrganizationAssessment: 'All assessment observations must include a performer that references an organization.'"
        }
    ],

    testcase26: [
        {
            severity: "error",
            messageContains: "Constraint failed: SHINNY-Assessment-Encounter: 'Assessment-Encounter should exist'"
        }
    ],

    testcase27: [
        {
            severity: "error",
            messageContains: "Observation.subject: minimum required = 1, but only found 0"
        }
    ],

    testcase28: [
        {
            severity: "error",
            messageContains: "Constraint failed: SHINNYServiceRequestSDOHCategory: 'Ensure that all Service Request categories have an SDOH code'"
        }
    ],

    testcase29: [
        {
            severity: "error",
            messageContains: "Constraint failed: Task-StatusReason: 'Ensure that all tasks exists, have a statusReason If the Task.status is rejected, cancelled, completed, or failed.'"
        }
    ],

    testcase30: [
        {
            severity: "error",
            messageContains: "Constraint failed: obs-6: 'dataAbsentReason SHALL only be present if Observation.value[x] is not present'"
        }
    ],
    testcase31: [
        {
            severity: "error",
            messageContains: "Bundle.id: minimum required = 1, but only found 0"
        },
        // {
        //     severity: "error",
        //     messageContains: "Bundle.type: minimum required = 1, but only found 0"
        // },
        {
            severity: "error",
            messageContains: "Meta.lastUpdated: minimum required = 1, but only found 0"
        }
    ],
    testcase32: [
        {
            severity: "error",
            messageContains: "Patient.id: minimum required = 1, but only found 0"
        },
        {
            severity: "error",
            messageContains: "Patient.name: minimum required = 1, but only found 0"
        },
        {
            severity: "error",
            messageContains: "Meta.lastUpdated: minimum required = 1, but only found 0"
        },
        {
            severity: "error",
            messageContains: "Patient.identifier:MR.value: minimum required = 1"
        },
        {
            severity: "error",
            messageContains: "Patient.identifier.value: minimum required = 1, but only found 0"
        },
        {
            severity: "error",
            messageContains: "Constraint failed: bdl-3: 'entry.request mandatory for batch/transaction/history, otherwise prohibited'"
        }
    ],
    testcase33: [
        {
            severity: "error",
            messageContains: "Organization.active: minimum required = 1, but only found 0 "
        },
        {
            severity: "error",
            messageContains: "Organization.name: minimum required = 1, but only found 0 "
        },
        {
            severity: "error",
            messageContains: "Constraint failed: SHINNY-Bundle-Patient-Org-RI: 'Checks for RI between Patient & Assigning Org'"
        },
        {
            severity: "error",
            messageContains: "The fullUrl 'http://test.shinny.org/us/ny/hrsn/Organization/OrganizationExampleOther-SCN1' looks like a RESTful server URL, but the resource has no id"
        },
        {
            severity: "error",
            messageContains: "Constraint failed: bdl-3: 'entry.request mandatory for batch/transaction/history, otherwise prohibited'"
        }

    ],
    testcase34: [
        {
            severity: "error",
            messageContains: "Consent.id: minimum required = 1, but only found 0"
        },
        {
            severity: "error",
            messageContains: "Meta.lastUpdated: minimum required = 1, but only found 0"
        },
        {
            severity: "error",
            messageContains: "Consent.status: minimum required = 1, but only found 0"
        },
        {
            severity: "error",
            messageContains: "Consent.scope: minimum required = 1, but only found 0"
        },
        {
            severity: "error",
            messageContains: "Consent.category: minimum required = 1, but only found 0 "
        },
        {
            severity: "error",
            messageContains: "Consent.patient: minimum required = 1, but only found 0"
        },
        {
            severity: "error",
            messageContains: "Consent.dateTime: minimum required = 1, but only found 0"
        },
        {
            severity: "error",
            messageContains: "Consent.organization: minimum required = 1, but only found 0"
        },
        {
            severity: "error",
            messageContains: "Consent.source[x]: minimum required = 1, but only found 0"
        },
        {
            severity: "error",
            messageContains: "Consent.provision: minimum required = 1, but only found 0"
        },
        {
            severity: "error",
            messageContains: "Constraint failed: bdl-3: 'entry.request mandatory for batch/transaction/history, otherwise prohibited'"
        },
        {
            severity: "error",
            messageContains: "The fullUrl 'http://test.shinny.org/us/ny/hrsn/Consent/ConsentExample' looks like a RESTful server URL, but the resource has no id"
        }

    ],
    testcase35: [
        {
            severity: "error",
            messageContains: "Encounter.status: minimum required = 1, but only found 0"
        },
        {
            severity: "error",
            messageContains: "Encounter.class: minimum required = 1, but only found 0"
        },
        {
            severity: "error",
            messageContains: "Encounter.id: minimum required = 1, but only found 0"
        },
        {
            severity: "error",
            messageContains: "Encounter.type: minimum required = 1, but only found 0"
        },
        {
            severity: "error",
            messageContains: "Encounter.subject: minimum required = 1, but only found 0"
        },
        {
            severity: "error",
            messageContains: "Meta.lastUpdated: minimum required = 1, but only found 0 "
        },
        {
            severity: "error",
            messageContains: "Constraint failed: bdl-3: 'entry.request mandatory for batch/transaction/history, otherwise prohibited'"
        },
        {
            severity: "error",
            messageContains: "Constraint failed: SHINNY-Bundle-Encounter-RI: 'Checks for RI between all resources & Encounter'"
        },
        {
            severity: "error",
            messageContains: "Constraint failed: SHINNY-Bundle-Obs-Patient-RI: 'Checks for RI between Obs & Patient'"
        },
        {
            severity: "error",
            messageContains: "Constraint failed: SHINNY-Bundle-Obs-Encounter-RI: 'Checks for RI between Screening Obs & Encounter'"
        },
        {
            severity: "error",
            messageContains: "The fullUrl 'http://test.shinny.org/us/ny/hrsn/Encounter/EncounterExample' looks like a RESTful server URL, but the resource has no id"
        }

    ],
    testcase36: [
        {
            severity: "error",
            messageContains: "Observation.status: minimum required = 1, but only found 0"
        },
        {
            severity: "error",
            messageContains: "Observation.code: minimum required = 1, but only found 0"
        },
        {
            severity: "error",
            messageContains: "Observation.id: minimum required = 1, but only found 0"
        },
        {
            severity: "error",
            messageContains: "Observation.category: minimum required = 3, but only found 0"
        },
        {
            severity: "error",
            messageContains: "Observation.subject: minimum required = 1, but only found 0"
        },
        {
            severity: "error",
            messageContains: "Observation.encounter: minimum required = 1, but only found 0"
        },
        {
            severity: "error",
            messageContains: "Observation.effective[x]: minimum required = 1, but only found 0"
        },
        {
            severity: "error",
            messageContains: "Meta.lastUpdated: minimum required = 1, but only found 0"
        },
        {
            severity: "error",
            messageContains: "Constraint failed: bdl-3: 'entry.request mandatory for batch/transaction/history, otherwise prohibited'"
        },
        {
            severity: "error",
            messageContains: "Constraint failed: SHINNY-Bundle-Obs-hasMember-RI: 'Checks for RI between Observation & child Observations'"
        },
        {
            severity: "error",
            messageContains: "Constraint failed: SHINNY-Obs-Category: 'Ensure that all Observation categories have an SDOH code' "
        },
        {
            severity: "error",
            messageContains: "The fullUrl 'http://test.shinny.org/us/ny/hrsn/Observation/ObservationResponseHousingInstability71802-3' looks like a RESTful server URL, but the resource has no id"
        }

    ],
    testcase37: [
        {
            severity: "error",
            messageContains: "The property telecom must be a JSON Array, not an Object "
        },
        {
            severity: "error",
            messageContains: "The property address must be a JSON Array, not an Object"
        },
        {
            severity: "error",
            messageContains: "The property location must be a JSON Array, not an Object "
        },
        {
            severity: "error",
            messageContains: "The property performer must be a JSON Array, not an Object"
        },
        {
            severity: "error",
            messageContains: "The property interpretation must be a JSON Array, not an Object"
        },
        {
            severity: "error",
            messageContains: "The property name must be a JSON Array, not an Object"
        },
        {
            severity: "error",
            messageContains: "The property contact must be a JSON Array, not an Object"
        },
        {
            severity: "error",
            messageContains: "The property communication must be a JSON Array, not an Object"
        }

    ],
    testcase38: [
        {
            severity: "error",
            messageContains: "The property scope must be an Object, not an Array"
        },
        {
            severity: "error",
            messageContains: "The property address must be an Object, not an Array"
        },
        {
            severity: "error",
            messageContains: "The property managingOrganization must be an Object, not an Array"
        },
        {
            severity: "error",
            messageContains: "The property subject must be an Object, not an Array"
        },
        {
            severity: "error",
            messageContains: "The property encounter must be an Object, not an Array"
        },
        {
            severity: "error",
            messageContains: "The property hasMember must be an Array, not an Array"
        },

    ],
    testcase39: [
        {
            severity: "error",
            messageContains: "Unrecognized property 'dateTime'"
        }
    ],
    testcase40: [
        {
            severity: "fatal",
            messageContains: "HAPI-1821: [element=\"lastUpdated\"] Invalid attribute value \"2024-02-2300:00:00Z\": Invalid date/time format: \"2024-02-2300:00:00Z\": Expected character 'T' at index 10 but found 0"
        }

    ],
    testcase41: [
        {
            severity: "error",
            messageContains: "TECHBD-1002: Bundle profile url must be provided"
        },
    ],
    testcase42: [
        {
            severity: "error",
            messageContains: "TECHBD-1000: Invalid or Partial JSON"
        },
    ],
};

