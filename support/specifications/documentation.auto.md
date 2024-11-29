# `csv-validation-using-ig`
 `description` Each field description outlines the FHIR resource paths that map to the corresponding CSV fields. It specifies the logical extraction path within a FHIR Bundle to locate the relevant data, ensuring clarity and consistency when deriving data fields from the source FHIR resources. For example: 

  - PATIENT_MR_ID_VALUE: Extracted from Bundle.entry.resource where resourceType = 'Patient', identifier where type.coding.code = 'MR', and value. 
  - FACILITY_ACTIVE: Extracted from Bundle.entry.resource where resourceType = 'Organization' and active.
## `qe_admin_data`
  - `path` flat-file/nyher-fhir-ig-example/QE_ADMIN_DATA_partner1-test-20241128-testcase1.csv
  - `schema`
      - `primaryKey` ['PATIENT_MR_ID_VALUE']
    - `foreignKeys` []
### `PATIENT_MR_ID_VALUE`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').identifier.where(type.coding.code = 'MR').value
  - `type` string
  - `constraints`:
    - `required` True
    - `unique` True
### `FACILITY_ACTIVE`
  - `description` Bundle.entry.resource.where(resourceType ='Organization').active
  - `type` string
### `FACILITY_ID`
  - `description` Append to the Bundle.entry.resource.where(resourceType ='Patient').identifier.where(type.coding.code = 'MR').system URI
  - `type` string
  - `constraints`:
    - `required` True
### `FACILITY_NAME`
  - `description` Bundle.entry.resource.where(resourceType ='Organization').name
  - `type` string
  - `constraints`:
    - `required` True
### `ORGANIZATION_TYPE_DISPLAY`
  - `description` Bundle.entry.resource.where(resourceType ='Organization').type.coding.display
  - `type` string
  - `constraints`:
    - `pattern` `^(prov|dept|team|govt|ins|pay|edu|reli|crs|cg|bus|other)(,\s*(prov|dept|team|govt|ins|pay|edu|reli|crs|cg|bus|other))*$`
### `ORGANIZATION_TYPE_CODE`
  - `description` Bundle.entry.resource.where(resourceType ='Organization').type.coding.code
  - `type` string
  - `constraints`:
    - `pattern` `^(prov|dept|team|govt|ins|pay|edu|reli|crs|cg|bus|other)(,\s*(prov|dept|team|govt|ins|pay|edu|reli|crs|cg|bus|other))*$`
### `ORGANIZATION_TYPE_SYSTEM`
  - `description` Bundle.entry.resource.where(resourceType ='Organization').type.coding.system
  - `type` string
  - `constraints`:
    - `enum` ['http://terminology.hl7.org/CodeSystem/organization-type']
### `FACILITY_ADDRESS1`
  - `description` Bundle.entry.resource.where(resourceType ='Organization').address.text & Bundle.entry.resource.where(resourceType ='Organization').address.line
  - `type` string
  - `constraints`:
    - `required` True
### `FACILITY_ADDRESS2`
  - `description` Bundle.entry.resource.where(resourceType ='Organization').address.text & Bundle.entry.resource.where(resourceType ='Organization').address.line
  - `type` string
### `FACILITY_CITY`
  - `description` Bundle.entry.resource.where(resourceType ='Organization').address.city & Bundle.entry.resource.where(resourceType ='Organization').address.text
  - `type` string
### `FACILITY_STATE`
  - `description` Bundle.entry.resource.where(resourceType ='Organization').address.state & Bundle.entry.resource.where(resourceType ='Organization').address.text
  - `type` string
  - `constraints`:
    - `enum` ['ak', 'al', 'ar', 'as', 'az', 'ca', 'co', 'ct', 'dc', 'de', 'fl', 'fm', 'ga', 'gu', 'hi', 'ia', 'id', 'il', 'in', 'ks', 'ky', 'la', 'ma', 'md', 'me', 'mh', 'mi', 'mn', 'mo', 'mp', 'ms', 'mt', 'nc', 'nd', 'ne', 'nh', 'nj', 'nm', 'nv', 'ny', 'oh', 'ok', 'or', 'pa', 'pr', 'pw', 'ri', 'sc', 'sd', 'tn', 'tx', 'ut', 'va', 'vi', 'vt', 'wa', 'wi', 'wv', 'wy']
### `FACILITY_DISTRICT`
  - `description` Bundle.entry.resource.where(resourceType ='Organization').address.district
  - `type` string
### `FACILITY_ZIP`
  - `description` Bundle.entry.resource.where(resourceType ='Organization').address.postalCode & Bundle.entry.resource.where(resourceType ='Organization').address.text
  - `type` string
### `FACILITY_LAST_UPDATED`
  - `description` Bundle.entry.resource.where(resourceType ='Organization').meta.lastUpdated
  - `type` string
  - `constraints`:
    - `pattern` `^([0-9]([0-9]([0-9][1-9]|[1-9]0)|[1-9]00)|[1-9]000)-(0[1-9]|1[0-2])-(0[1-9]|[1-2][0-9]|3[0-1])T([01][0-9]|2[0-3]):[0-5][0-9]:([0-5][0-9]|60)(\.[0-9]+)?(Z|(\+|-)((0[0-9]|1[0-3]):[0-5][0-9]|14:00))$`
### `FACILITY_PROFILE`
  - `description` Bundle.entry.resource.where(resourceType ='Organization').meta.profile
  - `type` string
  - `constraints`:
    - `enum` ['http://shinny.org/us/ny/hrsn/StructureDefinition/shin-ny-organization']
### `FACILITY_SCN_IDENTIFIER_TYPE_DISPLAY`
  - `description` Bundle.entry.resource.where(resourceType ='Organization').identifier.where(system = 'http://www.scn.ny.gov/').type.coding.display
  - `type` string
### `FACILITY_SCN_IDENTIFIER_TYPE_VALUE`
  - `description` Bundle.entry.resource.where(resourceType ='Organization').identifier.where(system = 'http://www.scn.ny.gov/').value
  - `type` string
### `FACILITY_SCN_IDENTIFIER_TYPE_SYSTEM`
  - `description` Bundle.entry.resource.where(resourceType ='Organization').identifier.where(system = 'http://www.scn.ny.gov/').system
  - `type` string
  - `constraints`:
    - `pattern` `^http://www\.scn\.gov/.+`
### `FACILITY_NPI_IDENTIFIER_TYPE_DISPLAY`
  - `description` Bundle.entry.resource.where(resourceType ='Organization').identifier.where(system = 'http://hl7.org/fhir/sid/us-npi').type.coding.display
  - `type` string
### `FACILITY_NPI_IDENTIFIER_TYPE_VALUE`
  - `description` Bundle.entry.resource.where(resourceType ='Organization').identifier.where(system = 'http://hl7.org/fhir/sid/us-npi').value
  - `type` string
### `FACILITY_NPI_IDENTIFIER_TYPE_SYSTEM`
  - `description` Bundle.entry.resource.where(resourceType ='Organization').identifier.where(system = 'http://hl7.org/fhir/sid/us-npi').system
  - `type` string
  - `constraints`:
    - `pattern` `^http://hl7.org/fhir/sid/us-npi/.+`
### `FACILITY_CMS_IDENTIFIER_TYPE_DISPLAY`
  - `description` Bundle.entry.resource.where(resourceType ='Organization').identifier.where(system = 'http://www.medicaid.gov').type.coding.display
  - `type` string
### `FACILITY_CMS_IDENTIFIER_TYPE_VALUE`
  - `description` Bundle.entry.resource.where(resourceType ='Organization').identifier.where(system = 'http://www.medicaid.gov').value
  - `type` string
### `FACILITY_CMS_IDENTIFIER_TYPE_SYSTEM`
  - `description` Bundle.entry.resource.where(resourceType ='Organization').identifier.where(system = 'http://www.medicaid.gov').system
  - `type` string
  - `constraints`:
    - `pattern` `^http://www.medicaid.gov/.+`
### `FACILITY_TEXT_STATUS`
  - `description` Bundle.entry.resource.where(resourceType ='Organization').text.status
  - `type` string
## `screening_observation_data`
  - `path` flat-file/nyher-fhir-ig-example/SCREENING_OBSERVATION_DATA_partner1-test-20241128-testcase1.csv
  - `schema`
      - `foreignKeys`
      - [1]
        - `fields` ['PATIENT_MR_ID_VALUE']
        - `reference`
          - `resource` qe_admin_data
          - `fields` ['PATIENT_MR_ID_VALUE']
### `PATIENT_MR_ID_VALUE`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').identifier.where(type.coding.code = 'MR').value
  - `type` string
  - `constraints`:
    - `required` True
