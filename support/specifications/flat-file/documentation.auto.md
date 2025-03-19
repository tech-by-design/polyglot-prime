# `csv-validation-using-ig`
- `description` Each field description outlines the FHIR resource paths that map to the corresponding CSV fields. It specifies the logical extraction path within a FHIR Bundle to locate the relevant data, ensuring clarity and consistency when deriving data fields from the source FHIR resources. CSV files must be encoded in **UTF-8** to ensure proper validation and processing. For example: 

  - PATIENT_MR_ID_VALUE: Extracted from Bundle.entry.resource where resourceType = 'Patient', identifier where type.coding.code = 'MR', and value. 
  - FACILITY_ACTIVE: Extracted from Bundle.entry.resource where resourceType = 'Organization' and active.
- `profile` tabular-data-package
## `qe_admin_data`
  - `path` nyher-fhir-ig-example/SDOH_QEadmin_CareRidgeSCN_testcase1_20250312040214.csv
  - `schema`
      - `primaryKey` ['PATIENT_MR_ID_VALUE']
    - `foreignKeys` []
### `PATIENT_MR_ID_VALUE`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').identifier.where(type.coding.code = 'MR').value
  - `type` string
  - `constraints`:
    - `required` True
    - `unique` True
    - `pattern` `[ \r\n\t\S]+`
### `FACILITY_ID`
  - `description` Append to the Bundle.entry.resource.where(resourceType ='Organization').id
  - `type` string
  - `constraints`:
    - `required` True
    - `pattern` `[A-Za-z0-9\-\.]{1,64}`
### `FACILITY_NAME`
  - `description` Bundle.entry.resource.where(resourceType ='Organization').name
  - `type` string
  - `constraints`:
    - `required` True
    - `pattern` `[ \r\n\t\S]+`
### `ORGANIZATION_TYPE_CODE`
  - `description` Bundle.entry.resource.where(resourceType ='Organization').type.coding.code
  - `type` string
  - `constraints`:
    - `enum` ['prov', 'dept', 'team', 'govt', 'ins', 'pay', 'edu', 'reli', 'crs', 'cg', 'bus', 'other']
### `ORGANIZATION_TYPE_DISPLAY`
  - `description` Bundle.entry.resource.where(resourceType ='Organization').type.coding.display
  - `type` string
  - `constraints`:
    - `enum` ['healthcare provider', 'hospital department', 'organizational team', 'government', 'insurance company', 'payer', 'educational institute', 'religious institution', 'clinical research sponsor', 'community group', 'non-healthcare business or corporation', 'other']
### `ORGANIZATION_TYPE_CODE_SYSTEM`
  - `description` Bundle.entry.resource.where(resourceType ='Organization').type.coding.system
  - `type` string
  - `constraints`:
    - `enum` ['http://terminology.hl7.org/CodeSystem/organization-type']
### `ENCOUNTER_LOCATION`
  - `description` Bundle.entry.resource.where(resourceType ='Encounter').location.location.reference
  - `type` string
  - `constraints`:
    - `required` True
### `FACILITY_ADDRESS1`
  - `description` Bundle.entry.resource.where(resourceType ='Organization').address.line
  - `type` string
### `FACILITY_ADDRESS2`
  - `description` Bundle.entry.resource.where(resourceType ='Organization').address.text
  - `type` string
### `FACILITY_CITY`
  - `description` Bundle.entry.resource.where(resourceType ='Organization').address.city & Bundle.entry.resource.where(resourceType ='Organization').address.text
  - `type` string
### `FACILITY_STATE`
  - `description` Bundle.entry.resource.where(resourceType ='Organization').address.state & Bundle.entry.resource.where(resourceType ='Organization').address.text
  - `type` string
  - `constraints`:
    - `enum` ['AK', 'AL', 'AR', 'AS', 'AZ', 'CA', 'CO', 'CT', 'DC', 'DE', 'FL', 'FM', 'GA', 'GU', 'HI', 'IA', 'ID', 'IL', 'IN', 'KS', 'KY', 'LA', 'MA', 'MD', 'ME', 'MH', 'MI', 'MN', 'MO', 'MP', 'MS', 'MT', 'NC', 'ND', 'NE', 'NH', 'NJ', 'NM', 'NV', 'NY', 'OH', 'OK', 'OR', 'PA', 'PR', 'PW', 'RI', 'SC', 'SD', 'TN', 'TX', 'UT', 'VA', 'VI', 'VT', 'WA', 'WI', 'WV', 'WY']
### `FACILITY_ZIP`
  - `description` Bundle.entry.resource.where(resourceType ='Organization').address.postalCode & Bundle.entry.resource.where(resourceType ='Organization').address.text
  - `type` string
  - `constraints`:
    - `pattern` `^\d{5}(-?\d{4})?$`
### `FACILITY_COUNTY`
  - `description` Bundle.entry.resource.where(resourceType ='Organization').address.district  & Bundle.entry.resource.where(resourceType ='Organization').address.text
  - `type` string
### `FACILITY_LAST_UPDATED`
  - `description` Bundle.entry.resource.where(resourceType ='Organization').meta.lastUpdated
  - `type` string
  - `constraints`:
    - `required` True
    - `pattern` `^([0-9]([0-9]([0-9][1-9]|[1-9]0)|[1-9]00)|[1-9]000)-(0[1-9]|1[0-2])-(0[1-9]|[1-2][0-9]|3[0-1])T([01][0-9]|2[0-3]):[0-5][0-9]:([0-5][0-9]|60)(\.[0-9]+)?(Z|(\+|-)((0[0-9]|1[0-3]):[0-5][0-9]|14:00))$`
