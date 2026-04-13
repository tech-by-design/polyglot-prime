<?xml version="1.0" encoding="UTF-8"?>
<!-- Version : 0.1.12 -->
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0"
                xmlns:ccda="urn:hl7-org:v3"
                xmlns:fhir="http://hl7.org/fhir"
                xmlns:sdtc="urn:hl7-org:sdtc"
                xmlns:exsl="http://exslt.org/common"
                extension-element-prefixes="exsl"
                exclude-result-prefixes="sdtc ccda exsl">

  <xsl:include href="cda-fhir-bundle-common-utils.xslt"/>
  <xsl:output method="text"/>
  
  <xsl:param name="currentTimestamp"/>
  <xsl:param name="patientCIN"/>
  <xsl:param name="patient-MRN"/>
  <xsl:param name="organizationNPI"/>
  <xsl:param name="organizationTIN"/>
  <xsl:param name="encounterType"/>
  <xsl:param name="facilityID"/>
  <xsl:variable name="patientRoleId" select="//ccda:patientRole/ccda:id[not(@assigningAuthorityName)]/@extension"/>
  <xsl:variable name="given_trimmed">
      <xsl:call-template name="string-trim">
        <xsl:with-param name="text" select="//ccda:patientRole/ccda:patient[1]/ccda:name[1]/ccda:given[1]"/>
      </xsl:call-template>
  </xsl:variable>
  <xsl:variable name="family_trimmed">
    <xsl:call-template name="string-trim">
      <xsl:with-param name="text" select="//ccda:patientRole/ccda:patient[1]/ccda:name[1]/ccda:family[1]"/>
    </xsl:call-template>
  </xsl:variable>
  <xsl:variable name="patientResourceName" select="concat($family_trimmed, ' ', $given_trimmed)"/>

  <xsl:variable name="bundleTimestamp" select="/ccda:ClinicalDocument/ccda:effectiveTime/@value"/>

  <xsl:param name="bundleId"/>
  <xsl:param name="patientResourceId"/>
  <xsl:param name="encounterResourceId"/>
  <xsl:param name="consentResourceId"/>
  <xsl:param name="organizationResourceId"/>
  <xsl:param name="questionnaireResourceId"/>
  <xsl:param name="observationResourceSha256Id"/>
  <xsl:param name="sexualOrientationResourceId"/>
  <xsl:param name="questionnaireResponseResourceSha256Id"/>
  <xsl:param name="procedureResourceSha256Id"/>
  <xsl:param name="grouperObservationResourceSha256Id"/> 
  <xsl:param name="categoryXml"/>
  <xsl:param name="grouperScreeningCode"/>
  <xsl:param name="locationResourceId"/>
  <xsl:param name="componentAnswersXml"/>
  <xsl:param name="X-TechBD-Part2"/>
  <xsl:param name="X-TechBD-OMH"/>
  <xsl:param name="X-TechBD-OPWDD"/>

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
  <xsl:param name="procedureMetaProfileUrl"/>
  <xsl:param name="locationMetaProfileUrl"/>

  <xsl:variable name="bundleMetaProfileUrlFull" select="concat($baseFhirUrl, $bundleMetaProfileUrl)"/>
  <xsl:variable name="patientMetaProfileUrlFull" select="concat($baseFhirUrl, $patientMetaProfileUrl)"/>
  <xsl:variable name="consentMetaProfileUrlFull" select="concat($baseFhirUrl, $consentMetaProfileUrl)"/>
  <xsl:variable name="encounterMetaProfileUrlFull" select="concat($baseFhirUrl, $encounterMetaProfileUrl)"/>
  <xsl:variable name="organizationMetaProfileUrlFull" select="concat($baseFhirUrl, $organizationMetaProfileUrl)"/>
  <xsl:variable name="observationMetaProfileUrlFull" select="concat($baseFhirUrl, $observationMetaProfileUrl)"/>
  <xsl:variable name="observationSexualOrientationMetaProfileUrlFull" select="concat($baseFhirUrl, $observationSexualOrientationMetaProfileUrl)"/>
  <xsl:variable name="questionnaireMetaProfileUrlFull" select="concat($baseFhirUrl, $questionnaireMetaProfileUrl)"/>
  <xsl:variable name="questionnaireResponseMetaProfileUrlFull" select="concat($baseFhirUrl, $questionnaireResponseMetaProfileUrl)"/>
  <xsl:variable name="practitionerMetaProfileUrlFull" select="concat($baseFhirUrl, $practitionerMetaProfileUrl)"/>
  <xsl:variable name="procedureMetaProfileUrlFull" select="concat($baseFhirUrl, $procedureMetaProfileUrl)"/>
  <!-- <xsl:variable name="locationMetaProfileUrlFull" select="concat($baseFhirUrl, $locationMetaProfileUrl)"/> -->

  <xsl:variable name="encounterEffectiveTime">
    <xsl:choose>
      <xsl:when test="/ccda:ClinicalDocument/ccda:component/ccda:structuredBody/ccda:component/ccda:section[@ID='encounters']/ccda:entry[position()=1]/ccda:encounter/ccda:effectiveTime">
        <xsl:choose>
          <xsl:when test="/ccda:ClinicalDocument/ccda:component/ccda:structuredBody/ccda:component/ccda:section[@ID='encounters']/ccda:entry[position()=1]/ccda:encounter/ccda:effectiveTime/@value">
            <xsl:value-of select="/ccda:ClinicalDocument/ccda:component/ccda:structuredBody/ccda:component/ccda:section[@ID='encounters']/ccda:entry[position()=1]/ccda:encounter/ccda:effectiveTime/@value"/>
          </xsl:when>
          <xsl:when test="/ccda:ClinicalDocument/ccda:component/ccda:structuredBody/ccda:component/ccda:section[@ID='encounters']/ccda:entry[position()=1]/ccda:encounter/ccda:effectiveTime/ccda:low/@value">
            <xsl:value-of select="/ccda:ClinicalDocument/ccda:component/ccda:structuredBody/ccda:component/ccda:section[@ID='encounters']/ccda:entry[position()=1]/ccda:encounter/ccda:effectiveTime/ccda:low/@value"/>
          </xsl:when>
        </xsl:choose>
      </xsl:when>
    </xsl:choose>
  </xsl:variable>

  <!-- Format the encounterEffectiveTime -->
  <xsl:variable name="encounterEffTimeValue">
    <xsl:call-template name="formatDateTime">
      <xsl:with-param name="dateTime" select="$encounterEffectiveTime"/>
    </xsl:call-template>
  </xsl:variable>
  <!-- Remove unwanted space,if any -->
  <xsl:variable name="encounterEffectiveTimeValue" select="normalize-space($encounterEffTimeValue)"/>
  
  <!-- Check whether the CCDA from Guthrie or Mohawk Valley Health System and get Encounter Status in a separate logic -->
  <!-- Determine if the CCDA is from Guthrie or Mohawk Valley Health System -->
  <xsl:variable name="orgName"
    select="translate(
                string(/ccda:ClinicalDocument/ccda:author[1]/ccda:assignedAuthor/ccda:representedOrganization/ccda:name), 
                'ABCDEFGHIJKLMNOPQRSTUVWXYZ',
                'abcdefghijklmnopqrstuvwxyz'
           )"/>
  <xsl:variable name="IsGuthrieCCDA"
      select="contains($orgName, 'guthrie') or contains($orgName, 'mohawk')"/>

  <!-- Encounter status from the encounters section only for Guthrie-->
  <xsl:variable name="guthrieEncounterStatusFromAct"
      select="/ccda:ClinicalDocument/ccda:component/ccda:structuredBody/ccda:component/ccda:section[@ID='encounters']/ccda:entry[1]/ccda:encounter[1]/ccda:entryRelationship[1]/ccda:act/ccda:statusCode/@code"/>

  <!-- Encounter status from the default path for Guthrie-->
  <xsl:variable name="guthrieEncounterStatusDefault"
      select="/ccda:ClinicalDocument/ccda:componentOf/ccda:encompassingEncounter/ccda:statusCode/@code" />

  <!-- Encounter status from the normal encounters section -->
  <xsl:variable name="normalEncounterStatus"
      select="/ccda:ClinicalDocument/ccda:component/ccda:structuredBody/ccda:component/ccda:section[@ID='encounters']/ccda:entry[position()=1]/ccda:encounter[1]/ccda:entryRelationship/ccda:act/ccda:entryRelationship/ccda:observation/ccda:statusCode/@code"/>

  <!-- Final encounterStatus -->
  <xsl:variable name="encounterStatus">
      <xsl:choose>
          <!-- Case 1: Guthrie AND Guthrie encounter status exists from entryRelationship.act-->
          <xsl:when test="$IsGuthrieCCDA and string-length($guthrieEncounterStatusFromAct) &gt; 0">
              <xsl:value-of select="$guthrieEncounterStatusFromAct"/>
          </xsl:when>

          <!-- Case 2: Guthrie AND Guthrie encounter status exists from encompassingEncounter-->
          <xsl:when test="$IsGuthrieCCDA and string-length($guthrieEncounterStatusDefault) &gt; 0">
              <xsl:value-of select="$guthrieEncounterStatusDefault"/>
          </xsl:when>

          <!-- Case 3: Otherwise use normal encounter status -->
          <xsl:otherwise>
              <xsl:value-of select="$normalEncounterStatus"/>
          </xsl:otherwise>
      </xsl:choose>
  </xsl:variable>
  <!-- End of Guthrie logic -->

  <!-- Get Organization name from the first encounter entry -->
  <xsl:variable name="organizationName" select="/ccda:ClinicalDocument/ccda:component/ccda:structuredBody/ccda:component/ccda:section[@ID='encounters']/ccda:entry[1]/ccda:encounter/ccda:participant[@typeCode='LOC']/ccda:participantRole[@classCode='SDLOC']/ccda:playingEntity/ccda:name"/>

  <xsl:template match="/">
  {
    "resourceType": "Bundle",
    "id": "<xsl:value-of select='$bundleId'/>",
    "meta": {
      "lastUpdated": "<xsl:value-of select='$currentTimestamp'/>",
      "profile": [
        "<xsl:value-of select='$bundleMetaProfileUrlFull'/>"
      ]
      <xsl:if test="$X-TechBD-Part2 = 'true' or $X-TechBD-OMH = 'true' or $X-TechBD-OPWDD = 'true'">
          ,"security": [
            <xsl:if test="$X-TechBD-Part2 = 'true'">
              {
                  "code": "ETH",
                  "system": "http://terminology.hl7.org/CodeSystem/v3-ActCode",
                  "display": "Substance abuse information sensitivity"
              }
            </xsl:if>
            <xsl:if test="$X-TechBD-OMH = 'true'">
              <xsl:if test="$X-TechBD-Part2 = 'true'">,</xsl:if>
              {
                  "code": "MH",
                  "system": "http://terminology.hl7.org/CodeSystem/v3-ActCode",
                  "display": "Mental health information sensitivity"
              }
            </xsl:if>
            <xsl:if test="$X-TechBD-OPWDD = 'true'">
              <xsl:if test="$X-TechBD-Part2 = 'true' or $X-TechBD-OMH = 'true'">,</xsl:if>
              {
                  "code": "DVD",
                  "system": "http://terminology.hl7.org/CodeSystem/v3-ActCode",
                  "display": "Developmental disability information sensitivity"
              }
            </xsl:if>
        ]
      </xsl:if>
    },
    "type": "transaction"
    <xsl:if test="$bundleTimestamp"> 
      , "timestamp": "<xsl:call-template name="formatDateTime">
                          <xsl:with-param name="dateTime" select="$bundleTimestamp"/>
                      </xsl:call-template>"
    </xsl:if>

    , "entry": [
      <xsl:apply-templates select="/ccda:ClinicalDocument/ccda:recordTarget/ccda:patientRole
                                | /ccda:ClinicalDocument/ccda:component/ccda:structuredBody/ccda:component/ccda:section[@ID='sexualOrientation']/ccda:entry/ccda:observation
                                | /ccda:ClinicalDocument/ccda:component/ccda:structuredBody/ccda:component/ccda:section[@ID='observations']/ccda:entry/ccda:observation/ccda:entryRelationship
                                | /ccda:ClinicalDocument/ccda:authorization/ccda:consent 
                                | /ccda:ClinicalDocument/ccda:author
                                | /ccda:ClinicalDocument/ccda:component/ccda:structuredBody/ccda:component/ccda:section[@ID='encounters']/ccda:entry[position()=1]/ccda:encounter
                                | /ccda:ClinicalDocument/ccda:component/ccda:structuredBody/ccda:component/ccda:section[@ID='encounters']/ccda:entry[position()=1]/ccda:encounter/ccda:participant/ccda:participantRole
                                "/>
            <!-- Call Grouper Observation template -->
            <xsl:call-template name="GrouperObservation"/>
    ]
  }
  </xsl:template>

  <!-- Patient Template -->
  <xsl:template name="Patient" match="/ccda:ClinicalDocument/ccda:recordTarget/ccda:patientRole">
    {
      "fullUrl": "<xsl:value-of select='$baseFhirUrl'/>/Patient/<xsl:value-of select='$patientResourceId'/>",
      "resource": {
        "resourceType": "Patient",
        "id": "<xsl:value-of select='$patientResourceId'/>",
        "meta": {
          "lastUpdated": "<xsl:value-of select='$currentTimestamp'/>",
          "profile": ["<xsl:value-of select='$patientMetaProfileUrlFull'/>"]
        }
        <xsl:if test="string(ccda:patient/ccda:languageCommunication/ccda:languageCode/@code)">
        , "language": "<xsl:value-of select="ccda:patient/ccda:languageCommunication/ccda:languageCode/@code"/>"
        </xsl:if>

        <!--If there is Official Name, print it, otherwise print first occuring name-->
        <xsl:if test="ccda:patient/ccda:name[@use='L']">
            , "name": [
                <xsl:call-template name="generateNameJson">
                    <xsl:with-param name="selectedName" select="ccda:patient/ccda:name[@use='L']"/>
                </xsl:call-template>
            ]
        </xsl:if>
        <xsl:if test="not(ccda:patient/ccda:name[@use='L']) and ccda:patient/ccda:name[1]">
            , "name": [
                <xsl:call-template name="generateNameJson">
                    <xsl:with-param name="selectedName" select="ccda:patient/ccda:name[1]"/>
                </xsl:call-template>
            ]
        </xsl:if>

        <xsl:variable name="genderCodeNorm"
            select="translate(
                normalize-space(ccda:patient/ccda:administrativeGenderCode/@code),
                'abcdefghijklmnopqrstuvwxyz',
                'ABCDEFGHIJKLMNOPQRSTUVWXYZ'
            )"/>
        , "gender": "<xsl:choose>
                    <xsl:when test="ccda:patient/ccda:administrativeGenderCode/@nullFlavor">
                        <xsl:call-template name="getNullFlavorDisplay">
                            <xsl:with-param name="nullFlavor" select="ccda:patient/ccda:administrativeGenderCode/@nullFlavor"/>
                        </xsl:call-template>
                    </xsl:when>

                    <!-- Code-based mapping -->
                    <xsl:when test="$genderCodeNorm = 'M'">male</xsl:when>
                    <xsl:when test="$genderCodeNorm = 'F'">female</xsl:when>
                    <xsl:when test="$genderCodeNorm = 'UN'">unknown</xsl:when>
                    <xsl:when test="$genderCodeNorm = 'OTH'">other</xsl:when>

                    <!-- DisplayName-based mapping -->
                    <xsl:when test="ccda:patient/ccda:administrativeGenderCode/@displayName">
                        <xsl:value-of select="translate(ccda:patient/ccda:administrativeGenderCode/@displayName, 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz')"/>
                    </xsl:when>  

                    <!-- final fallback -->                  
                    <xsl:otherwise>
                        <xsl:value-of select="translate(ccda:patient/ccda:administrativeGenderCode/@code, 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz')"/>
                    </xsl:otherwise>
                </xsl:choose>"
                
        <xsl:if test="string(ccda:patient/ccda:birthTime/@value)">
        , "birthDate": "<xsl:choose>
                        <xsl:when test='string-length(ccda:patient/ccda:birthTime/@value) >= 8'>
                          <xsl:value-of select='concat(substring(ccda:patient/ccda:birthTime/@value, 1, 4), "-", substring(ccda:patient/ccda:birthTime/@value, 5, 2), "-", substring(ccda:patient/ccda:birthTime/@value, 7, 2))'/>
                        </xsl:when>
                        <xsl:otherwise>
                          <xsl:value-of select='ccda:patient/ccda:birthTime/@value'/>
                        </xsl:otherwise>
                      </xsl:choose>"
        </xsl:if>
        <xsl:if test="ccda:addr[not(@nullFlavor)]">
            <xsl:call-template name="build-address-array">
              <xsl:with-param name="addresses" select="ccda:addr[not(@nullFlavor)]"/>
              <xsl:with-param name="resource_name" select="'Patient'"/>
            </xsl:call-template>
        </xsl:if>
        <xsl:if test="ccda:telecom[not(@nullFlavor)]">
            , "telecom": [
                <xsl:for-each select="ccda:telecom[not(@nullFlavor)]">
                    {
                        <xsl:if test="@value">
                            "system": "<xsl:choose>
                                    <xsl:when test="contains(@value, 'tel:')">phone</xsl:when>
                                    <xsl:when test="contains(@value, 'mailto:')">email</xsl:when>
                                    <xsl:when test="contains(@value, 'fax:')">fax</xsl:when>
                                    <xsl:when test="contains(@value, 'pager:')">pager</xsl:when>
                                    <xsl:when test="contains(@value, 'sms:')">sms</xsl:when>
                                    <xsl:when test="contains(@value, 'http://') 
                                                 or contains(@value, 'https://') 
                                                 or contains(@value, 'www.')">url</xsl:when>
                                    <xsl:otherwise>other</xsl:otherwise>
                                </xsl:choose>",
                        </xsl:if>
                        <xsl:if test="@use">
                            <xsl:variable name="use_trimmed" select="normalize-space(@use)"/>
                            "use": "<xsl:choose>
                                <xsl:when test="$use_trimmed='AS' or $use_trimmed='DIR' or $use_trimmed='PUB' or $use_trimmed='WP'">work</xsl:when>
                                <xsl:when test="$use_trimmed='BAD'">old</xsl:when>
                                <xsl:when test="$use_trimmed='H' or $use_trimmed='HP' or $use_trimmed='HV' or $use_trimmed='EC'">home</xsl:when>
                                <xsl:when test="$use_trimmed='MC' or $use_trimmed='PG'">mobile</xsl:when>
                                <xsl:when test="$use_trimmed='TMP'">temp</xsl:when>
                                <xsl:otherwise>home</xsl:otherwise> <!-- For Patient resource, default to 'home' if no match -->
                            </xsl:choose>",
                        </xsl:if>
                        "value": "<xsl:call-template name="clean-telecom-value">
                                    <xsl:with-param name="value" select="@value"/>
                                  </xsl:call-template>"
                    }
                    <xsl:if test="position() != last()">,</xsl:if>
                </xsl:for-each>
            ]
        </xsl:if>

      <xsl:if test="ccda:patient/ccda:raceCode or ccda:patient/ccda:ethnicGroupCode/@code or ccda:patient/ccda:administrativeGenderCode/@code">
      , "extension": [
        <!-- Declare OMB code sets -->
        <xsl:variable name="ombRaceCodes" select="'1002-5 2028-9 2054-5 2076-8 2106-3 UNK ASKU'" />
        <xsl:variable name="ombEthnicityCodes" select="'2135-2 2186-5 UNK ASKU'" />

        <!-- RACE extension -->
        <xsl:if test="ccda:patient/ccda:raceCode">
          {
            "url": "http://hl7.org/fhir/us/core/StructureDefinition/us-core-race",
            "extension": [
              <xsl:for-each select="ccda:patient/ccda:raceCode">
                <xsl:variable name="raceCode">
                  <xsl:choose>
                    <xsl:when test="@code"><xsl:value-of select="@code"/></xsl:when>
                    <xsl:when test="@nullFlavor"><xsl:value-of select="@nullFlavor"/></xsl:when>
                  </xsl:choose>
                </xsl:variable>

                <xsl:variable name="raceDisplay">
                  <xsl:choose>
                    <xsl:when test="@code"><xsl:value-of select="@displayName"/></xsl:when>
                    <xsl:otherwise>
                      <xsl:call-template name="getRaceEthnicityNullFlavorDisplay">
                        <xsl:with-param name="nullFlavor" select="@nullFlavor"/>
                      </xsl:call-template>
                    </xsl:otherwise>
                  </xsl:choose>
                </xsl:variable>

                <xsl:variable name="raceUrl">
                  <xsl:choose>
                    <xsl:when test="contains($ombRaceCodes, $raceCode)">ombCategory</xsl:when>
                    <xsl:otherwise>detailed</xsl:otherwise>
                  </xsl:choose>
                </xsl:variable>

                <xsl:variable name="raceSystem">
                  <xsl:choose>
                    <xsl:when test="$raceCode = 'UNK' or $raceCode = 'ASKU'">http://terminology.hl7.org/CodeSystem/v3-NullFlavor</xsl:when>
                    <xsl:when test="@codeSystem">urn:oid:<xsl:value-of select="@codeSystem"/></xsl:when>
                    <xsl:otherwise></xsl:otherwise>
                  </xsl:choose>
                </xsl:variable>

                {
                  "url": "<xsl:value-of select='$raceUrl'/>",
                  "valueCoding": {
                    "system": "<xsl:value-of select='$raceSystem'/>",
                    "code": "<xsl:value-of select='$raceCode'/>",
                    "display": "<xsl:value-of select='$raceDisplay'/>"
                  }
                }<xsl:if test="position() != last()">,</xsl:if>
              </xsl:for-each>
              ,{
                "url": "text",
                "valueString": "<xsl:for-each select='ccda:patient/ccda:raceCode'>
                                  <xsl:choose>
                                    <xsl:when test='@code'><xsl:value-of select='@displayName'/></xsl:when>
                                    <xsl:otherwise>
                                      <xsl:call-template name='getRaceEthnicityNullFlavorDisplay'>
                                        <xsl:with-param name='nullFlavor' select='@nullFlavor'/>
                                      </xsl:call-template>
                                    </xsl:otherwise>
                                  </xsl:choose>
                                  <xsl:if test='position() != last()'>, </xsl:if>
                                </xsl:for-each>"
              }
            ]
          }
        </xsl:if>

        <!-- ETHNICITY extension -->
        <xsl:if test="ccda:patient/ccda:ethnicGroupCode/@code">
          <xsl:if test="ccda:patient/ccda:raceCode">,</xsl:if>
          {
            "url": "http://hl7.org/fhir/us/core/StructureDefinition/us-core-ethnicity",
            "extension": [
              <xsl:for-each select="ccda:patient/ccda:ethnicGroupCode">
                <xsl:variable name="ethCode">
                  <xsl:choose>
                    <xsl:when test="@code"><xsl:value-of select="@code"/></xsl:when>
                    <xsl:when test="@nullFlavor"><xsl:value-of select="@nullFlavor"/></xsl:when>
                  </xsl:choose>
                </xsl:variable>

                <xsl:variable name="ethDisplay">
                  <xsl:choose>
                    <xsl:when test="@code"><xsl:value-of select="@displayName"/></xsl:when>
                    <xsl:otherwise>
                      <xsl:call-template name="getRaceEthnicityNullFlavorDisplay">
                        <xsl:with-param name="nullFlavor" select="@nullFlavor"/>
                      </xsl:call-template>
                    </xsl:otherwise>
                  </xsl:choose>
                </xsl:variable>

                <xsl:variable name="ethUrl">
                  <xsl:choose>
                    <xsl:when test="contains($ombEthnicityCodes, $ethCode)">ombCategory</xsl:when>
                    <xsl:otherwise>detailed</xsl:otherwise>
                  </xsl:choose>
                </xsl:variable>

                <xsl:variable name="ethSystem">
                  <xsl:choose>
                    <xsl:when test="@codeSystem">urn:oid:<xsl:value-of select="@codeSystem"/></xsl:when>
                    <xsl:otherwise></xsl:otherwise>
                  </xsl:choose>
                </xsl:variable>

                {
                  "url": "<xsl:value-of select='$ethUrl'/>",
                  "valueCoding": {
                    "system": "<xsl:value-of select='$ethSystem'/>",
                    "code": "<xsl:value-of select='$ethCode'/>",
                    "display": "<xsl:value-of select='$ethDisplay'/>"
                  }
                }<xsl:if test="position() != last()">,</xsl:if>
              </xsl:for-each>
              ,{
                "url": "text",
                "valueString": "<xsl:for-each select='ccda:patient/ccda:ethnicGroupCode'>
                                  <xsl:choose>
                                    <xsl:when test='@code'><xsl:value-of select='@displayName'/></xsl:when>
                                    <xsl:otherwise>
                                      <xsl:call-template name='getRaceEthnicityNullFlavorDisplay'>
                                        <xsl:with-param name='nullFlavor' select='@nullFlavor'/>
                                      </xsl:call-template>
                                    </xsl:otherwise>
                                  </xsl:choose>
                                  <xsl:if test='position() != last()'>, </xsl:if>
                                </xsl:for-each>"
              }
            ]
          }
        </xsl:if>

        <!-- GenderCode extension -->
        <xsl:if test="string(ccda:patient/ccda:administrativeGenderCode/@code)">
          <xsl:if test="ccda:patient/ccda:raceCode or ccda:patient/ccda:ethnicGroupCode/@code">,</xsl:if>
        {
            "url": "http://hl7.org/fhir/us/core/StructureDefinition/us-core-birthsex", 
            "valueCode": "<xsl:call-template name='mapAdministrativeGenderCode'>
                              <xsl:with-param name='genderCode' select='ccda:patient/ccda:administrativeGenderCode/@code'/>
                          </xsl:call-template>"
        }
        </xsl:if>
      ]
      </xsl:if>
      <xsl:variable name="cinId" select="$patientCIN"/>

      <!-- SSN: Prefer root '2.16.840.1.113883.4.1', fallback to pattern -->
      <xsl:variable name="ssnId" select="(ccda:id[@root='2.16.840.1.113883.4.1'] | 
                                          ccda:id[
                                            string-length(@extension) = 11 and 
                                            substring(@extension,4,1) = '-' and 
                                            substring(@extension,7,1) = '-' and 
                                            translate(concat(substring(@extension,1,3), substring(@extension,5,2), substring(@extension,8,4)), '0123456789', '') = ''
                                          ])[1]/@extension"/>

      <xsl:variable name="mrnId" select="$patient-MRN"/>

      <xsl:if test="$cinId or $ssnId or $mrnId">
      , "identifier": [
        <!-- CIN (EPI) -->
        <xsl:if test="$cinId">
          {
            "type": {
              "coding": [{
                "system": "http://terminology.hl7.org/CodeSystem/v2-0203",
                "code": "MA",
                "display": "Patient Medicaid Number"
              }],
              "text": "Patient Medicaid Number"
            },
            "system": "http://www.medicaid.gov/",
            "value": "<xsl:value-of select="$cinId"/>"
          }<xsl:if test="$ssnId or $mrnId">,</xsl:if>
        </xsl:if>

        <!-- SSN (JMR123) -->
        <xsl:if test="$ssnId">
          {
            "type": {
              "coding": [{
                "system": "http://terminology.hl7.org/CodeSystem/v2-0203",
                "code": "SS",
                "display": "Social Security Number"
              }],
              "text": "Social Security Number"
            },
            "system": "http://www.ssa.gov/",
            "value": "<xsl:value-of select="$ssnId"/>"
          }<xsl:if test="$mrnId">,</xsl:if>
        </xsl:if>

        <!-- MR (no assigningAuthorityName) -->
        <xsl:if test="$mrnId">
          {
            "type": {
              "coding": [{
                "system": "http://terminology.hl7.org/CodeSystem/v2-0203",
                "code": "MR",
                "display": "Medical Record Number"
              }],
              "text": "Medical Record Number"
            },
            "system": "http://www.scn.gov/facility/<xsl:value-of select="$facilityID"/>",
            "value": "<xsl:value-of select="$mrnId"/>"
            <xsl:if test="string($organizationResourceId)">
              , "assigner": {
                "reference": "Organization/<xsl:value-of select="$organizationResourceId"/>"
              }
            </xsl:if>
          }
        </xsl:if>
      ]
      </xsl:if>
      <xsl:if test="ccda:patient/sdtc:deceasedInd/@value">  
      , "deceasedBoolean": <xsl:value-of select="ccda:patient/sdtc:deceasedInd/@value"/>  
      </xsl:if>
      <xsl:variable name="mappedCode">
        <xsl:call-template name="mapMaritalStatusCode">
          <xsl:with-param name="statusCode" select="ccda:patient/ccda:maritalStatusCode/@code"/>
        </xsl:call-template>
      </xsl:variable>

      <!-- Output maritalStatus only if mappedCode is non-empty -->
      <xsl:if test="string($mappedCode)">
        , "maritalStatus": {
          "coding": [{
            "system": "http://terminology.hl7.org/CodeSystem/v3-MaritalStatus",
            "code": "<xsl:value-of select='$mappedCode'/>",
            "display": "<xsl:call-template name='mapMaritalStatus'>
                          <xsl:with-param name='statusCode' select='ccda:patient/ccda:maritalStatusCode/@code'/>
                        </xsl:call-template>"
          }]
        }
      </xsl:if>
      <xsl:if test="string(ccda:patient/ccda:languageCommunication/ccda:languageCode/@code)">
      , "communication" : [{
        "language" : {
          "coding" : [{
                "code" : "<xsl:value-of select="ccda:patient/ccda:languageCommunication/ccda:languageCode/@code"/>",
                "system" : "urn:ietf:bcp:47"
              }]
        }
        <xsl:if test="string(ccda:patient/ccda:languageCommunication/ccda:preferenceInd/@value)">
        , "preferred" : <xsl:value-of select="ccda:patient/ccda:languageCommunication/ccda:preferenceInd/@value"/>
        </xsl:if>
      }]
      </xsl:if>      
    }
    , "request" : {
        "method" : "POST",
        "url" : "<xsl:value-of select='$baseFhirUrl'/>/Patient/<xsl:value-of select='$patientResourceId'/>"
      }
  }
  </xsl:template>

  <!-- Encounter Template -->
  <xsl:template name="ComponentEncounter" match="/ccda:ClinicalDocument/ccda:component/ccda:structuredBody/ccda:component/ccda:section[@ID='encounters']/ccda:entry[position()=1]/ccda:encounter">
    ,{
      "fullUrl" : "<xsl:value-of select='$baseFhirUrl'/>/Encounter/<xsl:value-of select="$encounterResourceId"/>",
      "resource": {
        "resourceType": "Encounter",
        "id": "<xsl:value-of select="$encounterResourceId"/>",
        "meta" : {
          "lastUpdated" : "<xsl:value-of select='$currentTimestamp'/>",
          "profile" : ["<xsl:value-of select='$encounterMetaProfileUrlFull'/>"]
        },
        "status": "<xsl:call-template name='mapEncounterStatus'>
                            <xsl:with-param name='statusCode' select='$encounterStatus'/>
                        </xsl:call-template>",
        <xsl:if test="string($encounterType)">
          <xsl:variable name="encounterTypeDisplay">
            <xsl:call-template name="getEncounterTypeDisplay">
              <xsl:with-param name="encounterType" select="$encounterType"/>
            </xsl:call-template>
          </xsl:variable>

          "type": [
            {
              "coding": [
                {
                  "system": "http://snomed.info/sct",
                  "code": "<xsl:value-of select="$encounterType"/>",
                  "display": "<xsl:value-of select="$encounterTypeDisplay"/>"
                }
              ],
              "text": "<xsl:value-of select="$encounterTypeDisplay"/>"
            }
          ],
        </xsl:if>
        "class": {
          "system": "http://terminology.hl7.org/CodeSystem/v3-ActCode",
          "code": "<xsl:value-of select="ccda:code/@code"/>",
          "display": "<xsl:value-of select="ccda:code/@displayName"/>"
        },
        "subject" : {
          "reference" : "Patient/<xsl:value-of select='$patientResourceId'/>",
          "display" : "<xsl:value-of select="$patientResourceName"/>"
        }
        <xsl:choose>
          <!-- Check if low or high exists -->
          <xsl:when test="string(ccda:effectiveTime/ccda:low/@value) or string(ccda:effectiveTime/ccda:high/@value)">
            , "period": {
              "start": "<xsl:call-template name='formatDateTime'>
                          <xsl:with-param name='dateTime' select='ccda:effectiveTime/ccda:low/@value'/>
                      </xsl:call-template>",
              "end": "<xsl:call-template name='formatDateTime'>
                        <xsl:with-param name='dateTime' select='ccda:effectiveTime/ccda:high/@value'/>
                    </xsl:call-template>"
            }
          </xsl:when>
          <!-- Check if only value exists -->
          <xsl:when test="string(ccda:effectiveTime/@value)">
            , "period": {
              "start": "<xsl:call-template name='formatDateTime'>
                          <xsl:with-param name='dateTime' select='ccda:effectiveTime/@value'/>
                      </xsl:call-template>"
            }
          </xsl:when>
        </xsl:choose>
        <xsl:if test="string(ccda:participant/@typeCode) and (string(ccda:participant/ccda:assignedEntity/ccda:assignedPerson/ccda:name/ccda:given[1]) or string(ccda:participant/ccda:assignedEntity/ccda:assignedPerson/ccda:name/ccda:family))">
            , "participant": [
                {
                    "type": [
                        {
                            "coding": [
                                {
                                    "system": "http://terminology.hl7.org/CodeSystem/v3-ParticipationType",
                                    "code": "<xsl:value-of select="ccda:participant/@typeCode"/>",
                                    "display": "<xsl:call-template name='mapParticipantType'>
                                                  <xsl:with-param name='typeCode' select='ccda:participant/@typeCode'/>
                                              </xsl:call-template>"
                                }
                            ]
                        }
                      ]
                    , "individual": {
                        "display": "<xsl:value-of select="concat(ccda:participant/ccda:assignedEntity/ccda:assignedPerson/ccda:name/ccda:given[1], ' ', ccda:participant/ccda:assignedEntity/ccda:assignedPerson/ccda:name/ccda:family)"/>"
                    }
                }
            ]
        </xsl:if>
        <xsl:if test="string($locationResourceId) or normalize-space(ccda:participant/ccda:participantRole/ccda:playingEntity/ccda:name)">
        , "location": [
            {
                "location": {
                    <xsl:if test="string($locationResourceId)"> 
                      "reference": "Location/<xsl:value-of select="$locationResourceId"/>"
                      <xsl:if test="normalize-space(ccda:participant/ccda:participantRole/ccda:playingEntity/ccda:name)">,</xsl:if>
                    </xsl:if>
                    <xsl:if test="normalize-space(ccda:participant/ccda:participantRole/ccda:playingEntity/ccda:name)">
                      "display": "<xsl:call-template name="string-trim">
                                    <xsl:with-param name="text" select="ccda:participant/ccda:participantRole/ccda:playingEntity/ccda:name"/>
                                  </xsl:call-template>"
                    </xsl:if>
                }
            }
        ]
        </xsl:if>
      }      
      , "request" : {
        "method" : "POST",
        "url" : "<xsl:value-of select='$baseFhirUrl'/>/Encounter/<xsl:value-of select="$encounterResourceId"/>"
      }
    }
  </xsl:template>

  <!-- Consent Template -->
  <xsl:template name="Consent" match="/ccda:ClinicalDocument/ccda:authorization/ccda:consent">
    ,{
      "fullUrl" : "<xsl:value-of select='$baseFhirUrl'/>/Consent/<xsl:value-of select='$consentResourceId'/>",
      "resource": {
        "resourceType": "Consent",
        "id": "<xsl:value-of select='$consentResourceId'/>",
        "meta" : {
          "lastUpdated" : "<xsl:value-of select='$currentTimestamp'/>",
          "profile" : ["<xsl:value-of select='$consentMetaProfileUrlFull'/>"]
        },
        "status": "<xsl:choose>
                      <xsl:when test="ccda:statusCode/@code='active'">active</xsl:when>
                      <xsl:when test="ccda:statusCode/@code='completed'">active</xsl:when>
                      <xsl:when test="ccda:statusCode/@code='aborted'">not-done</xsl:when>
                      <xsl:when test="ccda:statusCode/@code='nullified'">entered-in-error</xsl:when>
                      <xsl:when test="ccda:statusCode/@code='held'">draft</xsl:when>
                      <xsl:when test="ccda:statusCode/@code='suspended'">inactive</xsl:when>
                      <xsl:otherwise>unknown</xsl:otherwise>
                  </xsl:choose>",
        "scope": {
                    "coding": [
                        {
                            "system": "http://terminology.hl7.org/CodeSystem/consentscope",
                            "code": "treatment",
                            "display": "Treatment"
                        }
                    ],
                    "text": "treatment"
                },
        "category": [
                    {
                        "coding": [
                            {
                                "system": "http://loinc.org",
                                "code": "59284-0",
                                "display": "Consent Document"
                            }
                        ]
                    },
                    {
                        "coding": [
                            {
                                "system": "http://terminology.hl7.org/CodeSystem/v3-ActCode",
                                "code": "IDSCL"
                            }
                        ]
                    }
                ]
        <xsl:if test="ccda:effectiveTime/@value or $currentTimestamp">
            , "dateTime": "<xsl:choose>
                                <xsl:when test="ccda:effectiveTime/@value">
                                    <xsl:call-template name="formatDateTime">
                                        <xsl:with-param name="dateTime" select="ccda:effectiveTime/@value"/>
                                    </xsl:call-template>
                                </xsl:when>
                                <xsl:otherwise>
                                    <xsl:value-of select="$currentTimestamp"/>
                                </xsl:otherwise>
                            </xsl:choose>"
        </xsl:if>
        , "patient" : {
            "reference" : "Patient/<xsl:value-of select='$patientResourceId'/>"
        }
        , "organization" : [{
          "reference" : "Organization/<xsl:value-of select='$organizationResourceId'/>"
        }]
        , "provision" : {
              "type" : "<xsl:choose>
                            <xsl:when test="contains(string(ccda:code/@displayName), 'deny')">deny</xsl:when>
                            <xsl:otherwise>permit</xsl:otherwise>
                        </xsl:choose>"
        }
        , "policy" : [{
            "authority" : "urn:uuid:d1eaac1a-22b7-4bb6-9c62-cc95d6fdf1a5"
          }]
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
  <xsl:template name="Organization" match="/ccda:ClinicalDocument/ccda:author">
    ,{
      "fullUrl" : "<xsl:value-of select='$baseFhirUrl'/>/Organization/<xsl:value-of select="$organizationResourceId"/>",
      "resource": {
        "resourceType": "Organization",
        "id": "<xsl:value-of select="$organizationResourceId"/>",
        "meta" : {
          "lastUpdated" : "<xsl:value-of select='$currentTimestamp'/>",
          "profile" : ["<xsl:value-of select='$organizationMetaProfileUrlFull'/>"]
        },
        "active": true,
        <xsl:if test="$organizationNPI or $organizationTIN">
          "identifier": [

              <!-- NPI -->
              <xsl:if test="$organizationNPI">
                {
                  "use": "official",
                  "type": {
                    "coding": [
                      {
                        "system": "http://terminology.hl7.org/CodeSystem/v2-0203",
                        "code": "NPI",
                        "display": "National Provider Identifier"
                      }
                    ]
                  },
                  "system": "http://hl7.org/fhir/sid/us-npi",
                  "value": "<xsl:value-of select='$organizationNPI'/>"
                }
              </xsl:if>
              
              <xsl:if test="$organizationNPI and $organizationTIN">,</xsl:if> <!-- Comma only if both exist -->

              <!-- TAX -->
              <xsl:if test="$organizationTIN">
                {
                  "use": "official",
                  "type": {
                    "coding": [
                      {
                        "system": "http://terminology.hl7.org/CodeSystem/v2-0203",
                        "code": "TAX",
                        "display": "Tax ID Number"
                      }
                    ]
                  },
                  "system": "http://www.irs.gov/",
                  "value": "<xsl:value-of select='$organizationTIN'/>"
                }
              </xsl:if>
          ],
        </xsl:if>

        <xsl:variable name="orgNameRaw">
          <xsl:choose>
            <xsl:when test="$organizationName"><xsl:value-of select="$organizationName"/></xsl:when>
            <xsl:otherwise><xsl:value-of select="ccda:assignedAuthor/ccda:representedOrganization/ccda:name"/></xsl:otherwise>
          </xsl:choose>
        </xsl:variable>
        "name": "<xsl:call-template name="string-trim">
                    <xsl:with-param name="text" select="$orgNameRaw"/>
                  </xsl:call-template>"

        <xsl:if test="ccda:assignedAuthor/ccda:representedOrganization/ccda:telecom[not(@nullFlavor)]">
            , "telecom": [
                <xsl:for-each select="ccda:assignedAuthor/ccda:representedOrganization/ccda:telecom[not(@nullFlavor)]">
                    {
                        <xsl:if test="@value">
                            "system": "<xsl:choose>
                                    <xsl:when test="contains(@value, 'tel:')">phone</xsl:when>
                                    <xsl:when test="contains(@value, 'mailto:')">email</xsl:when>
                                    <xsl:when test="contains(@value, 'fax:')">fax</xsl:when>
                                    <xsl:when test="contains(@value, 'pager:')">pager</xsl:when>
                                    <xsl:when test="contains(@value, 'sms:')">sms</xsl:when>
                                    <xsl:when test="contains(@value, 'http://') 
                                                 or contains(@value, 'https://') 
                                                 or contains(@value, 'www.')">url</xsl:when>
                                    <xsl:otherwise>other</xsl:otherwise>
                                </xsl:choose>",
                        </xsl:if>
                        <xsl:if test="@use">
                            <xsl:variable name="use_trimmed" select="normalize-space(@use)"/>
                            "use": "<xsl:choose>
                                <xsl:when test="$use_trimmed='AS' or $use_trimmed='DIR' or $use_trimmed='PUB' or $use_trimmed='WP'">work</xsl:when>
                                <xsl:when test="$use_trimmed='BAD'">old</xsl:when>
                                <!-- <xsl:when test="$use_trimmed='H' or $use_trimmed='HP' or $use_trimmed='HV' or $use_trimmed='EC'">home</xsl:when> -->
                                <xsl:when test="$use_trimmed='MC' or $use_trimmed='PG'">mobile</xsl:when>
                                <xsl:when test="$use_trimmed='TMP'">temp</xsl:when>
                                <xsl:otherwise>work</xsl:otherwise>
                            </xsl:choose>",
                        </xsl:if>
                        "value": "<xsl:call-template name="clean-telecom-value">
                                    <xsl:with-param name="value" select="@value"/>
                                  </xsl:call-template>"
                    }
                    <xsl:if test="position() != last()">,</xsl:if>
                </xsl:for-each>
            ]
        </xsl:if>
        <xsl:if test="ccda:assignedAuthor/ccda:representedOrganization/ccda:addr[not(@nullFlavor)]">
            <xsl:call-template name="build-address-array">
              <xsl:with-param name="addresses" select="ccda:assignedAuthor/ccda:representedOrganization/ccda:addr[not(@nullFlavor)]"/>
              <xsl:with-param name="resource_name" select="'Organization'"/>
            </xsl:call-template>
        </xsl:if>
      },
      "request" : {
        "method" : "POST",
        "url" : "<xsl:value-of select='$baseFhirUrl'/>/Organization/<xsl:value-of select="$organizationResourceId"/>"
      }
    }
  </xsl:template>

  <!-- Sexual orientation Observation Template -->
  <xsl:template name="SexualOrientation" match="/ccda:ClinicalDocument/ccda:component/ccda:structuredBody/ccda:component/ccda:section[@ID='sexualOrientation']/ccda:entry/ccda:observation">
    <xsl:if test="string(ccda:code/@code) = '76690-7'">
      
      <xsl:variable name="resourceUUID">
        <xsl:choose>
          <xsl:when test="normalize-space(ccda:id/@extension)">
            <xsl:value-of select="normalize-space(ccda:id/@extension)"/>
          </xsl:when>
          <xsl:otherwise>
            <xsl:value-of select="normalize-space($sexualOrientationResourceId)"/>
          </xsl:otherwise>
        </xsl:choose>
      </xsl:variable>

      <xsl:variable name="sexualOrientationResourceUUId">
        <xsl:call-template name="generateFixedLengthResourceId">
          <xsl:with-param name="prefixString" select="concat($facilityID, '-', position())"/>
          <xsl:with-param name="sha256ResourceId" select="$resourceUUID"/>
        </xsl:call-template>
      </xsl:variable>

      ,{
        "fullUrl" : "<xsl:value-of select='$baseFhirUrl'/>/Observation/<xsl:value-of select='$sexualOrientationResourceUUId'/>",
        "resource": {
          "resourceType": "Observation",
          "id": "<xsl:value-of select='$sexualOrientationResourceUUId'/>",
          "meta" : {
            "lastUpdated" : "<xsl:value-of select='$currentTimestamp'/>",
            "profile" : ["<xsl:value-of select='$observationSexualOrientationMetaProfileUrlFull'/>"]
          },
          "status": "<xsl:call-template name='mapObservationStatus'>
                        <xsl:with-param name='statusCode' select='ccda:statusCode/@code'/>
                    </xsl:call-template>",
          "code": {
            "coding": [
              {
                "system": "http://loinc.org",
                "code": "<xsl:value-of select='ccda:code/@code'/>",
                "display": "<xsl:value-of select='ccda:code/@displayName'/>"
              }
            ],
            "text" : "<xsl:value-of select='ccda:code/@displayName'/>"
          },
          <xsl:choose>
            <xsl:when test="string(ccda:value/@code) = 'UNK' or string(ccda:value/@nullFlavor) = 'UNK'">
              "valueCodeableConcept" : {
                "coding" : [{
                  "system" : "http://terminology.hl7.org/CodeSystem/v3-NullFlavor",
                  "code" : "UNK",
                  "display" : "Unknown"
                }]
              },
            </xsl:when>
            <xsl:when test="string(ccda:value/@code) = 'OTH' or string(ccda:value/@nullFlavor) = 'OTH'">
              "valueCodeableConcept" : {
                "coding" : [{
                  "system" : "http://terminology.hl7.org/CodeSystem/v3-NullFlavor",
                  "code" : "OTH",
                  "display" : "Other"
                }]
              },
            </xsl:when>
            <xsl:when test="string(ccda:value/@code) = ''">
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
                  "system" : "<xsl:call-template name="mapCodeSystem">
                                <xsl:with-param name="oid" select="ccda:value/@codeSystem"/>
                              </xsl:call-template>",
                  "code" : "<xsl:value-of select='ccda:value/@code'/>",
                  "display" : "<xsl:value-of select='ccda:value/@displayName'/>"
                }]
              },
            </xsl:otherwise>
          </xsl:choose>
          "subject": {
            "reference": "Patient/<xsl:value-of select='$patientResourceId'/>",
            "display" : "<xsl:value-of select="$patientResourceName"/>"
          }
          <xsl:if test="ccda:effectiveTime/@value or $encounterEffectiveTimeValue or $currentTimestamp">
            , "effectiveDateTime": "<xsl:choose>
                                      <xsl:when test="ccda:effectiveTime/@value">
                                          <xsl:call-template name="formatDateTime">
                                              <xsl:with-param name="dateTime" select="ccda:effectiveTime/@value"/>
                                          </xsl:call-template>
                                      </xsl:when>
                                      <xsl:when test="$encounterEffectiveTimeValue">
                                          <xsl:value-of select="$encounterEffectiveTimeValue"/>
                                      </xsl:when>
                                      <xsl:otherwise>
                                          <xsl:value-of select="$currentTimestamp"/>
                                      </xsl:otherwise>
                                  </xsl:choose>"
          </xsl:if>  
        },
        "request" : {
          "method" : "POST",
          "url" : "<xsl:value-of select='$baseFhirUrl'/>/Observation/<xsl:value-of select='$sexualOrientationResourceUUId'/>"
        }
      }
    </xsl:if>
  </xsl:template>

  <!-- Observation Template -->
  <xsl:template name="Observation" match="/ccda:ClinicalDocument/ccda:component/ccda:structuredBody/ccda:component/ccda:section[@ID='observations']/ccda:entry/ccda:observation/ccda:entryRelationship">
    <!--The observation resource will be generated only for the question codes present in the list specified in 'mapObservationCategoryCodes'-->
    <xsl:variable name="allowedCodes" select="' 71802-3 96778-6 96779-4 88122-7 88123-5 93030-5 96780-2 96782-8 95618-5 95617-7 95616-9 95615-1 95614-4 '" />
    <!-- Set questionCode -->
    <xsl:variable name="questionCode" select="ccda:observation/ccda:code/@code"/>

    <xsl:if test="
        contains(concat(' ', $allowedCodes, ' '), concat(' ', $questionCode, ' '))
        and not(preceding-sibling::ccda:entryRelationship[ccda:observation/ccda:code/@code = $questionCode])
        and (
          ccda:observation/ccda:value/ccda:translation/@code = 'X-SDOH-FLO-1570000066-Patient-unable-to-answer'
          or ccda:observation/ccda:value/ccda:translation/@code = 'X-SDOH-FLO-1570000066-Patient-declined'
          or (
            string(ccda:observation/ccda:value/@code) != 'UNK'
            and string-length(ccda:observation/ccda:value/@code) > 0
            and string($questionCode) != 'UNK'
            and string-length($questionCode) > 0
            and string-length(ccda:observation/ccda:value/@nullFlavor) = 0
            and string-length(ccda:observation/ccda:code/@nullFlavor) = 0
            and (ccda:observation/ccda:code/@codeSystemName = 'LOINC'
              or ccda:observation/ccda:code/@codeSystemName = 'SNOMED'
              or ccda:observation/ccda:code/@codeSystemName = 'SNOMED CT')
          )
          or (
            $questionCode = '95614-4'
            and string-length(ccda:observation/ccda:value/@value) > 0
          )
        )
      ">

        <!--The observation resource will be generated only for the question codes present in the list specified in 'mapObservationCategoryCodes'-->
        <xsl:variable name="categoryCode">
                <xsl:call-template name="mapObservationCategoryCodes">
                  <xsl:with-param name="questionCode" select="$questionCode"/>
                </xsl:call-template>
              </xsl:variable>

        <xsl:if test="string($categoryCode)">
            <xsl:variable name="encounterEffectiveTime">
                  <xsl:choose>
                      <xsl:when test="ccda:observation/ccda:effectiveTime/@value">
                          <xsl:call-template name="formatDateTime">
                              <xsl:with-param name="dateTime" select="ccda:observation/ccda:effectiveTime/@value"/>
                          </xsl:call-template>
                      </xsl:when>
                      <xsl:when test="$encounterEffectiveTimeValue">
                          <xsl:value-of select="$encounterEffectiveTimeValue"/>
                      </xsl:when>
                      <xsl:otherwise>
                          <xsl:value-of select="$currentTimestamp"/>
                      </xsl:otherwise>
                  </xsl:choose>
              </xsl:variable>

              <xsl:variable name="encounterEffectiveTimeDigits" select="translate($encounterEffectiveTime, '-:TZ', '')"/> <!-- Remove non-digit characters for ID generation -->

              <xsl:variable name="observationIdSource">
                <xsl:choose>
                  <!-- Use extension if present and not empty -->
                  <xsl:when test="ccda:observation/ccda:id/@extension and normalize-space(ccda:observation/ccda:id/@extension) != ''">
                    <xsl:value-of select="concat($questionCode, '-', ccda:observation/ccda:id/@extension)"/>
                  </xsl:when>

                  <!-- Fallback: questionCode + encounterEffectiveTime -->
                  <xsl:otherwise>
                    <xsl:value-of
                      select="concat($questionCode, '-', $encounterEffectiveTimeDigits)"/>
                  </xsl:otherwise>
                </xsl:choose>
              </xsl:variable>

            <xsl:variable name="observationResourceId">
              <xsl:call-template name="generateFixedLengthResourceId">
                <xsl:with-param name="prefixString" select="concat($facilityID, '-')"/>
                <xsl:with-param name="sha256ResourceId" select="$observationIdSource"/>
              </xsl:call-template>
            </xsl:variable>
            ,{
              "fullUrl": "<xsl:value-of select='$baseFhirUrl'/>/Observation/<xsl:value-of select='$observationResourceId'/>",
              "resource": {
                "resourceType": "Observation",
                "id": "<xsl:value-of select='$observationResourceId'/>",
                "meta": {
                  "lastUpdated": "<xsl:value-of select='$currentTimestamp'/>",
                  "profile": ["<xsl:value-of select='$observationMetaProfileUrlFull'/>"]
                },
                "language": "en",
                "status": "<xsl:call-template name='mapObservationStatus'>
                              <xsl:with-param name='statusCode' select='ccda:observation/ccda:statusCode/@code'/>
                          </xsl:call-template>",
                "category": [
                  {
                    "coding": [{
                      "system": "http://hl7.org/fhir/us/sdoh-clinicalcare/CodeSystem/SDOHCC-CodeSystemTemporaryCodes",
                      "code": "<xsl:value-of select='$categoryCode'/>",
                      "display": "<xsl:call-template name="mapSDOHCategoryCodeDisplay">
                                    <xsl:with-param name="questionCode" select="$questionCode"/>
                                    <xsl:with-param name="categoryCode" select="$categoryCode"/>
                                  </xsl:call-template>"
                    }
                    ],
                    "text" : "<xsl:call-template name="mapSDOHCategoryText">
                                <xsl:with-param name="questionCode" select="$questionCode"/>
                                <xsl:with-param name="categoryCode" select="$categoryCode"/>
                              </xsl:call-template>"
                  },
                  <xsl:choose>
                    <xsl:when test="string($categoryCode) = 'sdoh-category-unspecified'">
                      <xsl:choose>
                        <xsl:when test="ccda:observation/ccda:code/@code = '96782-8'">
                          { 
                            "coding": [{
                              "system": "http://snomed.info/sct",
                              "code": "365458002",
                              "display": "Education and/or schooling finding"
                            }]
                          },
                        </xsl:when>
                      </xsl:choose>
                    </xsl:when>
                  </xsl:choose>
                  {
                    "coding": [{
                        "system": "http://terminology.hl7.org/CodeSystem/observation-category",
                        "code": "social-history"
                    }]
                  },
                  {
                    "coding": [{
                        "system": "http://terminology.hl7.org/CodeSystem/observation-category",
                        "code": "survey"
                    }]
                  }
                ],
                "code": {
                  "coding": [
                    {
                      "system": "http://loinc.org",
                      "code": "<xsl:value-of select='ccda:observation/ccda:code/@code'/>",
                      "display": "<xsl:value-of select='normalize-space(ccda:observation/ccda:code/@displayName)'/>"
                    }
                  ]
                  <xsl:choose>
                    <xsl:when test="ccda:observation/ccda:code/@originalText">
                      , "text": "<xsl:value-of select='normalize-space(ccda:observation/ccda:code/@originalText)'/>"
                    </xsl:when>
                    <xsl:when test="ccda:observation/ccda:code/@displayName">
                      , "text": "<xsl:value-of select='normalize-space(ccda:observation/ccda:code/@displayName)'/>"
                    </xsl:when>
                  </xsl:choose>
                },
                <xsl:choose>
                      <xsl:when test="ccda:observation/ccda:value/ccda:translation/@code = 'X-SDOH-FLO-1570000066-Patient-unable-to-answer' 
                                  or ccda:observation/ccda:value/ccda:translation/@code = 'X-SDOH-FLO-1570000066-Patient-declined'">
                        <xsl:variable name="dataAbsentReasonCode">
                          <xsl:call-template name="getDataAbsentReasonFhirCode">
                            <xsl:with-param name="dataAbsentReason" select="ccda:observation/ccda:value/ccda:translation/@code"/>
                          </xsl:call-template>
                        </xsl:variable>
                        "dataAbsentReason": {
                            "coding": [
                                {
                                    "system": "http://terminology.hl7.org/CodeSystem/data-absent-reason",
                                    "code": "<xsl:value-of select="$dataAbsentReasonCode"/>",
                                    "display": "<xsl:call-template name="getDataAbsentReasonFhirDisplay">
                                                  <xsl:with-param name="dataAbsentReasonCode" select="$dataAbsentReasonCode"/>
                                                </xsl:call-template>"
                                }
                            ]
                        },
                      </xsl:when>
                      <xsl:when test="ccda:observation/ccda:code/@code = '96778-6'">
                        "component": [
                              {
                                  "code": {
                                    "coding": [
                                      {
                                        "system": "http://loinc.org",
                                        "code": "<xsl:value-of select='ccda:observation/ccda:code/@code'/>",
                                        "display": "<xsl:value-of select='normalize-space(ccda:observation/ccda:code/@displayName)'/>"
                                      }
                                    ]
                                    <xsl:choose>
                                      <xsl:when test="ccda:observation/ccda:code/@originalText">
                                        , "text": "<xsl:value-of select='normalize-space(ccda:observation/ccda:code/@originalText)'/>"
                                      </xsl:when>
                                      <xsl:when test="ccda:observation/ccda:code/@displayName">
                                        , "text": "<xsl:value-of select='normalize-space(ccda:observation/ccda:code/@displayName)'/>"
                                      </xsl:when>
                                    </xsl:choose>
                                  }
                                  <xsl:if test="string($componentAnswersXml) and string-length($componentAnswersXml) > 0">
                                    , "valueCodeableConcept": {
                                      "coding": <xsl:value-of select='$componentAnswersXml'/>
                                    }
                                  </xsl:if>
                              }
                        ],
                      </xsl:when>
                      <xsl:when test="string(ccda:observation/ccda:code/@code) = '95614-4'"> <!--Total Safety Score-->
                          "valueCodeableConcept" : {
                            "coding": [{
                              "system": "http://unitsofmeasure.org",
                              "display": "{Number}"
                            }],
                            "text": "<xsl:value-of select='ccda:observation/ccda:value/@value'/>"
                          },

                          <!-- Gather all filtered observations for derivedFrom -->
                          <xsl:variable name="derivedFromCodes" select="'|95618-5|95617-7|95616-9|95615-1|'" />
                          <!-- Gather valid derivedFrom observations -->
                          <xsl:variable name="derivedObservations">
                            <xsl:for-each select="/ccda:ClinicalDocument//ccda:observation">
                              <xsl:variable name="code" select="ccda:code/@code"/>
                              <xsl:if test="string($code)
                                            and contains($derivedFromCodes, concat('|', $code, '|'))
                                            and not(preceding::ccda:observation[ccda:code/@code = $code])">
                                <xsl:copy-of select="."/>
                              </xsl:if>
                            </xsl:for-each>
                          </xsl:variable>

                          <!-- Output derivedFrom JSON if observations found -->
                          <xsl:if test="exsl:node-set($derivedObservations)/ccda:observation">
                            "derivedFrom": [
                              <xsl:for-each select="exsl:node-set($derivedObservations)/ccda:observation">
                                <xsl:variable name="encounterEffectiveTimeDF">
                                    <xsl:choose>
                                        <xsl:when test="ccda:effectiveTime/@value">
                                            <xsl:call-template name="formatDateTime">
                                                <xsl:with-param name="dateTime" select="ccda:effectiveTime/@value"/>
                                            </xsl:call-template>
                                        </xsl:when>
                                        <xsl:when test="$encounterEffectiveTimeValue">
                                            <xsl:value-of select="$encounterEffectiveTimeValue"/>
                                        </xsl:when>
                                        <xsl:otherwise>
                                            <xsl:value-of select="$currentTimestamp"/>
                                        </xsl:otherwise>
                                    </xsl:choose>
                                </xsl:variable>

                                <xsl:variable name="encounterEffectiveTimeDigitsDF" select="translate($encounterEffectiveTimeDF, '-:TZ', '')"/> <!-- Remove non-digit characters for ID generation -->
                                <xsl:variable name="questionCodeDF" select="ccda:code/@code"/>

                                <xsl:variable name="observationIdSourceDF">
                                  <xsl:choose>
                                    <!-- Use extension if present and not empty -->
                                    <xsl:when test="ccda:id/@extension and normalize-space(ccda:id/@extension) != ''">
                                      <xsl:value-of select="concat($questionCodeDF, '-', ccda:id/@extension)"/>
                                    </xsl:when>

                                    <!-- Fallback: questionCode + encounterEffectiveTime -->
                                    <xsl:otherwise>
                                      <xsl:value-of
                                        select="concat($questionCodeDF, '-', $encounterEffectiveTimeDigitsDF)"/>
                                    </xsl:otherwise>
                                  </xsl:choose>
                                </xsl:variable>

                                <xsl:variable name="observationResourceIdDF">
                                  <xsl:call-template name="generateFixedLengthResourceId">
                                    <xsl:with-param name="prefixString" select="concat($facilityID, '-')"/>
                                    <xsl:with-param name="sha256ResourceId" select="$observationIdSourceDF"/>
                                  </xsl:call-template>
                                </xsl:variable>
                                { "reference": "Observation/<xsl:value-of select='$observationResourceIdDF'/>" }<xsl:if test="position() != last()">,</xsl:if>
                              </xsl:for-each>
                            ],
                          </xsl:if>
                      </xsl:when>    
                      <xsl:otherwise>
                          "valueCodeableConcept" : {
                            "coding": [{
                              "system": "http://loinc.org",
                              "code": "<xsl:value-of select='ccda:observation/ccda:value/@code'/>",
                              "display": "<xsl:value-of select='normalize-space(ccda:observation/ccda:value/@displayName)'/>"
                            }]
                            <xsl:choose>
                              <xsl:when test="ccda:observation/ccda:value/@originalText">
                                , "text": "<xsl:value-of select='normalize-space(ccda:observation/ccda:value/@originalText)'/>"
                              </xsl:when>
                              <xsl:when test="ccda:observation/ccda:value/@displayName">
                                , "text": "<xsl:value-of select='normalize-space(ccda:observation/ccda:value/@displayName)'/>"
                              </xsl:when>
                            </xsl:choose>
                          },
                      </xsl:otherwise>
                </xsl:choose>
                "subject": {
                  "reference": "Patient/<xsl:value-of select='$patientResourceId'/>",
                  "display": "<xsl:value-of select="$patientResourceName"/>"
                }
                <xsl:if test="normalize-space($encounterResourceId) != '' and $encounterResourceId != 'null'">
                , "encounter": {
                    "reference": "Encounter/<xsl:value-of select='$encounterResourceId'/>"
                  }
                </xsl:if>
                , "effectiveDateTime": "<xsl:value-of select='$encounterEffectiveTime'/>"
                <xsl:if test="string($organizationResourceId)">
                , "performer": [{
                              "reference": "Organization/<xsl:value-of select='$organizationResourceId'/>"
                          }]
                </xsl:if>
              },
              "request": {
                "method": "POST",
                "url": "<xsl:value-of select='$baseFhirUrl'/>/Observation/<xsl:value-of select='$observationResourceId'/>"
              }
            }
          </xsl:if>
        </xsl:if>
  </xsl:template>

  <!-- Location Template from Encounters section -->
  <xsl:template name="EncountersLocationResource" match="/ccda:ClinicalDocument/ccda:component/ccda:structuredBody/ccda:component/ccda:section[@ID='encounters']/ccda:entry[position()=1]/ccda:encounter/ccda:participant/ccda:participantRole">
  <xsl:if test="string($locationResourceId)">
    ,{
      "fullUrl": "<xsl:value-of select='$baseFhirUrl'/>/Location/<xsl:value-of select='$locationResourceId'/>",
      "resource": {
        "resourceType": "Location",
        "id": "<xsl:value-of select='$locationResourceId'/>",
        "meta": {
          "lastUpdated": "<xsl:value-of select='$currentTimestamp'/>",
          "profile" : ["<xsl:value-of select='$locationMetaProfileUrl'/>"]
        }
        <xsl:if test="normalize-space(ccda:playingEntity/ccda:name)">
          ,"name": "<xsl:call-template name="string-trim">
                    <xsl:with-param name="text" select="ccda:playingEntity/ccda:name"/>
                  </xsl:call-template>"
        </xsl:if>

        <xsl:if test="ccda:addr[not(@nullFlavor)]">
            <xsl:call-template name="build-address-object-only">
              <xsl:with-param name="addresses" select="ccda:addr[not(@nullFlavor)]"/>
              <xsl:with-param name="resource_name" select="'Location'"/>
            </xsl:call-template>            
        </xsl:if>
      },
      "request" : {
        "method" : "POST",
        "url" : "<xsl:value-of select='$baseFhirUrl'/>/Location/<xsl:value-of select="$locationResourceId"/>"
      }
    }
  </xsl:if>
  </xsl:template>