### `SCREENING_CODE`
  - `description` Bundle.entry.resource.where(resourceType ='Observation' and hasMember.exists()).code.coding.code
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['96777-8', '97023-6']
### `SCREENING_CODE_DESCRIPTION`
  - `description` Bundle.entry.resource.where(resourceType ='Observation' and hasMember.exists()).code.coding.display
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['accountable health communities (ahc) health-related social needs screening (hrsn) tool', 'accountable health communities (ahc) health-related social needs (hrsn) supplemental questions']
### `RECORDED_TIME`
  - `description` Bundle.entry.resource.where(resourceType ='Observation').effectiveDateTime
  - `type` string
  - `constraints`:
    - `required` True
    - `pattern` `([0-9]{4})-(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])T([01][0-9]|2[0-3]):([0-5][0-9]):([0-5][0-9]|60)(\.[0-9]+)?(Z|(\+|-)([01][0-9]|2[0-3]):([0-5][0-9]))`
### `QUESTION_CODE`
  - `description` Bundle.entry.resource.where(resourceType ='Observation' and not(hasMember.exists())).code.coding.code
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['96777-8', '71802-3', '96778-6', '88122-7', '88123-5', '93030-5', '96779-4', '95618-5', '95617-7', '95616-9', '95615-1', '95614-4', '76513-1', '96780-2', '96781-0', '93159-2', '97027-7', '96782-8', '89555-7', '68516-4', '68517-2', '96842-0', '95530-2', '68524-8', '44250-9', '44255-8', '93038-8', '69858-9', '69861-3', '77594-0', '71969-0']
### `QUESTION_CODE_DISPLAY`
  - `description` Bundle.entry.resource.where(resourceType ='Observation' and not(hasMember.exists())).code.coding.display
  - `type` string
  - `constraints`:
    - `required` True
### `QUESTION_CODE_TEXT`
  - `description` Bundle.entry.resource.where(resourceType ='Observation' and not(hasMember.exists())).code.text
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['what is your living situation today?', 'think about the place you live. do you have problems with any of the following?', 'within the past 12 months, you worried that your food would run out before you got money to buy more.', "within the past 12 months, the food you bought just didn't last and you didn't have money to get more.", 'in the past 12 months, has lack of reliable transportation kept you from medical appointments, meetings, work or from getting things needed for daily living?', 'in the past 12 months has the electric, gas, oil, or water company threatened to shut off services in your home?', 'how often does anyone, including family and friends, physically hurt you?', 'how often does anyone, including family and friends, insult or talk down to you?', 'how often does anyone, including family and friends, threaten you with harm?', 'how often does anyone, including family and friends, scream or curse at you?', 'total safety score', 'how hard is it for you to pay for the very basics like food, housing, medical care, and heating? would you say it is', 'do you want help finding or keeping work or a job?', 'if for any reason you need help with day-to-day activities such as bathing, preparing meals, shopping, managing finances, etc., do you get the help you need?', 'how often do you feel lonely or isolated from those around you?', 'do you speak a language other than english at home?', 'do you want help with school or training? for example, starting or completing job training or getting a high school diploma, ged or equivalent.', 'in the last 30 days, other than the activities you did for work, on average, how many days per week did you engage in moderate exercise (like walking fast, running, jogging, dancing, swimming, biking, or other similar activities)', 'on average, how many minutes did you usually spend exercising at this level on one of those days?', 'how many times in the past 12 months have you had 5 or more drinks in a day (males) or 4 or more drinks in a day (females)?', 'how often have you used any tobacco product in past 12 months?', 'how many times in the past year have you used prescription drugs for non-medical reasons?', 'how many times in the past year have you used illegal drugs?', 'little interest or pleasure in doing things?', 'feeling down, depressed, or hopeless?', 'stress means a situation in which a person feels tense, restless, nervous, or anxious, or is unable to sleep at night because his or her mind is troubled all the time. do you feel this kind of stress these days?', 'because of a physical, mental, or emotional condition, do you have serious difficulty concentrating, remembering, or making decisions?', "because of a physical, mental, or emotional condition, do you have difficulty doing errands alone such as visiting a physician's office or shopping", 'calculated weekly physical activity', 'promis-10 global mental health (gmh) score t-score']
### `OBSERVATION_CATEGORY_SDOH_TEXT`
  - `description` Bundle.entry.resource.where(resourceType ='Observation').category.where(coding.system = 'http://hl7.org/fhir/us/sdoh-clinicalcare/CodeSystem/SDOHCC-CodeSystemTemporaryCodes').text
  - `type` string
### `OBSERVATION_CATEGORY_SDOH_CODE`
  - `description` Bundle.entry.resource.where(resourceType ='Observation').category.where(coding.system = 'http://hl7.org/fhir/us/sdoh-clinicalcare/CodeSystem/SDOHCC-CodeSystemTemporaryCodes').coding.code
  - `type` string
  - `constraints`:
    - `enum` ['sdoh-category-unspecified', 'food-insecurity', 'housing-instability', 'homelessness', 'inadequate-housing', 'transportation-insecurity', 'financial-insecurity', 'material-hardship', 'educational-attainment', 'employment-status', 'veteran-status', 'stress', 'social-connection', 'intimate-partner-violence', 'elder-abuse', 'personal-health-literacy', 'health-insurance-coverage-status', 'medical-cost-burden', 'digital-literacy', 'digital-access', 'utility-insecurity', 'resulting-activity', 'sdoh-condition-category', 'payer-coverage', 'general-information', 'make-contact', 'review-material', 'risk-questionnaire', 'feedback-questionnaire', 'application-questionnaire', 'personal-characteristics-questionnaire', 'contact-entity', 'general-information-response', 'questionnaire-category', 'questionnaire-pdf', 'questionnaire-url', 'questionnaire-pdf-completed', 'contacting-subject-prohibited', 'self-reported', 'reported-by-related-person', 'observed', 'administrative', 'derived-specify', 'other-specify', 'personal-characteristic', 'chosen-contact']
### `OBSERVATION_CATEGORY_SDOH_DISPLAY`
  - `description` Bundle.entry.resource.where(resourceType ='Observation').category.where(coding.system = 'http://hl7.org/fhir/us/sdoh-clinicalcare/CodeSystem/SDOHCC-CodeSystemTemporaryCodes').coding.display
  - `type` string
  - `constraints`:
    - `enum` ['sdoh category unspecified', 'food insecurity', 'housing instability', 'homelessness', 'inadequate housing', 'transportation insecurity', 'financial insecurity', 'material hardship', 'educational attainment', 'employment status', 'veteran status', 'stress', 'social connection', 'intimate partner violence', 'elder abuse', 'personal health literacy', 'health insurance coverage status', 'medical cost burden', 'digital literacy', 'digital access', 'utility insecurity', 'resulting activity', 'current condition category from sdoh category', 'coverage by payer organization', 'general information', 'make contact', 'review material', 'risk questionnaire', 'feedback questionnaire', 'application questionnaire', 'personal characteristics questionnaire', 'contact entity', 'general information response', 'questionnaire category', 'questionnaire pdf', 'questionnaire url', 'questionnaire pdf completed', 'contacting subject prohibited', 'self reported', 'reported by related person', 'observed', 'administrative', 'derived specify', 'other specify', 'personal characteristic', 'chosen contact']
### `OBSERVATION_CATEGORY_SNOMED_CODE`
  - `description` Bundle.entry.resource.where(resourceType ='Observation').category.coding.code
  - `type` string
### `OBSERVATION_CATEGORY_SNOMED_DISPLAY`
  - `description` Bundle.entry.resource.where(resourceType ='Observation').category.coding.display
  - `type` string