## `screening_profile_data`
  - `path` nyher-fhir-ig-example/SDOH_ScreeningProf_CareRidgeSCN_testcase1_20250312040214.csv
  - `schema`
      - `primaryKey` ['ENCOUNTER_ID']
    - `foreignKeys` []
### `PATIENT_MR_ID_VALUE`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').identifier.where(type.coding.code = 'MR').value
  - `type` string
  - `constraints`:
    - `required` True
    - `pattern` `[ \r\n\t\S]+`
### `FACILITY_ID`
  - `description` Append to the Bundle.entry.resource.where(resourceType ='Organization').id
  - `type` string
  - `constraints`:
    - `required` True
    - `pattern` `[A-Za-z0-9\-\.]{1,64}`
### `FACILITY_NAME`
  - `description` Bundle.entry.resource.where(resourceType ='Organization').name
  - `type` string
  - `constraints`:
    - `required` True
    - `pattern` `[ \r\n\t\S]+`
### `ENCOUNTER_ID`
  - `description` Bundle.entry.resource.where(resourceType ='Encounter').id
  - `type` string
  - `constraints`:
    - `required` True
### `SCREENING_IDENTIFIER`
  - `description` Bundle.entry.resource.where(resourceType ='Observation').id
  - `type` string
  - `constraints`:
    - `required` True
    - `pattern` `[A-Za-z0-9\-\.]{1,64}`
### `ENCOUNTER_CLASS_CODE`
  - `description` Bundle.entry.resource.where(resourceType ='Encounter').class.code
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['AMB', 'EMER', 'FLD', 'HH', 'IMP', 'ACUTE', 'NONAC', 'OBSENC', 'PRENC', 'SS', 'VR']
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
  - `description` Bundle.entry.resource.where(resourceType ='Encounter').status(code)
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['planned', 'arrived', 'triaged', 'in-progress', 'onleave', 'finished', 'cancelled', 'entered-in-error', 'unknown']
### `ENCOUNTER_STATUS_CODE_DESCRIPTION`
  - `description` Bundle.entry.resource.where(resourceType ='Encounter').status(display)
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['planned', 'arrived', 'triaged', 'in-progress', 'on leave', 'finished', 'cancelled', 'entered in error', 'unknown']
### `ENCOUNTER_STATUS_CODE_SYSTEM`
  - `description` Bundle.entry.resource.where(resourceType ='Encounter').status(system)
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['http://hl7.org/fhir/encounter-status']
### `ENCOUNTER_TYPE_CODE`
  - `description` Bundle.entry.resource.where(resourceType ='Encounter').type.coding.code
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['23918007', '405672008']
### `ENCOUNTER_TYPE_CODE_DESCRIPTION`
  - `description` Bundle.entry.resource.where(resourceType ='Encounter').type.text
  - `type` string
  - `constraints`:
    - `enum` ['History taking, self-administered, by computer terminal', 'Direct questioning (procedure)']
### `ENCOUNTER_TYPE_CODE_SYSTEM`
  - `description` Bundle.entry.resource.where(resourceType ='Encounter').type.coding.system
  - `type` string
  - `constraints`:
    - `enum` ['http://snomed.info/sct']
### `ENCOUNTER_START_DATETIME`
  - `description` Bundle.entry.resource.where(resourceType ='Encounter').period.start
  - `type` string
  - `constraints`:
    - `required` True
    - `pattern` `([0-9]([0-9]([0-9][1-9]|[1-9]0)|[1-9]00)|[1-9]000)(-(0[1-9]|1[0-2])(-(0[1-9]|[1-2][0-9]|3[0-1])(T([01][0-9]|2[0-3]):[0-5][0-9]:([0-5][0-9]|60)(\.[0-9]+)?(Z|(\+|-)((0[0-9]|1[0-3]):[0-5][0-9]|14:00)))?)?)?`
### `ENCOUNTER_END_DATETIME`
  - `description` Bundle.entry.resource.where(resourceType ='Encounter').period.end
  - `type` string
  - `constraints`:
    - `pattern` `([0-9]([0-9]([0-9][1-9]|[1-9]0)|[1-9]00)|[1-9]000)(-(0[1-9]|1[0-2])(-(0[1-9]|[1-2][0-9]|3[0-1])(T([01][0-9]|2[0-3]):[0-5][0-9]:([0-5][0-9]|60)(\.[0-9]+)?(Z|(\+|-)((0[0-9]|1[0-3]):[0-5][0-9]|14:00)))?)?)?`
### `ENCOUNTER_LOCATION`
  - `description` Bundle.entry.resource.where(resourceType ='Encounter').location.location.reference
  - `type` string
  - `constraints`:
    - `required` True
### `PROCEDURE_STATUS_CODE`
  - `description` Bundle.entry.resource.where(resourceType ='Procedure').status
  - `type` string
  - `constraints`:
    - `enum` ['preparation', 'in-progress', 'not-done', 'on-hold', 'stopped', 'completed', 'entered-in-error', 'unknown']
### `PROCEDURE_CODE`
  - `description` Bundle.entry.resource.where(resourceType ='Procedure').code.coding.code
  - `type` string
### `PROCEDURE_CODE_DESCRIPTION`
  - `description` Bundle.entry.resource.where(resourceType ='Procedure').code.coding.display
  - `type` string
