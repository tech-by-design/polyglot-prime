<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0"
                xmlns:ccda="urn:hl7-org:v3"
                xmlns:fhir="http://hl7.org/fhir">

  <xsl:output method="text"/>
  
  <!-- Root template -->
  <xsl:param name="currentTimestamp"/>
  <xsl:variable name="patientRoleId" select="//ccda:patientRole/ccda:id/ccda:extension"/>
  <xsl:variable name="consentResourceId" select="translate(concat(generate-id(//ccda:consent), $patientRoleId, $currentTimestamp), ':-+', '')"/>
  <xsl:variable name="patientResourceId" select="translate(concat(generate-id(//ccda:patientRole), $patientRoleId, $currentTimestamp), ':-+', '')"/>
  <xsl:variable name="patientResourceName" select="concat(//ccda:patientRole/ccda:patient/ccda:name/ccda:family, ' ', //ccda:patientRole/ccda:patient/ccda:name/ccda:given)"/>
  <xsl:variable name="bundleId" select="translate(concat(generate-id(//ccda:patientRole/ccda:id/ccda:extension), $currentTimestamp), ':-+', '')"/>
  <xsl:variable name="encounterResourceId" select="translate(concat(generate-id(//ccda:encompassingEncounter), $patientRoleId, $currentTimestamp), ':-+', '')"/>
  <xsl:variable name="organizationResourceId" select="translate(concat(generate-id(//ccda:author), $patientRoleId, $currentTimestamp), ':-+', '')"/>
  <xsl:variable name="bundleTimestamp" select="//ccda:header/ccda:effectiveTime/ccda:value"/>
  <xsl:variable name="questionnaireResourceId" select="translate(concat(generate-id(//ccda:Questionnaire), $patientRoleId, $currentTimestamp), ':-+', '')"/>

  <!-- Parameters to get FHIR resource profile URLs -->
  <xsl:param name="baseFhirUrl"/>
  <xsl:param name="bundleMetaProfileUrl"/>
  <xsl:param name="patientMetaProfileUrl"/>
  <xsl:param name="consentMetaProfileUrl"/>
  <xsl:param name="encounterMetaProfileUrl"/>
  <xsl:param name="organizationMetaProfileUrl"/>
  <xsl:param name="observationMetaProfileUrl"/>
  <xsl:param name="observationSexualOrientationMetaProfileUrl"/>
  <xsl:param name="questionnaireMetaProfileUrl"/>
  <xsl:param name="questionnaireResponseMetaProfileUrl"/>
  <xsl:param name="practitionerMetaProfileUrl"/>

  <xsl:template match="/">
  {
    "resourceType": "Bundle",
    "id": "<xsl:value-of select='$bundleId'/>",
    "meta": {
      "lastUpdated": "<xsl:value-of select='$currentTimestamp'/>",
      "profile": [
        "<xsl:value-of select='$bundleMetaProfileUrl'/>"
      ]
    },
    "type": "transaction"
    <xsl:if test="$bundleTimestamp"> 
      , "timestamp": "<xsl:choose>
                        <xsl:when test='string-length($bundleTimestamp) >= 14'>
                          <xsl:value-of select='
                            concat(
                              substring($bundleTimestamp, 1, 4), "-", 
                              substring($bundleTimestamp, 5, 2), "-", 
                              substring($bundleTimestamp, 7, 2), "T", 
                              substring($bundleTimestamp, 9, 2), ":", 
                              substring($bundleTimestamp, 11, 2), ":", 
                              substring($bundleTimestamp, 13, 2), 
                              "Z"
                            )'/>
                        </xsl:when>
                        <xsl:when test='string-length($bundleTimestamp) >= 8'>
                          <xsl:value-of select='
                            concat(
                              substring($bundleTimestamp, 1, 4), "-", 
                              substring($bundleTimestamp, 5, 2), "-", 
                              substring($bundleTimestamp, 7, 2)
                            )'/>
                        </xsl:when>
                        <xsl:otherwise>
                          <xsl:value-of select='$bundleTimestamp'/>
                        </xsl:otherwise>
                      </xsl:choose>"
    </xsl:if>
    , "entry": [
      <xsl:apply-templates select="//ccda:patientRole 
                                | //ccda:encompassingEncounter 
                                | //ccda:observation 
                                | //ccda:location 
                                | //ccda:consent 
                                | //ccda:author
                                | //ccda:Questionnaire
                                "/>
      <xsl:apply-templates select="ccda:observation" mode="questionnaireresponse"/>
    ]
  }
  </xsl:template>

  <!-- Patient Template -->
  <xsl:template name="Patient" match="ccda:patientRole">
    {
      "fullUrl" : "<xsl:value-of select='$baseFhirUrl'/>/Patient/<xsl:value-of select='$patientResourceId'/>",
      "resource": {
        "resourceType": "Patient",
        "id": "<xsl:value-of select='$patientResourceId'/>",
        "meta" : {
          "lastUpdated" : "<xsl:value-of select='$currentTimestamp'/>",
          "profile" : ["<xsl:value-of select='$patientMetaProfileUrl'/>"]
        },
        <xsl:if test="string(ccda:patient/ccda:languageCommunication/ccda:languageCode/ccda:code)">
        "language" : "<xsl:value-of select="ccda:patient/ccda:languageCommunication/ccda:languageCode/ccda:code"/>",
        </xsl:if>
        "name": [
          {
            <xsl:if test="string(ccda:patient/ccda:name/ccda:given)">
            "extension" : [{
              "url" : "http://shinny.org/us/ny/hrsn/StructureDefinition/middle-name",
              "valueString" : "<xsl:value-of select="ccda:patient/ccda:name/ccda:given"/>"
            }],
            </xsl:if>
            <xsl:if test="string(ccda:patient/ccda:name/ccda:family)">
              "family": "<xsl:value-of select="ccda:patient/ccda:name/ccda:family"/>"
            </xsl:if>
            <xsl:if test="string(ccda:patient/ccda:name/ccda:given)">
              , "given": ["<xsl:value-of select="ccda:patient/ccda:name/ccda:given"/>"]
            </xsl:if>
            <xsl:if test="string(ccda:patient/ccda:name/ccda:prefix)">
              , "prefix": ["<xsl:value-of select='ccda:patient/ccda:name/ccda:prefix'/>"]
            </xsl:if>
            <xsl:if test="string(ccda:patient/ccda:name/ccda:suffix)">
              , "suffix": ["<xsl:value-of select="ccda:patient/ccda:name/ccda:suffix"/>"]
            </xsl:if>
          }
        ],
        "gender": "<xsl:choose>
                      <xsl:when test="ccda:patient/ccda:administrativeGenderCode/ccda:code = 'M'">male</xsl:when>
                      <xsl:when test="ccda:patient/ccda:administrativeGenderCode/ccda:code = 'F'">female</xsl:when>
                      <xsl:otherwise><xsl:value-of select="ccda:patient/ccda:administrativeGenderCode/ccda:code"/></xsl:otherwise>
                    </xsl:choose>"
        <xsl:if test="string(ccda:patient/ccda:birthTime/ccda:value)">
        , "birthDate": "<xsl:choose>
                        <xsl:when test='string-length(ccda:patient/ccda:birthTime/ccda:value) >= 8'>
                          <xsl:value-of select='concat(substring(ccda:patient/ccda:birthTime/ccda:value, 1, 4), "-", substring(ccda:patient/ccda:birthTime/ccda:value, 5, 2), "-", substring(ccda:patient/ccda:birthTime/ccda:value, 7, 2))'/>
                        </xsl:when>
                        <xsl:otherwise>
                          <xsl:value-of select='ccda:patient/ccda:birthTime/ccda:value'/>
                        </xsl:otherwise>
                      </xsl:choose>"
        </xsl:if>
        <xsl:if test="string(ccda:addr/ccda:streetAddressLine) or string(ccda:addr/ccda:city) or string(ccda:addr/ccda:state) or string(ccda:addr/ccda:postalCode) or string(ccda:addr/ccda:county)">
        , "address": [
          {
            <xsl:if test="string(ccda:addr/ccda:streetAddressLine) or string(ccda:addr/ccda:city) or string(ccda:addr/ccda:state) or string(ccda:addr/ccda:postalCode)">
              "text" : "<xsl:value-of select='ccda:addr/ccda:streetAddressLine'/>
              <xsl:if test="string(ccda:addr/ccda:city)"> <xsl:text> </xsl:text><xsl:value-of select='ccda:addr/ccda:city'/></xsl:if>
              <xsl:if test="string(ccda:addr/ccda:state)">, <xsl:value-of select='ccda:addr/ccda:state'/></xsl:if>
              <xsl:if test="string(ccda:addr/ccda:postalCode)"> <xsl:text> </xsl:text><xsl:value-of select='ccda:addr/ccda:postalCode'/></xsl:if>"
            </xsl:if>
            <xsl:if test="string(ccda:addr/ccda:streetAddressLine)">
              , "line": ["<xsl:value-of select="ccda:addr/ccda:streetAddressLine"/>"]
            </xsl:if>
            <xsl:if test="string(ccda:addr/ccda:city)">
              , "city": "<xsl:value-of select="ccda:addr/ccda:city"/>"
            </xsl:if>            
            <xsl:if test="string(ccda:addr/ccda:county)">
              , "district" : "<xsl:value-of select="ccda:addr/ccda:county"/>"
            </xsl:if>            
            <xsl:if test="string(ccda:addr/ccda:state)">
              , "state": "<xsl:value-of select="ccda:addr/ccda:state"/>"
            </xsl:if>            
            <xsl:if test="string(ccda:addr/ccda:postalCode)">
              , "postalCode": "<xsl:value-of select="ccda:addr/ccda:postalCode"/>"
            </xsl:if>            
          }
        ]
        </xsl:if> 
        <xsl:if test="string(ccda:telecom/ccda:value)">
        , "telecom": [
          {
            "system": "phone",
            "value": "<xsl:value-of select="ccda:telecom/ccda:value"/>",
            "use": "<xsl:choose>
                      <xsl:when test="ccda:telecom/ccda:use = 'HP'">home</xsl:when>
                      <xsl:when test="ccda:telecom/ccda:use = 'WP'">work</xsl:when>
                      <xsl:otherwise><xsl:value-of select="ccda:telecom/ccda:use"/></xsl:otherwise>
                    </xsl:choose>"
          }
        ]
        </xsl:if>
        , "extension" : [{
          "extension": [{
            "url" : "ombCategory",
            "valueCoding": {
              "system": "urn:oid:<xsl:value-of select="ccda:patient/ccda:raceCode/ccda:codeSystem"/>",
              "code": "<xsl:value-of select="ccda:patient/ccda:raceCode/ccda:code"/>",
              "display": "<xsl:value-of select="ccda:patient/ccda:raceCode/ccda:displayName"/>"
            }
          },
          {
            "url" : "text",
            "valueString" : "<xsl:value-of select="ccda:patient/ccda:raceCode/ccda:displayName"/>"
          }],
          "url" : "http://hl7.org/fhir/us/core/StructureDefinition/us-core-race"
        },
        {
          "extension": [{
            "url" : "ombCategory",
            "valueCoding": {
              "system": "urn:oid:<xsl:value-of select="ccda:patient/ccda:ethnicGroupCode/ccda:codeSystem"/>",
              "code": "<xsl:value-of select="ccda:patient/ccda:ethnicGroupCode/ccda:code"/>",
              "display": "<xsl:value-of select="ccda:patient/ccda:ethnicGroupCode/ccda:displayName"/>"
            }
          },
          {
            "url" : "text",
            "valueString" : "<xsl:value-of select="ccda:patient/ccda:ethnicGroupCode/ccda:displayName"/>"
          }],
          "url" : "http://hl7.org/fhir/us/core/StructureDefinition/us-core-ethnicity"
        },
        {
          "url" : "http://hl7.org/fhir/us/core/StructureDefinition/us-core-birthsex",
          "valueCode" : "<xsl:value-of select="ccda:patient/ccda:administrativeGenderCode/ccda:code"/>"
        }
      ],
      "identifier" : [{
          "type" : {
            "coding" : [{
              "system" : "http://terminology.hl7.org/CodeSystem/v2-0203",
              "code" : "MR",
              "display" : "Medical Record Number"
            }],
            "text" : "Medical Record Number"
          },
          "value" : "<xsl:value-of select='$patientRoleId'/>"
          <xsl:if test="string($organizationResourceId)">
          , "assigner" : {
            "reference" : "Organization/<xsl:value-of select="$organizationResourceId"/>"
          }
          </xsl:if>
        },
        {
          "type" : {
            "coding" : [{
              "system" : "http://terminology.hl7.org/CodeSystem/v2-0203",
              "code" : "MA",
              "display" : "Patient Medicaid Number"
            }],
            "text" : "Patient Medicaid Number"
          },
          "system" : "http://www.medicaid.gov/",
          "value" : "<xsl:value-of select='$patientRoleId'/>"
        },
        {
          "type" : {
            "coding" : [{
              "system" : "http://terminology.hl7.org/CodeSystem/v2-0203",
              "code" : "SS",
              "display" : "Social Security Number"
            }],
            "text" : "Social Security Number"
          },
          "system" : "http://www.ssa.gov/"
        }
      ],
      <xsl:if test="string(ccda:patient/ccda:languageCommunication/ccda:languageCode/ccda:code)">
      "communication" : [{
        "language" : {
          "coding" : [{
            "system" : "urn:ietf:bcp:47",
            "code" : "<xsl:value-of select="ccda:patient/ccda:languageCommunication/ccda:languageCode/ccda:code"/>"
          }]
        },
        "preferred" : <xsl:value-of select="ccda:patient/ccda:languageCommunication/ccda:preferenceInd/ccda:value"/>
      }],
      </xsl:if>
      "request" : {
        "method" : "POST",
        "url" : "<xsl:value-of select='$baseFhirUrl'/>/Patient/<xsl:value-of select='$patientResourceId'/>"
      }
    }
  }
  </xsl:template>

  <!-- Encounter Template -->
  <xsl:template name="Encounter" match="ccda:encompassingEncounter">
    ,{
      "fullUrl" : "<xsl:value-of select='$baseFhirUrl'/>/Encounter/<xsl:value-of select="$encounterResourceId"/>",
      "resource": {
        "resourceType": "Encounter",
        "id": "<xsl:value-of select="$encounterResourceId"/>",
        "meta" : {
          "lastUpdated" : "<xsl:value-of select='$currentTimestamp'/>",
          "profile" : ["<xsl:value-of select='$encounterMetaProfileUrl'/>"]
        },
        "status": "finished",
        "type": [
          {
            "coding": [
              {
                "system": "http://snomed.info/sct",
                "code": "<xsl:value-of select="ccda:code/ccda:value"/>",
                "display": "<xsl:value-of select="ccda:code/ccda:displayName"/>"
              }
            ],
            "text": "<xsl:value-of select="ccda:code/ccda:displayName"/>"
          }
        ],
        "subject" : {
          "reference" : "Patient/<xsl:value-of select='$patientResourceId'/>",
          "display" : "<xsl:value-of select="$patientResourceName"/>"
        },
        "period": {
          "start": "<xsl:choose>
                          <xsl:when test="string-length(ccda:effectiveTime/ccda:low/ccda:value) >= 14">
                            <xsl:value-of select="
                              concat(
                                substring(ccda:effectiveTime/ccda:low/ccda:value, 1, 4), '-', 
                                substring(ccda:effectiveTime/ccda:low/ccda:value, 5, 2), '-', 
                                substring(ccda:effectiveTime/ccda:low/ccda:value, 7, 2), 'T', 
                                substring(ccda:effectiveTime/ccda:low/ccda:value, 9, 2), ':', 
                                substring(ccda:effectiveTime/ccda:low/ccda:value, 11, 2), ':', 
                                substring(ccda:effectiveTime/ccda:low/ccda:value, 13, 2), 
                                'Z'
                              )"/>
                          </xsl:when>
                          <xsl:when test="string-length(ccda:effectiveTime/ccda:low/ccda:value) >= 8">
                            <xsl:value-of select="
                              concat(
                                substring(ccda:effectiveTime/ccda:low/ccda:value, 1, 4), '-', 
                                substring(ccda:effectiveTime/ccda:low/ccda:value, 5, 2), '-', 
                                substring(ccda:effectiveTime/ccda:low/ccda:value, 7, 2)
                              )"/>
                          </xsl:when>
                          <xsl:otherwise>
                            <xsl:value-of select="ccda:effectiveTime/ccda:low/ccda:value"/>
                          </xsl:otherwise>
                        </xsl:choose>",
          "end": "<xsl:choose>
                          <xsl:when test="string-length(ccda:effectiveTime/ccda:high/ccda:value) >= 14">
                            <xsl:value-of select="
                              concat(
                                substring(ccda:effectiveTime/ccda:high/ccda:value, 1, 4), '-', 
                                substring(ccda:effectiveTime/ccda:high/ccda:value, 5, 2), '-', 
                                substring(ccda:effectiveTime/ccda:high/ccda:value, 7, 2), 'T', 
                                substring(ccda:effectiveTime/ccda:high/ccda:value, 9, 2), ':', 
                                substring(ccda:effectiveTime/ccda:high/ccda:value, 11, 2), ':', 
                                substring(ccda:effectiveTime/ccda:high/ccda:value, 13, 2), 
                                'Z'
                              )"/>
                          </xsl:when>
                          <xsl:when test="string-length(ccda:effectiveTime/ccda:high/ccda:value) >= 8">
                            <xsl:value-of select="
                              concat(
                                substring(ccda:effectiveTime/ccda:high/ccda:value, 1, 4), '-', 
                                substring(ccda:effectiveTime/ccda:high/ccda:value, 5, 2), '-', 
                                substring(ccda:effectiveTime/ccda:high/ccda:value, 7, 2)
                              )"/>
                          </xsl:when>
                          <xsl:otherwise>
                            <xsl:value-of select="ccda:effectiveTime/ccda:high/ccda:value"/>
                          </xsl:otherwise>
                        </xsl:choose>"
        }
      },
      "request" : {
        "method" : "POST",
        "url" : "<xsl:value-of select='$baseFhirUrl'/>/Encounter/<xsl:value-of select="$encounterResourceId"/>"
      }
    }
  </xsl:template>

  <!-- Consent Template -->
  <xsl:template name="Consent" match="ccda:consent">
    ,{
      "fullUrl" : "<xsl:value-of select='$baseFhirUrl'/>/Consent/<xsl:value-of select='$consentResourceId'/>",
      "resource": {
        "resourceType": "Consent",
        "id": "<xsl:value-of select='$consentResourceId'/>",
        "meta" : {
          "lastUpdated" : "<xsl:value-of select='$currentTimestamp'/>",
          "profile" : ["<xsl:value-of select='$consentMetaProfileUrl'/>"]
        },
        "status": "<xsl:value-of select="ccda:statusCode/ccda:code"/>"
        <xsl:if test="string(ccda:entry/ccda:act/ccda:code/ccda:code)">
          , "scope" : {
            "coding" : [{
              "system" : "http://terminology.hl7.org/CodeSystem/consentscope",
              "code" : "<xsl:value-of select="ccda:entry/ccda:act/ccda:code/ccda:code"/>",
              "display" : "<xsl:value-of select="ccda:entry/ccda:act/ccda:code/ccda:displayName"/>"
            }],
            "text" : "<xsl:value-of select="ccda:entry/ccda:act/ccda:code/ccda:displayName"/>"
          }
        </xsl:if>
        <xsl:if test="string(ccda:code/ccda:code)">
          , "category": [
            {
              "coding": [
                {
                  "system": "http://loinc.org",
                  "code": "<xsl:value-of select="ccda:code/ccda:code"/>",
                  "display": "<xsl:value-of select="ccda:code/ccda:displayName"/>"
                }
              ]
            }
          ]
        </xsl:if>
        <xsl:if test="string(ccda:effectiveTime/ccda:value)">
        , "dateTime" : "<xsl:choose>
                          <xsl:when test="string-length(ccda:effectiveTime/ccda:value) >= 14">
                            <xsl:value-of select="
                              concat(
                                substring(ccda:effectiveTime/ccda:value, 1, 4), '-', 
                                substring(ccda:effectiveTime/ccda:value, 5, 2), '-', 
                                substring(ccda:effectiveTime/ccda:value, 7, 2), 'T', 
                                substring(ccda:effectiveTime/ccda:value, 9, 2), ':', 
                                substring(ccda:effectiveTime/ccda:value, 11, 2), ':', 
                                substring(ccda:effectiveTime/ccda:value, 13, 2), 
                                'Z'
                              )"/>
                          </xsl:when>
                          <xsl:when test="string-length(ccda:effectiveTime/ccda:value) >= 8">
                            <xsl:value-of select="
                              concat(
                                substring(ccda:effectiveTime/ccda:value, 1, 4), '-', 
                                substring(ccda:effectiveTime/ccda:value, 5, 2), '-', 
                                substring(ccda:effectiveTime/ccda:value, 7, 2)
                              )"/>
                          </xsl:when>
                          <xsl:otherwise>
                            <xsl:value-of select="ccda:effectiveTime/ccda:value"/>
                          </xsl:otherwise>
                        </xsl:choose>"
      </xsl:if>
        , "patient" : {
            "reference" : "Patient/<xsl:value-of select='$patientResourceId'/>"
        }
        , "organization" : [{
          "reference" : "Organization/<xsl:value-of select='$organizationResourceId'/>"
        }]
        <xsl:if test="string(ccda:provision/ccda:type)">
          , "provision" : {
            "type" : "<xsl:value-of select="ccda:provision/ccda:type"/>"
          }
        </xsl:if>
        <xsl:if test="string(ccda:policy/ccda:id)">
          , "policy" : [{
            "authority" : "<xsl:value-of select="ccda:policy/ccda:id"/>"
          }]
        </xsl:if>
        , "sourceAttachment" : {
          "contentType" : "application/pdf",
          "language" : "en"
        }
      },
      "request" : {
        "method" : "POST",
        "url" : "<xsl:value-of select='$baseFhirUrl'/>/Consent/<xsl:value-of select='$consentResourceId'/>"
      }
    }
  </xsl:template>

  <!-- Organization Template -->
  <xsl:template name="Organization" match="ccda:author">
    ,{
      "fullUrl" : "<xsl:value-of select='$baseFhirUrl'/>/Organization/<xsl:value-of select="$organizationResourceId"/>",
      "resource": {
        "resourceType": "Organization",
        "id": "<xsl:value-of select="$organizationResourceId"/>",
        "meta" : {
          "lastUpdated" : "<xsl:value-of select='$currentTimestamp'/>",
          "profile" : ["<xsl:value-of select='$organizationMetaProfileUrl'/>"]
        },
        "active": true,
        <xsl:if test="string(ccda:assignedAuthor/ccda:representedOrganization/ccda:name) or string(ccda:assignedAuthor/ccda:representedOrganization/ccda:id/ccda:extension)">
        "identifier": [
          {
            "type" : {
              "coding": [
                {
                  "display": "<xsl:value-of select="ccda:assignedAuthor/ccda:representedOrganization/ccda:name"/>"
                }
              ]
            },
            "system" : "http://www.scn.ny.gov/",
            "value" : "<xsl:value-of select="ccda:assignedAuthor/ccda:representedOrganization/ccda:id/ccda:extension"/>"
          }
        ],
        </xsl:if>
        "name" : "<xsl:value-of select="ccda:assignedAuthor/ccda:representedOrganization/ccda:name"/>"
        <xsl:if test="string(ccda:assignedAuthor/ccda:representedOrganization/ccda:telecom/ccda:value)">
          , "telecom" : [{
            "system" : "phone",
            "value" : "<xsl:value-of select="ccda:assignedAuthor/ccda:representedOrganization/ccda:telecom/ccda:value"/>",
            "use": "<xsl:choose>
                        <xsl:when test="ccda:assignedAuthor/ccda:representedOrganization/ccda:telecom/ccda:use = 'HP'">home</xsl:when>
                        <xsl:when test="ccda:assignedAuthor/ccda:representedOrganization/ccda:telecom/ccda:use = 'WP'">work</xsl:when>
                        <xsl:otherwise><xsl:value-of select="ccda:assignedAuthor/ccda:representedOrganization/ccda:telecom/ccda:use"/></xsl:otherwise>
                      </xsl:choose>"
          }]
        </xsl:if>
        <xsl:if test="string(ccda:assignedAuthor/ccda:representedOrganization/ccda:addr/ccda:streetAddressLine) or string(ccda:assignedAuthor/ccda:representedOrganization/ccda:addr/ccda:city) or string(ccda:assignedAuthor/ccda:representedOrganization/ccda:addr/ccda:state) or string(ccda:assignedAuthor/ccda:representedOrganization/ccda:addr/ccda:postalCode) or string(ccda:assignedAuthor/ccda:representedOrganization/ccda:addr/ccda:county)">
        , "address": [
          {
            <xsl:if test="string(ccda:assignedAuthor/ccda:representedOrganization/ccda:addr/ccda:streetAddressLine) or string(ccda:assignedAuthor/ccda:representedOrganization/ccda:addr/ccda:city) or string(ccda:assignedAuthor/ccda:representedOrganization/ccda:addr/ccda:state) or string(ccda:assignedAuthor/ccda:representedOrganization/ccda:addr/ccda:postalCode)">
              "text" : "<xsl:value-of select='ccda:assignedAuthor/ccda:representedOrganization/ccda:addr/ccda:streetAddressLine'/>
              <xsl:if test="string(ccda:assignedAuthor/ccda:representedOrganization/ccda:addr/ccda:city)"> <xsl:text> </xsl:text><xsl:value-of select='ccda:assignedAuthor/ccda:representedOrganization/ccda:addr/ccda:city'/></xsl:if>
              <xsl:if test="string(ccda:assignedAuthor/ccda:representedOrganization/ccda:addr/ccda:state)">, <xsl:value-of select='ccda:assignedAuthor/ccda:representedOrganization/ccda:addr/ccda:state'/></xsl:if>
              <xsl:if test="string(ccda:assignedAuthor/ccda:representedOrganization/ccda:addr/ccda:postalCode)"> <xsl:text> </xsl:text><xsl:value-of select='ccda:assignedAuthor/ccda:representedOrganization/ccda:addr/ccda:postalCode'/></xsl:if>"
            </xsl:if>
            <xsl:if test="string(ccda:assignedAuthor/ccda:representedOrganization/ccda:addr/ccda:streetAddressLine)">
              , "line": ["<xsl:value-of select="ccda:assignedAuthor/ccda:representedOrganization/ccda:addr/ccda:streetAddressLine"/>"]
            </xsl:if>
            <xsl:if test="string(ccda:assignedAuthor/ccda:representedOrganization/ccda:addr/ccda:city)">
              , "city": "<xsl:value-of select="ccda:assignedAuthor/ccda:representedOrganization/ccda:addr/ccda:city"/>"
            </xsl:if>            
            <xsl:if test="string(ccda:assignedAuthor/ccda:representedOrganization/ccda:addr/ccda:county)">
              , "district" : "<xsl:value-of select="ccda:assignedAuthor/ccda:representedOrganization/ccda:addr/ccda:county"/>"
            </xsl:if>            
            <xsl:if test="string(ccda:assignedAuthor/ccda:representedOrganization/ccda:addr/ccda:state)">
              , "state": "<xsl:value-of select="ccda:assignedAuthor/ccda:representedOrganization/ccda:addr/ccda:state"/>"
            </xsl:if>            
            <xsl:if test="string(ccda:assignedAuthor/ccda:representedOrganization/ccda:addr/ccda:postalCode)">
              , "postalCode": "<xsl:value-of select="ccda:assignedAuthor/ccda:representedOrganization/ccda:addr/ccda:postalCode"/>"
            </xsl:if>            
          }
        ]
        </xsl:if> 
      },
      "request" : {
        "method" : "POST",
        "url" : "<xsl:value-of select='$baseFhirUrl'/>/Organization/<xsl:value-of select="$organizationResourceId"/>"
      }
    }
  </xsl:template>

  <!-- Sexual orientation Observation Template -->
  <xsl:template name="SexualOrientation" match="ccda:observation[ccda:code/ccda:code = '76690-7']">
    <xsl:if test="ccda:code/ccda:code and string(ccda:code/ccda:code) = '76690-7'">
      <xsl:variable name="observationResourceId" select="translate(concat(generate-id(ccda:code/ccda:code), position(), $patientRoleId, $currentTimestamp), ':-+', '')"/>
      ,{
        "fullUrl" : "<xsl:value-of select='$baseFhirUrl'/>/Observation/<xsl:value-of select='$observationResourceId'/>",
        "resource": {
          "resourceType": "Observation",
          "id": "<xsl:value-of select='$observationResourceId'/>",
          "meta" : {
            "lastUpdated" : "<xsl:value-of select='$currentTimestamp'/>",
            "profile" : ["<xsl:value-of select='$observationSexualOrientationMetaProfileUrl'/>"]
          },
          "status": "<xsl:value-of select='ccda:statusCode/ccda:code'/>",
          "category": [
            {
              "coding": [{
                  "system": "http://terminology.hl7.org/CodeSystem/observation-category",
                  "code": "<xsl:value-of select='ccda:code/ccda:code'/>",
                  "display": "<xsl:value-of select='ccda:code/ccda:displayName'/>"
              }]
            }
          ],
          "code": {
            "coding": [
              {
                "system": "http://loinc.org",
                "code": "<xsl:value-of select='ccda:code/ccda:code'/>",
                "display": "<xsl:value-of select='ccda:code/ccda:displayName'/>"
              }
            ],
            "text" : "<xsl:value-of select='ccda:code/ccda:originalText'/>"
          },
          <xsl:choose>
            <xsl:when test="string(ccda:value/ccda:code) = 'UNK' or string(ccda:value/ccda:code) = ''">
              "valueCodeableConcept" : {
                "coding" : [{
                  "system" : "http://terminology.hl7.org/CodeSystem/v3-NullFlavor",
                  "code" : "UNK",
                  "display" : "Unknown"
                }]
              },
            </xsl:when>
            <xsl:otherwise>
              "valueCodeableConcept" : {
                "coding" : [{
                  "system" : "http://loinc.org",
                  "code" : "<xsl:value-of select='ccda:value/ccda:code'/>",
                  "display" : "<xsl:value-of select='ccda:value/ccda:displayName'/>"
                }]
              },
            </xsl:otherwise>
          </xsl:choose>
          "subject": {
            "reference": "Patient/<xsl:value-of select='$patientResourceId'/>",
            "display" : "<xsl:value-of select="$patientResourceName"/>"
          }
          <xsl:if test="string(ccda:effectiveTime/ccda:value)">
          , "effectiveDateTime" : "<xsl:choose>
                            <xsl:when test="string-length(ccda:effectiveTime/ccda:value) >= 14">
                              <xsl:value-of select="
                                concat(
                                  substring(ccda:effectiveTime/ccda:value, 1, 4), '-', 
                                  substring(ccda:effectiveTime/ccda:value, 5, 2), '-', 
                                  substring(ccda:effectiveTime/ccda:value, 7, 2), 'T', 
                                  substring(ccda:effectiveTime/ccda:value, 9, 2), ':', 
                                  substring(ccda:effectiveTime/ccda:value, 11, 2), ':', 
                                  substring(ccda:effectiveTime/ccda:value, 13, 2), 
                                  'Z'
                                )"/>
                            </xsl:when>
                            <xsl:when test="string-length(ccda:effectiveTime/ccda:value) >= 8">
                              <xsl:value-of select="
                                concat(
                                  substring(ccda:effectiveTime/ccda:value, 1, 4), '-', 
                                  substring(ccda:effectiveTime/ccda:value, 5, 2), '-', 
                                  substring(ccda:effectiveTime/ccda:value, 7, 2)
                                )"/>
                            </xsl:when>
                            <xsl:otherwise>
                              <xsl:value-of select="ccda:effectiveTime/ccda:value"/>
                            </xsl:otherwise>
                          </xsl:choose>"
          </xsl:if>        
        },
        "request" : {
          "method" : "POST",
          "url" : "<xsl:value-of select='$baseFhirUrl'/>/Observation/<xsl:value-of select='$observationResourceId'/>"
        }
      }
    </xsl:if>
  </xsl:template>

  <!-- Observation Template -->
  <xsl:template name="Observation" match="ccda:observation[ccda:code/ccda:code != '76690-7']">
    <xsl:if test="string(ccda:code/ccda:code) != '76690-7'">
      <xsl:variable name="observationResourceId" select="translate(concat(generate-id(ccda:code/ccda:code), position(), $patientRoleId, $currentTimestamp), ':-+', '')"/>
      ,{
        "fullUrl" : "<xsl:value-of select='$baseFhirUrl'/>/Observation/<xsl:value-of select='$observationResourceId'/>",
        "resource": {
          "resourceType": "Observation",
          "id": "<xsl:value-of select='$observationResourceId'/>",
          "meta" : {
            "lastUpdated" : "<xsl:value-of select='$currentTimestamp'/>",
            "profile" : ["<xsl:value-of select='$observationMetaProfileUrl'/>"]
          },
          "status": "<xsl:value-of select='ccda:statusCode/ccda:code'/>",
          "category": [
            {
              "coding": [{
                  "system": "http://terminology.hl7.org/CodeSystem/observation-category",
                  "code": "<xsl:value-of select='ccda:code/ccda:code'/>",
                  "display": "<xsl:value-of select='ccda:code/ccda:displayName'/>"
              }
              <xsl:if test="string(ccda:entryRelationship/ccda:observation)">
                  <xsl:for-each select="ccda:entryRelationship/ccda:observation">
                    ,{
                        "system": "http://terminology.hl7.org/CodeSystem/observation-category",
                        "code": "<xsl:value-of select='ccda:code/ccda:code'/>",
                        "display": "<xsl:value-of select='ccda:code/ccda:displayName'/>"
                    }
                  </xsl:for-each>
              </xsl:if>
              ]
            },
            {
              "coding" : [{
                  "system" : "http://terminology.hl7.org/CodeSystem/observation-category",
                  "code" : "social-history"
              }]
            },
            {
              "coding" : [{
                  "system" : "http://terminology.hl7.org/CodeSystem/observation-category",
                  "code" : "survey"
              }]
            }
          ],
          "code": {
            "coding": [
              {
                "system": "http://loinc.org",
                "code": "<xsl:value-of select='ccda:code/ccda:code'/>",
                "display": "<xsl:value-of select='ccda:code/ccda:displayName'/>"
              }
            ],
            "text" : "<xsl:value-of select='ccda:code/ccda:originalText'/>"
          },
          <xsl:choose>
            <xsl:when test="string(ccda:value/ccda:code) = 'UNK' or string(ccda:value/ccda:code) = ''">
              "valueCodeableConcept" : {
                "coding" : [{
                  "system" : "http://terminology.hl7.org/CodeSystem/v3-NullFlavor",
                  "code" : "UNK",
                  "display" : "Unknown"
                }]
              },
            </xsl:when>
            <xsl:otherwise>
              "valueCodeableConcept" : {
                "coding" : [{
                  "system" : "http://loinc.org",
                  "code" : "<xsl:value-of select='ccda:value/ccda:code'/>",
                  "display" : "<xsl:value-of select='ccda:value/ccda:displayName'/>"
                }],
                "text" : "<xsl:value-of select='ccda:value/ccda:displayName'/>"
              },
            </xsl:otherwise>
          </xsl:choose>
          "subject": {
            "reference": "Patient/<xsl:value-of select='$patientResourceId'/>",
            "display" : "<xsl:value-of select="$patientResourceName"/>"
          },
          "encounter" : {
            "reference" : "Encounter/<xsl:value-of select='$encounterResourceId'/>"
          }
          <xsl:if test="string(ccda:effectiveTime/ccda:value)">
          , "effectiveDateTime" : "<xsl:choose>
                            <xsl:when test="string-length(ccda:effectiveTime/ccda:value) >= 14">
                              <xsl:value-of select="
                                concat(
                                  substring(ccda:effectiveTime/ccda:value, 1, 4), '-', 
                                  substring(ccda:effectiveTime/ccda:value, 5, 2), '-', 
                                  substring(ccda:effectiveTime/ccda:value, 7, 2), 'T', 
                                  substring(ccda:effectiveTime/ccda:value, 9, 2), ':', 
                                  substring(ccda:effectiveTime/ccda:value, 11, 2), ':', 
                                  substring(ccda:effectiveTime/ccda:value, 13, 2), 
                                  'Z'
                                )"/>
                            </xsl:when>
                            <xsl:when test="string-length(ccda:effectiveTime/ccda:value) >= 8">
                              <xsl:value-of select="
                                concat(
                                  substring(ccda:effectiveTime/ccda:value, 1, 4), '-', 
                                  substring(ccda:effectiveTime/ccda:value, 5, 2), '-', 
                                  substring(ccda:effectiveTime/ccda:value, 7, 2)
                                )"/>
                            </xsl:when>
                            <xsl:otherwise>
                              <xsl:value-of select="ccda:effectiveTime/ccda:value"/>
                            </xsl:otherwise>
                          </xsl:choose>"
          </xsl:if>        
        },
        "request" : {
          "method" : "POST",
          "url" : "<xsl:value-of select='$baseFhirUrl'/>/Observation/<xsl:value-of select='$observationResourceId'/>"
        }
      }
      <!-- Apply the same observation data in the QuestionnaireResponse mode -->
        <xsl:apply-templates select="." mode="questionnaireresponse"/>
    </xsl:if>
  </xsl:template>

  <!-- Template to generate Questionnaire resource -->
  <xsl:template name="QuestionnaireResource" match="ccda:Questionnaire"> 
      ,{
          "fullUrl" : "<xsl:value-of select='$baseFhirUrl'/>/Questionnaire/<xsl:value-of select='$questionnaireResourceId'/>",
          "resource" : {
            "resourceType": "Questionnaire",
            "id": "<xsl:value-of select='$questionnaireResourceId'/>",
            "meta" : {
              "lastUpdated" : "<xsl:value-of select='$currentTimestamp'/>",
              "profile" : ["<xsl:value-of select='$questionnaireMetaProfileUrl'/>"]
            },
            <xsl:if test="string(ccda:code/ccda:codeSystem) or string(ccda:code/ccda:code)">
            "identifier" : [{
              <xsl:if test="string(ccda:code/ccda:codeSystem)">
              "system" : "urn:<xsl:value-of select='ccda:code/ccda:codeSystem'/>",
              </xsl:if>
              "value" : "<xsl:value-of select='ccda:code/ccda:code'/>"
            }],
            </xsl:if>
            "status": "active",
            "title": "<xsl:value-of select='../ccda:title'/>"
            <xsl:if test="string(../ccda:entry/ccda:observation/ccda:entryRelationship/ccda:observation)">
              ,"item": [
                  <xsl:for-each select="../ccda:entry/ccda:observation/ccda:entryRelationship/ccda:observation">
                      {
                          "linkId": "<xsl:value-of select='ccda:code/ccda:code'/>",
                          "code" : [{
                            "system" : "<xsl:value-of select='ccda:code/ccda:codeSystem'/>",
                            "code" : "<xsl:value-of select='ccda:code/ccda:code'/>",
                            "display" : "<xsl:value-of select='ccda:code/ccda:displayName'/>"
                          }],
                          "text": "<xsl:value-of select='ccda:value/ccda:displayName'/>",
                          "type": "string"
                      }
                      <xsl:if test="position() != last()">,</xsl:if>
                  </xsl:for-each>
              ]
            </xsl:if>
          },
          "request" : {
            "method" : "POST",
            "url" : "<xsl:value-of select='$baseFhirUrl'/>/Questionnaire/<xsl:value-of select='$questionnaireResourceId'/>"
          }
      }
  </xsl:template>

  <!-- Template to generate QuestionnaireResponse resource -->
  <xsl:template name="QuestionnaireResponseResource" match="ccda:observation[ccda:code/ccda:code != '76690-7']" mode="questionnaireresponse">
    <xsl:variable name="QuestionnaireResponseResourceId" select="translate(concat(generate-id(ccda:code/ccda:code), position(), $patientRoleId, $currentTimestamp), ':-+', '')"/>
      ,{
          "fullUrl" : "<xsl:value-of select='$baseFhirUrl'/>/QuestionnaireResponse/<xsl:value-of select='$QuestionnaireResponseResourceId'/>",
          "resource" : {
            "resourceType": "QuestionnaireResponse",
            "id": "<xsl:value-of select='$QuestionnaireResponseResourceId'/>",
            "meta" : {
              "lastUpdated" : "<xsl:value-of select='$currentTimestamp'/>",
              "profile" : ["<xsl:value-of select='$questionnaireResponseMetaProfileUrl'/>"]
            },
            "status": "completed",
            "questionnaire": "Questionnaire/<xsl:value-of select='$questionnaireResourceId'/>",
            "subject": {
                "reference": "Patient/<xsl:value-of select='$patientResourceId'/>"
            },
            "encounter" : {
              "reference" : "Encounter/<xsl:value-of select='$encounterResourceId'/>"
            }
            <xsl:if test="string(ccda:code/ccda:code)">
              ,"item": [
                  {
                      "linkId": "<xsl:value-of select='ccda:code/ccda:code'/>",
                      "text": "<xsl:value-of select='ccda:code/ccda:displayName'/>"
                      <xsl:if test="string(ccda:value/ccda:code) or string(ccda:value/ccda:displayName)">
                        ,"answer" : [{
                          "valueCoding" : {
                            "system" : "<xsl:value-of select='ccda:value/ccda:codeSystem'/>",
                            "code" : "<xsl:value-of select='ccda:value/ccda:code'/>",
                            "display" : "<xsl:value-of select='ccda:value/ccda:displayName'/>"
                          }
                        }]                          
                      </xsl:if>
                      <xsl:if test="string(ccda:entryRelationship/ccda:observation/ccda:code/ccda:code)">
                      , "item": [
                      <xsl:for-each select="ccda:entryRelationship/ccda:observation">
                        {
                              "linkId": "<xsl:value-of select='ccda:code/ccda:code'/>",
                              "text": "<xsl:value-of select='ccda:code/ccda:displayName'/>"
                              <xsl:if test="string(ccda:value/ccda:code) or string(ccda:value/ccda:displayName)">
                                ,"answer" : [{
                                  "valueCoding" : {
                                    "system" : "<xsl:value-of select='ccda:value/ccda:codeSystem'/>",
                                    "code" : "<xsl:value-of select='ccda:value/ccda:code'/>",
                                    "display" : "<xsl:value-of select='ccda:value/ccda:displayName'/>"
                                  }
                                }]
                              </xsl:if>                          
                          }
                          <xsl:if test="position() != last()">,</xsl:if>
                      </xsl:for-each>
                      ]
                      </xsl:if>
                  }
              ]
            </xsl:if>
          },
          "request" : {
            "method" : "POST",
            "url" : "<xsl:value-of select='$baseFhirUrl'/>/QuestionnaireResponse/<xsl:value-of select='$QuestionnaireResponseResourceId'/>"
          }
      }
  </xsl:template>
</xsl:stylesheet>