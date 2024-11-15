# `csv-validation-using-ig`
## `qe_admin_data`
  - `path` data/QE_ADMIN_DATA.csv
  - `schema`
      - `primaryKey` ['PARENT_MR_ID']
    - `foreignKeys` []
### `PARENT_MR_ID`
  - `type` string
  - `constraints`:
    - `required` True
    - `unique` True
    - `pattern` `^[A-Za-z0-9\-\.]{1,64}$`
### `FACILITY_ID`
  - `type` string
  - `constraints`:
    - `required` True
### `FACILITY_LONG_NAME`
  - `type` string
  - `constraints`:
    - `required` True
### `ORGANIZATION_TYPE`
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['prov', 'dept', 'team', 'govt', 'ins', 'pay', 'edu', 'reli', 'crs', 'cg', 'bus', 'other']
### `FACILITY_ADDRESS1`
  - `type` string
  - `constraints`:
    - `required` True
### `FACILITY_ADDRESS2`
  - `type` string
### `FACILITY_CITY`
  - `type` string
### `FACILITY_STATE`
  - `type` string
  - `constraints`:
    - `enum` ['ak', 'al', 'ar', 'as', 'az', 'ca', 'co', 'ct', 'dc', 'de', 'fl', 'fm', 'ga', 'gu', 'hi', 'ia', 'id', 'il', 'in', 'ks', 'ky', 'la', 'ma', 'md', 'me', 'mh', 'mi', 'mn', 'mo', 'mp', 'ms', 'mt', 'nc', 'nd', 'ne', 'nh', 'nj', 'nm', 'nv', 'ny', 'oh', 'ok', 'or', 'pa', 'pr', 'pw', 'ri', 'sc', 'sd', 'tn', 'tx', 'ut', 'va', 'vi', 'vt', 'wa', 'wi', 'wv', 'wy']
### `FACILITY_ZIP`
  - `type` string
### `FACILITY_LAST_UPDATED`
  - `type` string
  - `constraints`:
    - `pattern` `^([0-9]([0-9]([0-9][1-9]|[1-9]0)|[1-9]00)|[1-9]000)-(0[1-9]|1[0-2])-(0[1-9]|[1-2][0-9]|3[0-1])T([01][0-9]|2[0-3]):[0-5][0-9]:([0-5][0-9]|60)(\.[0-9]+)?(Z|(\+|-)((0[0-9]|1[0-3]):[0-5][0-9]|14:00))$`
### `FACILITY_SCN_IDENTIFIER_TYPE_CODE`
  - `type` string
### `FACILITY_SCN_IDENTIFIER_TYPE_VALUE`
  - `type` string
### `FACILITY_SCN_IDENTIFIER_TYPE_SYSTEM`
  - `type` string
  - `constraints`:
    - `pattern` `^http://www\.scn\.gov/.+`
### `FACILITY_NPI_IDENTIFIER_TYPE_CODE`
  - `type` string
### `FACILITY_NPI_IDENTIFIER_TYPE_VALUE`
  - `type` string
### `FACILITY_NPI_IDENTIFIER_TYPE_SYSTEM`
  - `type` string
  - `constraints`:
    - `pattern` `^http://hl7.org/fhir/sid/us-npi/.+`
### `FACILITY_CMS_IDENTIFIER_TYPE_CODE`
  - `type` string
### `FACILITY_CMS_IDENTIFIER_TYPE_VALUE`
  - `type` string
### `FACILITY_CMS_IDENTIFIER_TYPE_SYSTEM`
  - `type` string
  - `constraints`:
    - `pattern` `^http://www.medicaid.gov/.+`
## `screening_data`
  - `path` data/SCREENING.csv
  - `schema`
      - `foreignKeys`
      - [1]
        - `fields` ['PATIENT_MR_ID']
        - `reference`
          - `resource` qe_admin_data
          - `fields` ['PARENT_MR_ID']
    - `relationships`
      - [1]
        - `fields` ['ENCOUNTER_CLASS_CODE', 'ENCOUNTER_CLASS_CODE_DESCRIPTION']
        - `description` is the ENCOUNTER_CLASS_CODE,  ENCOUNTER_CLASS_CODE_DESCRIPTION of
        - `link` coupled
### `PATIENT_MR_ID`
  - `type` string
  - `constraints`:
    - `required` True
    - `pattern` `^[A-Za-z0-9\-\.]{1,64}$`
### `FACILITY_ID`
  - `type` string
  - `constraints`:
    - `required` True
    - `pattern` `^[A-Za-z0-9\-\.]{1,64}$`
### `ENCOUNTER_ID`
  - `type` string
  - `constraints`:
    - `required` True
    - `pattern` `^[A-Za-z0-9\-\.]{1,64}$`
### `ENCOUNTER_CLASS_CODE`
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['amb', 'emer', 'fld', 'hh', 'imp', 'acute', 'nonac', 'obsenc', 'prenc', 'ss', 'vr']
### `ENCOUNTER_CLASS_CODE_DESCRIPTION`
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['ambulatory', 'emergency', 'field', 'home health', 'inpatient encounter', 'inpatient acute', 'inpatient non-acute', 'observation encounter', 'pre-admission', 'short stay', 'virtual']
### `ENCOUNTER_CLASS_CODE_SYSTEM`
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['http://terminology.hl7.org/CodeSystem/v3-ActCode']
### `ENCOUNTER_STATUS_CODE`
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['planned', 'arrived', 'triaged', 'in-progress', 'onleave', 'finished', 'cancelled', 'entered-in-error', 'unknown']
### `ENCOUNTER_STATUS_CODE_DESCRIPTION`
  - `type` string
  - `constraints`:
    - `enum` ['planned', 'arrived', 'triaged', 'in progress', 'on leave', 'finished', 'cancelled', 'entered in Error', 'unknown']
### `ENCOUNTER_STATUS_CODE_SYSTEM`
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['http://terminology.hl7.org/CodeSystem/v3-ActCode']
### `ENCOUNTER_TYPE_CODE`
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['405672008', '23918007']
### `ENCOUNTER_TYPE_CODE_DESCRIPTION`
  - `type` string
### `ENCOUNTER_TYPE_CODE_SYSTEM`
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['http://snomed.info/sct']
### `ENCOUNTER_START_TIME`
  - `type` string
  - `constraints`:
    - `pattern` `([0-9]([0-9]([0-9][1-9]|[1-9]0)|[1-9]00)|[1-9]000)(-(0[1-9]|1[0-2])(-(0[1-9]|[1-2][0-9]|3[0-1])(T([01][0-9]|2[0-3]):[0-5][0-9]:([0-5][0-9]|60)(\.[0-9]+)?(Z|(\+|-)((0[0-9]|1[0-3]):[0-5][0-9]|14:00)))?)?)?`
### `ENCOUNTER_END_TIME`
  - `type` string
  - `constraints`:
    - `pattern` `([0-9]([0-9]([0-9][1-9]|[1-9]0)|[1-9]00)|[1-9]000)(-(0[1-9]|1[0-2])(-(0[1-9]|[1-2][0-9]|3[0-1])(T([01][0-9]|2[0-3]):[0-5][0-9]:([0-5][0-9]|60)(\.[0-9]+)?(Z|(\+|-)((0[0-9]|1[0-3]):[0-5][0-9]|14:00)))?)?)?`
### `ENCOUNTER_LAST_UPDATED`
  - `type` string
  - `constraints`:
    - `required` True
    - `pattern` `^([0-9]([0-9]([0-9][1-9]|[1-9]0)|[1-9]00)|[1-9]000)-(0[1-9]|1[0-2])-(0[1-9]|[1-2][0-9]|3[0-1])T([01][0-9]|2[0-3]):[0-5][0-9]:([0-5][0-9]|60)(\.[0-9]+)?(Z|(\+|-)((0[0-9]|1[0-3]):[0-5][0-9]|14:00))$`
### `LOCATION_NAME`
  - `type` string
### `LOCATION_STATUS`
  - `type` string
### `LOCATION_TYPE_CODE`
  - `type` string
### `LOCATION_TYPE_SYSTEM`
  - `type` string
### `LOCATION_ADDRESS1`
  - `type` string
### `LOCATION_ADDRESS2`
  - `type` string
### `LOCATION_CITY`
  - `type` string
### `LOCATION_DISTRICT`
  - `type` string
### `LOCATION_STATE`
  - `type` string
### `LOCATION_ZIP`
  - `type` string
### `LOCATION_PHYSICAL_TYPE_CODE`
  - `type` string
### `LOCATION_PHYSICAL_TYPE_SYSTEM`
  - `type` string
### `SCREENING_STATUS_CODE`
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['final', 'corrected', 'entered-in-error', 'unknown']
### `SCREENING_CODE`
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['96777-8', '97023-6']
### `SCREENING_CODE_DESCRIPTION`
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['accountable health communities (ahc) health-related social needs screening (hrsn) tool', 'accountable health communities (ahc) health-related social needs (hrsn) supplemental questions']
### `SCREENING_CODE_SYSTEM_NAME`
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['http://loinc.org']
### `RECORDED_TIME`
  - `type` string
  - `constraints`:
    - `required` True
    - `pattern` `([0-9]{4})-(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])T([01][0-9]|2[0-3]):([0-5][0-9]):([0-5][0-9]|60)(\.[0-9]+)?(Z|(\+|-)([01][0-9]|2[0-3]):([0-5][0-9]))`
