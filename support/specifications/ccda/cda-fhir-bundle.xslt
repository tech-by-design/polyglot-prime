<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0"
                xmlns:ccda="urn:hl7-org:v3"
                xmlns:fhir="http://hl7.org/fhir">

  <xsl:output method="text"/>
  
  <!-- Root template -->
  <xsl:template match="/">
    {
      "resourceType": "Bundle",
      "type": "collection",
      <!-- "entry": [
        <xsl:apply-templates select="//ccda:patientRole"/>
        <xsl:apply-templates select="//ccda:encompassingEncounter"/>
        <xsl:for-each select="//ccda:observation">
          <xsl:apply-templates select="."/>
          <xsl:if test="position() != last()">,</xsl:if>
        </xsl:for-each>
      ] -->
      "entry": [
        <xsl:apply-templates select="//ccda:patientRole | //ccda:encompassingEncounter | //ccda:observation"/>
      ]
    }
  </xsl:template>

  <!-- Patient Template -->
  <xsl:template match="ccda:patientRole">
    <xsl:if test="position() > 1">,</xsl:if>
    {
      "resource": {
        "resourceType": "Patient",
        "id": "<xsl:value-of select="ccda:id/ccda:root"/>",
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
            "url": "http://hl7.org/fhir/StructureDefinition/patient-race",
            "valueCodeableConcept": {
              "coding": [
                {
                  "system": "<xsl:value-of select="ccda:patient/ccda:raceCode/ccda:codeSystem"/>",
                  "code": "<xsl:value-of select="ccda:patient/ccda:raceCode/ccda:code"/>",
                  "display": "<xsl:value-of select="ccda:patient/ccda:raceCode/ccda:displayName"/>"
                }
              ]
            }
          },
          {
            "url": "http://hl7.org/fhir/StructureDefinition/patient-ethnicity",
            "valueCodeableConcept": {
              "coding": [
                {
                  "system": "<xsl:value-of select="ccda:patient/ccda:ethnicGroupCode/ccda:codeSystem"/>",
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
      }
    }
  </xsl:template>

  <!-- Encounter Template -->
  <xsl:template match="ccda:encompassingEncounter">
    <xsl:if test="position() > 1">,</xsl:if>
    {
      "resource": {
        "resourceType": "Encounter",
        "id": "<xsl:value-of select="ccda:id/ccda:extension"/>",
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
      }
    }
  </xsl:template>

  <!-- Observation Template -->
  <xsl:template match="ccda:observation">
    <xsl:if test="position() > 1">,</xsl:if>
    {
      "resource": {
        "resourceType": "Observation",
        "id": "<xsl:value-of select='normalize-space(ccda:id)'/>",
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
        "category": {
          "coding": [
            {
              "system": "http://terminology.hl7.org/CodeSystem/observation-category",
              "code": "<xsl:value-of select='ccda:code/ccda:code'/>",
              "display": "<xsl:value-of select='ccda:code/ccda:displayName'/>"
            }
          ]
        },
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
      }
    }
    <!-- <xsl:if test="position() != last()">,</xsl:if> -->
  </xsl:template>

</xsl:stylesheet>