### `ANSWER_CODE`
  - `description` Bundle.entry.resource.where(resourceType ='Observation' and not(hasMember.exists())).valueCodeableConcept.coding.code
  - `type` string
  - `constraints`:
    - `enum` ['la31993-1', 'la31994-9', 'la31995-6', 'la31996-4', 'la28580-1', 'la31997-2', 'la31998-0', 'la31999-8', 'la32000-4', 'la32001-2', 'la9-3', 'la28397-0', 'la6729-3', 'la28398-8', 'la28397-0', 'la6729-3', 'la28398-8', 'la33-6', 'la32-8', 'la33-6', 'la32-8', 'la32002-0', 'la6270-8', 'la10066-1', 'la10082-8', 'la16644-9', 'la6482-9', 'la6270-8', 'la10066-1', 'la10082-8', 'la16644-9', 'la6482-9', 'la6270-8', 'la10066-1', 'la10082-8', 'la16644-9', 'la6482-9', 'la6270-8', 'la10066-1', 'la10082-8', 'la16644-9', 'la6482-9', 'la15832-1', 'la22683-9', 'la31980-8', 'la31981-6', 'la31982-4', 'la31983-2', 'la31976-6', 'la31977-4', 'la31978-2', 'la31979-0', 'la6270-8', 'la10066-1', 'la10082-8', 'la10044-8', 'la9933-8', 'la33-6', 'la32-8', 'la33-6', 'la32-8', 'la6111-4', 'la6112-2', 'la6113-0', 'la6114-8', 'la6115-5', 'la10137-0', 'la10138-8', 'la10139-6', 'la6111-4', 'la13942-0', 'la19282-5', 'la28855-7', 'la28858-1', 'la28854-0', 'la28853-2', 'la28891-2', 'la32059-0', 'la32060-8', 'la6270-8', 'la26460-8', 'la18876-5', 'la18891-4', 'la18934-2', 'la6270-8', 'la26460-8', 'la18876-5', 'la18891-4', 'la18934-2', 'la6270-8', 'la26460-8', 'la18876-5', 'la18891-4', 'la18934-2', 'la6270-8', 'la26460-8', 'la18876-5', 'la18891-4', 'la18934-2', 'la6568-5', 'la6569-3', 'la6570-1', 'la6571-9', 'la6568-5', 'la6569-3', 'la6570-1', 'la6571-9', 'la6568-5', 'la13863-8', 'la13909-9', 'la13902-4', 'la13914-9', 'la30122-8', 'la33-6', 'la32-8', 'la33-6', 'la32-8']
### `ANSWER_CODE_DESCRIPTION`
  - `description` Bundle.entry.resource.where(resourceType ='Observation' and not(hasMember.exists())).valueCodeableConcept.coding.display
  - `type` string
  - `constraints`:
    - `enum` ['i have a steady place to live', 'i have a place to live today, but i am worried about losing it in the future', 'i do not have a steady place to live (i am temporarily staying with others, in a hotel, in a shelter,living outside on the street, on a beach, in a car, abandoned building, bus or train station, or in a park)', 'pests such as bugs, ants, or mice', 'mold', 'lead paint or pipes', 'lack of heat', 'oven or stove not working', 'smoke detectors missing or not working', 'water leaks', 'none of the above', 'often true', 'sometimes true', 'never true', 'often true', 'sometimes true', 'never true', 'yes', 'no', 'yes', 'no', 'already shut off', 'never (1)', 'rarely (2)', 'sometimes (3)', 'fairly often (4)', 'frequently (5)', 'never (1)', 'rarely (2)', 'sometimes (3)', 'fairly often (4)', 'frequently (5)', 'never (1)', 'rarely (2)', 'sometimes (3)', 'fairly often (4)', 'frequently (5)', 'never (1)', 'rarely (2)', 'sometimes (3)', 'fairly often (4)', 'frequently (5)', 'very hard', 'somewhat hard', 'not hard at all', 'yes, help finding work', 'yes, help keeping work', 'i do not need or want help', "i don't need any help", 'i get all the help i need', 'i could use a little more help', 'i need a lot more help', 'never', 'rarely', 'sometimes', 'often', 'always', 'yes', 'no', 'yes', 'no', '0', '1', '2', '3', '4', '5', '6', '7', '0', '10', '20', '30', '40', '50', '60', '90', '120', '150 or greater', 'never', 'once or twice', 'monthly', 'weekly', 'daily or almost daily', 'never', 'once or twice', 'monthly', 'weekly', 'daily or almost daily', 'never', 'once or twice', 'monthly', 'weekly', 'daily or almost daily', 'never', 'once or twice', 'monthly', 'weekly', 'daily or almost daily', 'not at all (0)', 'several days (1)', 'more than half the days (2)', 'nearly every day (3)', 'not at all (0)', 'several days (1)', 'more than half the days (2)', 'nearly every day (3)', 'not at all', 'a little bit', 'somewhat', 'quite a bit', 'very much', 'i choose not to answer this question', 'yes', 'no', 'yes', 'no']
### `INTERPRETATION_CODE`
  - `description` Bundle.entry.resource.where(resourceType ='Observation').interpretation.coding.code
  - `type` string
### `INTERPRETATION_DISPLAY`
  - `description` Bundle.entry.resource.where(resourceType ='Observation').interpretation.coding.display
  - `type` string
### `DATA_ABSENT_REASON_CODE`
  - `description` Bundle.entry.resource.where(resourceType ='Observation' and not(hasMember.exists())).dataAbsentReason.coding.code
  - `type` string
  - `constraints`:
    - `enum` ['asked-unknown', 'temp-unknown', 'not-asked', 'asked-declined', 'masked', 'not-applicable', 'unsupported', 'as-text', 'error', 'not-a-number', 'negative-infinity', 'positive-infinity', 'not-performed', 'not-permitted']
### `DATA_ABSENT_REASON_DISPLAY`
  - `description` Bundle.entry.resource.where(resourceType ='Observation' and not(hasMember.exists())).dataAbsentReason.coding.display
  - `type` string
  - `constraints`:
    - `enum` ['asked but unknown', 'temporarily unknown', 'not asked', 'asked but declined', 'masked', 'not applicable', 'unsupported', 'as text', 'error', 'not a number (nan)', 'negative infinity (ninf)', 'positive infinity (pinf)', 'not performed', 'not permitted']
## `screening_location_data`
  - `path` flat-file/nyher-fhir-ig-example/SCREENING_LOCATION_DATA_partner1-test-20241128-testcase1.csv
  - `schema`
      - `foreignKeys`
      - [1]
        - `fields` ['PATIENT_MR_ID_VALUE']
        - `reference`
          - `resource` qe_admin_data
          - `fields` ['PATIENT_MR_ID_VALUE']
### `PATIENT_MR_ID_VALUE`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').identifier.where(type.coding.code = 'MR').value
  - `type` string
  - `constraints`:
    - `required` True
### `LOCATION_NAME`
  - `description` Bundle.entry.resource.where(resourceType ='Location').name
  - `type` string
### `LOCATION_STATUS`
  - `description` Bundle.entry.resource.where(resourceType ='Location').status
  - `type` string
### `LOCATION_TYPE_CODE`
  - `description` Bundle.entry.resource.where(resourceType ='Location').type.coding.code
  - `type` string
### `LOCATION_TYPE_SYSTEM`
  - `description` Bundle.entry.resource.where(resourceType ='Location').type.coding.system
  - `type` string
### `LOCATION_ADDRESS_TYPE`
  - `description` Bundle.entry.resource.where(resourceType ='Location').address.type
  - `type` string
### `LOCATION_ADDRESS1`
  - `description` Bundle.entry.resource.where(resourceType ='Location').address.line
  - `type` string
### `LOCATION_ADDRESS2`
  - `description` Bundle.entry.resource.where(resourceType ='Location').address.line
  - `type` string
### `LOCATION_CITY`
  - `description` Bundle.entry.resource.where(resourceType ='Location').address.city
  - `type` string
### `LOCATION_DISTRICT`
  - `description` Bundle.entry.resource.where(resourceType ='Location').address.district
  - `type` string
### `LOCATION_STATE`
  - `description` Bundle.entry.resource.where(resourceType ='Location').address.state
  - `type` string
### `LOCATION_ZIP`
  - `description` Bundle.entry.resource.where(resourceType ='Location').address.zip
  - `type` string
### `LOCATION_PHYSICAL_TYPE_CODE`
  - `description` Bundle.entry.resource.where(resourceType ='Location').physicalType.coding.code
  - `type` string
### `LOCATION_PHYSICAL_TYPE_SYSTEM`
  - `description` Bundle.entry.resource.where(resourceType ='Location').physicalType.coding.system
  - `type` string
### `LOCATION_TEXT_STATUS`
  - `description` Bundle.entry.resource.where(resourceType ='Location').text.status
  - `type` string
### `LOCATION_LAST_UPDATED`
  - `description` Bundle.entry.resource.where(resourceType ='Location').meta.lastUpdated
  - `type` string
  - `constraints`:
    - `required` True
    - `pattern` `^([0-9]([0-9]([0-9][1-9]|[1-9]0)|[1-9]00)|[1-9]000)-(0[1-9]|1[0-2])-(0[1-9]|[1-2][0-9]|3[0-1])T([01][0-9]|2[0-3]):[0-5][0-9]:([0-5][0-9]|60)(\.[0-9]+)?(Z|(\+|-)((0[0-9]|1[0-3]):[0-5][0-9]|14:00))$`
## `screening_encounter_data`
  - `path` flat-file/nyher-fhir-ig-example/SCREENING_ENCOUNTER_DATA_partner1-test-20241128-testcase1.csv
  - `schema`
      - `foreignKeys`
      - [1]
        - `fields` ['PATIENT_MR_ID_VALUE']
        - `reference`
          - `resource` qe_admin_data
          - `fields` ['PATIENT_MR_ID_VALUE']