### `QUESTION_CODE`
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['96777-8', '71802-3', '96778-6', '88122-7', '88123-5', '93030-5', '96779-4', '95618-5', '95617-7', '95616-9', '95615-1', '95614-4', '76513-1', '96780-2', '96781-0', '93159-2', '97027-7', '96782-8', '89555-7', '68516-4', '68517-2', '96842-0', '95530-2', '68524-8', '44250-9', '44255-8', '93038-8', '69858-9', '69861-3']
### `QUESTION_CODE_DESCRIPTION`
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['what is your living situation today?', 'think about the place you live. do you have problems with any of the following?', 'within the past 12 months, you worried that your food would run out before you got money to buy more.', "within the past 12 months, the food you bought just didn't last and you didn't have money to get more.", 'in the past 12 months, has lack of reliable transportation kept you from medical appointments, meetings, work or from getting things needed for daily living?', 'in the past 12 months has the electric, gas, oil, or water company threatened to shut off services in your home?', 'how often does anyone, including family and friends, physically hurt you?', 'how often does anyone, including family and friends, insult or talk down to you?', 'how often does anyone, including family and friends, threaten you with harm?', 'how often does anyone, including family and friends, scream or curse at you?', 'total safety score', 'how hard is it for you to pay for the very basics like food, housing, medical care, and heating? would you say it is', 'do you want help finding or keeping work or a job?', 'if for any reason you need help with day-to-day activities such as bathing, preparing meals, shopping, managing finances, etc., do you get the help you need?', 'how often do you feel lonely or isolated from those around you?', 'do you speak a language other than english at home?', 'do you want help with school or training? for example, starting or completing job training or getting a high school diploma, ged or equivalent.', 'in the last 30 days, other than the activities you did for work, on average, how many days per week did you engage in moderate exercise (like walking fast, running, jogging, dancing, swimming, biking, or other similar activities)', 'on average, how many minutes did you usually spend exercising at this level on one of those days?', 'how many times in the past 12 months have you had 5 or more drinks in a day (males) or 4 or more drinks in a day (females)?', 'how many times in the past 12 months have you used tobacco products (like cigarettes, cigars, snuff, chew, electronic cigarettes)?', 'how many times in the past year have you used prescription drugs for non-medical reasons?', 'how many times in the past year have you used illegal drugs?', 'little interest or pleasure in doing things?', 'feeling down, depressed, or hopeless?', 'stress means a situation in which a person feels tense, restless, nervous, or anxious, or is unable to sleep at night because his or her mind is troubled all the time. do you feel this kind of stress these days?', 'because of a physical, mental, or emotional condition, do you have serious difficulty concentrating, remembering, or making decisions?', "because of a physical, mental, or emotional condition, do you have difficulty doing errands alone such as visiting a physician's office or shopping"]
### `QUESTION_CODE_SYSTEM_NAME`
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['http://loinc.org']
### `UCUM_UNITS`
  - `type` string
### `SDOH_DOMAIN`
  - `type` string
  - `constraints`:
    - `pattern` `^(sdoh-category-unspecified|food-insecurity|housing-instability|homelessness|inadequate-housing|transportation-insecurity|financial-insecurity|material-hardship|educational-attainment|employment-status|veteran-status|stress|social-connection|intimate-partner-violence|elder-abuse|personal-health-literacy|health-insurance-coverage-status|medical-cost-burden|digital-literacy|digital-access|utility-insecurity)(,\s*(sdoh-category-unspecified|food-insecurity|housing-instability|homelessness|inadequate-housing|transportation-insecurity|financial-insecurity|material-hardship|educational-attainment|employment-status|veteran-status|stress|social-connection|intimate-partner-violence|elder-abuse|personal-health-literacy|health-insurance-coverage-status|medical-cost-burden|digital-literacy|digital-access|utility-insecurity))*$`
### `PARENT_QUESTION_CODE`
  - `type` string
  - `constraints`:
    - `enum` ['96777-8', '71802-3', '96778-6', '88122-7', '88123-5', '93030-5', '96779-4', '95618-5', '95617-7', '95616-9', '95615-1', '95614-4', '76513-1', '96780-2', '96781-0', '93159-2', '97027-7', '96782-8', '89555-7', '68516-4', '68517-2', '96842-0', '95530-2', '68524-8', '44250-9', '44255-8', '93038-8', '69858-9', '69861-3']
### `ANSWER_CODE`
  - `type` string
  - `constraints`:
    - `enum` ['la31993-1', 'la31994-9', 'la31995-6', 'la31996-4', 'la28580-1', 'la31997-2', 'la31998-0', 'la31999-8', 'la32000-4', 'la32001-2', 'la9-3', 'la28397-0', 'la6729-3', 'la28398-8', 'la28397-0', 'la6729-3', 'la28398-8', 'la33-6', 'la32-8', 'la33-6', 'la32-8', 'la32002-0', 'la6270-8', 'la10066-1', 'la10082-8', 'la16644-9', 'la6482-9', 'la6270-8', 'la10066-1', 'la10082-8', 'la16644-9', 'la6482-9', 'la6270-8', 'la10066-1', 'la10082-8', 'la16644-9', 'la6482-9', 'la6270-8', 'la10066-1', 'la10082-8', 'la16644-9', 'la6482-9', 'la15832-1', 'la22683-9', 'la31980-8', 'la31981-6', 'la31982-4', 'la31983-2', 'la31976-6', 'la31977-4', 'la31978-2', 'la31979-0', 'la6270-8', 'la10066-1', 'la10082-8', 'la10044-8', 'la9933-8', 'la33-6', 'la32-8', 'la33-6', 'la32-8', 'la6111-4', 'la6112-2', 'la6113-0', 'la6114-8', 'la6115-5', 'la10137-0', 'la10138-8', 'la10139-6', 'la6111-4', 'la13942-0', 'la19282-5', 'la28855-7', 'la28858-1', 'la28854-0', 'la28853-2', 'la28891-2', 'la32059-0', 'la32060-8', 'la6270-8', 'la26460-8', 'la18876-5', 'la18891-4', 'la18934-2', 'la6270-8', 'la26460-8', 'la18876-5', 'la18891-4', 'la18934-2', 'la6270-8', 'la26460-8', 'la18876-5', 'la18891-4', 'la18934-2', 'la6270-8', 'la26460-8', 'la18876-5', 'la18891-4', 'la18934-2', 'la6568-5', 'la6569-3', 'la6570-1', 'la6571-9', 'la6568-5', 'la6569-3', 'la6570-1', 'la6571-9', 'la6568-5', 'la13863-8', 'la13909-9', 'la13902-4', 'la13914-9', 'la30122-8', 'la33-6', 'la32-8', 'la33-6', 'la32-8']
### `ANSWER_CODE_DESCRIPTION`
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['i have a steady place to live', 'i have a place to live today, but i am worried about losing it in the future', 'i do not have a steady place to live (i am temporarily staying with others, in a hotel, in a shelter,living outside on the street, on a beach, in a car, abandoned building, bus or train station, or in a park)', 'pests such as bugs, ants, or mice', 'mold', 'lead paint or pipes', 'lack of heat', 'oven or stove not working', 'smoke detectors missing or not working', 'water leaks', 'none of the above', 'often true', 'sometimes true', 'never true', 'often true', 'sometimes true', 'never true', 'yes', 'no', 'yes', 'no', 'already shut off', 'never (1)', 'rarely (2)', 'sometimes (3)', 'fairly often (4)', 'frequently (5)', 'never (1)', 'rarely (2)', 'sometimes (3)', 'fairly often (4)', 'frequently (5)', 'never (1)', 'rarely (2)', 'sometimes (3)', 'fairly often (4)', 'frequently (5)', 'never (1)', 'rarely (2)', 'sometimes (3)', 'fairly often (4)', 'frequently (5)', 'very hard', 'somewhat hard', 'not hard at all', 'yes, help finding work', 'yes, help keeping work', 'i do not need or want help', "i don't need any help", 'i get all the help i need', 'i could use a little more help', 'i need a lot more help', 'never', 'rarely', 'sometimes', 'often', 'always', 'yes', 'no', 'yes', 'no', 0, 1, 2, 3, 4, 5, 6, 7, 0, 10, 20, 30, 40, 50, 60, 90, 120, '150 or greater', 'never', 'once or twice', 'monthly', 'weekly', 'daily or almost daily', 'never', 'once or twice', 'monthly', 'weekly', 'daily or almost daily', 'never', 'once or twice', 'monthly', 'weekly', 'daily or almost daily', 'never', 'once or twice', 'monthly', 'weekly', 'daily or almost daily', 'not at all (0)', 'several days (1)', 'more than half the days (2)', 'nearly every day (3)', 'not at all (0)', 'several days (1)', 'more than half the days (2)', 'nearly every day (3)', 'not at all', 'a little bit', 'somewhat', 'quite a bit', 'very much', 'i choose not to answer this question', 'yes', 'no', 'yes', 'no']
### `ANSWER_CODE_SYSTEM_NAME`
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['http://loinc.org']
## `demographic_data`
  - `path` data/DEMOGRAPHIC_DATA.csv
  - `schema`
      - `foreignKeys`
      - [1]
        - `fields` ['PATIENT_MR_ID']
        - `reference`
          - `resource` qe_admin_data
          - `fields` ['PARENT_MR_ID']
### `PATIENT_MR_ID`
  - `type` string
  - `constraints`:
    - `required` True
    - `pattern` `^[A-Za-z0-9\-\.]{1,64}$`
### `FACILITY_ID`
  - `type` string
  - `constraints`:
    - `required` True
    - `pattern` `^[A-Za-z0-9\-\.]{1,64}$`
### `CONSENT_STATUS`
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['draft', 'proposed', 'active', 'rejected', 'inactive', 'entered-in-error']
### `CONSENT_TIME`
  - `type` string
  - `constraints`:
    - `required` True
    - `pattern` `([0-9]([0-9]([0-9][1-9]|[1-9]0)|[1-9]00)|[1-9]000)(-(0[1-9]|1[0-2])(-(0[1-9]|[1-2][0-9]|3[0-1])(T([01][0-9]|2[0-3]):[0-5][0-9]:([0-5][0-9]|60)(\.[0-9]+)?(Z|(\+|-)((0[0-9]|1[0-3]):[0-5][0-9]|14:00)))?)?)?`