<!-- Grouper Observation Template -->
  <xsl:template name="GrouperObservation">
    <xsl:variable name="grouperObs" select="/ccda:ClinicalDocument/ccda:component/ccda:structuredBody/ccda:component/ccda:section[@ID='observations']/ccda:entry/ccda:observation"/>
    <xsl:variable name="screeningCode">
      <xsl:choose>
        <xsl:when test="string($grouperObs/ccda:code/@code)">
          <xsl:value-of select="$grouperObs/ccda:code/@code"/>
        </xsl:when>
        <xsl:when test="string($grouperScreeningCode) and normalize-space($grouperScreeningCode) != ''">
          <xsl:value-of select="$grouperScreeningCode"/>
        </xsl:when>
        <xsl:otherwise>96777-8</xsl:otherwise>
      </xsl:choose>
    </xsl:variable>

    <xsl:if test="($grouperObs != '') and (normalize-space($categoryXml) != '[]')">
      <xsl:for-each select="$grouperObs[ccda:code/@code = $screeningCode]"> <!--'96777-8'-->       
        <xsl:variable name="grouperObservationResourceId">
          <xsl:call-template name="generateFixedLengthResourceId">
            <xsl:with-param name="prefixString" select="concat($facilityID, '-')"/>
            <xsl:with-param name="sha256ResourceId" select="$grouperObservationResourceSha256Id"/>
          </xsl:call-template>
        </xsl:variable>
        ,{
          "fullUrl": "<xsl:value-of select='$baseFhirUrl'/>/Observation/<xsl:value-of select='$grouperObservationResourceId'/>",
          "resource": {
            "resourceType": "Observation",
            "id": "<xsl:value-of select="$grouperObservationResourceId"/>",
            "meta": {
              "lastUpdated": "<xsl:value-of select='$currentTimestamp'/>",
              "profile": ["<xsl:value-of select='$observationMetaProfileUrlFull'/>"]
            },
            "language": "en",
            "status": "<xsl:call-template name='mapObservationStatus'>
                          <xsl:with-param name='statusCode' select='ccda:statusCode/@code'/>
                        </xsl:call-template>",
            "code": {
              "coding": [
                {
                  "system": "<xsl:call-template name="mapScreeningCodeSystem">
                                <xsl:with-param name="screeningCode" select="$screeningCode"/>
                              </xsl:call-template>",
                  "code": "<xsl:value-of select='$screeningCode'/>",
                  "display": "<xsl:call-template name="mapScreeningCodeDisplay">
                                <xsl:with-param name="screeningCode" select="$screeningCode"/>
                              </xsl:call-template>"
                }
                <xsl:if test="starts-with($screeningCode, 'NYS')">
                  ,{
                    "code": "100698-0",
                    "system": "http://loinc.org",
                    "display": "Social Determinants of Health screening report Document"
                  }
                </xsl:if>
              ]
            },
            "subject": {
              "reference": "Patient/<xsl:value-of select='$patientResourceId'/>",
              "display": "<xsl:value-of select='$patientResourceName'/>"
            },
            <xsl:if test="normalize-space($encounterResourceId) != '' and $encounterResourceId != 'null'">
              "encounter": {
                "reference": "Encounter/<xsl:value-of select='$encounterResourceId'/>"
              },
            </xsl:if>
            "effectiveDateTime": "<xsl:choose>
                                      <xsl:when test='ccda:effectiveTime/@value'>
                                        <xsl:call-template name='formatDateTime'>
                                          <xsl:with-param name='dateTime' select='ccda:effectiveTime/@value'/>
                                        </xsl:call-template>
                                      </xsl:when>
                                      <xsl:when test='$encounterEffectiveTimeValue'>
                                        <xsl:value-of select='$encounterEffectiveTimeValue'/>
                                      </xsl:when>
                                      <xsl:otherwise>
                                        <xsl:value-of select='$currentTimestamp'/>
                                      </xsl:otherwise>
                                   </xsl:choose>",
            "issued": "<xsl:value-of select='$currentTimestamp'/>",
            <xsl:if test="string($organizationResourceId)">
              "performer": [{
                "reference": "Organization/<xsl:value-of select='$organizationResourceId'/>"
              }],
            </xsl:if>
            "category": [
              {
                "coding": <xsl:value-of select='$categoryXml'/>
              },
              {
                "coding": [{
                  "system": "http://terminology.hl7.org/CodeSystem/observation-category",
                  "code": "social-history"
                }]
              },
              {
                "coding": [{
                  "system": "http://terminology.hl7.org/CodeSystem/observation-category",
                  "code": "survey"
                }]
              }
            ],
            "hasMember": [
              <xsl:variable name="allowedCodes" select="'|71802-3|96778-6|96779-4|88122-7|88123-5|93030-5|96780-2|96782-8|95618-5|95617-7|95616-9|95615-1|95614-4|'" />

              <!-- Gather filtered observations first -->
              <xsl:variable name="filteredObservations">
                <xsl:for-each select="ccda:entryRelationship/ccda:observation">
                  <xsl:variable name="questionCode" select="ccda:code/@code"/>
                  <xsl:if test="string($questionCode) 
                                and contains($allowedCodes, concat('|', $questionCode, '|'))
                                and not(preceding::ccda:observation[ccda:code/@code = $questionCode])
                                and (
                                  (ccda:value/ccda:translation/@code = 'X-SDOH-FLO-1570000066-Patient-unable-to-answer'
                                  or ccda:value/ccda:translation/@code = 'X-SDOH-FLO-1570000066-Patient-declined')
                                  or (
                                    string(ccda:value/@code) != 'UNK'
                                    and string-length(ccda:value/@code) > 0
                                    and string($questionCode) != 'UNK'
                                    and string-length($questionCode) > 0
                                    and string-length(ccda:value/@nullFlavor) = 0
                                    and string-length(ccda:code/@nullFlavor) = 0
                                    and (ccda:code/@codeSystemName = 'LOINC' or ccda:code/@codeSystemName = 'SNOMED' or ccda:code/@codeSystemName = 'SNOMED CT')
                                  )
                                  or (
                                      $questionCode = '95614-4'
                                      and string-length(ccda:value/@value) > 0
                                    )
                                )">
                    <xsl:copy-of select="."/>
                  </xsl:if>
                </xsl:for-each>
              </xsl:variable>

              <!-- Output JSON from filtered set -->
              <xsl:for-each select="exsl:node-set($filteredObservations)/ccda:observation">
                <xsl:variable name="questionCode" select="ccda:code/@code"/>
                <xsl:variable name="encounterEffectiveTime">
                    <xsl:choose>
                        <xsl:when test="ccda:effectiveTime/@value">
                            <xsl:call-template name="formatDateTime">
                                <xsl:with-param name="dateTime" select="ccda:effectiveTime/@value"/>
                            </xsl:call-template>
                        </xsl:when>
                        <xsl:when test="$encounterEffectiveTimeValue">
                            <xsl:value-of select="$encounterEffectiveTimeValue"/>
                        </xsl:when>
                        <xsl:otherwise>
                            <xsl:value-of select="$currentTimestamp"/>
                        </xsl:otherwise>
                    </xsl:choose>
                </xsl:variable>

                <xsl:variable name="encounterEffectiveTimeDigits" select="translate($encounterEffectiveTime, '-:TZ', '')"/> <!-- Remove non-digit characters for ID generation -->

                <xsl:variable name="observationIdSource">
                  <xsl:choose>
                    <!-- Use extension if present and not empty -->
                    <xsl:when test="ccda:id/@extension and normalize-space(ccda:id/@extension) != ''">
                      <xsl:value-of select="concat($questionCode, '-', ccda:id/@extension)"/>
                    </xsl:when>

                    <!-- Fallback: questionCode + encounterEffectiveTime -->
                    <xsl:otherwise>
                      <xsl:value-of
                        select="concat($questionCode, '-', $encounterEffectiveTimeDigits)"/>
                    </xsl:otherwise>
                  </xsl:choose>
                </xsl:variable>

                <xsl:variable name="observationResourceId">
                  <xsl:call-template name="generateFixedLengthResourceId">
                    <xsl:with-param name="prefixString" select="concat($facilityID, '-')"/>
                    <xsl:with-param name="sha256ResourceId" select="$observationIdSource"/>
                  </xsl:call-template>
                </xsl:variable>

                { "reference": "Observation/<xsl:value-of select='$observationResourceId'/>" }<xsl:if test="position() != last()">,</xsl:if>
              </xsl:for-each>
            ]
          },
          "request": {
            "method": "POST",
            "url": "<xsl:value-of select='$baseFhirUrl'/>/Observation/<xsl:value-of select='$grouperObservationResourceId'/>"
          }
        }
      </xsl:for-each>
    </xsl:if>
  </xsl:template>

</xsl:stylesheet>