### `PATIENT_MR_ID_VALUE`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').identifier.where(type.coding.code = 'MR').value
  - `type` string
  - `constraints`:
    - `required` True
### `ENCOUNTER_ID`
  - `description` Bundle.entry.resource.where(resourceType ='Encounter').id
  - `type` string
  - `constraints`:
    - `required` True
    - `pattern` `^[A-Za-z0-9\-\.]{1,64}$`
### `ENCOUNTER_CLASS_CODE`
  - `description` Bundle.entry.resource.where(resourceType ='Encounter').class.code
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['amb', 'emer', 'fld', 'hh', 'imp', 'acute', 'nonac', 'obsenc', 'prenc', 'ss', 'vr']
### `ENCOUNTER_CLASS_CODE_DESCRIPTION`
  - `description` Bundle.entry.resource.where(resourceType ='Encounter').class.display
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['ambulatory', 'emergency', 'field', 'home health', 'inpatient encounter', 'inpatient acute', 'inpatient non-acute', 'observation encounter', 'pre-admission', 'short stay', 'virtual']
### `ENCOUNTER_CLASS_CODE_SYSTEM`
  - `description` Bundle.entry.resource.where(resourceType ='Encounter').class.system
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['http://terminology.hl7.org/CodeSystem/v3-ActCode']
### `ENCOUNTER_STATUS_CODE`
  - `description` Bundle.entry.resource.where(resourceType ='Encounter').status
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['planned', 'arrived', 'triaged', 'in-progress', 'onleave', 'finished', 'cancelled', 'entered-in-error', 'unknown']
### `ENCOUNTER_TYPE_CODE`
  - `description` Bundle.entry.resource.where(resourceType ='Encounter').type.coding.code
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['405672008', '23918007']
### `ENCOUNTER_TYPE_CODE_DESCRIPTION`
  - `description` Bundle.entry.resource.where(resourceType ='Encounter').type.text
  - `type` string
### `ENCOUNTER_TYPE_CODE_SYSTEM`
  - `description` Bundle.entry.resource.where(resourceType ='Encounter').type.coding.system
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['http://snomed.info/sct']
### `ENCOUNTER_START_TIME`
  - `description` Bundle.entry.resource.where(resourceType ='Encounter').period.start
  - `type` string
  - `constraints`:
    - `pattern` `([0-9]([0-9]([0-9][1-9]|[1-9]0)|[1-9]00)|[1-9]000)(-(0[1-9]|1[0-2])(-(0[1-9]|[1-2][0-9]|3[0-1])(T([01][0-9]|2[0-3]):[0-5][0-9]:([0-5][0-9]|60)(\.[0-9]+)?(Z|(\+|-)((0[0-9]|1[0-3]):[0-5][0-9]|14:00)))?)?)?`
### `ENCOUNTER_END_TIME`
  - `description` Bundle.entry.resource.where(resourceType ='Encounter').period.end
  - `type` string
  - `constraints`:
    - `pattern` `([0-9]([0-9]([0-9][1-9]|[1-9]0)|[1-9]00)|[1-9]000)(-(0[1-9]|1[0-2])(-(0[1-9]|[1-2][0-9]|3[0-1])(T([01][0-9]|2[0-3]):[0-5][0-9]:([0-5][0-9]|60)(\.[0-9]+)?(Z|(\+|-)((0[0-9]|1[0-3]):[0-5][0-9]|14:00)))?)?)?`
### `ENCOUNTER_LAST_UPDATED`
  - `description` Bundle.entry.resource.where(resourceType ='Encounter').meta.lastUpdated
  - `type` string
  - `constraints`:
    - `required` True
    - `pattern` `^([0-9]([0-9]([0-9][1-9]|[1-9]0)|[1-9]00)|[1-9]000)-(0[1-9]|1[0-2])-(0[1-9]|[1-2][0-9]|3[0-1])T([01][0-9]|2[0-3]):[0-5][0-9]:([0-5][0-9]|60)(\.[0-9]+)?(Z|(\+|-)((0[0-9]|1[0-3]):[0-5][0-9]|14:00))$`
### `ENCOUNTER_PROFILE`
  - `description` Bundle.entry.resource.where(resourceType ='Encounter').meta.profile
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['http://shinny.org/us/ny/hrsn/StructureDefinition/shinny-encounter']
### `ENCOUNTER_TEXT_STATUS`
  - `description` Bundle.entry.resource.where(resourceType ='Encounter').text.status
  - `type` string
## `screening_consent_data`
  - `path` flat-file/nyher-fhir-ig-example/SCREENING_CONSENT_DATA_partner1-test-20241128-testcase1.csv
  - `schema`
      - `foreignKeys`
      - [1]
        - `fields` ['PATIENT_MR_ID_VALUE']
        - `reference`
          - `resource` qe_admin_data
          - `fields` ['PATIENT_MR_ID_VALUE']
### `PATIENT_MR_ID_VALUE`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').identifier.where(type.coding.code = 'MR').value
  - `type` string
  - `constraints`:
    - `required` True
### `CONSENT_PROFILE`
  - `description` Bundle.entry.resource.where(resourceType ='Consent').meta.profile
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['http://shinny.org/us/ny/hrsn/StructureDefinition/shinny-Consent']
### `CONSENT_LAST_UPDATED`
  - `description` Bundle.entry.resource.where(resourceType ='Consent').meta.lastUpdated
  - `type` string
  - `constraints`:
    - `required` True
    - `pattern` `^([0-9]([0-9]([0-9][1-9]|[1-9]0)|[1-9]00)|[1-9]000)-(0[1-9]|1[0-2])-(0[1-9]|[1-2][0-9]|3[0-1])T([01][0-9]|2[0-3]):[0-5][0-9]:([0-5][0-9]|60)(\.[0-9]+)?(Z|(\+|-)((0[0-9]|1[0-3]):[0-5][0-9]|14:00))$`
### `CONSENT_TEXT_STATUS`
  - `description` Bundle.entry.resource.where(resourceType ='Consent').text.status
  - `type` string
### `CONSENT_STATUS`
  - `description` Bundle.entry.resource.where(resourceType ='Consent').status
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['draft', 'proposed', 'active', 'rejected', 'inactive', 'entered-in-error']
### `CONSENT_SCOPE_CODE`
  - `description` Bundle.entry.resource.where(resourceType ='Consent').scope.coding.code
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['adr', 'research', 'patient-privacy', 'treatment']
### `CONSENT_SCOPE_TEXT`
  - `description` Bundle.entry.resource.where(resourceType ='Consent').scope.text
  - `type` string
  - `constraints`:
    - `required` True
### `CONSENT_CATEGORY_IDSCL_CODE`
  - `description` Bundle.entry.resource.where(resourceType ='Consent').category.coding.code.where(code='IDSCL')
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['idscl']
### `CONSENT_CATEGORY_IDSCL_SYSTEM`
  - `description` Bundle.entry.resource.where(resourceType ='Consent').category.coding.system.where(code='IDSCL')
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['http://terminology.hl7.org/CodeSystem/v3-ActCode']
### `CONSENT_CATEGORY_LOINC_CODE`
  - `description` Bundle.entry.resource.where(resourceType ='Consent').category.coding.code.where(system='http://loinc.org')
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['59284-0', '57016-8', '57017-6', '64292-6']
### `CONSENT_CATEGORY_LOINC_SYSTEM`
  - `description` Bundle.entry.resource.where(resourceType ='Consent').category.coding.system.where(system='http://loinc.org')
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['http://loinc.org']
### `CONSENT_CATEGORY_LOINC_DISPLAY`
  - `description` Bundle.entry.resource.where(resourceType ='Consent').category.coding.display.where(system='http://loinc.org')
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['patient consent', 'privacy policy acknowledgement document', 'privacy policy organization document', 'release of information consent']
### `CONSENT_DATE_TIME`
  - `description` Bundle.entry.resource.where(resourceType ='Consent').dateTime
  - `type` string
  - `constraints`:
    - `pattern` `^([0-9]([0-9]([0-9][1-9]|[1-9]0)|[1-9]00)|[1-9]000)-(0[1-9]|1[0-2])-(0[1-9]|[1-2][0-9]|3[0-1])T([01][0-9]|2[0-3]):[0-5][0-9]:([0-5][0-9]|60)(\.[0-9]+)?(Z|(\+|-)((0[0-9]|1[0-3]):[0-5][0-9]|14:00))$`
### `CONSENT_POLICY_AUTHORITY`
  - `description` Bundle.entry.resource.where(resourceType ='Consent').policy.authority
  - `type` string