### `GIVEN_NAME`
  - `type` string
  - `constraints`:
    - `required` True
    - `minLength` 1
    - `pattern` `^[A-Za-z]+$`
### `MIDDLE_NAME`
  - `type` string
  - `constraints`:
    - `pattern` `^[A-Za-z]+$`
### `FAMILY_NAME`
  - `type` string
  - `constraints`:
    - `required` True
    - `pattern` `^[A-Za-z]+$`
### `GENDER`
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['male', 'female', 'other', 'unknown']
### `SEX_AT_BIRTH_CODE`
  - `type` string
  - `constraints`:
    - `required` True
    - `pattern` `^[^\s]+(\s[^\s]+)*$`
    - `enum` ['f', 'm', 'unk']
### `SEX_AT_BIRTH_CODE_DESCRIPTION`
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['female', 'male', 'unknown']
### `SEX_AT_BIRTH_CODE_SYSTEM`
  - `type` string
  - `constraints`:
    - `enum` ['http://terminology.hl7.org/CodeSystem/v3-NullFlavor', 'http://terminology.hl7.org/CodeSystem/v3-AdministrativeGender', 'http://hl7.org/fhir/us/core/structuredefinition/us-core-birthsex']
### `PATIENT_BIRTH_DATE`
  - `type` string
  - `constraints`:
    - `required` True
    - `pattern` `^([0-9]([0-9]([0-9][1-9]|[1-9]0)|[1-9]00)|[1-9]000)(-(0[1-9]|1[0-2])(-(0[1-9]|[1-2][0-9]|3[0-1]))?)?$`
### `ADDRESS1`
  - `type` string
  - `constraints`:
    - `pattern` `.*\d.*`
### `ADDRESS2`
  - `type` string
### `CITY`
  - `type` string
### `DISTRICT`
  - `type` string
### `STATE`
  - `type` string
  - `constraints`:
    - `enum` ['ak', 'al', 'ar', 'as', 'az', 'ca', 'co', 'ct', 'dc', 'de', 'fl', 'fm', 'ga', 'gu', 'hi', 'ia', 'id', 'il', 'in', 'ks', 'ky', 'la', 'ma', 'md', 'me', 'mh', 'mi', 'mn', 'mo', 'mp', 'ms', 'mt', 'nc', 'nd', 'ne', 'nh', 'nj', 'nm', 'nv', 'ny', 'oh', 'ok', 'or', 'pa', 'pr', 'pw', 'ri', 'sc', 'sd', 'tn', 'tx', 'ut', 'va', 'vi', 'vt', 'wa', 'wi', 'wv', 'wy']
### `ZIP`
  - `type` string
  - `constraints`:
    - `pattern` `^\d{5}(\d{4})?$`
### `PHONE`
  - `type` string
### `SSN`
  - `type` string
### `PERSONAL_PRONOUNS_CODE`
  - `type` string
  - `constraints`:
    - `enum` ['LA29518-0', 'LA29519-8', 'LA29520-6', 'oth', 'unk']
### `PERSONAL_PRONOUNS_CODE_DESCRIPTION`
  - `type` string
  - `constraints`:
    - `enum` ['he/him/his/his/himself', 'she/her/her/hers/herself', 'they/them/their/theirs/themselves', 'other', 'unknown']
### `PERSONAL_PRONOUNS_CODE_SYSTEM_NAME`
  - `type` string
  - `constraints`:
    - `enum` ['http://loinc.org/', 'http://loinc.org', 'http://terminology.hl7.org/CodeSystem/v3-NullFlavor']
### `GENDER_IDENTITY_CODE`
  - `type` string
  - `constraints`:
    - `enum` ['33791000087105', '407376001', '407377005', '446131000124102', '446141000124107', '446151000124109', 'oth', 'unk', 'asked-declined']
### `GENDER_IDENTITY_CODE_DESCRIPTION`
  - `type` string
  - `constraints`:
    - `enum` ['identifies as nonbinary gender (finding)', 'male-to-female transsexual (finding)', 'female-to-male transsexual (finding)', 'identifies as non-conforming gender (finding)', 'identifies as female gender (finding)', 'identifies as male gender (finding)', 'other', 'unknown', 'asked but declined']
### `GENDER_IDENTITY_CODE_SYSTEM_NAME`
  - `type` string
  - `constraints`:
    - `enum` ['http://terminology.hl7.org/CodeSystem/v3-NullFlavor', 'http://terminology.hl7.org/CodeSystem/data-absent-reason', 'http://snomed.info/sct', 'http://shinny.org/us/ny/hrsn/StructureDefinition/shinny-gender-identity']
### `SEXUAL_ORIENTATION_CODE`
  - `type` string
  - `constraints`:
    - `required` True
### `SEXUAL_ORIENTATION_CODE_DESCRIPTION`
  - `type` string
