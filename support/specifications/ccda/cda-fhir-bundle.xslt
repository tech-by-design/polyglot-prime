<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0"
                xmlns:ccda="urn:hl7-org:v3"
                xmlns:fhir="http://hl7.org/fhir">

  <xsl:output method="text"/>
  
  <!-- Root template -->
  <xsl:param name="currentTimestamp"/>
  <xsl:variable name="concentResourceId" select="generate-id(/)"/>
  <xsl:variable name="patientResourceId" select="//ccda:patientRole/ccda:id/ccda:extension"/>
  <xsl:variable name="patientResourceName" select="//ccda:patientRole/ccda:patient/ccda:name/ccda:family | //ccda:patientRole/ccda:patient/ccda:name/ccda:given"/>

  <xsl:template match="/">
    {
      "resourceType": "Bundle",
      "id": "<xsl:value-of select='generate-id()'/>",
      "meta" : {
        "lastUpdated" : "<xsl:value-of select='$currentTimestamp'/>",
        "profile" : ["http://shinny.org/us/ny/hrsn/StructureDefinition/SHINNYBundleProfile"]
      },
      "type" : "transaction", <!--"type": "collection", -->
      "timestamp" : "<xsl:value-of select='$currentTimestamp'/>",
      "entry": [
        <xsl:apply-templates select="//ccda:patientRole | //ccda:encompassingEncounter | //ccda:observation | //ccda:location  | //ccda:consent | //ccda:author"/>
      ]
    }
  </xsl:template>

  <!-- Patient Template -->
  <xsl:template match="ccda:patientRole">
    <xsl:if test="position() > 1">,</xsl:if>
    {
      "fullUrl" : "http://shinny.org/us/ny/hrsn/Patient/<xsl:value-of select="ccda:id/ccda:extension"/>",
      "resource": {
        "resourceType": "Patient",
        "id": "<xsl:value-of select="ccda:id/ccda:extension"/>",
        "meta" : {
          "lastUpdated" : "<xsl:value-of select='$currentTimestamp'/>",
          "profile" : ["http://shinny.org/us/ny/hrsn/StructureDefinition/shinny-patient"]
        },
        "language" : "<xsl:value-of select="ccda:patient/ccda:languageCommunication/ccda:languageCode/ccda:code"/>",
        "name": [
          {
            "use": "official",
            "family": "<xsl:value-of select="ccda:patient/ccda:name/ccda:family"/>",
            "given": [
              "<xsl:value-of select="ccda:patient/ccda:name/ccda:given"/>"
            ],
            "prefix": [
              "<xsl:value-of select="ccda:patient/ccda:name/ccda:prefix"/>"
            ],
            "suffix": [
              "<xsl:value-of select="ccda:patient/ccda:name/ccda:suffix"/>"
            ]
          }
        ],
        "gender": "<xsl:choose>
                      <xsl:when test="ccda:patient/ccda:administrativeGenderCode/ccda:code = 'M'">male</xsl:when>
                      <xsl:when test="ccda:patient/ccda:administrativeGenderCode/ccda:code = 'F'">female</xsl:when>
                      <!-- <xsl:otherwise>unknown</xsl:otherwise> -->
                      <xsl:otherwise><xsl:value-of select="ccda:patient/ccda:administrativeGenderCode/ccda:code"/></xsl:otherwise>
                    </xsl:choose>",
        <!-- "birthDate": "<xsl:value-of select="ccda:patient/ccda:birthTime/ccda:value"/>", -->
        "birthDate": "<xsl:choose>
                        <xsl:when test='string-length(ccda:patient/ccda:birthTime/ccda:value) = 14'>
                          <xsl:value-of select='concat(substring(ccda:patient/ccda:birthTime/ccda:value, 1, 4), "-", substring(ccda:patient/ccda:birthTime/ccda:value, 5, 2), "-", substring(ccda:patient/ccda:birthTime/ccda:value, 7, 2), "T", substring(ccda:patient/ccda:birthTime/ccda:value, 9, 2), ":", substring(ccda:patient/ccda:birthTime/ccda:value, 11, 2), ":", substring(ccda:patient/ccda:birthTime/ccda:value, 13, 2))'/>
                        </xsl:when>
                        <xsl:when test='string-length(ccda:patient/ccda:birthTime/ccda:value) = 8'>
                          <xsl:value-of select='concat(substring(ccda:patient/ccda:birthTime/ccda:value, 1, 4), "-", substring(ccda:patient/ccda:birthTime/ccda:value, 5, 2), "-", substring(ccda:patient/ccda:birthTime/ccda:value, 7, 2))'/>
                        </xsl:when>
                        <xsl:otherwise>
                          <xsl:value-of select='ccda:patient/ccda:birthTime/ccda:value'/>
                        </xsl:otherwise>
                      </xsl:choose>",
        "address": [
          {
            "line": [
              "<xsl:value-of select="ccda:addr/ccda:streetAddressLine"/>"
            ],
            "city": "<xsl:value-of select="ccda:addr/ccda:city"/>",
            "state": "<xsl:value-of select="ccda:addr/ccda:state"/>",
            "postalCode": "<xsl:value-of select="ccda:addr/ccda:postalCode"/>"
          }
        ],
        "telecom": [
          {
            "system": "phone",
            "value": "<xsl:value-of select="ccda:telecom/ccda:value"/>",
            "use": "<xsl:value-of select="ccda:telecom/ccda:use"/>"
          }
        ],
        "extension": [
          {
            "url": "http://hl7.org/fhir/us/core/StructureDefinition/us-core-race",
            "valueCodeableConcept": {
              "coding": [
                {
                  "system": "urn:oid:<xsl:value-of select="ccda:patient/ccda:raceCode/ccda:codeSystem"/>",
                  "code": "<xsl:value-of select="ccda:patient/ccda:raceCode/ccda:code"/>",
                  "display": "<xsl:value-of select="ccda:patient/ccda:raceCode/ccda:displayName"/>"
                }
              ]
            }
          },
          {
            "url": "http://hl7.org/fhir/us/core/StructureDefinition/us-core-ethnicity",
            "valueCodeableConcept": {
              "coding": [
                {
                  "system": "urn:oid:<xsl:value-of select="ccda:patient/ccda:ethnicGroupCode/ccda:codeSystem"/>",
                  "code": "<xsl:value-of select="ccda:patient/ccda:ethnicGroupCode/ccda:code"/>",
                  "display": "<xsl:value-of select="ccda:patient/ccda:ethnicGroupCode/ccda:displayName"/>"
                }
              ]
            }
          },
          {
            "url": "http://hl7.org/fhir/StructureDefinition/patient-language",
            "valueCodeableConcept": {
              "coding": [
                {
                  "system": "urn:ietf:bcp:47",
                  "code": "<xsl:value-of select="ccda:patient/ccda:languageCommunication/ccda:languageCode/ccda:code"/>"
                }
              ],
              "text": "<xsl:value-of select="ccda:patient/ccda:languageCommunication/ccda:preferenceInd/ccda:value"/>"
            }
          }
        ]
      },
      "request" : {
        "method" : "POST",
        "url" : "http://shinny.org/us/ny/hrsn/Patient/<xsl:value-of select="ccda:id/ccda:extension"/>"
      }
    }
  </xsl:template>

  <!-- Encounter Template -->
  <xsl:template match="ccda:encompassingEncounter">
    <xsl:if test="position() > 1">,</xsl:if>
    {
      "fullUrl" : "http://shinny.org/us/ny/hrsn/Encounter/<xsl:value-of select="ccda:id/ccda:extension"/>",
      "resource": {
        "resourceType": "Encounter",
        "id": "<xsl:value-of select="ccda:id/ccda:extension"/>",
        "meta" : {
          "lastUpdated" : "<xsl:value-of select='$currentTimestamp'/>",
          "profile" : ["http://shinny.org/us/ny/hrsn/StructureDefinition/shinny-encounter"]
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
            ]
          }
        ],
        "period": {
          "start": "<xsl:value-of select="ccda:effectiveTime/ccda:low/ccda:value"/>",
          "end": "<xsl:value-of select="ccda:effectiveTime/ccda:high/ccda:value"/>"
        }
      },
      "request" : {
        "method" : "POST",
        "url" : "http://shinny.org/us/ny/hrsn/Encounter/<xsl:value-of select="ccda:id/ccda:extension"/>"
      }
    }
  </xsl:template>

  <!-- Consent Template -->
  <xsl:template match="ccda:consent">
    <xsl:if test="position() > 1">,</xsl:if>
    {
      "fullUrl" : "http://shinny.org/us/ny/hrsn/Consent/<xsl:value-of select='$concentResourceId'/>",
      "resource": {
        "resourceType": "Consent",
        "id": "<xsl:value-of select='$concentResourceId'/>",
        "meta" : {
          "lastUpdated" : "<xsl:value-of select='$currentTimestamp'/>",
          "profile" : ["http://shinny.org/us/ny/hrsn/StructureDefinition/shinny-Consent"]
        },
        "status": "<xsl:value-of select="ccda:statusCode/ccda:code"/>",
        "category": [
          {
            "coding": [
              {
                "system": "<xsl:value-of select="ccda:code/ccda:codeSystem"/>", <!--"http://loinc.org",-->
                "code": "<xsl:value-of select="ccda:code/ccda:code"/>",
                "display": "<xsl:value-of select="ccda:code/ccda:displayName"/>"
              }
            ]
          }
        ],
        "subject" : {
          "reference" : "Patient/<xsl:value-of select='$patientResourceId'/>",
          "display" : "<xsl:value-of select='$patientResourceName'/>"
        },
        "organization" : [{
          "reference" : "Organization/<xsl:value-of select='//ccda:assignedAuthor/ccda:representedOrganization/ccda:id/ccda:extension'/>"
        }]
      },
      "request" : {
        "method" : "POST",
        "url" : "http://shinny.org/us/ny/hrsn/Consent/<xsl:value-of select='$concentResourceId'/>"
      }
    }
  </xsl:template>

  <!-- Organization Template -->
  <xsl:template match="ccda:author">
    <xsl:if test="position() > 1">,</xsl:if>
    {
      "fullUrl" : "http://shinny.org/us/ny/hrsn/Organization/<xsl:value-of select="ccda:assignedAuthor/ccda:representedOrganization/ccda:id/ccda:extension"/>",
      "resource": {
        "resourceType": "Organization",
        "id": "<xsl:value-of select="ccda:assignedAuthor/ccda:representedOrganization/ccda:id/ccda:extension"/>",
        "meta" : {
          "lastUpdated" : "<xsl:value-of select='$currentTimestamp'/>",
          "profile" : ["http://shinny.org/us/ny/hrsn/StructureDefinition/shin-ny-organization"]
        },
        "active": true,
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
        "name" : "<xsl:value-of select="ccda:assignedAuthor/ccda:representedOrganization/ccda:name"/>",
        "address" : [{
          "text" : "<xsl:value-of select="ccda:assignedAuthor/ccda:representedOrganization/ccda:addr/ccda:streetAddressLine"/>, <xsl:value-of select="ccda:assignedAuthor/ccda:representedOrganization/ccda:addr/ccda:city"/>, <xsl:value-of select="ccda:assignedAuthor/ccda:representedOrganization/ccda:addr/ccda:state"/> <xsl:value-of select="ccda:assignedAuthor/ccda:representedOrganization/ccda:addr/ccda:postalCode"/>",
          "line" : ["<xsl:value-of select="ccda:assignedAuthor/ccda:representedOrganization/ccda:addr/ccda:streetAddressLine"/>"],
          "city" : "<xsl:value-of select="ccda:assignedAuthor/ccda:representedOrganization/ccda:addr/ccda:city"/>",
          "district" : "<xsl:value-of select="ccda:assignedAuthor/ccda:representedOrganization/ccda:addr/ccda:county"/>",
          "state" : "<xsl:value-of select="ccda:assignedAuthor/ccda:representedOrganization/ccda:addr/ccda:state"/>",
          "postalCode" : "<xsl:value-of select="ccda:assignedAuthor/ccda:representedOrganization/ccda:addr/ccda:postalCode"/>"
        }]
      },

      "request" : {
        "method" : "POST",
        "url" : "http://shinny.org/us/ny/hrsn/Organization/<xsl:value-of select="ccda:assignedAuthor/ccda:representedOrganization/ccda:id/ccda:extension"/>"
      }
    }
  </xsl:template>

  <!-- Observation Template -->
  <xsl:template match="ccda:observation">
    <xsl:if test="position() > 1">,</xsl:if>
    {
      "fullUrl" : "http://shinny.org/us/ny/hrsn/Observation/<xsl:value-of select='normalize-space(ccda:id)'/>",
      "resource": {
        "resourceType": "Observation",
        "id": "<xsl:value-of select='normalize-space(ccda:id)'/>",
        "meta" : {
          "lastUpdated" : "<xsl:value-of select='$currentTimestamp'/>",
          "profile" : ["http://shinny.org/us/ny/hrsn/StructureDefinition/shinny-observation-screening-response"]
        },
        "status": "<xsl:value-of select='ccda:statusCode/ccda:code'/>",
        "code": {
          "coding": [
            {
              "system": "http://loinc.org",
              "code": "<xsl:value-of select='ccda:code/ccda:code'/>",
              "display": "<xsl:value-of select='ccda:code/ccda:displayName'/>"
            }
          ]
        },
        "valueString": "<xsl:value-of select='ccda:value/ccda:displayName'/>",
        "subject": {
          "reference": "Patient/<xsl:value-of select='ancestor::ccda:ClinicalDocument/ccda:recordTarget/ccda:patientRole/ccda:id/ccda:root'/>"
        },
        "category": [{
          "coding": [
            {
              "system": "http://terminology.hl7.org/CodeSystem/observation-category",
              "code": "<xsl:value-of select='ccda:code/ccda:code'/>",
              "display": "<xsl:value-of select='ccda:code/ccda:displayName'/>"
            }
          ]
        }],
        <!-- "effectiveDateTime": "<xsl:value-of select='ccda:effectiveTime/ccda:value'/>" -->
        "effectiveDateTime": "<xsl:choose>
                                <xsl:when test='string-length(ccda:effectiveTime/ccda:value) > 14'>
                                  <xsl:value-of select="
                                    concat(
                                      substring(ccda:effectiveTime/ccda:value, 1, 4), '-',
                                      substring(ccda:effectiveTime/ccda:value, 5, 2), '-',
                                      substring(ccda:effectiveTime/ccda:value, 7, 2), 'T',
                                      substring(ccda:effectiveTime/ccda:value, 9, 2), ':',
                                      substring(ccda:effectiveTime/ccda:value, 11, 2), ':',
                                      substring(ccda:effectiveTime/ccda:value, 13, 2),
                                      substring(ccda:effectiveTime/ccda:value, 15, 6)
                                    )"/>
                                </xsl:when>
                                <xsl:when test='string-length(ccda:effectiveTime/ccda:value) = 14'>
                                  <xsl:value-of select='concat(substring(ccda:effectiveTime/ccda:value, 1, 4), "-", substring(ccda:effectiveTime/ccda:value, 5, 2), "-", substring(ccda:effectiveTime/ccda:value, 7, 2), "T", substring(ccda:effectiveTime/ccda:value, 9, 2), ":", substring(ccda:effectiveTime/ccda:value, 11, 2), ":", substring(ccda:effectiveTime/ccda:value, 13, 2))'/>
                                </xsl:when>
                                <xsl:when test='string-length(ccda:effectiveTime/ccda:value) = 8'>
                                  <xsl:value-of select='concat(substring(ccda:effectiveTime/ccda:value, 1, 4), "-", substring(ccda:effectiveTime/ccda:value, 5, 2), "-", substring(ccda:effectiveTime/ccda:value, 7, 2))'/>
                                </xsl:when>
                                <xsl:otherwise>
                                  <xsl:value-of select='ccda:effectiveTime/ccda:value'/>
                                </xsl:otherwise>
                              </xsl:choose>"
      },
      "request" : {
        "method" : "POST",
        "url" : "http://shinny.org/us/ny/hrsn/Observation/<xsl:value-of select='normalize-space(ccda:id)'/>"
      }
    }
  </xsl:template>

</xsl:stylesheet>