### `CONSENT_PROVISION_TYPE`
  - `description` Bundle.entry.resource.where(resourceType ='Consent').provision.type
  - `type` string
  - `constraints`:
    - `enum` ['deny', 'permit']
## `screening_resources_data`
  - `path` flat-file/nyher-fhir-ig-example/SCREENING_RESOURCES_DATA_partner1-test-20241128-testcase1.csv
  - `schema`
      - `foreignKeys`
      - [1]
        - `fields` ['PATIENT_MR_ID_VALUE']
        - `reference`
          - `resource` qe_admin_data
          - `fields` ['PATIENT_MR_ID_VALUE']
### `PATIENT_MR_ID_VALUE`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').identifier.where(type.coding.code = 'MR').value
  - `type` string
  - `constraints`:
    - `required` True
### `SCREENING_STATUS_CODE`
  - `description` Bundle.entry.resource.where(resourceType ='Observation').status
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['final', 'corrected', 'entered-in-error', 'unknown']
### `SCREENING_LAST_UPDATED`
  - `description` Bundle.entry.resource.where(resourceType ='Observation').meta.lastUpdated
  - `type` string
  - `constraints`:
    - `required` True
    - `pattern` `^([0-9]([0-9]([0-9][1-9]|[1-9]0)|[1-9]00)|[1-9]000)-(0[1-9]|1[0-2])-(0[1-9]|[1-2][0-9]|3[0-1])T([01][0-9]|2[0-3]):[0-5][0-9]:([0-5][0-9]|60)(\.[0-9]+)?(Z|(\+|-)((0[0-9]|1[0-3]):[0-5][0-9]|14:00))$`
### `SCREENING_PROFILE`
  - `description` Bundle.entry.resource.where(resourceType ='Observation').meta.profile
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['http://shinny.org/us/ny/hrsn/StructureDefinition/shinny-observation-screening-response']
### `SCREENING_LANGUAGE`
  - `description` Bundle.entry.resource.where(resourceType ='Observation').language
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['ar', 'bn', 'cs', 'da', 'de', 'de-at', 'de-ch', 'de-de', 'el', 'en', 'en-au', 'en-ca', 'en-gb', 'en-in', 'en-nz', 'en-sg', 'en-us', 'es', 'es-ar', 'es-es', 'es-uy', 'fi', 'fr', 'fr-be', 'fr-ch', 'fr-fr', 'fy', 'fy-nl', 'hi', 'hr', 'it', 'it-ch', 'it-it', 'ja', 'ko', 'nl', 'nl-be', 'nl-nl', 'no', 'no-no', 'pa', 'pl', 'pt', 'pt-br', 'ru', 'ru-ru', 'sr', 'sr-rs', 'sv', 'sv-se', 'te', 'zh', 'zh-cn', 'zh-hk', 'zh-sg', 'zh-tw']
### `SCREENING_TEXT_STATUS`
  - `description` Bundle.entry.resource.where(resourceType ='Observation').text.status
  - `type` string
### `SCREENING_CODE_SYSTEM_NAME`
  - `description` Bundle.entry.resource.where(resourceType ='Observation' and hasMember.exists()).code.coding.system
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['http://loinc.org']
### `QUESTION_CODE_SYSTEM_NAME`
  - `description` Bundle.entry.resource.where(resourceType ='Observation' and not(hasMember.exists())).code.system
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['http://loinc.org']
### `OBSERVATION_CATEGORY_SDOH_SYSTEM`
  - `description` Bundle.entry.resource.where(resourceType ='Observation').category.where(coding.system = 'http://hl7.org/fhir/us/sdoh-clinicalcare/CodeSystem/SDOHCC-CodeSystemTemporaryCodes').coding.system
  - `type` string
  - `constraints`:
    - `enum` ['http://hl7.org/fhir/us/sdoh-clinicalcare/CodeSystem/SDOHCC-CodeSystemTemporaryCodes']
### `OBSERVATION_CATEGORY_SOCIAL_HISTORY_CODE`
  - `description` Bundle.entry.resource.where(resourceType ='Observation').category.where(coding.code = 'social-history').coding.code
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['social-history']
### `OBSERVATION_CATEGORY_SOCIAL_HISTORY_SYSTEM`
  - `description` Bundle.entry.resource.where(resourceType ='Observation').category.where(coding.code = 'social-history').coding.system
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['http://terminology.hl7.org/CodeSystem/observation-category']
### `OBSERVATION_CATEGORY_SURVEY_CODE`
  - `description` Bundle.entry.resource.where(resourceType ='Observation').category.where(coding.code = 'survey').coding.code
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['survey']
### `OBSERVATION_CATEGORY_SURVEY_SYSTEM`
  - `description` Bundle.entry.resource.where(resourceType ='Observation').category.where(coding.code = 'survey').coding.system
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['http://terminology.hl7.org/CodeSystem/observation-category']
### `OBSERVATION_CATEGORY_SNOMED_SYSTEM`
  - `description` Bundle.entry.resource.where(resourceType ='Observation').category.where(coding.system = 'http://snomed.info/sct').coding.system
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['http://snomed.info/sct']
### `ANSWER_CODE_SYSTEM_NAME`
  - `description` Bundle.entry.resource.where(resourceType ='Observation' and not(hasMember.exists())).valueCodeableConcept.coding.system
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['http://loinc.org']
### `INTERPRETATION_SYSTEM`
  - `description` Bundle.entry.resource.where(resourceType ='Observation').interpretation.coding.system
  - `type` string
  - `constraints`:
    - `enum` ['http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation']
### `DATA_ABSENT_REASON_SYSTEM`
  - `description` Bundle.entry.resource.where(resourceType ='Observation' and not(hasMember.exists())).dataAbsentReason.coding.system
  - `type` string
  - `constraints`:
    - `enum` ['https://terminology.hl7.org/6.0.2/CodeSystem-data-absent-reason']
## `demographic_data`
  - `path` flat-file/nyher-fhir-ig-example/DEMOGRAPHIC_DATA_partner1-test-20241128-testcase1.csv
  - `schema`
      - `foreignKeys`
      - [1]
        - `fields` ['PATIENT_MR_ID_VALUE']
        - `reference`
          - `resource` qe_admin_data
          - `fields` ['PATIENT_MR_ID_VALUE']
### `PATIENT_MR_ID_VALUE`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').identifier.where(type.coding.code = 'MR').value
  - `type` string
  - `constraints`:
    - `required` True
### `PATIENT_MR_ID_SYSTEM`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').identifier.where(type.coding.code = 'MR').system
  - `type` string
  - `constraints`:
    - `required` True
### `PATIENT_MR_ID_TYPE_CODE`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').identifier.where(type.coding.code = 'MR').type.coding.code
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['MR']
### `PATIENT_MR_ID_TYPE_SYSTEM`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').identifier.where(type.coding.code = 'MR').type.coding.system
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['http://terminology.hl7.org/CodeSystem/v2-0203']
### `PATIENT_MA_ID_VALUE`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').identifier.where(type.coding.code = 'MA').value
  - `type` string
### `PATIENT_MA_ID_SYSTEM`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').identifier.where(type.coding.code = 'MA').system
  - `type` string
### `PATIENT_MA_ID_TYPE_CODE`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').identifier.where(type.coding.code = 'MA').type.coding.code
  - `type` string
  - `constraints`:
    - `enum` ['MA']
### `PATIENT_MA_ID_TYPE_SYSTEM`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').identifier.where(type.coding.code = 'MA').type.coding.system
  - `type` string
  - `constraints`:
    - `enum` ['http://terminology.hl7.org/CodeSystem/v2-0203']
### `PATIENT_SS_ID_VALUE`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').identifier.where(type.coding.code = 'SS').value
  - `type` string
### `PATIENT_SS_ID_SYSTEM`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').identifier.where(type.coding.code = 'SS').system
  - `type` string
### `PATIENT_SS_ID_TYPE_CODE`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').identifier.where(type.coding.code = 'SS').type.coding.code
  - `type` string
  - `constraints`:
    - `enum` ['SS']
### `PATIENT_SS_ID_TYPE_SYSTEM`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').identifier.where(type.coding.code = 'SS').type.coding.system
  - `type` string
  - `constraints`:
    - `enum` ['http://terminology.hl7.org/CodeSystem/v2-0203']
### `GIVEN_NAME`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').name.given
  - `type` string
  - `constraints`:
    - `required` True
    - `minLength` 1
    - `pattern` `^[A-Za-z]+$`
### `MIDDLE_NAME`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').name.extension.valueString
  - `type` string
  - `constraints`:
    - `pattern` `^[A-Za-z]+$`
### `MIDDLE_NAME_EXTENSION_URL`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').name.extension.url
  - `type` string
  - `constraints`:
    - `enum` ['http://shinny.org/us/ny/hrsn/StructureDefinition/middle-name']