### `SEXUAL_ORIENTATION_CODE_SYSTEM_NAME`
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['http://loinc.org']
### `PREFERRED_LANGUAGE_CODE`
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['aar', 'abk', 'ace', 'ach', 'ada', 'ady', 'afa', 'afh', 'afr', 'ain', 'aka', 'akk', 'alb (b)', 'sqi (t)', 'ale', 'alg', 'alt', 'amh', 'ang', 'anp', 'apa', 'ara', 'arc', 'arg', 'arm (b)', 'hye (t)', 'arn', 'arp', 'art', 'arw', 'asm', 'ast', 'ath', 'aus', 'ava', 'ave', 'awa', 'aym', 'aze', 'bad', 'bai', 'bak', 'bal', 'bam', 'ban', 'baq (b)', 'eus (t)', 'bas', 'bat', 'bej', 'bel', 'bem', 'ben', 'ber', 'bho', 'bih', 'bik', 'bin', 'bis', 'bla', 'bnt', 'tib (b)', 'bod (t)', 'bos', 'bra', 'bre', 'btk', 'bua', 'bug', 'bul', 'bur (b)', 'mya (t)', 'byn', 'cad', 'cai', 'car', 'cat', 'cau', 'ceb', 'cel', 'cze (b)', 'ces (t)', 'cha', 'chb', 'che', 'chg', 'chi (b)', 'zho (t)', 'chk', 'chm', 'chn', 'cho', 'chp', 'chr', 'chu', 'chv', 'chy', 'cmc', 'cnr', 'cop', 'cor', 'cos', 'cpe', 'cpf', 'cpp', 'cre', 'crh', 'crp', 'csb', 'cus', 'wel (b)', 'cym (t)', 'cze (b)', 'ces (t)', 'dak', 'dan', 'dar', 'day', 'del', 'den', 'ger (b)', 'deu (t)', 'dgr', 'din', 'div', 'doi', 'dra', 'dsb', 'dua', 'dum', 'dut (b)', 'nld (t)', 'dyu', 'dzo', 'efi', 'egy', 'eka', 'gre (b)', 'ell (t)', 'elx', 'eng', 'en', 'enm', 'epo', 'est', 'baq (b)', 'eus (t)', 'ewe', 'ewo', 'fan', 'fao', 'per (b)', 'fas (t)', 'fat', 'fij', 'fil', 'fin', 'fiu', 'fon', 'fre (b)', 'fra (t)', 'fre (b)', 'fra (t)', 'frm', 'fro', 'frr', 'frs', 'fry', 'ful', 'fur', 'gaa', 'gay', 'gba', 'gem', 'geo (b)', 'kat (t)', 'ger (b)', 'deu (t)', 'gez', 'gil', 'gla', 'gle', 'glg', 'glv', 'gmh', 'goh', 'gon', 'gor', 'got', 'grb', 'grc', 'gre (b)', 'ell (t)', 'grn', 'gsw', 'guj', 'gwi', 'hai', 'hat', 'hau', 'haw', 'heb', 'her', 'hil', 'him', 'hin', 'hit', 'hmn', 'hmo', 'hrv', 'hsb', 'hun', 'hup', 'arm (b)', 'hye (t)', 'iba', 'ibo', 'ice (b)', 'isl (t)', 'ido', 'iii', 'ijo', 'iku', 'ile', 'ilo', 'ina', 'inc', 'ind', 'ine', 'inh', 'ipk', 'ira', 'iro', 'ice (b)', 'isl (t)', 'ita', 'jav', 'jbo', 'jpn', 'jpr', 'jrb', 'kaa', 'kab', 'kac', 'kal', 'kam', 'kan', 'kar', 'kas', 'geo (b)', 'kat (t)', 'kau', 'kaw', 'kaz', 'kbd', 'kha', 'khi', 'khm', 'kho', 'kik', 'kin', 'kir', 'kmb', 'kok', 'kom', 'kon', 'kor', 'kos', 'kpe', 'krc', 'krl', 'kro', 'kru', 'kua', 'kum', 'kur', 'kut', 'lad', 'lah', 'lam', 'lao', 'lat', 'lav', 'lez', 'lim', 'lin', 'lit', 'lol', 'loz', 'ltz', 'lua', 'lub', 'lug', 'lui', 'lun', 'luo', 'lus', 'mac (b)', 'mkd (t)', 'mad', 'mag', 'mah', 'mai', 'mak', 'mal', 'man', 'mao (b)', 'mri (t)', 'map', 'mar', 'mas', 'may (b)', 'msa (t)', 'mdf', 'mdr', 'men', 'mga', 'mic', 'min', 'mis', 'mac (b)', 'mkd (t)', 'mkh', 'mlg', 'mlt', 'mnc', 'mni', 'mno', 'moh', 'mon', 'mos', 'mao (b)', 'mri (t)', 'may (b)', 'msa (t)', 'mul', 'mun', 'mus', 'mwl', 'mwr', 'bur (b)', 'mya (t)', 'myn', 'myv', 'nah', 'nai', 'nap', 'nau', 'nav', 'nbl', 'nde', 'ndo', 'nds', 'nep', 'new', 'nia', 'nic', 'niu', 'dut (b)', 'nld (t)', 'nno', 'nob', 'nog', 'non', 'nor', 'nqo', 'nso', 'nub', 'nwc', 'nya', 'nym', 'nyn', 'nyo', 'nzi', 'oci', 'oji', 'ori', 'orm', 'osa', 'oss', 'ota', 'oto', 'paa', 'pag', 'pal', 'pam', 'pan', 'pap', 'pau', 'peo', 'per (b)', 'fas (t)', 'phi', 'phn', 'pli', 'pol', 'pon', 'por', 'pra', 'pro', 'pus', 'qaa-qtz', 'que', 'raj', 'rap', 'rar', 'roa', 'roh', 'rom', 'rum (b)', 'ron (t)', 'rum (b)', 'ron (t)', 'run', 'rup', 'rus', 'sad', 'sag', 'sah', 'sai', 'sal', 'sam', 'san', 'sas', 'sat', 'scn', 'sco', 'sel', 'sem', 'sga', 'sgn', 'shn', 'sid', 'sin', 'sio', 'sit', 'sla', 'slo (b)', 'slk (t)', 'slo (b)', 'slk (t)', 'slv', 'sma', 'sme', 'smi', 'smj', 'smn', 'smo', 'sms', 'sna', 'snd', 'snk', 'sog', 'som', 'son', 'sot', 'spa', 'alb (b)', 'sqi (t)', 'srd', 'srn', 'srp', 'srr', 'ssa', 'ssw', 'suk', 'sun', 'sus', 'sux', 'swa', 'swe', 'syc', 'syr', 'tah', 'tai', 'tam', 'tat', 'tel', 'tem', 'ter', 'tet', 'tgk', 'tgl', 'tha', 'tib (b)', 'bod (t)', 'tig', 'tir', 'tiv', 'tkl', 'tlh', 'tli', 'tmh', 'tog', 'ton', 'tpi', 'tsi', 'tsn', 'tso', 'tuk', 'tum', 'tup', 'tur', 'tut', 'tvl', 'twi', 'tyv', 'udm', 'uga', 'uig', 'ukr', 'umb', 'und', 'urd', 'uzb', 'vai', 'ven', 'vie', 'vol', 'vot', 'wak', 'wal', 'war', 'was', 'wel (b)', 'cym (t)', 'wen', 'wln', 'wol', 'xal', 'xho', 'yao', 'yap', 'yid', 'yor', 'ypk', 'zap', 'zbl', 'zen', 'zgh', 'zha', 'chi (b)', 'zho (t)', 'znd', 'zul', 'zun', 'zxx', 'zza']
### `PREFERRED_LANGUAGE_CODE_DESCRIPTION`
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['afar', 'abkhazian', 'achinese', 'acoli', 'adangme', 'adyghe; adygei', 'afro-asiatic languages', 'afrihili', 'afrikaans', 'ainu', 'akan', 'akkadian', 'albanian', 'aleut', 'algonquian languages', 'southern altai', 'amharic', 'english, old (ca.450-1100)', 'angika', 'apache languages', 'arabic', 'official aramaic (700-300 bce); imperial aramaic (700-300 bce)', 'aragonese', 'armenian', 'mapudungun; mapuche', 'arapaho', 'artificial languages', 'arawak', 'assamese', 'asturian; bable; leonese; asturleonese', 'athapascan languages', 'australian languages', 'avaric', 'avestan', 'awadhi', 'aymara', 'azerbaijani', 'banda languages', 'bamileke languages', 'bashkir', 'baluchi', 'bambara', 'balinese', 'basque', 'basa', 'baltic languages', 'beja; bedawiyet', 'belarusian', 'bemba', 'bengali', 'berber languages', 'bhojpuri', 'bihari languages', 'bikol', 'bini; edo', 'bislama', 'siksika', 'bantu languages', 'tibetan', 'bosnian', 'braj', 'breton', 'batak languages', 'buriat', 'buginese', 'bulgarian', 'burmese', 'blin; bilin', 'caddo', 'central american indian languages', 'galibi carib', 'catalan; valencian', 'caucasian languages', 'cebuano', 'celtic languages', 'czech', 'chamorro', 'chibcha', 'chechen', 'chagatai', 'chinese', 'chuukese', 'mari', 'chinook jargon', 'choctaw', 'chipewyan; dene suline', 'cherokee', 'church slavic; old slavonic; church slavonic; old bulgarian; old church slavonic', 'chuvash', 'cheyenne', 'chamic languages', 'montenegrin', 'coptic', 'cornish', 'corsican', 'creoles and pidgins, english based', 'creoles and pidgins, french-based', 'creoles and pidgins, portuguese-based', 'cree', 'crimean tatar; crimean turkish', 'creoles and pidgins', 'kashubian', 'cushitic languages', 'welsh', 'czech', 'dakota', 'danish', 'dargwa', 'land dayak languages', 'delaware', 'slave (athapascan)', 'german', 'dogrib', 'dinka', 'divehi; dhivehi; maldivian', 'dogri', 'dravidian languages', 'lower sorbian', 'duala', 'dutch, middle (ca.1050-1350)', 'dutch; flemish', 'dyula', 'dzongkha', 'efik', 'egyptian (ancient)', 'ekajuk', 'greek, modern (1453-)', 'elamite', 'english', 'english, middle (1100-1500)', 'esperanto', 'estonian', 'basque', 'ewe', 'ewondo', 'fang', 'faroese', 'persian', 'fanti', 'fijian', 'filipino; pilipino', 'finnish', 'finno-ugrian languages', 'fon', 'french', 'french', 'french, middle (ca.1400-1600)', 'french, old (842-ca.1400)', 'northern frisian', 'eastern frisian', 'western frisian', 'fulah', 'friulian', 'ga', 'gayo', 'gbaya', 'germanic languages', 'georgian', 'german', 'geez', 'gilbertese', 'gaelic; scottish gaelic', 'irish', 'galician', 'manx', 'german, middle high (ca.1050-1500)', 'german, old high (ca.750-1050)', 'gondi', 'gorontalo', 'gothic', 'grebo', 'greek, ancient (to 1453)', 'greek, modern (1453-)', 'guarani', 'swiss german; alemannic; alsatian', 'gujarati', "gwich'in", 'haida', 'haitian; haitian creole', 'hausa', 'hawaiian', 'hebrew', 'herero', 'hiligaynon', 'himachali languages; western pahari languages', 'hindi', 'hittite', 'hmong; mong', 'hiri motu', 'croatian', 'upper sorbian', 'hungarian', 'hupa', 'armenian', 'iban', 'igbo', 'icelandic', 'ido', 'sichuan yi; nuosu', 'ijo languages', 'inuktitut', 'interlingue; occidental', 'iloko', 'interlingua (international auxiliary language association)', 'indic languages', 'indonesian', 'indo-european languages', 'ingush', 'inupiaq', 'iranian languages', 'iroquoian languages', 'icelandic', 'italian', 'javanese', 'lojban', 'japanese', 'judeo-persian', 'judeo-arabic', 'kara-kalpak', 'kabyle', 'kachin; jingpho', 'kalaallisut; greenlandic', 'kamba', 'kannada', 'karen languages', 'kashmiri', 'georgian', 'kanuri', 'kawi', 'kazakh', 'kabardian', 'khasi', 'khoisan languages', 'central khmer', 'khotanese; sakan', 'kikuyu; gikuyu', 'kinyarwanda', 'kirghiz; kyrgyz', 'kimbundu', 'konkani', 'komi', 'kongo', 'korean', 'kosraean', 'kpelle', 'karachay-balkar', 'karelian', 'kru languages', 'kurukh', 'kuanyama; kwanyama', 'kumyk', 'kurdish', 'kutenai', 'ladino', 'lahnda', 'lamba', 'lao', 'latin', 'latvian', 'lezghian', 'limburgan; limburger; limburgish', 'lingala', 'lithuanian', 'mongo', 'lozi', 'luxembourgish; letzeburgesch', 'luba-lulua', 'luba-katanga', 'ganda', 'luiseno', 'lunda', 'luo (kenya and tanzania)', 'lushai', 'macedonian', 'madurese', 'magahi', 'marshallese', 'maithili', 'makasar', 'malayalam', 'mandingo', 'maori', 'austronesian languages', 'marathi', 'masai', 'malay', 'moksha', 'mandar', 'mende', 'irish, middle (900-1200)', "mi'kmaq; micmac", 'minangkabau', 'uncoded languages', 'macedonian', 'mon-khmer languages', 'malagasy', 'maltese', 'manchu', 'manipuri', 'manobo languages', 'mohawk', 'mongolian', 'mossi', 'maori', 'malay', 'multiple languages', 'munda languages', 'creek', 'mirandese', 'marwari', 'burmese', 'mayan languages', 'erzya', 'nahuatl languages', 'north american indian languages', 'neapolitan', 'nauru', 'navajo; navaho', 'ndebele, south; south ndebele', 'ndebele, north; north ndebele', 'ndonga', 'low german; low saxon; german, low; saxon, low', 'nepali', 'nepal bhasa; newari', 'nias', 'niger-kordofanian languages', 'niuean', 'dutch; flemish', 'norwegian nynorsk; nynorsk, norwegian', 'bokmål, norwegian; norwegian bokmål', 'nogai', 'norse, old', 'norwegian', "n'ko", 'pedi; sepedi; northern sotho', 'nubian languages', 'classical newari; old newari; classical nepal bhasa', 'chichewa; chewa; nyanja', 'nyamwezi', 'nyankole', 'nyoro', 'nzima', 'occitan (post 1500)', 'ojibwa', 'oriya', 'oromo', 'osage', 'ossetian; ossetic', 'turkish, ottoman (1500-1928)', 'otomian languages', 'papuan languages', 'pangasinan', 'pahlavi', 'pampanga; kapampangan', 'panjabi; punjabi', 'papiamento', 'palauan', 'persian, old (ca.600-400 b.c.)', 'persian', 'philippine languages', 'phoenician', 'pali', 'polish', 'pohnpeian', 'portuguese', 'prakrit languages', 'provençal, old (to 1500); occitan, old (to 1500)', 'pushto; pashto', 'reserved for local use', 'quechua', 'rajasthani', 'rapanui', 'rarotongan; cook islands maori', 'romance languages', 'romansh', 'romany', 'romanian; moldavian; moldovan', 'romanian; moldavian; moldovan', 'rundi', 'aromanian; arumanian; macedo-romanian', 'russian', 'sandawe', 'sango', 'yakut', 'south american indian languages', 'salishan languages', 'samaritan aramaic', 'sanskrit', 'sasak', 'santali', 'sicilian', 'scots', 'selkup', 'semitic languages', 'irish, old (to 900)', 'sign languages', 'shan', 'sidamo', 'sinhala; sinhalese', 'siouan languages', 'sino-tibetan languages', 'slavic languages', 'slovak', 'slovak', 'slovenian', 'southern sami', 'northern sami', 'sami languages', 'lule sami', 'inari sami', 'samoan', 'skolt sami', 'shona', 'sindhi', 'soninke', 'sogdian', 'somali', 'songhai languages', 'sotho, southern', 'spanish; castilian', 'albanian', 'sardinian', 'sranan tongo', 'serbian', 'serer', 'nilo-saharan languages', 'swati', 'sukuma', 'sundanese', 'susu', 'sumerian', 'swahili', 'swedish', 'classical syriac', 'syriac', 'tahitian', 'tai languages', 'tamil', 'tatar', 'telugu', 'timne', 'tereno', 'tetum', 'tajik', 'tagalog', 'thai', 'tibetan', 'tigre', 'tigrinya', 'tiv', 'tokelau', 'klingon; tlhingan-hol', 'tlingit', 'tamashek', 'tonga (nyasa)', 'tonga (tonga islands)', 'tok pisin', 'tsimshian', 'tswana', 'tsonga', 'turkmen', 'tumbuka', 'tupi languages', 'turkish', 'altaic languages', 'tuvalu', 'twi', 'tuvinian', 'udmurt', 'ugaritic', 'uighur; uyghur', 'ukrainian', 'umbundu', 'undetermined', 'urdu', 'uzbek', 'vai', 'venda', 'vietnamese', 'volapük', 'votic', 'wakashan languages', 'wolaitta; wolaytta', 'waray', 'washo', 'welsh', 'sorbian languages', 'walloon', 'wolof', 'kalmyk; oirat', 'xhosa', 'yao', 'yapese', 'yiddish', 'yoruba', 'yupik languages', 'zapotec', 'blissymbols; blissymbolics; bliss', 'zenaga', 'standard moroccan tamazight', 'zhuang; chuang', 'chinese', 'zande languages', 'zulu', 'zuni', 'no linguistic content; not applicable', 'zaza; dimili; dimli; kirdki; kirmanjki; zazaki']
### `PREFERRED_LANGUAGE_CODE_SYSTEM_NAME`
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['iso', 'iso 639-2', 'http://hl7.org/fhir/us/core/valueset/simple-language', 'urn:ietf:bcp:47']
### `RACE_CODE`
  - `type` string
  - `constraints`:
    - `enum` ['1002-5', '2028-9', '2054-5', '2076-8', '2106-3', '2131-1', 'asku', 'unk', '1004-1', '1006-6', '1008-2', '1010-8', '1011-6', '1012-4', '1013-2', '1014-0', '1015-7', '1016-5', '1017-3', '1018-1', '1019-9', '1021-5', '1022-3', '1023-1', '1024-9', '1026-4', '1028-0', '1030-6', '1031-4', '1033-0', '1035-5', '1037-1', '1039-7', '1041-3', '1042-1', '1044-7', '1045-4', '1046-2', '1047-0', '1048-8', '1049-6', '1050-4', '1051-2', '1053-8', '1054-6', '1055-3', '1056-1', '1057-9', '1058-7', '1059-5', '1060-3', '1061-1', '1062-9', '1063-7', '1064-5', '1065-2', '1066-0', '1068-6', '1069-4', '1070-2', '1071-0', '1072-8', '1073-6', '1074-4', '1076-9', '1078-5', '1080-1', '1082-7', '1083-5', '1084-3', '1086-8', '1088-4', '1089-2', '1090-0', '1091-8', '1092-6', '1093-4', '1094-2', '1095-9', '1096-7', '1097-5', '1098-3', '1100-7', '1102-3', '1103-1', '1104-9', '1106-4', '1108-0', '1109-8', '1110-6', '1112-2', '1114-8', '1115-5', '1116-3', '1117-1', '1118-9', '1119-7', '1120-5', '1121-3', '1123-9', '1124-7', '1125-4', '1126-2', '1127-0', '1128-8', '1129-6', '1130-4', '1131-2', '1132-0', '1133-8', '1134-6', '1135-3', '1136-1', '1137-9', '1138-7', '1139-5', '1140-3', '1141-1', '1142-9', '1143-7', '1144-5', '1145-2', '1146-0', '1147-8', '1148-6', '1150-2', '1151-0', '1153-6', '1155-1', '1156-9', '1157-7', '1158-5', '1159-3', '1160-1', '1162-7', '1163-5', '1165-0', '1167-6', '1169-2', '1171-8', '1173-4', '1175-9', '1176-7', '1178-3', '1180-9', '1182-5', '1184-1', '1186-6', '1187-4', '1189-0', '1191-6', '1193-2', '1194-0', '1195-7', '1196-5', '1197-3', '1198-1', '1199-9', '1200-5', '1201-3', '1202-1', '1203-9', '1204-7', '1205-4', '1207-0', '1209-6', '1211-2', '1212-0', '1214-6', '1215-3', '1216-1', '1217-9', '1218-7', '1219-5', '1220-3', '1222-9', '1223-7', '1224-5', '1225-2', '1226-0', '1227-8', '1228-6', '1229-4', '1230-2', '1231-0', '1233-6', '1234-4', '1235-1', '1236-9', '1237-7', '1238-5', '1239-3', '1240-1', '1241-9', '1242-7', '1243-5', '1244-3', '1245-0', '1246-8', '1247-6', '1248-4', '1250-0', '1252-6', '1254-2', '1256-7', '1258-3', '1260-9', '1262-5', '1264-1', '1265-8', '1267-4', '1269-0', '1271-6', '1272-4', '1273-2', '1275-7', '1277-3', '1279-9', '1281-5', '1282-3', '1283-1', '1285-6', '1286-4', '1287-2', '1288-0', '1289-8', '1290-6', '1291-4', '1292-2', '1293-0', '1294-8', '1295-5', '1297-1', '1299-7', '1301-1', '1303-7', '1305-2', '1306-0', '1307-8', '1309-4', '1310-2', '1312-8', '1313-6', '1314-4', '1315-1', '1317-7', '1319-3', '1321-9', '1323-5', '1325-0', '1326-8', '1327-6', '1328-4', '1329-2', '1331-8', '1332-6', '1333-4', '1334-2', '1335-9', '1336-7', '1337-5', '1338-3', '1340-9', '1342-5', '1344-1', '1345-8', '1346-6', '1348-2', '1350-8', '1352-4', '1354-0', '1356-5', '1358-1', '1359-9', '1360-7', '1361-5', '1363-1', '1365-6', '1366-4', '1368-0', '1370-6', '1372-2', '1374-8', '1376-3', '1378-9', '1380-5', '1382-1', '1383-9', '1384-7', '1385-4', '1387-0', '1389-6', '1391-2', '1392-0', '1393-8', '1394-6', '1395-3', '1396-1', '1397-9', '1398-7', '1399-5', '1400-1', '1401-9', '1403-5', '1405-0', '1407-6', '1409-2', '1411-8', '1412-6', '1413-4', '1414-2', '1416-7', '1417-5', '1418-3', '1419-1', '1420-9', '1421-7', '1422-5', '1423-3', '1424-1', '1425-8', '1426-6', '1427-4', '1428-2', '1429-0', '1430-8', '1431-6', '1432-4', '1433-2', '1434-0', '1435-7', '1436-5', '1437-3', '1439-9', '1441-5', '1442-3', '1443-1', '1445-6', '1446-4', '1448-0', '1450-6', '1451-4', '1453-0', '1454-8', '1456-3', '1457-1', '1458-9', '1460-5', '1462-1', '1464-7', '1465-4', '1466-2', '1467-0', '1468-8', '1469-6', '1470-4', '1471-2', '1472-0', '1474-6', '1475-3', '1476-1', '1478-7', '1479-5', '1480-3', '1481-1', '1482-9', '1483-7', '1484-5', '1485-2', '1487-8', '1489-4', '1490-2', '1491-0', '1492-8', '1493-6', '1494-4', '1495-1', '1496-9', '1497-7', '1498-5', '1499-3', '1500-8', '1501-6', '1502-4', '1503-2', '1504-0', '1505-7', '1506-5', '1507-3', '1508-1', '1509-9', '1510-7', '1511-5', '1512-3', '1513-1', '1514-9', '1515-6', '1516-4', '1518-0', '1519-8', '1520-6', '1521-4', '1522-2', '1523-0', '1524-8', '1525-5', '1526-3', '1527-1', '1528-9', '1529-7', '1530-5', '1531-3', '1532-1', '1533-9', '1534-7', '1535-4', '1536-2', '1537-0', '1538-8', '1539-6', '1541-2', '1543-8', '1545-3', '1547-9', '1549-5', '1551-1', '1552-9', '1553-7', '1554-5', '1556-0', '1558-6', '1560-2', '1562-8', '1564-4', '1566-9', '1567-7', '1568-5', '1569-3', '1570-1', '1571-9', '1573-5', '1574-3', '1576-8', '1578-4', '1579-2', '1580-0', '1582-6', '1584-2', '1586-7', '1587-5', '1588-3', '1589-1', '1590-9', '1591-7', '1592-5', '1593-3', '1594-1', '1595-8', '1596-6', '1597-4', '1598-2', '1599-0', '1600-6', '1602-2', '1603-0', '1604-8', '1605-5', '1607-1', '1609-7', '1610-5', '1611-3', '1612-1', '1613-9', '1614-7', '1615-4', '1616-2', '1617-0', '1618-8', '1619-6', '1620-4', '1621-2', '1622-0', '1623-8', '1624-6', '1625-3', '1626-1', '1627-9', '1628-7', '1629-5', '1630-3', '1631-1', '1632-9', '1633-7', '1634-5', '1635-2', '1636-0', '1637-8', '1638-6', '1639-4', '1640-2', '1641-0', '1643-6', '1645-1', '1647-7', '1649-3', '1651-9', '1653-5', '1654-3', '1655-0', '1656-8', '1657-6', '1659-2', '1661-8', '1663-4', '1665-9', '1667-5', '1668-3', '1670-9', '1671-7', '1672-5', '1673-3', '1675-8', '1677-4', '1679-0', '1680-8', '1681-6', '1683-2', '1685-7', '1687-3', '1688-1', '1689-9', '1690-7', '1692-3', '1694-9', '1696-4', '1697-2', '1698-0', '1700-4', '1702-0', '1704-6', '1705-3', '1707-9', '1709-5', '1711-1', '1712-9', '1713-7', '1715-2', '1717-8', '1718-6', '1719-4', '1720-2', '1722-8', '1724-4', '1725-1', '1726-9', '1727-7', '1728-5', '1729-3', '1730-1', '1731-9', '1732-7', '1733-5', '1735-0', '1737-6', '1739-2', '1740-0', '1741-8', '1742-6', '1743-4', '1744-2', '1745-9', '1746-7', '1747-5', '1748-3', '1749-1', '1750-9', '1751-7', '1752-5', '1753-3', '1754-1', '1755-8', '1756-6', '1757-4', '1758-2', '1759-0', '1760-8', '1761-6', '1762-4', '1763-2', '1764-0', '1765-7', '1766-5', '1767-3', '1768-1', '1769-9', '1770-7', '1771-5', '1772-3', '1773-1', '1774-9', '1775-6', '1776-4', '1777-2', '1778-0', '1779-8', '1780-6', '1781-4', '1782-2', '1783-0', '1784-8', '1785-5', '1786-3', '1787-1', '1788-9', '1789-7', '1790-5', '1791-3', '1792-1', '1793-9', '1794-7', '1795-4', '1796-2', '1797-0', '1798-8', '1799-6', '1800-2', '1801-0', '1802-8', '1803-6', '1804-4', '1805-1', '1806-9', '1807-7', '1808-5', '1809-3', '1811-9', '1813-5', '1814-3', '1815-0', '1816-8', '1817-6', '1818-4', '1819-2', '1820-0', '1821-8', '1822-6', '1823-4', '1824-2', '1825-9', '1826-7', '1827-5', '1828-3', '1829-1', '1830-9', '1831-7', '1832-5', '1833-3', '1834-1', '1835-8', '1837-4', '1838-2', '1840-8', '1842-4', '1844-0', '1845-7', '1846-5', '1847-3', '1848-1', '1849-9', '1850-7', '1851-5', '1852-3', '1853-1', '1854-9', '1855-6', '1856-4', '1857-2', '1858-0', '1859-8', '1860-6', '1861-4', '1862-2', '1863-0', '1864-8', '1865-5', '1866-3', '1867-1', '1868-9', '1869-7', '1870-5', '1871-3', '1872-1', '1873-9', '1874-7', '1875-4', '1876-2', '1877-0', '1878-8', '1879-6', '1880-4', '1881-2', '1882-0', '1883-8', '1884-6', '1885-3', '1886-1', '1887-9', '1888-7', '1889-5', '1891-1', '1892-9', '1893-7', '1894-5', '1896-0', '1897-8', '1898-6', '1899-4', '1900-0', '1901-8', '1902-6', '1903-4', '1904-2', '1905-9', '1906-7', '1907-5', '1908-3', '1909-1', '1910-9', '1911-7', '1912-5', '1913-3', '1914-1', '1915-8', '1916-6', '1917-4', '1918-2', '1919-0', '1920-8', '1921-6', '1922-4', '1923-2', '1924-0', '1925-7', '1926-5', '1927-3', '1928-1', '1929-9', '1930-7', '1931-5', '1932-3', '1933-1', '1934-9', '1935-6', '1936-4', '1937-2', '1938-0', '1939-8', '1940-6', '1941-4', '1942-2', '1943-0', '1944-8', '1945-5', '1946-3', '1947-1', '1948-9', '1949-7', '1950-5', '1951-3', '1952-1', '1953-9', '1954-7', '1955-4', '1956-2', '1957-0', '1958-8', '1959-6', '1960-4', '1961-2', '1962-0', '1963-8', '1964-6', '1966-1', '1968-7', '1969-5', '1970-3', '1972-9', '1973-7', '1974-5', '1975-2', '1976-0', '1977-8', '1978-6', '1979-4', '1980-2', '1981-0', '1982-8', '1984-4', '1985-1', '1986-9', '1987-7', '1988-5', '1990-1', '1992-7', '1993-5', '1994-3', '1995-0', '1996-8', '1997-6', '1998-4', '1999-2', '2000-8', '2002-4', '2004-0', '2006-5', '2007-3', '2008-1', '2009-9', '2010-7', '2011-5', '2012-3', '2013-1', '2014-9', '2015-6', '2016-4', '2017-2', '2018-0', '2019-8', '2020-6', '2021-4', '2022-2', '2023-0', '2024-8', '2025-5', '2026-3', '2029-7', '2030-5', '2031-3', '2032-1', '2033-9', '2034-7', '2035-4', '2036-2', '2037-0', '2038-8', '2039-6', '2040-4', '2041-2', '2042-0', '2043-8', '2044-6', '2045-3', '2046-1', '2047-9', '2048-7', '2049-5', '2050-3', '2051-1', '2052-9', '2056-0', '2058-6', '2060-2', '2061-0', '2062-8', '2063-6', '2064-4', '2065-1', '2066-9', '2067-7', '2068-5', '2069-3', '2070-1', '2071-9', '2072-7', '2073-5', '2074-3', '2075-0', '2078-4', '2079-2', '2080-0', '2081-8', '2082-6', '2083-4', '2085-9', '2086-7', '2087-5', '2088-3', '2089-1', '2090-9', '2091-7', '2092-5', '2093-3', '2094-1', '2095-8', '2096-6', '2097-4', '2098-2', '2100-6', '2101-4', '2102-2', '2103-0', '2104-8', '2108-9', '2109-7', '2110-5', '2111-3', '2112-1', '2113-9', '2114-7', '2115-4', '2116-2', '2118-8', '2119-6', '2120-4', '2121-2', '2122-0', '2123-8', '2124-6', '2125-3', '2126-1', '2127-9', '2129-5', '2131-1', '2500-7', 'asku', 'oth', 'unk']