### `PROCEDURE_CODE_SYSTEM`
  - `description` Bundle.entry.resource.where(resourceType ='Procedure').code.coding.system
  - `type` string
  - `constraints`:
    - `enum` ['http://www.ama-assn.org/go/cpt', 'http://snomed.info/sct', 'urn:oid:2.16.840.1.113883.6.285', 'http://www.cms.gov/Medicare/Coding/ICD10', 'urn:oid:2.16.840.1.113883.6.13']
### `PROCEDURE_CODE_MODIFIER`
  - `description` Bundle.entry.resource.where(resourceType ='Procedure').modifierExtension.value
  - `type` string
### `CONSENT_STATUS`
  - `description` Bundle.entry.resource.where(resourceType ='Consent').status
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['draft', 'proposed', 'active', 'rejected', 'inactive', 'entered-in-error']
### `CONSENT_DATE_TIME`
  - `description` Bundle.entry.resource.where(resourceType ='Consent').dateTime
  - `type` string
  - `constraints`:
    - `required` True
    - `pattern` `^([0-9]([0-9]([0-9][1-9]|[1-9]0)|[1-9]00)|[1-9]000)-(0[1-9]|1[0-2])-(0[1-9]|[1-2][0-9]|3[0-1])T([01][0-9]|2[0-3]):[0-5][0-9]:([0-5][0-9]|60)(\.[0-9]+)?(Z|(\+|-)((0[0-9]|1[0-3]):[0-5][0-9]|14:00))$`
### `SCREENING_LAST_UPDATED`
  - `description` Bundle.entry.resource.where(resourceType ='Observation').meta.lastUpdated
  - `type` string
  - `constraints`:
    - `pattern` `^([0-9]([0-9]([0-9][1-9]|[1-9]0)|[1-9]00)|[1-9]000)-(0[1-9]|1[0-2])-(0[1-9]|[1-2][0-9]|3[0-1])T([01][0-9]|2[0-3]):[0-5][0-9]:([0-5][0-9]|60)(\.[0-9]+)?(Z|(\+|-)((0[0-9]|1[0-3]):[0-5][0-9]|14:00))$`
### `SCREENING_STATUS_CODE`
  - `description` Bundle.entry.resource.where(resourceType ='Observation').status.code
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['final', 'corrected', 'entered-in-error', 'unknown']
### `SCREENING_STATUS_CODE_DESCRIPTION`
  - `description` Bundle.entry.resource.where(resourceType ='Observation').status.display
  - `type` string
  - `constraints`:
    - `enum` ['final', 'corrected', 'entered in error', 'unknown']
### `SCREENING_STATUS_CODE_SYSTEM`
  - `description` Bundle.entry.resource.where(resourceType ='Observation').status.coding.system
  - `type` string
  - `constraints`:
    - `enum` ['http://hl7.org/fhir/observation-status']
### `SCREENING_LANGUAGE_CODE`
  - `description` Bundle.entry.resource.where(resourceType ='Observation').language.code
  - `type` string
  - `constraints`:
    - `enum` ['ar', 'bn', 'cs', 'da', 'de', 'de-AT', 'de-CH', 'de-DE', 'el', 'en', 'en-AU', 'en-CA', 'en-GB', 'en-IN', 'en-NZ', 'en-SG', 'en-US', 'es', 'es-AR', 'es-ES', 'es-UY', 'fi', 'fr', 'fr-BE', 'fr-CH', 'fr-FR', 'fy', 'fy-NL', 'hi', 'hr', 'it', 'it-CH', 'it-IT', 'ja', 'ko', 'nl', 'nl-BE', 'nl-NL', 'no', 'no-NO', 'pa', 'pl', 'pt', 'pt-BR', 'ru', 'ru-RU', 'sr', 'sr-RS', 'sv', 'sv-SE', 'te', 'zh', 'zh-CN', 'zh-HK', 'zh-SG', 'zh-TW']
### `SCREENING_LANGUAGE_DESCRIPTION`
  - `description` Bundle.entry.resource.where(resourceType ='Observation').language.display
  - `type` string
  - `constraints`:
    - `enum` ['arabic', 'bengali', 'czech', 'danish', 'german', 'german (austria)', 'german (switzerland)', 'german (germany)', 'greek', 'english', 'english (australia)', 'english (canada)', 'english (great britain)', 'english (india)', 'english (new zealand)', 'english (singapore)', 'english (united states)', 'spanish', 'spanish (argentina)', 'spanish (spain)', 'spanish (uruguay)', 'finnish', 'french', 'french (belgium)', 'french (switzerland)', 'french (france)', 'frysian', 'frysian (netherlands)', 'hindi', 'croatian', 'italian', 'italian (switzerland)', 'italian (italy)', 'japanese', 'korean', 'dutch', 'dutch (belgium)', 'dutch (netherlands)', 'norwegian', 'norwegian (norway)', 'punjabi', 'polish', 'portuguese', 'portuguese (brazil)', 'russian', 'russian (russia)', 'serbian', 'serbian (serbia)', 'swedish', 'swedish (sweden)', 'telegu', 'chinese', 'chinese (china)', 'chinese (hong kong)', 'chinese (singapore)', 'chinese (taiwan)']
### `SCREENING_LANGUAGE_CODE_SYSTEM`
  - `description` Bundle.entry.resource.where(resourceType ='Observation').language.system
  - `type` string
  - `constraints`:
    - `enum` ['urn:ietf:bcp:47']
### `SCREENING_ENTITY_ID`
  - `description` Bundle.entry.resource.where(resourceType ='Organization').identifier.value
  - `type` string
  - `constraints`:
    - `required` True