### `FAMILY_NAME`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').name.family
  - `type` string
  - `constraints`:
    - `required` True
    - `pattern` `^[A-Za-z]+$`
### `PREFIX_NAME`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').name.prefix
  - `type` string
### `SUFFIX_NAME`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').name.suffix
  - `type` string
### `GENDER`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').gender
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['male', 'female', 'other', 'unknown']
### `EXTENSION_SEX_AT_BIRTH_CODE_VALUE`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').extension.where(url='http://hl7.org/fhir/us/core/StructureDefinition/us-core-birthsex').valueCode
  - `type` string
  - `constraints`:
    - `enum` ['f', 'm', 'unk']
### `EXTENSION_SEX_AT_BIRTH_CODE_URL`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').extension.url
  - `type` string
  - `constraints`:
    - `enum` ['http://hl7.org/fhir/us/core/StructureDefinition/us-core-birthsex']
### `PATIENT_BIRTH_DATE`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').birthDate
  - `type` string
  - `constraints`:
    - `required` True
    - `pattern` `^([0-9]([0-9]([0-9][1-9]|[1-9]0)|[1-9]00)|[1-9]000)(-(0[1-9]|1[0-2])(-(0[1-9]|[1-2][0-9]|3[0-1]))?)?$`
### `ADDRESS1`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').address.line & Bundle.entry.resource.where(resourceType ='Patient').address.text
  - `type` string
  - `constraints`:
    - `pattern` `.*\d.*`
### `ADDRESS2`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').address.line & Bundle.entry.resource.where(resourceType ='Patient').address.text
  - `type` string
### `CITY`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').address.city
  - `type` string
### `DISTRICT`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').address.district
  - `type` string
### `STATE`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').address.state
  - `type` string
  - `constraints`:
    - `enum` ['ak', 'al', 'ar', 'as', 'az', 'ca', 'co', 'ct', 'dc', 'de', 'fl', 'fm', 'ga', 'gu', 'hi', 'ia', 'id', 'il', 'in', 'ks', 'ky', 'la', 'ma', 'md', 'me', 'mh', 'mi', 'mn', 'mo', 'mp', 'ms', 'mt', 'nc', 'nd', 'ne', 'nh', 'nj', 'nm', 'nv', 'ny', 'oh', 'ok', 'or', 'pa', 'pr', 'pw', 'ri', 'sc', 'sd', 'tn', 'tx', 'ut', 'va', 'vi', 'vt', 'wa', 'wi', 'wv', 'wy']
### `ZIP`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').address.postalCode
  - `type` string
  - `constraints`:
    - `pattern` `^\d{5}(\d{4})?$`
### `TELECOM_VALUE`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').telecom.value
  - `type` string
### `TELECOM_SYSTEM`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').telecom.system
  - `type` string
  - `constraints`:
    - `enum` ['phone', 'fax', 'email', 'pager', 'url', 'sms', 'other']
### `TELECOM_USE`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').telecom.use
  - `type` string
  - `constraints`:
    - `enum` ['home', 'work', 'temp', 'old', 'mobile']
### `EXTENSION_PERSONAL_PRONOUNS_URL`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').extension.url
  - `type` string
  - `constraints`:
    - `enum` ['http://shinny.org/us/ny/hrsn/StructureDefinition/shinny-personal-pronouns']
### `EXTENSION_PERSONAL_PRONOUNS_CODE`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').extension.where(url='http://shinny.org/us/ny/hrsn/StructureDefinition/shinny-personal-pronouns').valueCodeableConcept.coding.code
  - `type` string
  - `constraints`:
    - `enum` ['LA29518-0', 'LA29519-8', 'LA29520-6', 'oth', 'unk']
### `EXTENSION_PERSONAL_PRONOUNS_DISPLAY`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').extension.where(url='http://shinny.org/us/ny/hrsn/StructureDefinition/shinny-personal-pronouns').valueCodeableConcept.coding.display
  - `type` string
  - `constraints`:
    - `enum` ['he/him/his/his/himself', 'she/her/her/hers/herself', 'they/them/their/theirs/themselves', 'other', 'unknown']
### `EXTENSION_PERSONAL_PRONOUNS_SYSTEM`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').extension.where(url='http://shinny.org/us/ny/hrsn/StructureDefinition/shinny-personal-pronouns').valueCodeableConcept.coding.system
  - `type` string
  - `constraints`:
    - `enum` ['http://loinc.org/', 'http://loinc.org', 'http://terminology.hl7.org/CodeSystem/v3-NullFlavor']
### `EXTENSION_GENDER_IDENTITY_URL`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').extension.url
  - `type` string
  - `constraints`:
    - `enum` ['http://shinny.org/us/ny/hrsn/StructureDefinition/shinny-gender-identity']
### `EXTENSION_GENDER_IDENTITY_CODE`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').extension.where(url='http://terminology.hl7.org/CodeSystem/v3-NullFlavor').valueCodeableConcept.coding.code
  - `type` string
  - `constraints`:
    - `enum` ['33791000087105', '407376001', '407377005', '446131000124102', '446141000124107', '446151000124109', 'oth', 'unk', 'asked-declined']
### `EXTENSION_GENDER_IDENTITY_DISPLAY`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').extension.where(url='http://terminology.hl7.org/CodeSystem/v3-NullFlavor').valueCodeableConcept.coding.display
  - `type` string
  - `constraints`:
    - `enum` ['identifies as nonbinary gender (finding)', 'male-to-female transsexual (finding)', 'female-to-male transsexual (finding)', 'identifies as non-conforming gender (finding)', 'identifies as female gender (finding)', 'identifies as male gender (finding)', 'other', 'unknown', 'asked but declined']
### `EXTENSION_GENDER_IDENTITY_SYSTEM`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').extension.where(url='http://terminology.hl7.org/CodeSystem/v3-NullFlavor').valueCodeableConcept.coding.system
  - `type` string
  - `constraints`:
    - `enum` ['http://terminology.hl7.org/CodeSystem/v3-NullFlavor', 'http://terminology.hl7.org/CodeSystem/data-absent-reason', 'http://snomed.info/sct', 'http://shinny.org/us/ny/hrsn/StructureDefinition/shinny-gender-identity']