### `RACE_CODE_DESCRIPTION`
  - `type` string
  - `constraints`:
    - `enum` ['american indian or alaska native', 'asian', 'black or african american', 'native hawaiian or other pacific islander', 'white', 'other race', 'asked but unknown', 'unknown', 'american indian', 'abenaki', 'algonquian', 'apache', 'chiricahua', 'fort sill apache', 'jicarilla apache', 'lipan apache', 'mescalero apache', 'oklahoma apache', 'payson apache', 'san carlos apache', 'white mountain apache', 'arapaho', 'northern arapaho', 'southern arapaho', 'wind river arapaho', 'arikara', 'assiniboine', 'assiniboine sioux', 'fort peck assiniboine sioux', 'bannock', 'blackfeet', 'brotherton', 'burt lake band', 'caddo', 'oklahoma cado', 'cahuilla', 'agua caliente cahuilla', 'augustine', 'cabazon', 'los coyotes', 'morongo', 'santa rosa cahuilla', 'torres-martinez', 'california tribes', 'cahto', 'chimariko', 'coast miwok', 'digger', 'kawaiisu', 'kern river', 'mattole', 'red wood', 'santa rosa', 'takelma', 'wappo', 'yana', 'yuki', 'canadian and latin american indian', 'canadian indian', 'central american indian', 'french american indian', 'mexican american indian', 'south american indian', 'spanish american indian', 'catawba', 'cayuse', 'chehalis', 'chemakuan', 'hoh', 'quileute', 'chemehuevi', 'cherokee', 'cherokee alabama', 'cherokees of northeast alabama', 'cherokees of southeast alabama', 'eastern cherokee', 'echota cherokee', 'etowah cherokee', 'northern cherokee', 'tuscola', 'united keetowah band of cherokee', 'western cherokee', 'cherokee shawnee', 'cheyenne', 'northern cheyenne', 'southern cheyenne', 'cheyenne-arapaho', 'chickahominy', 'eastern chickahominy', 'western chickahominy', 'chickasaw', 'chinook', 'clatsop', 'columbia river chinook', 'kathlamet', 'upper chinook', 'wakiakum chinook', 'willapa chinook', 'wishram', 'chippewa', 'bad river', 'bay mills chippewa', 'bois forte', 'burt lake chippewa', 'fond du lac', 'grand portage', 'grand traverse band of ottawa/chippewa', 'keweenaw', 'lac courte oreilles', 'lac du flambeau', 'lac vieux desert chippewa', 'lake superior', 'leech lake', 'little shell chippewa', 'mille lacs', 'minnesota chippewa', 'ontonagon', 'red cliff chippewa', 'red lake chippewa', 'saginaw chippewa', 'st. croix chippewa', 'sault ste. marie chippewa', 'sokoagon chippewa', 'turtle mountain', 'white earth', 'chippewa cree', "rocky boy's chippewa cree", 'chitimacha', 'choctaw', 'clifton choctaw', 'jena choctaw', 'mississippi choctaw', 'mowa band of choctaw', 'oklahoma choctaw', 'chumash', 'santa ynez', 'clear lake', "coeur d'alene", 'coharie', 'colorado river', 'colville', 'comanche', 'oklahoma comanche', 'coos', 'coquilles', 'costanoan', 'coushatta', 'alabama coushatta', 'cowlitz', 'cree', 'creek', 'alabama creek', 'alabama quassarte', 'eastern creek', 'eastern muscogee', 'kialegee', 'lower muscogee', 'machis lower creek indian', 'poarch band', 'principal creek indian nation', 'star clan of muscogee creeks', 'thlopthlocco', 'tuckabachee', 'croatan', 'crow', 'cupeno', 'agua caliente', 'delaware', 'eastern delaware', 'lenni-lenape', 'munsee', 'oklahoma delaware', 'rampough mountain', 'sand hill', 'diegueno', 'campo', 'capitan grande', 'cuyapaipe', 'la posta', 'manzanita', 'mesa grande', 'san pasqual', 'santa ysabel', 'sycuan', 'eastern tribes', 'attacapa', 'biloxi', 'georgetown (eastern tribes)', 'moor', 'nansemond', 'natchez', 'nausu waiwash', 'nipmuc', 'paugussett', 'pocomoke acohonock', 'southeastern indians', 'susquehanock', 'tunica biloxi', 'waccamaw-siousan', 'wicomico', 'esselen', 'fort belknap', 'fort berthold', 'fort mcdowell', 'fort hall', 'gabrieleno', 'grand ronde', 'gros ventres', 'atsina', 'haliwa', 'hidatsa', 'hoopa', 'trinity', 'whilkut', 'hoopa extension', 'houma', 'inaja-cosmit', 'iowa', 'iowa of kansas-nebraska', 'iowa of oklahoma', 'iroquois', 'cayuga', 'mohawk', 'oneida', 'onondaga', 'seneca', 'seneca nation', 'seneca-cayuga', 'tonawanda seneca', 'tuscarora', 'wyandotte', 'juaneno', 'kalispel', 'karuk', 'kaw', 'kickapoo', 'oklahoma kickapoo', 'texas kickapoo', 'kiowa', 'oklahoma kiowa', 'klallam', 'jamestown', 'lower elwha', 'port gamble klallam', 'klamath', 'konkow', 'kootenai', 'lassik', 'long island', 'matinecock', 'montauk', 'poospatuck', 'setauket', 'luiseno', 'la jolla', 'pala', 'pauma', 'pechanga', 'soboba', 'twenty-nine palms', 'temecula', 'lumbee', 'lummi', 'maidu', 'mountain maidu', 'nishinam', 'makah', 'maliseet', 'mandan', 'mattaponi', 'menominee', 'miami', 'illinois miami', 'indiana miami', 'oklahoma miami', 'miccosukee', 'micmac', 'aroostook', 'mission indians', 'miwok', 'modoc', 'mohegan', 'mono', 'nanticoke', 'narragansett', 'navajo', 'alamo navajo', 'canoncito navajo', 'ramah navajo', 'nez perce', 'nomalaki', 'northwest tribes', 'alsea', 'celilo', 'columbia', 'kalapuya', 'molala', 'talakamish', 'tenino', 'tillamook', 'wenatchee', 'yahooskin', 'omaha', 'oregon athabaskan', 'osage', 'otoe-missouria', 'ottawa', 'burt lake ottawa', 'michigan ottawa', 'oklahoma ottawa', 'paiute', 'bishop', 'bridgeport', 'burns paiute', 'cedarville', 'fort bidwell', 'fort independence', 'kaibab', 'las vegas', 'lone pine', 'lovelock', 'malheur paiute', 'moapa', 'northern paiute', 'owens valley', 'pyramid lake', 'san juan southern paiute', 'southern paiute', 'summit lake', 'utu utu gwaitu paiute', 'walker river', 'yerington paiute', 'pamunkey', 'passamaquoddy', 'indian township', 'pleasant point passamaquoddy', 'pawnee', 'oklahoma pawnee', 'penobscot', 'peoria', 'oklahoma peoria', 'pequot', 'marshantucket pequot', 'pima', 'gila river pima-maricopa', 'salt river pima-maricopa', 'piscataway', 'pit river', 'pomo', 'central pomo', 'dry creek', 'eastern pomo', 'kashia', 'northern pomo', 'scotts valley', 'stonyford', 'sulphur bank', 'ponca', 'nebraska ponca', 'oklahoma ponca', 'potawatomi', 'citizen band potawatomi', 'forest county', 'hannahville', 'huron potawatomi', 'pokagon potawatomi', 'prairie band', 'wisconsin potawatomi', 'powhatan', 'pueblo', 'acoma', 'arizona tewa', 'cochiti', 'hopi', 'isleta', 'jemez', 'keres', 'laguna', 'nambe', 'picuris', 'piro', 'pojoaque', 'san felipe', 'san ildefonso', 'san juan pueblo', 'san juan de', 'san juan', 'sandia', 'santa ana', 'santa clara', 'santo domingo', 'taos', 'tesuque', 'tewa', 'tigua', 'zia', 'zuni', 'puget sound salish', 'duwamish', 'kikiallus', 'lower skagit', 'muckleshoot', 'nisqually', 'nooksack', 'port madison', 'puyallup', 'samish', 'sauk-suiattle', 'skokomish', 'skykomish', 'snohomish', 'snoqualmie', 'squaxin island', 'steilacoom', 'stillaguamish', 'suquamish', 'swinomish', 'tulalip', 'upper skagit', 'quapaw', 'quinault', 'rappahannock', 'reno-sparks', 'round valley', 'sac and fox', 'iowa sac and fox', 'missouri sac and fox', 'oklahoma sac and fox', 'salinan', 'salish', 'salish and kootenai', 'schaghticoke', 'scott valley', 'seminole', 'big cypress', 'brighton', 'florida seminole', 'hollywood seminole', 'oklahoma seminole', 'serrano', 'san manual', 'shasta', 'shawnee', 'absentee shawnee', 'eastern shawnee', 'shinnecock', 'shoalwater bay', 'shoshone', 'battle mountain', 'duckwater', 'elko', 'ely', 'goshute', 'panamint', 'ruby valley', 'skull valley', 'south fork shoshone', 'te-moak western shoshone', 'timbi-sha shoshone', 'washakie', 'wind river shoshone', 'yomba', 'shoshone paiute', 'duck valley', 'fallon', 'fort mcdermitt', 'siletz', 'sioux', 'blackfoot sioux', 'brule sioux', 'cheyenne river sioux', 'crow creek sioux', 'dakota sioux', 'flandreau santee', 'fort peck', 'lake traverse sioux', 'lower brule sioux', 'lower sioux', 'mdewakanton sioux', 'miniconjou', 'oglala sioux', 'pine ridge sioux', 'pipestone sioux', 'prairie island sioux', 'prior lake sioux', 'rosebud sioux', 'sans arc sioux', 'santee sioux', 'sisseton-wahpeton', 'sisseton sioux', 'spirit lake sioux', 'standing rock sioux', 'teton sioux', 'two kettle sioux', 'upper sioux', 'wahpekute sioux', 'wahpeton sioux', 'wazhaza sioux', 'yankton sioux', 'yanktonai sioux', 'siuslaw', 'spokane', 'stewart', 'stockbridge', 'susanville', "tohono o'odham", 'ak-chin', 'gila bend', 'san xavier', 'sells', 'tolowa', 'tonkawa', 'tygh', 'umatilla', 'umpqua', 'cow creek umpqua', 'ute', 'allen canyon', 'uintah ute', 'ute mountain ute', 'wailaki', 'walla-walla', 'wampanoag', 'gay head wampanoag', 'mashpee wampanoag', 'warm springs', 'wascopum', 'washoe', 'alpine', 'carson', 'dresslerville', 'wichita', 'wind river', 'winnebago', 'ho-chunk', 'nebraska winnebago', 'winnemucca', 'wintun', 'wiyot', 'table bluff', 'yakama', 'yakama cowlitz', 'yaqui', 'barrio libre', 'pascua yaqui', 'yavapai apache', 'yokuts', 'chukchansi', 'tachi', 'tule river', 'yuchi', 'yuman', 'cocopah', 'havasupai', 'hualapai', 'maricopa', 'mohave', 'quechan', 'yavapai', 'yurok', 'coast yurok', 'alaska native', 'alaska indian', 'alaskan athabascan', 'ahtna', 'alatna', 'alexander', 'allakaket', 'alanvik', 'anvik', 'arctic', 'beaver', 'birch creek', 'cantwell', 'chalkyitsik', 'chickaloon', 'chistochina', 'chitina', 'circle', 'cook inlet', 'copper center', 'copper river', 'dot lake', 'doyon', 'eagle', 'eklutna', 'evansville', 'fort yukon', 'gakona', 'galena', 'grayling', 'gulkana', 'healy lake', 'holy cross', 'hughes', 'huslia', 'iliamna', 'kaltag', 'kluti kaah', 'knik', 'koyukuk', 'lake minchumina', 'lime', 'mcgrath', 'manley hot springs', 'mentasta lake', 'minto', 'nenana', 'nikolai', 'ninilchik', 'nondalton', 'northway', 'nulato', 'pedro bay', 'rampart', 'ruby', 'salamatof', 'seldovia', 'slana', 'shageluk', 'stevens', 'stony river', 'takotna', 'tanacross', 'tanaina', 'tanana', 'tanana chiefs', 'tazlina', 'telida', 'tetlin', 'tok', 'tyonek', 'venetie', 'wiseman', 'southeast alaska', 'tlingit-haida', 'angoon', 'central council of tlingit and haida tribes', 'chilkat', 'chilkoot', 'craig', 'douglas', 'haida', 'hoonah', 'hydaburg', 'kake', 'kasaan', 'kenaitze', 'ketchikan', 'klawock', 'pelican', 'petersburg', 'saxman', 'sitka', 'tenakee springs', 'tlingit', 'wrangell', 'yakutat', 'tsimshian', 'metlakatla', 'eskimo', 'greenland eskimo', 'inupiat eskimo', 'ambler', 'anaktuvuk', 'anaktuvuk pass', 'arctic slope inupiat', 'arctic slope corporation', 'atqasuk', 'barrow', 'bering straits inupiat', 'brevig mission', 'buckland', 'chinik', 'council', 'deering', 'elim', 'golovin', 'inalik diomede', 'inupiaq', 'kaktovik', 'kawerak', 'kiana', 'kivalina', 'kobuk', 'kotzebue', 'koyuk', 'kwiguk', 'mauneluk inupiat', 'nana inupiat', 'noatak', 'nome', 'noorvik', 'nuiqsut', 'point hope', 'point lay', 'selawik', 'shaktoolik', 'shishmaref', 'shungnak', 'solomon', 'teller', 'unalakleet', 'wainwright', 'wales', 'white mountain', 'white mountain inupiat', "mary's igloo", 'siberian eskimo', 'gambell', 'savoonga', 'siberian yupik', 'yupik eskimo', 'akiachak', 'akiak', 'alakanuk', 'aleknagik', 'andreafsky', 'aniak', 'atmautluak', 'bethel', "bill moore's slough", 'bristol bay yupik', 'calista yupik', 'chefornak', 'chevak', 'chuathbaluk', "clark's point", 'crooked creek', 'dillingham', 'eek', 'ekuk', 'ekwok', 'emmonak', 'goodnews bay', 'hooper bay', 'iqurmuit (russian mission)', 'kalskag', 'kasigluk', 'kipnuk', 'koliganek', 'kongiganak', 'kotlik', 'kwethluk', 'kwigillingok', 'levelock', 'lower kalskag', 'manokotak', 'marshall', 'mekoryuk', 'mountain village', 'naknek', 'napaumute', 'napakiak', 'napaskiak', 'newhalen', 'new stuyahok', 'newtok', 'nightmute', 'nunapitchukv', 'oscarville', 'pilot station', 'pitkas point', 'platinum', 'portage creek', 'quinhagak', 'red devil', 'st. michael', 'scammon bay', "sheldon's point", 'sleetmute', 'stebbins', 'togiak', 'toksook', 'tulukskak', 'tuntutuliak', 'tununak', 'twin hills', 'georgetown (yupik-eskimo)', "st. mary's", 'umkumiate', 'aleut', 'alutiiq aleut', 'tatitlek', 'ugashik', 'bristol bay aleut', 'chignik', 'chignik lake', 'egegik', 'igiugig', 'ivanof bay', 'king salmon', 'kokhanok', 'perryville', 'pilot point', 'port heiden', 'chugach aleut', 'chenega', 'chugach corporation', 'english bay', 'port graham', 'eyak', 'koniag aleut', 'akhiok', 'agdaagux', 'karluk', 'kodiak', 'larsen bay', 'old harbor', 'ouzinkie', 'port lions', 'sugpiaq', 'suqpigaq', 'unangan aleut', 'akutan', 'aleut corporation', 'aleutian', 'aleutian islander', 'atka', 'belkofski', 'chignik lagoon', 'king cove', 'false pass', 'nelson lagoon', 'nikolski', 'pauloff harbor', 'qagan toyagungin', 'qawalangin', 'st. george', 'st. paul', 'sand point', 'south naknek', 'unalaska', 'unga', 'asian indian', 'bangladeshi', 'bhutanese', 'burmese', 'cambodian', 'chinese', 'taiwanese', 'filipino', 'hmong', 'indonesian', 'japanese', 'korean', 'laotian', 'malaysian', 'okinawan', 'pakistani', 'sri lankan', 'thai', 'vietnamese', 'iwo jiman', 'maldivian', 'nepalese', 'singaporean', 'madagascar', 'black', 'african american', 'african', 'botswanan', 'ethiopian', 'liberian', 'namibian', 'nigerian', 'zairean', 'bahamian', 'barbadian', 'dominican', 'dominica islander', 'haitian', 'jamaican', 'tobagoan', 'trinidadian', 'west indian', 'polynesian', 'native hawaiian', 'samoan', 'tahitian', 'tongan', 'tokelauan', 'micronesian', 'guamanian or chamorro', 'guamanian', 'chamorro', 'mariana islander', 'marshallese', 'palauan', 'carolinian', 'kosraean', 'pohnpeian', 'saipanese', 'kiribati', 'chuukese', 'yapese', 'melanesian', 'fijian', 'papua new guinean', 'solomon islander', 'new hebrides', 'european', 'armenian', 'english', 'french', 'german', 'irish', 'italian', 'polish', 'scottish', 'middle eastern or north african', 'assyrian', 'egyptian', 'iranian', 'iraqi', 'lebanese', 'palestinian', 'syrian', 'afghanistani', 'israeli', 'arab', 'other race', 'other pacific islander', 'asked but unknown', 'other', 'unknown']