### `SCREENING_ENTITY_ID_CODE_SYSTEM`
  - `description` Bundle.entry.resource.where(resourceType ='Organization').identifier.system
  - `type` string
  - `constraints`:
    - `required` True
    - `pattern` `^(https?:\/\/)(www\.)?(hl7\.org\/fhir\/sid\/us-npi|medicaid\.gov|scn\.ny\.gov|cbo\.ny\.gov|hl7\.org\/oid|irs\.gov)(\/)?$`
## `screening_observation_data`
  - `path` nyher-fhir-ig-example/SDOH_ScreeningObs_CareRidgeSCN_testcase1_20250312040214.csv
  - `schema`
      - `foreignKeys`
      - [1]
        - `fields` ['ENCOUNTER_ID']
        - `reference`
          - `resource` screening_profile_data
          - `fields` ['ENCOUNTER_ID']
      - [2]
        - `fields` ['PATIENT_MR_ID_VALUE']
        - `reference`
          - `resource` qe_admin_data
          - `fields` ['PATIENT_MR_ID_VALUE']
### `PATIENT_MR_ID_VALUE`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').identifier.where(type.coding.code = 'MR').value
  - `type` string
  - `constraints`:
    - `required` True
    - `pattern` `[ \r\n\t\S]+`
### `FACILITY_ID`
  - `description` Append to the Bundle.entry.resource.where(resourceType ='Organization').id
  - `type` string
  - `constraints`:
    - `required` True
    - `pattern` `[A-Za-z0-9\-\.]{1,64}`
### `FACILITY_NAME`
  - `description` Bundle.entry.resource.where(resourceType ='Organization').name
  - `type` string
  - `constraints`:
    - `required` True
    - `pattern` `[ \r\n\t\S]+`
### `ENCOUNTER_ID`
  - `description` Bundle.entry.resource.where(resourceType ='Encounter').id
  - `type` string
  - `constraints`:
    - `required` True
### `SCREENING_IDENTIFIER`
  - `description` Bundle.entry.resource.where(resourceType ='Observation').id
  - `type` string
  - `constraints`:
    - `required` True
    - `pattern` `[A-Za-z0-9\-\.]{1,64}`
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
### `SCREENING_CODE_SYSTEM`
  - `description` Bundle.entry.resource.where(resourceType ='Observation' and hasMember.exists()).code.coding.system
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['http://loinc.org']
### `QUESTION_CODE`
  - `description` Bundle.entry.resource.where(resourceType ='Observation' and not(hasMember.exists())).code.coding.code
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['96777-8', '71802-3', '96778-6', '88122-7', '88123-5', '93030-5', '96779-4', '95618-5', '95617-7', '95616-9', '95615-1', '95614-4', '76513-1', '96780-2', '96781-0', '93159-2', '97027-7', '96782-8', '89555-7', '68516-4', '68517-2', '96842-0', '95530-2', '68524-8', '44250-9', '44255-8', '93038-8', '69858-9', '69861-3', '77594-0', '71969-0']
### `QUESTION_CODE_DESCRIPTION`
  - `description` Bundle.entry.resource.where(resourceType ='Observation' and not(hasMember.exists())).code.coding.display
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['what is your living situation today?', 'think about the place you live. do you have problems with any of the following?', 'within the past 12 months, you worried that your food would run out before you got money to buy more.', "within the past 12 months, the food you bought just didn't last and you didn't have money to get more.", 'in the past 12 months, has lack of reliable transportation kept you from medical appointments, meetings, work or from getting things needed for daily living?', 'in the past 12 months has the electric, gas, oil, or water company threatened to shut off services in your home?', 'how often does anyone, including family and friends, physically hurt you?', 'how often does anyone, including family and friends, insult or talk down to you?', 'how often does anyone, including family and friends, threaten you with harm?', 'how often does anyone, including family and friends, scream or curse at you?', 'total safety score', 'how hard is it for you to pay for the very basics like food, housing, medical care, and heating? would you say it is', 'do you want help finding or keeping work or a job?', 'if for any reason you need help with day-to-day activities such as bathing, preparing meals, shopping, managing finances, etc., do you get the help you need?', 'how often do you feel lonely or isolated from those around you?', 'do you speak a language other than english at home?', 'do you want help with school or training? for example, starting or completing job training or getting a high school diploma, ged or equivalent.', 'in the last 30 days, other than the activities you did for work, on average, how many days per week did you engage in moderate exercise (like walking fast, running, jogging, dancing, swimming, biking, or other similar activities)', 'on average, how many minutes did you usually spend exercising at this level on one of those days?', 'how many times in the past 12 months have you had 5 or more drinks in a day (males) or 4 or more drinks in a day (females)?', 'how often have you used any tobacco product in past 12 months?', 'how many times in the past year have you used prescription drugs for non-medical reasons?', 'how many times in the past year have you used illegal drugs?', 'little interest or pleasure in doing things?', 'feeling down, depressed, or hopeless?', 'stress means a situation in which a person feels tense, restless, nervous, or anxious, or is unable to sleep at night because his or her mind is troubled all the time. do you feel this kind of stress these days?', 'because of a physical, mental, or emotional condition, do you have serious difficulty concentrating, remembering, or making decisions?', "because of a physical, mental, or emotional condition, do you have difficulty doing errands alone such as visiting a physician's office or shopping", 'calculated weekly physical activity', 'promis-10 global mental health (gmh) score t-score']
### `QUESTION_CODE_SYSTEM`
  - `description` Bundle.entry.resource.where(resourceType ='Observation' and not(hasMember.exists())).code.coding.system
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['http://loinc.org']
### `ANSWER_CODE`
  - `description` Bundle.entry.resource.where(resourceType ='Observation' and not(hasMember.exists())).valueCodeableConcept.coding.code
  - `type` string
  - `constraints`:
    - `enum` ['la9-3', 'la32-8', 'la33-6', 'la6111-4', 'la6112-2', 'la6113-0', 'la6114-8', 'la6115-5', 'la6270-8', 'la6482-9', 'la6568-5', 'la6569-3', 'la6570-1', 'la6571-9', 'la6729-3', 'la9933-8', 'la10044-8', 'la10066-1', 'la10082-8', 'la10137-0', 'la10138-8', 'la10139-6', 'la13863-8', 'la13902-4', 'la13909-9', 'la13914-9', 'la13942-0', 'la15832-1', 'la16644-9', 'la18876-5', 'la18891-4', 'la18934-2', 'la19282-5', 'la22683-9', 'la26460-8', 'la28397-0', 'la28398-8', 'la28580-1', 'la28853-2', 'la28854-0', 'la28855-7', 'la28858-1', 'la28891-2', 'la30122-8', 'la31976-6', 'la31977-4', 'la31978-2', 'la31979-0', 'la31980-8', 'la31981-6', 'la31982-4', 'la31983-2', 'la31993-1', 'la31994-9', 'la31995-6', 'la31996-4', 'la31997-2', 'la31998-0', 'la31999-8', 'la32000-4', 'la32001-2', 'la32002-0', 'la32059-0', 'la32060-8']