### `PREFERRED_LANGUAGE_CODE_SYSTEM_NAME`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').communication.language.coding.system & Bundle.entry.resource.where(resourceType ='Patient').communication.preferred = true
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['iso', 'iso 639-2', 'http://hl7.org/fhir/us/core/valueset/simple-language', 'urn:ietf:bcp:47']
### `PREFERRED_LANGUAGE_CODE_SYSTEM_CODE`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').communication.language.coding.code & Bundle.entry.resource.where(resourceType ='Patient').communication.preferred = true
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['aar', 'abk', 'ace', 'ach', 'ada', 'ady', 'afa', 'afh', 'afr', 'ain', 'aka', 'akk', 'alb (b)', 'sqi (t)', 'ale', 'alg', 'alt', 'amh', 'ang', 'anp', 'apa', 'ara', 'arc', 'arg', 'arm (b)', 'hye (t)', 'arn', 'arp', 'art', 'arw', 'asm', 'ast', 'ath', 'aus', 'ava', 'ave', 'awa', 'aym', 'aze', 'bad', 'bai', 'bak', 'bal', 'bam', 'ban', 'baq (b)', 'eus (t)', 'bas', 'bat', 'bej', 'bel', 'bem', 'ben', 'ber', 'bho', 'bih', 'bik', 'bin', 'bis', 'bla', 'bnt', 'tib (b)', 'bod (t)', 'bos', 'bra', 'bre', 'btk', 'bua', 'bug', 'bul', 'bur (b)', 'mya (t)', 'byn', 'cad', 'cai', 'car', 'cat', 'cau', 'ceb', 'cel', 'cze (b)', 'ces (t)', 'cha', 'chb', 'che', 'chg', 'chi (b)', 'zho (t)', 'chk', 'chm', 'chn', 'cho', 'chp', 'chr', 'chu', 'chv', 'chy', 'cmc', 'cnr', 'cop', 'cor', 'cos', 'cpe', 'cpf', 'cpp', 'cre', 'crh', 'crp', 'csb', 'cus', 'wel (b)', 'cym (t)', 'cze (b)', 'ces (t)', 'dak', 'dan', 'dar', 'day', 'del', 'den', 'ger (b)', 'deu (t)', 'dgr', 'din', 'div', 'doi', 'dra', 'dsb', 'dua', 'dum', 'dut (b)', 'nld (t)', 'dyu', 'dzo', 'efi', 'egy', 'eka', 'gre (b)', 'ell (t)', 'elx', 'eng', 'en', 'enm', 'epo', 'est', 'baq (b)', 'eus (t)', 'ewe', 'ewo', 'fan', 'fao', 'per (b)', 'fas (t)', 'fat', 'fij', 'fil', 'fin', 'fiu', 'fon', 'fre (b)', 'fra (t)', 'fre (b)', 'fra (t)', 'frm', 'fro', 'frr', 'frs', 'fry', 'ful', 'fur', 'gaa', 'gay', 'gba', 'gem', 'geo (b)', 'kat (t)', 'ger (b)', 'deu (t)', 'gez', 'gil', 'gla', 'gle', 'glg', 'glv', 'gmh', 'goh', 'gon', 'gor', 'got', 'grb', 'grc', 'gre (b)', 'ell (t)', 'grn', 'gsw', 'guj', 'gwi', 'hai', 'hat', 'hau', 'haw', 'heb', 'her', 'hil', 'him', 'hin', 'hit', 'hmn', 'hmo', 'hrv', 'hsb', 'hun', 'hup', 'arm (b)', 'hye (t)', 'iba', 'ibo', 'ice (b)', 'isl (t)', 'ido', 'iii', 'ijo', 'iku', 'ile', 'ilo', 'ina', 'inc', 'ind', 'ine', 'inh', 'ipk', 'ira', 'iro', 'ice (b)', 'isl (t)', 'ita', 'jav', 'jbo', 'jpn', 'jpr', 'jrb', 'kaa', 'kab', 'kac', 'kal', 'kam', 'kan', 'kar', 'kas', 'geo (b)', 'kat (t)', 'kau', 'kaw', 'kaz', 'kbd', 'kha', 'khi', 'khm', 'kho', 'kik', 'kin', 'kir', 'kmb', 'kok', 'kom', 'kon', 'kor', 'kos', 'kpe', 'krc', 'krl', 'kro', 'kru', 'kua', 'kum', 'kur', 'kut', 'lad', 'lah', 'lam', 'lao', 'lat', 'lav', 'lez', 'lim', 'lin', 'lit', 'lol', 'loz', 'ltz', 'lua', 'lub', 'lug', 'lui', 'lun', 'luo', 'lus', 'mac (b)', 'mkd (t)', 'mad', 'mag', 'mah', 'mai', 'mak', 'mal', 'man', 'mao (b)', 'mri (t)', 'map', 'mar', 'mas', 'may (b)', 'msa (t)', 'mdf', 'mdr', 'men', 'mga', 'mic', 'min', 'mis', 'mac (b)', 'mkd (t)', 'mkh', 'mlg', 'mlt', 'mnc', 'mni', 'mno', 'moh', 'mon', 'mos', 'mao (b)', 'mri (t)', 'may (b)', 'msa (t)', 'mul', 'mun', 'mus', 'mwl', 'mwr', 'bur (b)', 'mya (t)', 'myn', 'myv', 'nah', 'nai', 'nap', 'nau', 'nav', 'nbl', 'nde', 'ndo', 'nds', 'nep', 'new', 'nia', 'nic', 'niu', 'dut (b)', 'nld (t)', 'nno', 'nob', 'nog', 'non', 'nor', 'nqo', 'nso', 'nub', 'nwc', 'nya', 'nym', 'nyn', 'nyo', 'nzi', 'oci', 'oji', 'ori', 'orm', 'osa', 'oss', 'ota', 'oto', 'paa', 'pag', 'pal', 'pam', 'pan', 'pap', 'pau', 'peo', 'per (b)', 'fas (t)', 'phi', 'phn', 'pli', 'pol', 'pon', 'por', 'pra', 'pro', 'pus', 'qaa-qtz', 'que', 'raj', 'rap', 'rar', 'roa', 'roh', 'rom', 'rum (b)', 'ron (t)', 'rum (b)', 'ron (t)', 'run', 'rup', 'rus', 'sad', 'sag', 'sah', 'sai', 'sal', 'sam', 'san', 'sas', 'sat', 'scn', 'sco', 'sel', 'sem', 'sga', 'sgn', 'shn', 'sid', 'sin', 'sio', 'sit', 'sla', 'slo (b)', 'slk (t)', 'slo (b)', 'slk (t)', 'slv', 'sma', 'sme', 'smi', 'smj', 'smn', 'smo', 'sms', 'sna', 'snd', 'snk', 'sog', 'som', 'son', 'sot', 'spa', 'alb (b)', 'sqi (t)', 'srd', 'srn', 'srp', 'srr', 'ssa', 'ssw', 'suk', 'sun', 'sus', 'sux', 'swa', 'swe', 'syc', 'syr', 'tah', 'tai', 'tam', 'tat', 'tel', 'tem', 'ter', 'tet', 'tgk', 'tgl', 'tha', 'tib (b)', 'bod (t)', 'tig', 'tir', 'tiv', 'tkl', 'tlh', 'tli', 'tmh', 'tog', 'ton', 'tpi', 'tsi', 'tsn', 'tso', 'tuk', 'tum', 'tup', 'tur', 'tut', 'tvl', 'twi', 'tyv', 'udm', 'uga', 'uig', 'ukr', 'umb', 'und', 'urd', 'uzb', 'vai', 'ven', 'vie', 'vol', 'vot', 'wak', 'wal', 'war', 'was', 'wel (b)', 'cym (t)', 'wen', 'wln', 'wol', 'xal', 'xho', 'yao', 'yap', 'yid', 'yor', 'ypk', 'zap', 'zbl', 'zen', 'zgh', 'zha', 'chi (b)', 'zho (t)', 'znd', 'zul', 'zun', 'zxx', 'zza']
### `EXTENSION_RACE_URL`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').extension.url
  - `type` string
  - `constraints`:
    - `enum` ['http://hl7.org/fhir/us/core/StructureDefinition/us-core-race']
### `EXTENSION_OMBCATEGORY_RACE_URL`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').extension.where(url='http://hl7.org/fhir/us/core/StructureDefinition/us-core-race').extension.where(url = 'ombCategory').url
  - `type` string
  - `constraints`:
    - `enum` ['ombCategory']
### `EXTENSION_OMBCATEGORY_RACE_CODE`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').extension.where(url='http://hl7.org/fhir/us/core/StructureDefinition/us-core-race').extension.where(url = 'ombCategory').valueCoding.code
  - `type` string
  - `constraints`:
    - `enum` ['1002-5', '2028-9', '2054-5', '2076-8', '2106-3', 'UNK', 'ASKU']
### `EXTENSION_OMBCATEGORY_RACE_CODE_DESCRIPTION`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').extension.where(url='http://hl7.org/fhir/us/core/StructureDefinition/us-core-race').extension.where(url = 'ombCategory').valueCoding.display
  - `type` string
  - `constraints`:
    - `enum` ['American Indian or Alaska Native', 'Asian', 'Black or African American', 'Native Hawaiian or Other Pacific Islander', 'White', 'Unknown', 'Asked but no answer']
### `EXTENSION_OMBCATEGORY_RACE_CODE_SYSTEM_NAME`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').extension.where(url='http://hl7.org/fhir/us/core/StructureDefinition/us-core-race').extension.where(url = 'ombCategory').valueCoding.system
  - `type` string
  - `constraints`:
    - `enum` ['urn:oid:2.16.840.1.113883.6.238', 'http://terminology.hl7.org/CodeSystem/v3-NullFlavor']
### `EXTENSION_TEXT_RACE_URL`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').extension.where(url='http://hl7.org/fhir/us/core/StructureDefinition/us-core-race').extension.where(url = 'text').url
  - `type` string
  - `constraints`:
    - `enum` ['text']
### `EXTENSION_TEXT_RACE_CODE_VALUE`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').extension.where(url='http://hl7.org/fhir/us/core/StructureDefinition/us-core-race').extension.where(url = 'text').valueString
  - `type` string
### `EXTENSION_ETHNICITY_URL`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').extension.url
  - `type` string
  - `constraints`:
    - `enum` ['http://hl7.org/fhir/us/core/StructureDefinition/us-core-ethnicity']
### `EXTENSION_OMBCATEGORY_ETHNICITY_URL`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').extension.where(url='http://hl7.org/fhir/us/core/StructureDefinition/us-core-ethnicity').extension.where(url = 'ombCategory').url
  - `type` string
  - `constraints`:
    - `enum` ['ombCategory']
### `EXTENSION_OMBCATEGORY_ETHNICITY_CODE`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').extension.where(url='http://hl7.org/fhir/us/core/StructureDefinition/us-core-ethnicity').extension.where(url = 'ombCategory').valueCoding.code
  - `type` string
  - `constraints`:
    - `enum` ['2135-2', '2186-5']