### `RACE_CODE_SYSTEM_NAME`
  - `type` string
  - `constraints`:
    - `enum` ['http://terminology.hl7.org/CodeSystem/v3-NullFlavor', 'urn:oid:2.16.840.1.113883.6.238']
### `ETHNICITY_CODE`
  - `type` string
  - `constraints`:
    - `enum` ['2135-2', '2186-5', '2137-8', '2148-5', '2155-0', '2165-9', '2178-2', '2180-8', '2182-4', '2184-0', '2138-6', '2139-4', '2140-2', '2141-0', '2142-8', '2143-6', '2144-4', '2145-1', '2146-9', '2149-3', '2150-1', '2151-9', '2152-7', '2153-5', '2156-8', '2157-6', '2158-4', '2159-2', '2160-0', '2161-8', '2162-6', '2163-4', '2166-7', '2167-5', '2168-3', '2169-1', '2170-9', '2171-7', '2172-5', '2173-3', '2174-1', '2175-8', '2176-6']
### `ETHNICITY_CODE_DESCRIPTION`
  - `type` string
  - `constraints`:
    - `enum` ['hispanic or latino', 'not hispanic or latino', 'spaniard', 'mexican', 'central american', 'south american', 'latin american', 'puerto rican', 'cuban', 'dominican', 'andalusian', 'asturian', 'castillian', 'catalonian', 'belearic islander', 'gallego', 'valencian', 'canarian', 'spanish basque', 'mexican american', 'mexicano', 'chicano', 'la raza', 'mexican american indian', 'costa rican', 'guatemalan', 'honduran', 'nicaraguan', 'panamanian', 'salvadoran', 'central american indian', 'canal zone', 'argentinean', 'bolivian', 'chilean', 'colombian', 'ecuadorian', 'paraguayan', 'peruvian', 'uruguayan', 'venezuelan', 'south american indian', 'criollo']