### `ANSWER_CODE_DESCRIPTION`
  - `description` Bundle.entry.resource.where(resourceType ='Observation' and not(hasMember.exists())).valueCodeableConcept.coding.display
  - `type` string
  - `constraints`:
    - `enum` ['i have a steady place to live', 'i have a place to live today, but i am worried about losing it in the future', 'i do not have a steady place to live (i am temporarily staying with others, in a hotel, in a shelter,living outside on the street, on a beach, in a car, abandoned building, bus or train station, or in a park)', 'pests such as bugs, ants, or mice', 'mold', 'lead paint or pipes', 'lack of heat', 'oven or stove not working', 'smoke detectors missing or not working', 'water leaks', 'none of the above', 'often true', 'sometimes true', 'never true', 'often true', 'sometimes true', 'never true', 'yes', 'no', 'yes', 'no', 'already shut off', 'never (1)', 'rarely (2)', 'sometimes (3)', 'fairly often (4)', 'frequently (5)', 'never (1)', 'rarely (2)', 'sometimes (3)', 'fairly often (4)', 'frequently (5)', 'never (1)', 'rarely (2)', 'sometimes (3)', 'fairly often (4)', 'frequently (5)', 'never (1)', 'rarely (2)', 'sometimes (3)', 'fairly often (4)', 'frequently (5)', 'very hard', 'somewhat hard', 'not hard at all', 'yes, help finding work', 'yes, help keeping work', 'i do not need or want help', "i don't need any help", 'i get all the help i need', 'i could use a little more help', 'i need a lot more help', 'never', 'rarely', 'sometimes', 'often', 'always', 'yes', 'no', 'yes', 'no', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '10', '11', '12', '13', '14', '15', '16', '17', '18', '19', '20', '30', '40', '50', '60', '70', '80', '90', '100', '120', '140', '150', '160', '180', '200', '210', '240', '250', '270', '280', '300', '350', '360', '420', '450', '480', '540', '600', '630', '720', '750', '840', '900', '1050', '150 or greater', 'never', 'once or twice', 'monthly', 'weekly', 'daily or almost daily', 'never', 'once or twice', 'monthly', 'weekly', 'daily or almost daily', 'never', 'once or twice', 'monthly', 'weekly', 'daily or almost daily', 'never', 'once or twice', 'monthly', 'weekly', 'daily or almost daily', 'not at all (0)', 'several days (1)', 'more than half the days (2)', 'nearly every day (3)', 'not at all (0)', 'several days (1)', 'more than half the days (2)', 'nearly every day (3)', 'not at all', 'a little bit', 'somewhat', 'quite a bit', 'very much', 'i choose not to answer this question', 'yes', 'no', 'yes', 'no']
### `ANSWER_CODE_SYSTEM`
  - `description` Bundle.entry.resource.where(resourceType ='Observation' and not(hasMember.exists())).valueCodeableConcept.coding.system
  - `type` string
  - `constraints`:
    - `enum` ['http://loinc.org']