### `EXTENSION_OMBCATEGORY_ETHNICITY_CODE_DESCRIPTION`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').extension.where(url='http://hl7.org/fhir/us/core/StructureDefinition/us-core-ethnicity').extension.where(url = 'ombCategory').valueCoding.display
  - `type` string
  - `constraints`:
    - `enum` ['hispanic or latino', 'non hispanic or latino']
### `EXTENSION_OMBCATEGORY_ETHNICITY_CODE_SYSTEM_NAME`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').extension.where(url='http://hl7.org/fhir/us/core/StructureDefinition/us-core-ethnicity').extension.where(url = 'ombCategory').valueCoding.system
  - `type` string
  - `constraints`:
    - `enum` ['urn:oid:2.16.840.1.113883.6.238']
### `EXTENSION_TEXT_ETHNICITY_URL`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').extension.where(url='http://hl7.org/fhir/us/core/StructureDefinition/us-core-ethnicity').extension.where(url = 'text').url
  - `type` string
  - `constraints`:
    - `enum` ['text']
### `EXTENSION_TEXT_ETHNICITY_CODE_VALUE`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').extension.where(url='http://hl7.org/fhir/us/core/StructureDefinition/us-core-ethnicity').extension.where(url = 'text').valueString
  - `type` string
### `PATIENT_LAST_UPDATED`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').meta.lastUpdated
  - `type` string
  - `constraints`:
    - `required` True
    - `pattern` `^([0-9]([0-9]([0-9][1-9]|[1-9]0)|[1-9]00)|[1-9]000)-(0[1-9]|1[0-2])-(0[1-9]|[1-2][0-9]|3[0-1])T([01][0-9]|2[0-3]):[0-5][0-9]:([0-5][0-9]|60)(\.[0-9]+)?(Z|(\+|-)((0[0-9]|1[0-3]):[0-5][0-9]|14:00))$`
### `PATIENT_PROFILE`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').meta.profile
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['http://shinny.org/us/ny/hrsn/StructureDefinition/shinny-patient']
### `RESOURCE_CONTENT_LANGUAGE`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').language
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['ar', 'bn', 'cs', 'da', 'de', 'de-at', 'de-ch', 'de-de', 'el', 'en', 'en-au', 'en-ca', 'en-gb', 'en-in', 'en-nz', 'en-sg', 'en-us', 'es', 'es-ar', 'es-es', 'es-uy', 'fi', 'fr', 'fr-be', 'fr-ch', 'fr-fr', 'fy', 'fy-nl', 'hi', 'hr', 'it', 'it-ch', 'it-it', 'ja', 'ko', 'nl', 'nl-be', 'nl-nl', 'no', 'no-no', 'pa', 'pl', 'pt', 'pt-br', 'ru', 'ru-ru', 'sr', 'sr-rs', 'sv', 'sv-se', 'te', 'zh', 'zh-cn', 'zh-hk', 'zh-sg', 'zh-tw']
### `RELATIONSHIP_PERSON_CODE`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').contact.relationship.coding.code
  - `type` string
  - `constraints`:
    - `enum` ['sel', 'spo', 'dom', 'chd', 'gch', 'nch', 'sch', 'fch', 'dep', 'wrd', 'par', 'mth', 'fth', 'cgv', 'grd', 'grp', 'exf', 'sib', 'bro', 'sis', 'fnd', 'oad', 'eme', 'emr', 'asc', 'emc', 'own', 'tra', 'mgr', 'non', 'unk', 'oth']
### `RELATIONSHIP_PERSON_DESCRIPTION`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').contact.relationship.coding.display
  - `type` string
  - `constraints`:
    - `enum` ['self', 'spouse', 'life partner', 'child', 'grandchild', 'natural child', 'stepchild', 'foster child', 'handicapped dependent', 'ward of court', 'parent', 'mother', 'father', 'care giver', 'guardian', 'grandparent', 'extended family', 'sibling', 'brother', 'sister', 'friend', 'other adult', 'employee', 'employer', 'associate', 'emergency contact', 'owner', 'trainer', 'manager', 'none', 'unknown', 'other']
### `RELATIONSHIP_PERSON_SYSTEM`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').contact.relationship.coding.system
  - `type` string
  - `constraints`:
    - `enum` ['http://terminology.hl7.org/CodeSystem/v2-0063']
### `RELATIONSHIP_PERSON_GIVEN_NAME`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').contact.name.given
  - `type` string
### `RELATIONSHIP_PERSON_FAMILY_NAME`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').contact.name.family
  - `type` string
### `RELATIONSHIP_PERSON_TELECOM_SYSTEM`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').contact.telecom.system
  - `type` string
### `RELATIONSHIP_PERSON_TELECOM_VALUE`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').contact.telecom.value
  - `type` string
### `PATIENT_TEXT_STATUS`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').text.status
  - `type` string
### `SEXUAL_ORIENTATION_VALUE_CODE`
  - `description` Bundle.entry.resource.where(resourceType = 'Observation').where(meta.profile = 'http://shinny.org/us/ny/hrsn/StructureDefinition/shin-ny-observation-sexual-orientation').valueCodeableConcept.coding.code
  - `type` string
  - `constraints`:
    - `enum` ['20430005', '38628009', '42035005', '765288000', 'oth', 'unk', 'asked-declined']
### `SEXUAL_ORIENTATION_VALUE_CODE_DESCRIPTION`
  - `description` Bundle.entry.resource.where(resourceType = 'Observation').where(meta.profile = 'http://shinny.org/us/ny/hrsn/StructureDefinition/shin-ny-observation-sexual-orientation').valueCodeableConcept.coding.display
  - `type` string
  - `constraints`:
    - `enum` ['heterosexual (finding)', 'homosexual (finding)', 'bisexual (finding)', 'sexually attracted to neither male nor female sex (finding)', 'other', 'unknown', 'asked but declined']
### `SEXUAL_ORIENTATION_VALUE_CODE_SYSTEM_NAME`
  - `description` Bundle.entry.resource.where(resourceType = 'Observation').where(meta.profile = 'http://shinny.org/us/ny/hrsn/StructureDefinition/shin-ny-observation-sexual-orientation').valueCodeableConcept.coding.system
  - `type` string
  - `constraints`:
    - `enum` ['http://snomed.info/sct', 'http://terminology.hl7.org/CodeSystem/v3-NullFlavor']
### `SEXUAL_ORIENTATION_LAST_UPDATED`
  - `description` Bundle.entry.resource.where(resourceType ='Observation').meta.lastUpdated
  - `type` string
  - `constraints`:
    - `pattern` `^([0-9]([0-9]([0-9][1-9]|[1-9]0)|[1-9]00)|[1-9]000)-(0[1-9]|1[0-2])-(0[1-9]|[1-2][0-9]|3[0-1])T([01][0-9]|2[0-3]):[0-5][0-9]:([0-5][0-9]|60)(\.[0-9]+)?(Z|(\+|-)((0[0-9]|1[0-3]):[0-5][0-9]|14:00))$`
### `SEXUAL_ORIENTATION_PROFILE`
  - `description` Bundle.entry.resource.where(resourceType ='Observation').meta.profile
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['http://shinny.org/us/ny/hrsn/StructureDefinition/shin-ny-observation-sexual-orientation']
### `SEXUAL_ORIENTATION_STATUS`
  - `description` Bundle.entry.resource.where(resourceType = 'Observation').where(meta.profile = 'http://shinny.org/us/ny/hrsn/StructureDefinition/shin-ny-observation-sexual-orientation').status
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['registered', 'preliminary', 'final', 'amended', 'corrected', 'cancelled', 'entered-in-error', 'unknown']
### `SEXUAL_ORIENTATION_TEXT_STATUS`
  - `description` Bundle.entry.resource.where(resourceType ='Observation').text.status
  - `type` string
### `SEXUAL_ORIENTATION_CODE_CODE`
  - `description` Bundle.entry.resource.where(resourceType = 'Observation').where(meta.profile = 'http://shinny.org/us/ny/hrsn/StructureDefinition/shin-ny-observation-sexual-orientation').code.coding.code
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['76690-7']
### `SEXUAL_ORIENTATION_CODE_DISPLAY`
  - `description` Bundle.entry.resource.where(resourceType = 'Observation').where(meta.profile = 'http://shinny.org/us/ny/hrsn/StructureDefinition/shin-ny-observation-sexual-orientation').code.coding.display
  - `type` string
### `SEXUAL_ORIENTATION_CODE_SYSTEM_NAME`
  - `description` Bundle.entry.resource.where(resourceType = 'Observation').where(meta.profile = 'http://shinny.org/us/ny/hrsn/StructureDefinition/shin-ny-observation-sexual-orientation').code.coding.system
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['http://loinc.org']