### `ETHNICITY_CODE_SYSTEM_NAME`
  - `type` string
  - `constraints`:
    - `enum` ['http://terminology.hl7.org/CodeSystem/v3-NullFlavor', 'urn:oid:2.16.840.1.113883.6.238']
### `MEDICAID_CIN`
  - `type` string
### `PATIENT_LAST_UPDATED`
  - `type` string
  - `constraints`:
    - `required` True
    - `pattern` `^([0-9]([0-9]([0-9][1-9]|[1-9]0)|[1-9]00)|[1-9]000)-(0[1-9]|1[0-2])-(0[1-9]|[1-2][0-9]|3[0-1])T([01][0-9]|2[0-3]):[0-5][0-9]:([0-5][0-9]|60)(\.[0-9]+)?(Z|(\+|-)((0[0-9]|1[0-3]):[0-5][0-9]|14:00))$`
### `RELATIONSHIP_PERSON_CODE`
  - `type` string
  - `constraints`:
    - `enum` ['sel', 'spo', 'dom', 'chd', 'gch', 'nch', 'sch', 'fch', 'dep', 'wrd', 'par', 'mth', 'fth', 'cgv', 'grd', 'grp', 'exf', 'sib', 'bro', 'sis', 'fnd', 'oad', 'eme', 'emr', 'asc', 'emc', 'own', 'tra', 'mgr', 'non', 'unk', 'oth']
### `RELATIONSHIP_PERSON_DESCRIPTION`
  - `type` string
  - `constraints`:
    - `enum` ['self', 'spouse', 'life partner', 'child', 'grandchild', 'natural child', 'stepchild', 'foster child', 'handicapped dependent', 'ward of court', 'parent', 'mother', 'father', 'care giver', 'guardian', 'grandparent', 'extended family', 'sibling', 'brother', 'sister', 'friend', 'other adult', 'employee', 'employer', 'associate', 'emergency contact', 'owner', 'trainer', 'manager', 'none', 'unknown', 'other']
### `RELATIONSHIP_PERSON_SYSTEM`
  - `type` string
  - `constraints`:
    - `enum` ['http://terminology.hl7.org/CodeSystem/v2-0063']
### `RELATIONSHIP_PERSON_GIVEN_NAME`
  - `type` string
### `RELATIONSHIP_PERSON_FAMILY_NAME`
  - `type` string
### `RELATIONSHIP_PERSON_TELECOM_SYSTEM`
  - `type` string
### `RELATIONSHIP_PERSON_TELECOM_VALUE`
  - `type` string