### `OBSERVATION_CATEGORY_SDOH_CODE`
  - `description` Bundle.entry.resource.where(resourceType ='Observation').category.where(coding.system = 'http://hl7.org/fhir/us/sdoh-clinicalcare/CodeSystem/SDOHCC-CodeSystemTemporaryCodes').coding.code
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['sdoh-category-unspecified', 'food-insecurity', 'housing-instability', 'homelessness', 'inadequate-housing', 'transportation-insecurity', 'financial-insecurity', 'material-hardship', 'educational-attainment', 'employment-status', 'veteran-status', 'stress', 'social-connection', 'intimate-partner-violence', 'elder-abuse', 'personal-health-literacy', 'health-insurance-coverage-status', 'medical-cost-burden', 'digital-literacy', 'digital-access', 'utility-insecurity', 'resulting-activity', 'sdoh-condition-category', 'payer-coverage', 'general-information', 'make-contact', 'review-material', 'risk-questionnaire', 'feedback-questionnaire', 'application-questionnaire', 'personal-characteristics-questionnaire', 'contact-entity', 'general-information-response', 'questionnaire-category', 'questionnaire-pdf', 'questionnaire-url', 'questionnaire-pdf-completed', 'contacting-subject-prohibited', 'self-reported', 'reported-by-related-person', 'observed', 'administrative', 'derived-specify', 'other-specify', 'personal-characteristic', 'chosen-contact']
### `OBSERVATION_CATEGORY_SDOH_TEXT`
  - `description` Bundle.entry.resource.where(resourceType ='Observation').category.where(coding.system = 'http://hl7.org/fhir/us/sdoh-clinicalcare/CodeSystem/SDOHCC-CodeSystemTemporaryCodes').text
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
### `POTENTIAL_NEED_INDICATED`
  - `description` Bundle.entry.resource.where(resourceType ='Observation').interpretation.coding.code
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['_GeneticObservationInterpretation', 'CAR', '_ObservationInterpretationChange', 'B', 'D', 'U', 'W', '_ObservationInterpretationExceptions', '<', '>', 'IE', '_ObservationInterpretationNormality', 'A', 'AA', 'HH', 'LL', 'H', 'HU', 'L', 'LU', 'N', '_ObservationInterpretationSusceptibility', 'I', 'NCL', 'NS', 'R', 'SYN-R', 'S', 'SDD', 'SYN-S', 'EX', 'HX', 'LX', 'ObservationInterpretationDetection', 'IND', 'E', 'NEG', 'ND', 'POS', 'DET', 'ObservationInterpretationExpectation', 'EXP', 'UNE', 'ReactivityObservationInterpretation', 'NR', 'RR', 'WR', 'NULL']
### `SCREENING_START_DATETIME`
  - `description` Bundle.entry.resource.where(resourceType = 'Observation').effectiveDateTime OR Bundle.entry.resource.where(resourceType = 'Observation').effectivePeriod.start
  - `type` string
  - `constraints`:
    - `required` True
    - `pattern` `^([0-9]([0-9]([0-9][1-9]|[1-9]0)|[1-9]00)|[1-9]000)-(0[1-9]|1[0-2])-(0[1-9]|[1-2][0-9]|3[0-1])T([01][0-9]|2[0-3]):[0-5][0-9]:([0-5][0-9]|60)(\.[0-9]+)?(Z|(\+|-)((0[0-9]|1[0-3]):[0-5][0-9]|14:00))$`
### `SCREENING_END_DATETIME`
  - `description` Bundle.entry.resource.where(resourceType = 'Observation').effectivePeriod.end
  - `type` string
  - `constraints`:
    - `pattern` `^([0-9]([0-9]([0-9][1-9]|[1-9]0)|[1-9]00)|[1-9]000)-(0[1-9]|1[0-2])-(0[1-9]|[1-2][0-9]|3[0-1])T([01][0-9]|2[0-3]):[0-5][0-9]:([0-5][0-9]|60)(\.[0-9]+)?(Z|(\+|-)((0[0-9]|1[0-3]):[0-5][0-9]|14:00))$`
## `pt_info_data`
  - `path` nyher-fhir-ig-example/SDOH_ScreeningProf_CareRidgeSCN_testcase1_20250312040214.csv
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
    - `pattern` `[ \r\n\t\S]+`
### `FACILITY_NAME`
  - `description` Bundle.entry.resource.where(resourceType ='Organization').name
  - `type` string
  - `constraints`:
    - `required` True
    - `pattern` `[ \r\n\t\S]+`
### `PATIENT_MEDICAID_ID`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').identifier.where(type.coding.code = 'MA').value
  - `type` string
  - `constraints`:
    - `required` True
    - `pattern` `^[A-Za-z]{2}\d{5}[A-Za-z]$`
### `PATIENT_SS_ID_VALUE`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').identifier.where(type.coding.code = 'SS').value
  - `type` string
### `FAMILY_NAME`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').name.family
  - `type` string
  - `constraints`:
    - `required` True
    - `pattern` `[\r\n\t\S]+`
### `GIVEN_NAME`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').name.given
  - `type` string
  - `constraints`:
    - `required` True
    - `minLength` 1
    - `pattern` `[\r\n\t\S]+`
### `MIDDLE_NAME`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').name.extension.valueString
  - `type` string
  - `constraints`:
    - `pattern` `[\r\n\t\S]+`
### `ADMINISTRATIVE_SEX_CODE`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').gender(code)
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['male', 'female', 'other', 'unknown']
### `ADMINISTRATIVE_SEX_CODE_DESCRIPTION`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').gender(display)
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['male', 'female', 'other', 'unknown']
### `ADMINISTRATIVE_SEX_CODE_SYSTEM`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').gender(system)
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['http://hl7.org/fhir/administrative-gender']
### `SEX_AT_BIRTH_CODE`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').extension.where(url='http://hl7.org/fhir/us/core/StructureDefinition/us-core-birthsex').valueCode
  - `type` string
  - `constraints`:
    - `enum` ['f', 'm', 'unk']
### `SEX_AT_BIRTH_CODE_DESCRIPTION`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').extension.where(url='http://hl7.org/fhir/us/core/StructureDefinition/us-core-birthsex').valueCode
  - `type` string
  - `constraints`:
    - `enum` ['female', 'male', 'unknown']
### `SEX_AT_BIRTH_CODE_SYSTEM`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').extension.where(url='http://hl7.org/fhir/us/core/StructureDefinition/us-core-birthsex').valueCode
  - `type` string
  - `constraints`:
    - `enum` ['http://terminology.hl7.org/CodeSystem/v3-AdministrativeGender', 'http://terminology.hl7.org/CodeSystem/v3-NullFlavor']
### `PATIENT_BIRTH_DATE`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').birthDate
  - `type` string
  - `constraints`:
    - `required` True
    - `pattern` `^([0-9]([0-9]([0-9][1-9]|[1-9]0)|[1-9]00)|[1-9]000)(-(0[1-9]|1[0-2])(-(0[1-9]|[1-2][0-9]|3[0-1]))?)?$`
### `ADDRESS1`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').address.line
  - `type` string
  - `constraints`:
    - `required` True
### `ADDRESS2`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').address.text
  - `type` string
### `CITY`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').address.city
  - `type` string
  - `constraints`:
    - `required` True
### `STATE`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').address.state
  - `type` string
  - `constraints`:
    - `required` True
    - `enum` ['AK', 'AL', 'AR', 'AS', 'AZ', 'CA', 'CO', 'CT', 'DC', 'DE', 'FL', 'FM', 'GA', 'GU', 'HI', 'IA', 'ID', 'IL', 'IN', 'KS', 'KY', 'LA', 'MA', 'MD', 'ME', 'MH', 'MI', 'MN', 'MO', 'MP', 'MS', 'MT', 'NC', 'ND', 'NE', 'NH', 'NJ', 'NM', 'NV', 'NY', 'OH', 'OK', 'OR', 'PA', 'PR', 'PW', 'RI', 'SC', 'SD', 'TN', 'TX', 'UT', 'VA', 'VI', 'VT', 'WA', 'WI', 'WV', 'WY']
### `ZIP`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').address.postalCode
  - `type` string
  - `constraints`:
    - `required` True
    - `pattern` `^\d{5}(-?\d{4})?$`
### `COUNTY`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').address.district
  - `type` string
  - `constraints`:
    - `required` True
### `TELECOM_VALUE`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').telecom.value
  - `type` string
### `TELCOM_USE`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').telecom.use
  - `type` string
  - `constraints`:
    - `enum` ['home', 'work', 'temp', 'old', 'mobile']
### `RACE_CODE`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').extension.where(url='http://hl7.org/fhir/us/core/StructureDefinition/us-core-race').extension.where(url = 'ombCategory').valueCoding.code
  - `type` string
### `RACE_CODE_DESCRIPTION`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').extension.where(url='http://hl7.org/fhir/us/core/StructureDefinition/us-core-race').extension.where(url = 'ombCategory').valueCoding.display
  - `type` string
### `RACE_CODE_SYSTEM`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').extension.where(url='http://hl7.org/fhir/us/core/StructureDefinition/us-core-race').extension.where(url = 'ombCategory').valueCoding.system
  - `type` string
  - `constraints`:
    - `enum` ['urn:oid:2.16.840.1.113883.6.238', 'http://terminology.hl7.org/CodeSystem/v3-NullFlavor']
### `ETHNICITY_CODE`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').extension.where(url='http://hl7.org/fhir/us/core/StructureDefinition/us-core-ethnicity').extension.where(url = 'ombCategory').valueCoding.code
  - `type` string
### `ETHNICITY_CODE_DESCRIPTION`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').extension.where(url='http://hl7.org/fhir/us/core/StructureDefinition/us-core-ethnicity').extension.where(url = 'ombCategory').valueCoding.display
  - `type` string
### `ETHNICITY_CODE_SYSTEM`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').extension.where(url='http://hl7.org/fhir/us/core/StructureDefinition/us-core-ethnicity').extension.where(url = 'ombCategory').valueCoding.system
  - `type` string
  - `constraints`:
    - `enum` ['urn:oid:2.16.840.1.113883.6.238', 'http://terminology.hl7.org/CodeSystem/v3-NullFlavor']
### `PERSONAL_PRONOUNS_CODE`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').extension.where(url='http://shinny.org/us/ny/hrsn/StructureDefinition/shinny-personal-pronouns').valueCodeableConcept.coding.code
  - `type` string
  - `constraints`:
    - `enum` ['LA29518-0', 'LA29519-8', 'LA29520-6', 'OTH', 'UNK']
### `PERSONAL_PRONOUNS_DESCRIPTION`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').extension.where(url='http://shinny.org/us/ny/hrsn/StructureDefinition/shinny-personal-pronouns').valueCodeableConcept.coding.display
  - `type` string
  - `constraints`:
    - `enum` ['he/him/his/his/himself', 'she/her/her/hers/herself', 'they/them/their/theirs/themselves', 'other', 'unknown']
### `PERSONAL_PRONOUNS_SYSTEM`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').extension.where(url='http://shinny.org/us/ny/hrsn/StructureDefinition/shinny-personal-pronouns').valueCodeableConcept.coding.system
  - `type` string
  - `constraints`:
    - `enum` ['http://loinc.org/', 'http://loinc.org', 'http://terminology.hl7.org/CodeSystem/v3-NullFlavor']
### `GENDER_IDENTITY_CODE`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').extension.where(url='http://terminology.hl7.org/CodeSystem/v3-NullFlavor').valueCodeableConcept.coding.code
  - `type` string
  - `constraints`:
    - `enum` ['33791000087105', '407376001', '407377005', '446131000124102', '446141000124107', '446151000124109', 'OTH', 'UNK', 'asked-declined']
### `GENDER_IDENTITY_CODE_DESCRIPTION`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').extension.where(url='http://terminology.hl7.org/CodeSystem/v3-NullFlavor').valueCodeableConcept.coding.display
  - `type` string
  - `constraints`:
    - `enum` ['identifies as nonbinary gender (finding)', 'male-to-female transsexual (finding)', 'female-to-male transsexual (finding)', 'identifies as non-conforming gender (finding)', 'identifies as female gender (finding)', 'identifies as male gender (finding)', 'other', 'unknown', 'asked but declined']
### `GENDER_IDENTITY_CODE_SYSTEM`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').extension.where(url='http://terminology.hl7.org/CodeSystem/v3-NullFlavor').valueCodeableConcept.coding.system
  - `type` string
  - `constraints`:
    - `enum` ['http://terminology.hl7.org/CodeSystem/v3-NullFlavor', 'http://terminology.hl7.org/CodeSystem/data-absent-reason', 'http://snomed.info/sct', 'http://shinny.org/us/ny/hrsn/StructureDefinition/shinny-gender-identity']
### `PREFERRED_LANGUAGE_CODE`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').communication.language.coding.code & Bundle.entry.resource.where(resourceType ='Patient').communication.preferred = true
  - `type` string
  - `constraints`:
    - `enum` ['ar', 'bn', 'cs', 'da', 'de', 'de-AT', 'de-CH', 'de-DE', 'el', 'en', 'en-AU', 'en-CA', 'en-GB', 'en-IN', 'en-NZ', 'en-SG', 'en-US', 'es', 'es-AR', 'es-ES', 'es-UY', 'fi', 'fr', 'fr-BE', 'fr-CH', 'fr-FR', 'fy', 'fy-NL', 'hi', 'hr', 'it', 'it-CH', 'it-IT', 'ja', 'ko', 'nl', 'nl-BE', 'nl-NL', 'no', 'no-NO', 'pa', 'pl', 'pt', 'pt-BR', 'ru', 'ru-RU', 'sr', 'sr-RS', 'sv', 'sv-SE', 'te', 'zh', 'zh-CN', 'zh-HK', 'zh-SG', 'zh-TW']
### `PREFERRED_LANGUAGE_CODE_DESCRIPTION`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').communication.language.coding.display & Bundle.entry.resource.where(resourceType ='Patient').communication.preferred = true
  - `type` string
  - `constraints`:
    - `enum` ['arabic', 'bengali', 'czech', 'danish', 'german', 'german (austria)', 'german (switzerland)', 'german (germany)', 'greek', 'english', 'english (australia)', 'english (canada)', 'english (great britain)', 'english (india)', 'english (new zealand)', 'english (singapore)', 'english (united states)', 'spanish', 'spanish (argentina)', 'spanish (spain)', 'spanish (uruguay)', 'finnish', 'french', 'french (belgium)', 'french (switzerland)', 'french (france)', 'frysian', 'frysian (netherlands)', 'hindi', 'croatian', 'italian', 'italian (switzerland)', 'italian (italy)', 'japanese', 'korean', 'dutch', 'dutch (belgium)', 'dutch (netherlands)', 'norwegian', 'norwegian (norway)', 'punjabi', 'polish', 'portuguese', 'portuguese (brazil)', 'russian', 'russian (russia)', 'serbian', 'serbian (serbia)', 'swedish', 'swedish (sweden)', 'telegu', 'chinese', 'chinese (china)', 'chinese (hong kong)', 'chinese (singapore)', 'chinese (taiwan)']
### `PREFERRED_LANGUAGE_CODE_SYSTEM`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').communication.language.coding.system & Bundle.entry.resource.where(resourceType ='Patient').communication.preferred = true
  - `type` string
  - `constraints`:
    - `enum` ['iso', 'iso 639-2', 'http://hl7.org/fhir/us/core/valueset/simple-language', 'urn:ietf:bcp:47']
### `SEXUAL_ORIENTATION_CODE`
  - `description` Bundle.entry.resource.where(resourceType = 'Observation').where(meta.profile = 'http://shinny.org/us/ny/hrsn/StructureDefinition/shin-ny-observation-sexual-orientation').valueCodeableConcept.coding.code
  - `type` string
  - `constraints`:
    - `enum` ['20430005', '38628009', '42035005', '765288000', 'OTH', 'UNK', 'asked-declined']
### `SEXUAL_ORIENTATION_CODE_DESCRIPTION`
  - `description` Bundle.entry.resource.where(resourceType = 'Observation').where(meta.profile = 'http://shinny.org/us/ny/hrsn/StructureDefinition/shin-ny-observation-sexual-orientation').valueCodeableConcept.coding.display
  - `type` string
  - `constraints`:
    - `enum` ['heterosexual (finding)', 'homosexual (finding)', 'bisexual (finding)', 'sexually attracted to neither male nor female sex (finding)', 'other', 'unknown', 'asked but declined']
### `SEXUAL_ORIENTATION_CODE_SYSTEM`
  - `description` Bundle.entry.resource.where(resourceType = 'Observation').where(meta.profile = 'http://shinny.org/us/ny/hrsn/StructureDefinition/shin-ny-observation-sexual-orientation').valueCodeableConcept.coding.system
  - `type` string
  - `constraints`:
    - `enum` ['http://snomed.info/sct', 'http://terminology.hl7.org/CodeSystem/v3-NullFlavor']
### `PATIENT_LAST_UPDATED`
  - `description` Bundle.entry.resource.where(resourceType ='Patient').meta.lastUpdated
  - `type` string
  - `constraints`:
    - `pattern` `^([0-9]([0-9]([0-9][1-9]|[1-9]0)|[1-9]00)|[1-9]000)-(0[1-9]|1[0-2])-(0[1-9]|[1-2][0-9]|3[0-1])T([01][0-9]|2[0-3]):[0-5][0-9]:([0-5][0-9]|60)(\.[0-9]+)?(Z|(\+|-)((0[0-9]|1[0-3]):[0-5][0-9]|14:00))$`