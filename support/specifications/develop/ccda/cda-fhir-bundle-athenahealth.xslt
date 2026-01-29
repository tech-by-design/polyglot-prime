<?xml version="1.0" encoding="UTF-8"?>
<!-- Version : 0.1.2 -->
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0"
                xmlns:ccda="urn:hl7-org:v3"
                xmlns:fhir="http://hl7.org/fhir"
                xmlns:sdtc="urn:hl7-org:sdtc"
                xmlns:exsl="http://exslt.org/common"
                extension-element-prefixes="exsl"
                exclude-result-prefixes="sdtc ccda exsl">

  <xsl:output method="text"/>
  
  <xsl:param name="currentTimestamp"/>
  <xsl:param name="patientCIN"/>
  <xsl:param name="organizationNPI"/>
  <xsl:param name="organizationTIN"/>
  <xsl:param name="encounterType"/>
  <xsl:param name="facilityID"/>
  <xsl:variable name="patientRoleId" select="//ccda:patientRole/ccda:id[not(@assigningAuthorityName)]/@extension"/>
  <xsl:variable name="patientResourceName" select="concat(//ccda:patientRole/ccda:patient[1]/ccda:name[1]/ccda:family[1], ' ', //ccda:patientRole/ccda:patient[1]/ccda:name[1]/ccda:given[1])"/>
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
  <xsl:param name="grouperObservationResourceSha256Id"/> 
  <xsl:param name="categoryXml"/>
  <xsl:param name="grouperScreeningCode"/>
  <xsl:param name="locationResourceId"/>
  <xsl:param name="componentAnswersXml"/>

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
  <!-- <xsl:variable name="locationMetaProfileUrlFull" select="concat($baseFhirUrl, $locationMetaProfileUrl)"/> -->

  <xsl:variable name="encounterEffectiveTime">
    <xsl:choose>
      <!-- First, try encompassingEncounter -->
      <xsl:when test="/ccda:ClinicalDocument/ccda:componentOf/ccda:encompassingEncounter/ccda:effectiveTime">
        <xsl:choose>
          <xsl:when test="/ccda:ClinicalDocument/ccda:componentOf/ccda:encompassingEncounter/ccda:effectiveTime/@value">
            <xsl:value-of select="/ccda:ClinicalDocument/ccda:componentOf/ccda:encompassingEncounter/ccda:effectiveTime/@value"/>
          </xsl:when>
          <xsl:when test="/ccda:ClinicalDocument/ccda:componentOf/ccda:encompassingEncounter/ccda:effectiveTime/ccda:low/@value">
            <xsl:value-of select="/ccda:ClinicalDocument/ccda:componentOf/ccda:encompassingEncounter/ccda:effectiveTime/ccda:low/@value"/>
          </xsl:when>
        </xsl:choose>
      </xsl:when>

      <!-- Fallback to first encounter entry -->
      <xsl:when test="/ccda:ClinicalDocument/ccda:component/ccda:structuredBody/ccda:component/ccda:section[@ID='encounters']/ccda:entry[1]/ccda:encounter/ccda:effectiveTime">
        <xsl:choose>
          <xsl:when test="/ccda:ClinicalDocument/ccda:component/ccda:structuredBody/ccda:component/ccda:section[@ID='encounters']/ccda:entry[1]/ccda:encounter/ccda:effectiveTime/@value">
            <xsl:value-of select="/ccda:ClinicalDocument/ccda:component/ccda:structuredBody/ccda:component/ccda:section[@ID='encounters']/ccda:entry[1]/ccda:encounter/ccda:effectiveTime/@value"/>
          </xsl:when>
          <xsl:when test="/ccda:ClinicalDocument/ccda:component/ccda:structuredBody/ccda:component/ccda:section[@ID='encounters']/ccda:entry[1]/ccda:encounter/ccda:effectiveTime/ccda:low/@value">
            <xsl:value-of select="/ccda:ClinicalDocument/ccda:component/ccda:structuredBody/ccda:component/ccda:section[@ID='encounters']/ccda:entry[1]/ccda:encounter/ccda:effectiveTime/ccda:low/@value"/>
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
           )" />
  <xsl:variable name="IsGuthrieCCDA"
      select="contains($orgName, 'guthrie') or contains($orgName, 'mohawk')" />

  <!-- Encounter status from the encounters section only for Guthrie-->
  <xsl:variable name="guthrieEncounterStatusFromAct"
      select="/ccda:ClinicalDocument/ccda:component/ccda:structuredBody/ccda:component/ccda:section[@ID='encounters']/ccda:entry[1]/ccda:encounter[1]/ccda:entryRelationship[1]/ccda:act/ccda:statusCode/@code"/>

  <!-- Encounter status from the default path for Guthrie-->
  <xsl:variable name="guthrieEncounterStatusDefault"
      select="/ccda:ClinicalDocument/ccda:componentOf/ccda:encompassingEncounter/ccda:statusCode/@code" />

  <!-- Encounter status from the normal encounters section -->
  <!-- <xsl:variable name="normalEncounterStatus"
      select="/ccda:ClinicalDocument/ccda:component/ccda:structuredBody/ccda:component/ccda:section[@ID='encounters']/ccda:entry[1]/ccda:encounter[1]/ccda:statusCode/@code"/> -->
  <xsl:variable name="normalEncounterStatus">
    <xsl:choose>
      <!-- Case 1: statusCode exists -->
      <xsl:when test="
        string-length(
          normalize-space(
            /ccda:ClinicalDocument/ccda:component/ccda:structuredBody/ccda:component/ccda:section[@ID='encounters']/ccda:entry[1]/ccda:encounter[1]/ccda:statusCode/@code
          )
        ) &gt; 0
      ">
        <xsl:value-of select="/ccda:ClinicalDocument/ccda:component/ccda:structuredBody/ccda:component/ccda:section[@ID='encounters']/ccda:entry[1]/ccda:encounter[1]/ccda:statusCode/@code"/>
      </xsl:when>

      <!-- Case 2: statusCode missing AND moodCode = EVN -->
      <xsl:when test="
        normalize-space(
          /ccda:ClinicalDocument/ccda:component/ccda:structuredBody/ccda:component/ccda:section[@ID='encounters']/ccda:entry[1]/ccda:encounter[1]/@moodCode
        ) = 'EVN'
      ">
        completed
      </xsl:when>

      <!-- Case 3: Otherwise empty -->
      <xsl:otherwise/>    
    </xsl:choose>
  </xsl:variable>

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
    <xsl:variable name="organizationName" select="normalize-space(/ccda:ClinicalDocument/ccda:author/ccda:assignedAuthor/ccda:representedOrganization/ccda:name)"/>


  <xsl:template match="/">
  {
    "resourceType": "Bundle",
    "id": "<xsl:value-of select='$bundleId'/>",
    "meta": {
      "lastUpdated": "<xsl:value-of select='$currentTimestamp'/>",
      "profile": [
        "<xsl:value-of select='$bundleMetaProfileUrlFull'/>"
      ]
    },
    "type": "transaction"
    <xsl:if test="$bundleTimestamp"> 
      , "timestamp": "<xsl:call-template name="formatDateTime">
                          <xsl:with-param name="dateTime" select="$bundleTimestamp"/>
                      </xsl:call-template>"
    </xsl:if>
    , "entry": [
        <xsl:apply-templates select="/ccda:ClinicalDocument/ccda:recordTarget/ccda:patientRole
                                | /ccda:ClinicalDocument/ccda:componentOf/ccda:encompassingEncounter
                                | /ccda:ClinicalDocument/ccda:component/ccda:structuredBody/ccda:component/ccda:section[@ID='sexualOrientation']/ccda:entry/ccda:observation
                                | /ccda:ClinicalDocument/ccda:component/ccda:structuredBody/ccda:component/ccda:section[@ID='observations']/ccda:entry/ccda:observation/ccda:entryRelationship/ccda:observation
                                | /ccda:ClinicalDocument/ccda:authorization/ccda:consent
                                | /ccda:ClinicalDocument/ccda:author
                                | /ccda:ClinicalDocument/ccda:componentOf/ccda:encompassingEncounter/ccda:location/ccda:healthCareFacility/ccda:location
                                | /ccda:ClinicalDocument/ccda:component/ccda:structuredBody/ccda:component/ccda:section[@ID='encounters']/ccda:entry[position()=1]/ccda:encounter/ccda:participant/ccda:participantRole
                            "/>          
            <!-- Call Grouper Observation template -->
            <xsl:call-template name="GrouperObservation"/>
            <!--| /ccda:ClinicalDocument/ccda:component/ccda:structuredBody/ccda:component/ccda:section[ccda:code/@code='29762-2']/ccda:entry/ccda:observation/ccda:entryRelationship/ccda:observation-->                                
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
        , "gender": "<xsl:choose>
                    <xsl:when test="ccda:patient/ccda:administrativeGenderCode/@nullFlavor">
                        <xsl:call-template name="getNullFlavorDisplay">
                            <xsl:with-param name="nullFlavor" select="ccda:patient/ccda:administrativeGenderCode/@nullFlavor"/>
                        </xsl:call-template>
                    </xsl:when>
                    <xsl:when test="ccda:patient/ccda:administrativeGenderCode/@displayName">
                        <xsl:value-of select="translate(ccda:patient/ccda:administrativeGenderCode/@displayName, 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz')"/>
                    </xsl:when>
                    <xsl:when test="ccda:patient/ccda:administrativeGenderCode/@code='M'">male</xsl:when>
                    <xsl:when test="ccda:patient/ccda:administrativeGenderCode/@code='F'">female</xsl:when>
                    <xsl:when test="ccda:patient/ccda:administrativeGenderCode/@code='UN'">other</xsl:when>
                    <xsl:otherwise><xsl:value-of select="ccda:patient/ccda:administrativeGenderCode/@code"/></xsl:otherwise>
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
            , "address": [
                <xsl:for-each select="ccda:addr[not(@nullFlavor)]">
                    {
                        <xsl:if test="@use">
                            "use": "<xsl:choose>
                                <xsl:when test="@use='HP' or @use='H'">home</xsl:when>
                                <xsl:when test="@use='WP'">work</xsl:when>
                                <xsl:when test="@use='TMP'">temp</xsl:when>
                                <xsl:when test="@use='OLD' or @use='BAD'">old</xsl:when>
                                <!-- <xsl:otherwise><xsl:value-of select="@use"/></xsl:otherwise> -->
                                <xsl:otherwise>home</xsl:otherwise>
                            </xsl:choose>"
                        </xsl:if>
                        <xsl:variable name="formattedAddress">
                            <xsl:call-template name="format-address">
                                <xsl:with-param name="addr" select="../ccda:addr"/>
                            </xsl:call-template>
                        </xsl:variable>

                        <xsl:if test="normalize-space($formattedAddress) != ''">
                            , "text": "<xsl:value-of select="$formattedAddress"/>"
                        </xsl:if>
                        <xsl:if test="ccda:streetAddressLine">
                            , "line": [
                                <xsl:for-each select="ccda:streetAddressLine">
                                    "<xsl:value-of select="."/>"
                                    <xsl:if test="position() != last()">, </xsl:if>
                                </xsl:for-each>
                            ]
                        </xsl:if>
                        <xsl:if test="normalize-space(ccda:city)">
                            , "city": "<xsl:value-of select="normalize-space(ccda:city)"/>"
                        </xsl:if>
                        <xsl:if test="normalize-space(ccda:county)">
                            , "district": "<xsl:value-of select="normalize-space(ccda:county)"/>"
                        </xsl:if>
                        <xsl:if test="normalize-space(ccda:state)">
                            , "state": "<xsl:value-of select="normalize-space(ccda:state)"/>"
                        </xsl:if>
                        <xsl:if test="normalize-space(ccda:postalCode)">
                            , "postalCode": "<xsl:value-of select="normalize-space(ccda:postalCode)"/>"
                        </xsl:if>
                        <xsl:if test="normalize-space(ccda:country)">
                            , "country": "<xsl:value-of select="normalize-space(ccda:country)"/>"
                        </xsl:if>
                        <xsl:if test="ccda:useablePeriod">
                            ,"period": {
                                <xsl:if test="string(ccda:useablePeriod/ccda:low/@value)">
                                    "start": "<xsl:call-template name="formatDateTime">
                                                  <xsl:with-param name="dateTime" select="ccda:useablePeriod/ccda:low/@value"/>
                                              </xsl:call-template>"
                                </xsl:if>
                                <xsl:if test="string(ccda:useablePeriod/ccda:high/@value)">
                                    <xsl:if test="string(ccda:useablePeriod/ccda:low/@value)">, </xsl:if>
                                    "end": "<xsl:call-template name="formatDateTime">
                                                <xsl:with-param name="dateTime" select="ccda:useablePeriod/ccda:high/@value"/>
                                            </xsl:call-template>"
                                </xsl:if>
                            }
                        </xsl:if>
                    } <xsl:if test="position() != last()">,</xsl:if>
                </xsl:for-each>
            ]
        </xsl:if>
        <xsl:if test="ccda:telecom[not(@nullFlavor)]">
            , "telecom": [
                <xsl:for-each select="ccda:telecom[not(@nullFlavor)]">
                    {
                        <xsl:if test="@value">
                            "system": "<xsl:choose>
                                    <xsl:when test="contains(@value, 'tel:')">phone</xsl:when>
                                    <xsl:when test="contains(@value, 'mailto:')">email</xsl:when>
                                    <xsl:otherwise>other</xsl:otherwise>
                                </xsl:choose>",
                        </xsl:if>
                        <xsl:if test="@use">
                            "use": "<xsl:choose>
                                <xsl:when test="@use='AS' or @use='DIR' or @use='PUB' or @use='WP'">work</xsl:when>
                                <xsl:when test="@use='BAD'">old</xsl:when>
                                <xsl:when test="@use='H' or @use='HP' or @use='HV'">home</xsl:when>
                                <xsl:when test="@use='MC' or @use='PG'">mobile</xsl:when>
                                <xsl:when test="@use='TMP'">temp</xsl:when>
                                <xsl:otherwise><xsl:value-of select="@use"/></xsl:otherwise>
                            </xsl:choose>",
                        </xsl:if>
                        "value": "<xsl:value-of select="@value"/>"
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
                    <xsl:when test="@code">urn:oid:<xsl:value-of select="@codeSystem"/></xsl:when>
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
                    <xsl:when test="@code">urn:oid:<xsl:value-of select="@codeSystem"/></xsl:when>
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

      <!-- Declare variables for CIN, SSN, and MRN -->
      <xsl:variable name="cinId" select="$patientCIN"/>

      <xsl:variable name="ssnId" select="(ccda:id[@root='2.16.840.1.113883.4.1'] | 
                                          ccda:id[
                                            string-length(@extension) = 11 and 
                                            substring(@extension,4,1) = '-' and 
                                            substring(@extension,7,1) = '-' and 
                                            translate(concat(substring(@extension,1,3), substring(@extension,5,2), substring(@extension,8,4)), '0123456789', '') = ''
                                          ])[1]/@extension"/>

       <!-- MRN (Athena rule): @extension from first ID tag under Patient Role, take the value that appears immediately after "P-" which is not in the SSN format -->       
      <xsl:variable name="mrnId"
        select="substring-after((ccda:id[
            not(
                string-length(substring-after(@extension,'P-')) = 11 and
                substring(substring-after(@extension,'P-'),4,1) = '-' and
                substring(substring-after(@extension,'P-'),7,1) = '-' and
                translate(
                    concat(
                        substring(substring-after(@extension,'P-'),1,3),
                        substring(substring-after(@extension,'P-'),5,2),
                        substring(substring-after(@extension,'P-'),8,4)
                    ),
                    '0123456789',
                    ''
                ) = ''
            )
        ])[1]/@extension,'P-')" />

      <!-- Only output "identifier" array if any variable has value -->
      <xsl:if test="$cinId or $ssnId or $mrnId">
        , "identifier": [
          <!-- CIN -->
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
            }
            <xsl:if test="$ssnId or $mrnId">,</xsl:if>
          </xsl:if>
          <!-- SSN -->
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
            }
            <xsl:if test="$mrnId">,</xsl:if>
          </xsl:if>
          <!-- MRN -->
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
  <xsl:template name="Encounter" match="/ccda:ClinicalDocument/ccda:componentOf/ccda:encompassingEncounter">
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
        <xsl:if test="string(ccda:encounterParticipant/@typeCode) and (string(ccda:encounterParticipant/ccda:assignedEntity/ccda:assignedPerson/ccda:name/ccda:given[1]) or string(ccda:encounterParticipant/ccda:assignedEntity/ccda:assignedPerson/ccda:name/ccda:family))">
        , "participant": [
                {
                  <xsl:if test="string(ccda:encounterParticipant/@typeCode)">
                    "type": [
                        {
                            "coding": [
                                {
                                    "system": "http://terminology.hl7.org/CodeSystem/v3-ParticipationType",
                                    "code": "<xsl:value-of select="ccda:encounterParticipant/@typeCode"/>",
                                    "display": "<xsl:call-template name='mapParticipantType'>
                                                  <xsl:with-param name='typeCode' select='ccda:encounterParticipant/@typeCode'/>
                                              </xsl:call-template>"
                                }
                            ]
                        }
                      ]
                    </xsl:if>
                    <xsl:if test="string(ccda:encounterParticipant/ccda:assignedEntity/ccda:assignedPerson/ccda:name/ccda:given[1])  or string(ccda:encounterParticipant/ccda:assignedEntity/ccda:assignedPerson/ccda:name/ccda:family)">
                    , "individual": {                        
                        "display": "<xsl:value-of select="concat(ccda:encounterParticipant/ccda:assignedEntity/ccda:assignedPerson/ccda:name/ccda:given[1], ' ', ccda:encounterParticipant/ccda:assignedEntity/ccda:assignedPerson/ccda:name/ccda:family)"/>"
                    }
                    </xsl:if>
                }
            ]
        </xsl:if>
        <xsl:if test="string($locationResourceId) or string(ccda:location/ccda:healthCareFacility/ccda:location/ccda:name)">
        , "location": [
            {
                "location": {
                    <xsl:if test="string($locationResourceId)"> 
                      "reference": "Location/<xsl:value-of select="$locationResourceId"/>",
                    </xsl:if>
                    "display": "<xsl:value-of select="normalize-space(ccda:location/ccda:healthCareFacility/ccda:location/ccda:name)"/>"
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
            <!-- <xsl:choose> -->

              <!-- NPI -->
              <!-- <xsl:when test="$organizationNPI"> -->
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
              <!-- </xsl:when> -->
              
              <xsl:if test="$organizationNPI and $organizationTIN">,</xsl:if> <!-- Comma only if both exist -->

              <!-- TAX -->
              <!-- <xsl:when test="$organizationTIN"> -->
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
              <!-- </xsl:when> -->
            <!-- </xsl:choose> -->
          ],
        </xsl:if>
        "name" : "<xsl:choose>
                    <xsl:when test="$organizationName"><xsl:value-of select="$organizationName"/></xsl:when>
                    <xsl:otherwise><xsl:value-of select="normalize-space(ccda:assignedAuthor/ccda:representedOrganization/ccda:name)"/></xsl:otherwise>
                  </xsl:choose>"
        <xsl:if test="ccda:assignedAuthor/ccda:representedOrganization/ccda:telecom[not(@nullFlavor)]">
            , "telecom": [
                <xsl:for-each select="ccda:assignedAuthor/ccda:representedOrganization/ccda:telecom[not(@nullFlavor)]">
                    {
                        <xsl:if test="@value">
                            "system": "<xsl:choose>
                                    <xsl:when test="contains(@value, 'tel:')">phone</xsl:when>
                                    <xsl:when test="contains(@value, 'mailto:')">email</xsl:when>
                                    <xsl:otherwise>other</xsl:otherwise>
                                </xsl:choose>",
                        </xsl:if>
                        <xsl:if test="@use">
                            "use": "<xsl:choose>
                                <xsl:when test="@use='AS' or @use='DIR' or @use='PUB' or @use='WP'">work</xsl:when>
                                <xsl:when test="@use='BAD'">old</xsl:when>
                                <xsl:when test="@use='H' or @use='HP' or @use='HV'">home</xsl:when>
                                <xsl:when test="@use='MC' or @use='PG'">mobile</xsl:when>
                                <xsl:when test="@use='TMP'">temp</xsl:when>
                                <xsl:otherwise><xsl:value-of select="@use"/></xsl:otherwise>
                            </xsl:choose>",
                        </xsl:if>
                        "value": "<xsl:value-of select="@value"/>"
                    }
                    <xsl:if test="position() != last()">,</xsl:if>
                </xsl:for-each>
            ]
        </xsl:if>
        <xsl:if test="ccda:assignedAuthor/ccda:representedOrganization/ccda:addr[not(@nullFlavor)]">
            , "address": [
                <xsl:for-each select="ccda:assignedAuthor/ccda:representedOrganization/ccda:addr[not(@nullFlavor)]">
                    {
                        <xsl:if test="string(@use)">
                            "use": "<xsl:choose>
                                <xsl:when test="@use='HP' or @use='H'">home</xsl:when>
                                <xsl:when test="@use='WP'">work</xsl:when>
                                <xsl:when test="@use='TMP'">temp</xsl:when>
                                <xsl:when test="@use='OLD' or @use='BAD'">old</xsl:when>
                                <xsl:otherwise><xsl:value-of select="@use"/></xsl:otherwise>
                            </xsl:choose>"
                        </xsl:if>
                        <xsl:variable name="formattedAddress">
                            <xsl:call-template name="format-address">
                                <xsl:with-param name="addr" select="../ccda:addr"/>
                            </xsl:call-template>
                        </xsl:variable>

                        <xsl:if test="normalize-space($formattedAddress) != ''">
                            , "text": "<xsl:value-of select="$formattedAddress"/>"
                        </xsl:if>
                        <xsl:if test="ccda:streetAddressLine">
                            , "line": [
                                <xsl:for-each select="ccda:streetAddressLine">
                                    "<xsl:value-of select="."/>"
                                    <xsl:if test="position() != last()">, </xsl:if>
                                </xsl:for-each>
                            ]
                        </xsl:if>
                        <xsl:if test="normalize-space(ccda:city)">
                            , "city": "<xsl:value-of select="normalize-space(ccda:city)"/>"
                        </xsl:if>
                        <xsl:if test="normalize-space(ccda:county)">
                            , "district": "<xsl:value-of select="normalize-space(ccda:county)"/>"
                        </xsl:if>
                        <xsl:if test="normalize-space(ccda:state)">
                            , "state": "<xsl:value-of select="normalize-space(ccda:state)"/>"
                        </xsl:if>
                        <xsl:if test="normalize-space(ccda:postalCode)">
                            , "postalCode": "<xsl:value-of select="normalize-space(ccda:postalCode)"/>"
                        </xsl:if>
                        <xsl:if test="normalize-space(ccda:country)">
                            , "country": "<xsl:value-of select="normalize-space(ccda:country)"/>"
                        </xsl:if>
                        <xsl:if test="ccda:useablePeriod">
                            ,"period": {
                                <xsl:if test="string(ccda:useablePeriod/ccda:low/@value)">
                                    "start": "<xsl:call-template name="formatDateTime">
                                                  <xsl:with-param name="dateTime" select="ccda:useablePeriod/ccda:low/@value"/>
                                              </xsl:call-template>"
                                </xsl:if>
                                <xsl:if test="string(ccda:useablePeriod/ccda:high/@value)">
                                    <xsl:if test="string(ccda:useablePeriod/ccda:low/@value)">, </xsl:if>
                                    "end": "<xsl:call-template name="formatDateTime">
                                                <xsl:with-param name="dateTime" select="ccda:useablePeriod/ccda:high/@value"/>
                                            </xsl:call-template>"
                                </xsl:if>
                            }
                        </xsl:if>
                    } <xsl:if test="position() != last()">,</xsl:if>
                </xsl:for-each>
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
  <xsl:template name="SexualOrientation" match="/ccda:ClinicalDocument/ccda:component/ccda:structuredBody/ccda:component/ccda:section[@ID='sexualOrientation']/ccda:entry/ccda:observation">
    <xsl:if test="string(ccda:code/@code) = '76690-7'">

      <xsl:variable name="resourceUUID">
          <xsl:choose>
            <xsl:when test="normalize-space(ccda:id/@extension)">
              <xsl:value-of select="normalize-space(ccda:id/@extension)"/>
            </xsl:when>
            <xsl:otherwise>
              <xsl:value-of select="$sexualOrientationResourceId"/>
            </xsl:otherwise>
          </xsl:choose>
      </xsl:variable>

      <xsl:variable name="sexualOrientationResourceUUId">
        <xsl:call-template name="generateFixedLengthResourceId">
          <xsl:with-param name="prefixString" select="concat($facilityID, '-')"/>
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
            ]
            <xsl:if test="string(ccda:code/@displayName)">
              ,"text" : "<xsl:value-of select='ccda:code/@displayName'/>"
            </xsl:if>
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
                    "system" : "<xsl:call-template name='mapCodeSystem'>
                                  <xsl:with-param name='oid' select='ccda:value/@codeSystem'/>
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

<!-- Observation Template (Athena: direct entry/observation under section code 29762-2) -->
<xsl:template name="Observation" match="/ccda:ClinicalDocument/ccda:component/ccda:structuredBody/ccda:component/ccda:section[@ID='observations']/ccda:entry/ccda:observation/ccda:entryRelationship/ccda:observation">
  <xsl:variable name="allowedCodes" select="' 71802-3 96778-6 96779-4 88122-7 88123-5 93030-5 96780-2 96782-8 95618-5 95617-7 95616-9 95615-1 95614-4 '" />
  <!-- The observation resource will be generated only for the question codes present in the list specified in 'mapObservationCategoryCodes' -->
  <xsl:variable name="questionCode" select="ccda:code/@code"/>

  <!-- Check if questionCode is in the allowed list -->
  <xsl:if test="contains(concat(' ', $allowedCodes, ' '), concat(' ', $questionCode, ' '))">
    <!-- Generate the observation only if it is the first occurrence of this code -->
    <xsl:if test="not(preceding::ccda:observation[ccda:code/@code = $questionCode])">
      <xsl:if test="(string(ccda:value/ccda:translation/@code) = 'X-SDOH-FLO-1570000066-Patient-unable-to-answer' or string(ccda:value/ccda:translation/@code) = 'X-SDOH-FLO-1570000066-Patient-declined' ) 
              or (
                string(ccda:value/@code) != 'UNK' 
                and string-length(ccda:value/@code) > 0 
                and string(ccda:code/@code) != 'UNK' 
                and string-length(ccda:code/@code) > 0
                and string-length(ccda:value/@nullFlavor) = 0
                and string-length(ccda:code/@nullFlavor) = 0
                and (ccda:code/@codeSystemName = 'LOINC' or ccda:code/@codeSystemName = 'SNOMED' or ccda:code/@codeSystemName = 'SNOMED CT')
              )
              or (string(ccda:code/@code) = '95614-4' and string-length(ccda:value/@value) > 0)
            ">

        <!-- The observation resource will be generated only for the question codes present in the list specified in 'mapObservationCategoryCodes' -->
        <xsl:variable name="categoryCode">
          <xsl:call-template name="mapObservationCategoryCodes">
            <xsl:with-param name="questionCode" select="$questionCode"/>
          </xsl:call-template>
        </xsl:variable>

        <xsl:if test="string($categoryCode)">

          <!-- <xsl:variable name="resourceUUID">
            <xsl:choose>
              <xsl:when test="normalize-space(ccda:id/@extension)">
                <xsl:value-of select="normalize-space(ccda:id/@extension)"/>
              </xsl:when>
              <xsl:otherwise>
                <xsl:value-of select="normalize-space(ccda:id/@root)"/>
              </xsl:otherwise>
            </xsl:choose>
          </xsl:variable> -->
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
                <!-- <xsl:value-of select="ccda:id/@extension"/> -->
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
              <!-- <xsl:with-param name="prefixString" select="$questionCode"/>
              <xsl:with-param name="sha256ResourceId" select="$observationResourceSha256Id"/> -->
              <xsl:with-param name="prefixString" select="concat($facilityID, '-')"/>
              <!-- <xsl:with-param name="sha256ResourceId" select="$resourceUUID"/> -->
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
                            <xsl:with-param name='statusCode' select='ccda:statusCode/@code'/>
                          </xsl:call-template>",
              "category": [
                {
                  "coding": [{
                    "system": "http://hl7.org/fhir/us/sdoh-clinicalcare/CodeSystem/SDOHCC-CodeSystemTemporaryCodes",
                    "code": "<xsl:value-of select='$categoryCode'/>",
                    "display": "<xsl:call-template name='mapSDOHCategoryCodeDisplay'>
                                  <xsl:with-param name='questionCode' select='$questionCode'/>
                                  <xsl:with-param name='categoryCode' select='$categoryCode'/>
                                </xsl:call-template>"
                  }],
                  "text" : "<xsl:call-template name='mapSDOHCategoryText'>
                              <xsl:with-param name='questionCode' select='$questionCode'/>
                              <xsl:with-param name='categoryCode' select='$categoryCode'/>
                            </xsl:call-template>"
                },
                <xsl:choose>
                  <xsl:when test="string($categoryCode) = 'sdoh-category-unspecified'">
                    <xsl:choose>
                      <xsl:when test="ccda:code/@code = '96782-8'">
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
                    "code": "<xsl:value-of select='ccda:code/@code'/>",
                    "display": "<xsl:value-of select='normalize-space(ccda:code/@displayName)'/>"
                  }
                ]
                <xsl:choose>
                  <xsl:when test="ccda:code/@originalText">
                    , "text": "<xsl:value-of select='normalize-space(ccda:code/@originalText)'/>"
                  </xsl:when>
                  <xsl:when test="ccda:code/@displayName">
                    , "text": "<xsl:value-of select='normalize-space(ccda:code/@displayName)'/>"
                  </xsl:when>
                </xsl:choose>
              },
              <xsl:choose>
                <xsl:when test="ccda:value/ccda:translation/@code = 'X-SDOH-FLO-1570000066-Patient-unable-to-answer' 
                              or ccda:value/ccda:translation/@code = 'X-SDOH-FLO-1570000066-Patient-declined'">
                  <xsl:variable name="dataAbsentReasonCode">
                    <xsl:call-template name="getDataAbsentReasonFhirCode">
                      <xsl:with-param name="dataAbsentReason" select="ccda:value/ccda:translation/@code"/>
                    </xsl:call-template>
                  </xsl:variable>
                  "dataAbsentReason": {
                      "coding": [
                          {
                              "system": "http://terminology.hl7.org/CodeSystem/data-absent-reason",
                              "code": "<xsl:value-of select='$dataAbsentReasonCode'/>",
                              "display": "<xsl:call-template name='getDataAbsentReasonFhirDisplay'>
                                            <xsl:with-param name='dataAbsentReasonCode' select='$dataAbsentReasonCode'/>
                                          </xsl:call-template>"
                          }
                      ]
                  },
                </xsl:when>
                <!-- component for question code '96778-6' -->
                <xsl:when test="string(ccda:code/@code) = '96778-6'">
                  "component": [
                        {
                            "code": {
                              "coding": [
                                {
                                  "system": "http://loinc.org",
                                  "code": "<xsl:value-of select='ccda:code/@code'/>",
                                  "display": "<xsl:value-of select='normalize-space(ccda:code/@displayName)'/>"
                                }
                              ]
                              <xsl:choose>
                                <xsl:when test="ccda:code/@originalText">
                                  , "text": "<xsl:value-of select='normalize-space(ccda:code/@originalText)'/>"
                                </xsl:when>
                                <xsl:when test="ccda:code/@displayName">
                                  , "text": "<xsl:value-of select='normalize-space(ccda:code/@displayName)'/>"
                                </xsl:when>
                              </xsl:choose>
                            },
                            "valueCodeableConcept": {
                              "coding": <xsl:value-of select='$componentAnswersXml'/>
                            }
                        }
                  ],
                </xsl:when>

                <!-- Total Safety Score 95614-4 -->
                <xsl:when test="string(ccda:code/@code) = '95614-4'">
                  "valueCodeableConcept" : {
                    "coding": [{
                      "system": "http://unitsofmeasure.org",
                      "display": "{Number}"
                    }],
                    "text": "<xsl:value-of select='ccda:value/@value'/>"
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
                        <!-- <xsl:variable name="resourceUUID">
                          <xsl:choose>
                            <xsl:when test="normalize-space(ccda:id/@extension)">
                              <xsl:value-of select="normalize-space(ccda:id/@extension)"/>
                            </xsl:when>
                            <xsl:otherwise>
                              <xsl:value-of select="normalize-space(ccda:id/@root)"/>
                            </xsl:otherwise>
                          </xsl:choose>
                        </xsl:variable> -->
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
                              <!-- <xsl:value-of select="ccda:id/@extension"/> -->
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

                <!-- Default valueCodeableConcept -->
                <xsl:otherwise>
                    "valueCodeableConcept" : {
                      "coding": [{
                        "system": "http://loinc.org",
                        "code": "<xsl:value-of select='ccda:value/@code'/>",
                        "display": "<xsl:value-of select='normalize-space(ccda:value/@displayName)'/>"
                      }]
                      <xsl:choose>
                        <xsl:when test="ccda:value/@originalText">
                          , "text": "<xsl:value-of select='normalize-space(ccda:value/@originalText)'/>"
                        </xsl:when>
                        <xsl:when test="ccda:value/@displayName">
                          , "text": "<xsl:value-of select='normalize-space(ccda:value/@displayName)'/>"
                        </xsl:when>
                      </xsl:choose>
                    },
                </xsl:otherwise>
              </xsl:choose>

              "subject": {
                "reference": "Patient/<xsl:value-of select='$patientResourceId'/>",
                "display": "<xsl:value-of select='$patientResourceName'/>"
              }
              <xsl:if test="normalize-space($encounterResourceId) != '' and $encounterResourceId != 'null'">
              , "encounter": {
                  "reference": "Encounter/<xsl:value-of select='$encounterResourceId'/>"
                }
              </xsl:if>

              <!-- effectiveDateTime block -->
              <!-- <xsl:if test="ccda:effectiveTime/@value or $encounterEffectiveTimeValue or $currentTimestamp">
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
              </xsl:if> -->
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
    </xsl:if>
  </xsl:if>
</xsl:template>

  

  <xsl:template name="formatDateTime">
      <xsl:param name="dateTime"/>
      <xsl:choose>
          <!-- If format is YYYYMMDDHHMMSS -->
          <xsl:when test="string-length($dateTime) >= 14">
              <xsl:value-of select="concat(
                  substring($dateTime, 1, 4), '-', 
                  substring($dateTime, 5, 2), '-', 
                  substring($dateTime, 7, 2), 'T', 
                  substring($dateTime, 9, 2), ':', 
                  substring($dateTime, 11, 2), ':', 
                  substring($dateTime, 13, 2),
                  'Z'
              )"/>
          </xsl:when>
          <!-- If format is YYYYMMDD -->
          <xsl:when test="string-length($dateTime) >= 8">
              <xsl:value-of select="concat(
                  substring($dateTime, 1, 4), '-', 
                  substring($dateTime, 5, 2), '-', 
                  substring($dateTime, 7, 2), 'T00:00:00Z'
              )"/>
          </xsl:when>
          <!-- If format is unknown, return as is -->
          <xsl:otherwise>
              <xsl:value-of select="$dateTime"/>
          </xsl:otherwise>
      </xsl:choose>
  </xsl:template>

    <xsl:template name="getNullFlavorDisplay">
    <xsl:param name="nullFlavor"/>
    <xsl:choose>
        <xsl:when test="$nullFlavor = 'NI'">unknown</xsl:when>
        <xsl:when test="$nullFlavor = 'UNK'">unknown</xsl:when>
        <xsl:when test="$nullFlavor = 'ASKU'">asked-unknown</xsl:when>
        <xsl:when test="$nullFlavor = 'NASK'">not-asked</xsl:when>
        <xsl:when test="$nullFlavor = 'NAV'">temp-unknown</xsl:when>
        <xsl:when test="$nullFlavor = 'OTH'">unsupported</xsl:when>
        <xsl:when test="$nullFlavor = 'MSK'">masked</xsl:when>
        <xsl:when test="$nullFlavor = 'NA'">not-applicable</xsl:when>
        <xsl:otherwise>unknown</xsl:otherwise>
    </xsl:choose>
</xsl:template>

<xsl:template name="getRaceEthnicityNullFlavorDisplay">
  <xsl:param name="nullFlavor"/>
  <xsl:choose>
      <xsl:when test="$nullFlavor = 'UNK'">unknown</xsl:when>
      <xsl:when test="$nullFlavor = 'ASKU'">asked but unknown</xsl:when>
      <xsl:when test="$nullFlavor = 'OTH'">other</xsl:when>
      <xsl:otherwise>unknown</xsl:otherwise>
  </xsl:choose>
</xsl:template>

<xsl:template name="mapObservationStatus">
    <xsl:param name="statusCode"/>
        
    <!-- Convert value to lowercase for case-insensitive matching -->
    <xsl:variable name="cleanCode"
        select="translate(normalize-space(string($statusCode)),
                          'ABCDEFGHIJKLMNOPQRSTUVWXYZ',
                          'abcdefghijklmnopqrstuvwxyz')" />

    <xsl:choose>
        <xsl:when test="$cleanCode = 'completed'">final</xsl:when>
        <xsl:when test="$cleanCode = 'final'">final</xsl:when>
        <xsl:when test="$cleanCode = 'active'">preliminary</xsl:when>
        <xsl:when test="$cleanCode = 'aborted'">cancelled</xsl:when>
        <xsl:when test="$cleanCode = 'cancelled'">cancelled</xsl:when>
        <xsl:when test="$cleanCode = 'held'">registered</xsl:when>
        <xsl:when test="$cleanCode = 'suspended'">registered</xsl:when>
        <xsl:when test="$cleanCode = 'nullified'">entered-in-error</xsl:when>
        <xsl:otherwise>unknown</xsl:otherwise>
    </xsl:choose>
</xsl:template>

<xsl:template name="mapEncounterStatus"> 
    <xsl:param name="statusCode"/>
    
    <!-- Convert value to lowercase for case-insensitive matching -->
    <xsl:variable name="cleanCode"
        select="translate(normalize-space(string($statusCode)),
                          'ABCDEFGHIJKLMNOPQRSTUVWXYZ',
                          'abcdefghijklmnopqrstuvwxyz')" />

    <xsl:choose>
        <xsl:when test="$cleanCode = 'completed' or 
                        $cleanCode = 'normal'">finished</xsl:when>
        <xsl:when test="$cleanCode = 'active'">in-progress</xsl:when>
        <xsl:when test="$cleanCode = 'cancelled' or 
                        $cleanCode = 'aborted'">cancelled</xsl:when>
        <xsl:when test="$cleanCode = 'suspended'">on-hold</xsl:when>
        <xsl:when test="$cleanCode = 'nullified' or 
                        $cleanCode = 'corrected'">entered-in-error</xsl:when>
        <xsl:when test="$cleanCode = 'new'">planned</xsl:when>
        <xsl:otherwise>unknown</xsl:otherwise>
    </xsl:choose>
</xsl:template>

<xsl:template name="mapParticipantType">
    <xsl:param name="typeCode"/>
            
    <!-- Convert value to uppercase for case-insensitive matching -->
    <xsl:variable name="cleanCode"
        select="translate(normalize-space(string($typeCode)),
                          'abcdefghijklmnopqrstuvwxyz',
                          'ABCDEFGHIJKLMNOPQRSTUVWXYZ')" />

    <xsl:choose>
        <xsl:when test="$cleanCode = 'ADM'">admitter</xsl:when>
        <xsl:when test="$cleanCode = 'ATND'">attender</xsl:when>
        <xsl:when test="$cleanCode = 'CALLBCK'">callback contact</xsl:when>
        <xsl:when test="$cleanCode = 'CON'">consultant</xsl:when>
        <xsl:when test="$cleanCode = 'DIS'">discharger</xsl:when>
        <xsl:when test="$cleanCode = 'ESC'">escort</xsl:when>
        <xsl:when test="$cleanCode = 'REF'">referrer</xsl:when>
        <xsl:when test="$cleanCode = 'SPRF'">secondary performer</xsl:when>
        <xsl:when test="$cleanCode = 'PPRF'">primary performer</xsl:when>
        <xsl:when test="$cleanCode = 'PART'">Participation</xsl:when>
        <xsl:when test="$cleanCode = 'TRANSLATOR'">Translator</xsl:when>
        <xsl:when test="$cleanCode = 'EMERGEMCY'">Emergency</xsl:when>
        <xsl:when test="$cleanCode = 'AUT'">author (originator)</xsl:when>
        <xsl:when test="$cleanCode = 'INF'">informant</xsl:when>
        <xsl:when test="$cleanCode = 'TRANS'">Transcriber</xsl:when>
        <xsl:when test="$cleanCode = 'ENT'">data entry person</xsl:when>
        <xsl:when test="$cleanCode = 'WIT'">witness</xsl:when>
        <xsl:when test="$cleanCode = 'NOTARY'">notary</xsl:when>
        <xsl:when test="$cleanCode = 'CST'">custodian</xsl:when>
        <xsl:when test="$cleanCode = 'DIR'">direct target</xsl:when>
        <xsl:when test="$cleanCode = 'ALY'">analyte</xsl:when>
        <xsl:when test="$cleanCode = 'BBY'">baby</xsl:when>
        <xsl:when test="$cleanCode = 'CAT'">catalyst</xsl:when>
        <xsl:when test="$cleanCode = 'CSM'">consumable</xsl:when>
        <xsl:when test="$cleanCode = 'TPA'">therapeutic agent</xsl:when>
        <xsl:when test="$cleanCode = 'DEV'">device</xsl:when>
        <xsl:when test="$cleanCode = 'NRD'">non-reuseable device</xsl:when>
        <xsl:when test="$cleanCode = 'RDV'">reusable device</xsl:when>
        <xsl:when test="$cleanCode = 'DON'">donor</xsl:when>
        <xsl:when test="$cleanCode = 'EXPAGNT'">ExposureAgent</xsl:when>
        <xsl:when test="$cleanCode = 'EXPART'">ExposureParticipation</xsl:when>
        <xsl:when test="$cleanCode = 'EXPTRGT'">ExposureTarget</xsl:when>
        <xsl:when test="$cleanCode = 'EXSRC'">ExposureSource</xsl:when>
        <xsl:when test="$cleanCode = 'PRD'">product</xsl:when>
        <xsl:when test="$cleanCode = 'SBJ'">subject</xsl:when>
        <xsl:when test="$cleanCode = 'SPC'">specimen</xsl:when>
        <xsl:when test="$cleanCode = 'IND'">indirect target</xsl:when>
        <xsl:when test="$cleanCode = 'BEN'">beneficiary</xsl:when>
        <xsl:when test="$cleanCode = 'CAGNT'">causative agent</xsl:when>
        <xsl:when test="$cleanCode = 'COV'">coverage target</xsl:when>
        <xsl:when test="$cleanCode = 'GUAR'">guarantor party</xsl:when>
        <xsl:when test="$cleanCode = 'HLD'">holder</xsl:when>
        <xsl:when test="$cleanCode = 'RCT'">record target</xsl:when>
        <xsl:when test="$cleanCode = 'RCV'">receiver</xsl:when>
        <xsl:when test="$cleanCode = 'IRCP'">information recipient</xsl:when>
        <xsl:when test="$cleanCode = 'NOT'">urgent notification contact</xsl:when>
        <xsl:when test="$cleanCode = 'PRCP'">primary information recipient</xsl:when>
        <xsl:when test="$cleanCode = 'REFB'">Referred By</xsl:when>
        <xsl:when test="$cleanCode = 'REFT'">Referred to</xsl:when>
        <xsl:when test="$cleanCode = 'TRC'">tracker</xsl:when>
        <xsl:when test="$cleanCode = 'LOC'">location</xsl:when>
        <xsl:when test="$cleanCode = 'DST'">destination</xsl:when>
        <xsl:when test="$cleanCode = 'ELOC'">entry location</xsl:when>
        <xsl:when test="$cleanCode = 'ORG'">origin</xsl:when>
        <xsl:when test="$cleanCode = 'RML'">remote</xsl:when>
        <xsl:when test="$cleanCode = 'VIA'">via</xsl:when>
        <xsl:when test="$cleanCode = 'PRF'">performer</xsl:when>
        <xsl:when test="$cleanCode = 'DIST'">distributor</xsl:when>
        <xsl:when test="$cleanCode = 'PPRF'">primary performer</xsl:when>
        <xsl:when test="$cleanCode = 'SPRF'">secondary performer</xsl:when>
        <xsl:when test="$cleanCode = 'RESP'">responsible party</xsl:when>
        <xsl:when test="$cleanCode = 'VRF'">verifier</xsl:when>
        <xsl:when test="$cleanCode = 'AUTHEN'">authenticator</xsl:when>
        <xsl:when test="$cleanCode = 'LA'">legal authenticator</xsl:when>
        <xsl:otherwise>Unknown</xsl:otherwise>
    </xsl:choose>
</xsl:template>

<xsl:template name="mapCodeSystem">
  <xsl:param name="oid"/>
  <xsl:choose>
    <!-- SNOMED CT -->
    <xsl:when test="$oid = '2.16.840.1.113883.6.96'">
      <xsl:text>http://snomed.info/sct</xsl:text>
    </xsl:when>
    <!-- LOINC -->
    <xsl:when test="$oid = '2.16.840.1.113883.6.1'">
      <xsl:text>http://loinc.org</xsl:text>
    </xsl:when>
    <!-- CPT -->
    <xsl:when test="$oid = '2.16.840.1.113883.6.12'">
      <xsl:text>http://www.ama-assn.org/go/cpt</xsl:text>
    </xsl:when>
    <!-- HCPCS -->
    <!-- <xsl:when test="$oid = '2.16.840.1.113883.6.285'">
      <xsl:text>http://www.cms.gov/Medicare/Coding/HCPCSReleaseCodeSets</xsl:text> 
    </xsl:when> -->
    <!-- ICD-10-CM -->
    <xsl:when test="$oid = '2.16.840.1.113883.6.90'">
      <xsl:text>http://hl7.org/fhir/sid/icd-10-cm</xsl:text>
    </xsl:when>
    <!-- Default: urn:oid fallback -->
    <xsl:otherwise>
      <xsl:text>urn:oid:</xsl:text>
      <xsl:value-of select="$oid"/>
    </xsl:otherwise>
  </xsl:choose>
</xsl:template>

<xsl:template name="mapMaritalStatus">
    <xsl:param name="statusCode"/>
        
    <!-- Convert value to uppercase for case-insensitive matching -->
    <xsl:variable name="cleanCode"
        select="translate(normalize-space(string($statusCode)),
                          'abcdefghijklmnopqrstuvwxyz',
                          'ABCDEFGHIJKLMNOPQRSTUVWXYZ')" />
    <xsl:choose>
        <xsl:when test="$cleanCode = 'M'">married</xsl:when>
        <xsl:when test="$cleanCode = 'S'">Never Married</xsl:when>
        <xsl:when test="$cleanCode = 'A'">Annulled</xsl:when>
        <xsl:when test="$cleanCode = 'D'">Divorced</xsl:when>
        <xsl:when test="$cleanCode = 'I'">Interlocutory</xsl:when>
        <xsl:when test="$cleanCode = 'L'">Legally Separated</xsl:when>
        <xsl:when test="$cleanCode = 'C'">Common Law</xsl:when>
        <xsl:when test="$cleanCode = 'P'">Polygamous</xsl:when>
        <xsl:when test="$cleanCode = 'T'">Domestic partner</xsl:when>
        <xsl:when test="$cleanCode = 'U'">unmarried</xsl:when>
        <xsl:when test="$cleanCode = 'W'">Widowed</xsl:when>
        <xsl:otherwise>unknown</xsl:otherwise>
    </xsl:choose>
</xsl:template>

<xsl:template name="mapMaritalStatusCode">
    <xsl:param name="statusCode"/>
            
    <!-- Convert value to uppercase for case-insensitive matching -->
    <xsl:variable name="cleanCode"
        select="translate(normalize-space(string($statusCode)),
                          'abcdefghijklmnopqrstuvwxyz',
                          'ABCDEFGHIJKLMNOPQRSTUVWXYZ')" />
    <xsl:choose>
        <xsl:when test='$cleanCode = "M" or
                  $cleanCode = "S" or
                  $cleanCode = "A" or
                  $cleanCode = "D" or
                  $cleanCode = "I" or
                  $cleanCode = "L" or
                  $cleanCode = "C" or
                  $cleanCode = "P" or
                  $cleanCode = "T" or
                  $cleanCode = "U" or
                  $cleanCode = "W"'>
          <xsl:value-of select='$cleanCode'/>
        </xsl:when>
        <xsl:otherwise/>
    </xsl:choose>
</xsl:template>

<xsl:template name="mapAdministrativeGenderCode">
  <xsl:param name="genderCode"/>
              
  <!-- Convert value to uppercase for case-insensitive matching -->
  <xsl:variable name="cleanCode"
      select="translate(normalize-space(string($genderCode)),
                        'abcdefghijklmnopqrstuvwxyz',
                        'ABCDEFGHIJKLMNOPQRSTUVWXYZ')" />
  <xsl:choose>
    <xsl:when test="$cleanCode = 'M' or $cleanCode = 'MALE'">M</xsl:when>
    <xsl:when test="$cleanCode = 'F' or $cleanCode = 'FEMALE'">F</xsl:when>
    <xsl:otherwise>UNK</xsl:otherwise>
  </xsl:choose>
</xsl:template>

<xsl:template name="mapObservationCategoryCodes">
  <xsl:param name="questionCode"/>
  <xsl:choose>
    <xsl:when test="$questionCode = '71802-3'">housing-instability</xsl:when>
    <xsl:when test="$questionCode = '96778-6'">inadequate-housing</xsl:when>
    <xsl:when test="$questionCode = '96779-4'">utility-insecurity</xsl:when>
    <xsl:when test="$questionCode = '88122-7' or $questionCode = '88123-5'">food-insecurity</xsl:when>
    <xsl:when test="$questionCode = '93030-5'">transportation-insecurity</xsl:when>
    <xsl:when test="$questionCode = '96780-2'">employment-status</xsl:when>
    <xsl:when test="$questionCode = '96782-8' or 
                    $questionCode = '95618-5' or 
                    $questionCode = '95617-7' or 
                    $questionCode = '95616-9' or 
                    $questionCode = '95615-1' or 
                    $questionCode = '95614-4'">sdoh-category-unspecified</xsl:when>
    <xsl:otherwise/>
  </xsl:choose>
</xsl:template>

<xsl:template name="mapSDOHCategoryCodeDisplay">
  <xsl:param name="questionCode"/>
  <xsl:param name="categoryCode"/>
  <xsl:choose>
    <xsl:when test="$questionCode = '71802-3'">Housing Instability</xsl:when>
    <xsl:when test="$questionCode = '96778-6'">Inadequate Housing</xsl:when>
    <xsl:when test="$questionCode = '96779-4'">Utility Insecurity</xsl:when>
    <xsl:when test="$questionCode = '88122-7' or $questionCode = '88123-5'">Food Insecurity</xsl:when>
    <xsl:when test="$questionCode = '93030-5'">Transportation Insecurity</xsl:when>
    <xsl:when test="$questionCode = '96780-2'">Employment Status</xsl:when>
    <xsl:when test="$questionCode = '96782-8' or
                    $questionCode = '95618-5' or 
                    $questionCode = '95617-7' or 
                    $questionCode = '95616-9' or 
                    $questionCode = '95615-1' or 
                    $questionCode = '95614-4'">SDOH Category Unspecified</xsl:when>
    <xsl:otherwise>
      <xsl:value-of select="$categoryCode"/>
    </xsl:otherwise>
  </xsl:choose>
</xsl:template>

<xsl:template name="mapSDOHCategoryText">
  <xsl:param name="questionCode"/>
  <xsl:param name="categoryCode"/>
  <xsl:choose>
    <xsl:when test="$questionCode = '71802-3'">Housing Instability</xsl:when>
    <xsl:when test="$questionCode = '96778-6'">Inadequate Housing</xsl:when>
    <xsl:when test="$questionCode = '96779-4'">Utility Insecurity</xsl:when>
    <xsl:when test="$questionCode = '88122-7' or $questionCode = '88123-5'">Food Insecurity</xsl:when>
    <xsl:when test="$questionCode = '93030-5'">Transportation Insecurity</xsl:when>
    <xsl:when test="$questionCode = '96780-2'">Employment Status</xsl:when>
    <xsl:when test="$questionCode = '96782-8'">Education/Training</xsl:when>
    <xsl:when test="$questionCode = '95618-5' or 
                    $questionCode = '95617-7' or 
                    $questionCode = '95616-9' or 
                    $questionCode = '95615-1' or 
                    $questionCode = '95614-4'">Interpersonal Safety</xsl:when>
    <xsl:otherwise>
      <xsl:value-of select="$categoryCode"/>
    </xsl:otherwise>
  </xsl:choose>
</xsl:template>

<!-- Reusable ID generator template -->
<xsl:template name="generateFixedLengthResourceId">
  <xsl:param name="prefixString"/>
  <xsl:param name="sha256ResourceId"/>

  <xsl:variable name="trimmedHashId" select="substring(concat($prefixString, $sha256ResourceId), 1, 64)"/>
  <xsl:variable name="resourceUId" select="$trimmedHashId"/>
  <xsl:copy-of select="$resourceUId"/>
</xsl:template>

<xsl:template name="generateNameJson">
    <xsl:param name="selectedName"/>
    {
        <!-- middle name extension -->
        <xsl:if test="string($selectedName/ccda:given)">
            "extension": [{
                "url": "<xsl:value-of select='$baseFhirUrl'/>/StructureDefinition/middle-name",
                "valueString": "<xsl:value-of select="$selectedName/ccda:given"/>"
            }]
        </xsl:if>

        <!-- use -->
        <xsl:if test="$selectedName/@use">
            <xsl:if test="string($selectedName/ccda:given)">, </xsl:if>
            "use": "<xsl:choose>
                        <xsl:when test="$selectedName/@use='L'">official</xsl:when>
                        <xsl:when test="$selectedName/@use='P'">usual</xsl:when>
                        <xsl:otherwise>
                            <xsl:value-of select="$selectedName/@use"/>
                        </xsl:otherwise>
                    </xsl:choose>"
        </xsl:if>

        <!-- prefix -->
        <xsl:if test="string($selectedName/ccda:prefix)">
            <xsl:if test="string($selectedName/ccda:given) or $selectedName/@use">, </xsl:if>
            "prefix": ["<xsl:value-of select='$selectedName/ccda:prefix'/>"]
        </xsl:if>

        <!-- given -->
        <xsl:if test="string($selectedName/ccda:given)">
            <xsl:if test="$selectedName/@use or string($selectedName/ccda:prefix)">, </xsl:if>
            "given": ["<xsl:value-of select='$selectedName/ccda:given'/>"]
        </xsl:if>

        <!-- family -->
        <xsl:if test="string($selectedName/ccda:family)">
            <xsl:if test="
                $selectedName/@use or
                string($selectedName/ccda:prefix) or
                string($selectedName/ccda:given)
            ">, </xsl:if>
            "family": "<xsl:value-of select='$selectedName/ccda:family'/>"
        </xsl:if>

        <!-- suffix -->
        <xsl:if test="string($selectedName/ccda:suffix)">
            <xsl:if test="
                $selectedName/@use or
                string($selectedName/ccda:prefix) or
                string($selectedName/ccda:given) or
                string($selectedName/ccda:family)
            ">, </xsl:if>
            "suffix": ["<xsl:value-of select='$selectedName/ccda:suffix'/>"]
        </xsl:if>

        <!-- period -->
        <xsl:if test="
            ($selectedName/ccda:validTime/ccda:low and
            not($selectedName/ccda:validTime/ccda:low/@nullFlavor = 'UNK' or
                $selectedName/ccda:validTime/ccda:low/@nullFlavor = 'NA'))
            or
            ($selectedName/ccda:validTime/ccda:high and
            not($selectedName/ccda:validTime/ccda:high/@nullFlavor = 'UNK' or
                $selectedName/ccda:validTime/ccda:high/@nullFlavor = 'NA'))
        ">
            <xsl:if test="
                string($selectedName/ccda:given) or
                string($selectedName/ccda:prefix) or
                string($selectedName/ccda:family) or
                string($selectedName/ccda:suffix) or
                $selectedName/@use
            ">, </xsl:if>

            "period": {
                <xsl:if test="
                    $selectedName/ccda:validTime/ccda:low and
                    not($selectedName/ccda:validTime/ccda:low/@nullFlavor = 'UNK' or
                        $selectedName/ccda:validTime/ccda:low/@nullFlavor = 'NA')
                ">
                    "start": "<xsl:call-template name="formatDateTime">
                                  <xsl:with-param name="dateTime"
                                      select="$selectedName/ccda:validTime/ccda:low/@value"/>
                              </xsl:call-template>"
                    <xsl:if test="
                        $selectedName/ccda:validTime/ccda:high and
                        not($selectedName/ccda:validTime/ccda:high/@nullFlavor = 'UNK' or
                            $selectedName/ccda:validTime/ccda:high/@nullFlavor = 'NA')
                    ">,</xsl:if>
                </xsl:if>

                <xsl:if test="
                    $selectedName/ccda:validTime/ccda:high and
                    not($selectedName/ccda:validTime/ccda:high/@nullFlavor = 'UNK' or
                        $selectedName/ccda:validTime/ccda:high/@nullFlavor = 'NA')
                ">
                    "end": "<xsl:call-template name="formatDateTime">
                                <xsl:with-param name="dateTime"
                                    select="$selectedName/ccda:validTime/ccda:high/@value"/>
                            </xsl:call-template>"
                </xsl:if>
            }
        </xsl:if>
    }
</xsl:template>

<xsl:template name="getEncounterTypeDisplay">
  <xsl:param name="encounterType"/>
  <xsl:choose>
    <xsl:when test="$encounterType = '405672008'">Direct questioning (procedure)</xsl:when>
    <xsl:when test="$encounterType = '23918007'">History taking, self-administered, by computer terminal</xsl:when>
    <xsl:otherwise/>
  </xsl:choose>
</xsl:template>

<xsl:template name="getDataAbsentReasonFhirCode">
  <xsl:param name="dataAbsentReason"/>
  <xsl:choose>
    <xsl:when test="$dataAbsentReason = 'X-SDOH-FLO-1570000066-Patient-unable-to-answer'">asked-unknown</xsl:when>
    <xsl:when test="$dataAbsentReason = 'X-SDOH-FLO-1570000066-Patient-declined'">asked-declined</xsl:when>
    <xsl:otherwise/>
  </xsl:choose>
</xsl:template>

<xsl:template name="getDataAbsentReasonFhirDisplay">
  <xsl:param name="dataAbsentReasonCode"/>
  <xsl:choose>
    <xsl:when test="$dataAbsentReasonCode = 'asked-unknown'">Asked But Unknown</xsl:when>
    <xsl:when test="$dataAbsentReasonCode = 'asked-declined'">Asked But Declined</xsl:when>
    <xsl:otherwise/>
  </xsl:choose>
</xsl:template>

<!-- Grouper Observation Template -->
  <xsl:template name="GrouperObservation">
    <xsl:variable name="grouperObs" select="/ccda:ClinicalDocument/ccda:component/ccda:structuredBody/ccda:component/ccda:section[@ID='observations']/ccda:entry/ccda:observation"/>
    <xsl:variable name="grouperScreening" select="/ccda:ClinicalDocument/ccda:component/ccda:structuredBody/ccda:component/ccda:section[@ID='observations']/ccda:entry/ccda:observation[1]"/>
    <xsl:if test="($grouperObs != '') and (normalize-space($categoryXml) != '[]')">

        <xsl:variable name="grouperObservationResourceId">
          <xsl:call-template name="generateFixedLengthResourceId">
            <!-- <xsl:with-param name="prefixString" select="$grouperScreeningCode"/>-->
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
                          <xsl:with-param name='statusCode' select='$grouperScreening/ccda:statusCode/@code'/>
                        </xsl:call-template>",
            "code": {
              "coding": [
                {
                  "system": "<xsl:call-template name="mapScreeningCodeSystem">
                                <xsl:with-param name="screeningCode" select="$grouperScreeningCode"/>
                              </xsl:call-template>",
                  "code": "<xsl:value-of select='$grouperScreeningCode'/>",
                  "display": "<xsl:call-template name="mapScreeningCodeDisplay">
                                <xsl:with-param name="screeningCode" select="$grouperScreeningCode"/>
                              </xsl:call-template>"
                }
                <xsl:if test="starts-with($grouperScreeningCode, 'NYS')">
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
                                      <xsl:when test='$grouperScreening/ccda:effectiveTime/@value'>
                                        <xsl:call-template name='formatDateTime'>
                                          <xsl:with-param name='dateTime' select='$grouperScreening/ccda:effectiveTime/@value'/>
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
                <xsl:for-each select="$grouperObs/ccda:entryRelationship/ccda:observation">
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

                <!-- <xsl:variable name="resourceUUID">
                  <xsl:choose>
                    <xsl:when test="normalize-space(ccda:id/@extension)">
                      <xsl:value-of select="normalize-space(ccda:id/@extension)"/>
                    </xsl:when>
                    <xsl:otherwise>
                      <xsl:value-of select="normalize-space(ccda:id/@root)"/>
                    </xsl:otherwise>
                  </xsl:choose>
                </xsl:variable> -->

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
                      <!-- <xsl:value-of select="ccda:id/@extension"/> -->
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
                    <!-- <xsl:with-param name="sha256ResourceId" select="$resourceUUID"/> -->
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
    </xsl:if>
  </xsl:template>

  <!-- Location Template from encompassingEncounter -->
  <xsl:template name="LocationResource" match="/ccda:ClinicalDocument/ccda:componentOf/ccda:encompassingEncounter/ccda:location/ccda:healthCareFacility/ccda:location">
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
        <xsl:if test="normalize-space(ccda:name)">
          ,"name": "<xsl:value-of select="normalize-space(ccda:name)"/>"
        </xsl:if>

        <xsl:if test="ccda:addr[not(@nullFlavor)]">
            , "address": 
                    {
                        <xsl:if test="string(ccda:addr/@use)">
                            "use": "<xsl:choose>
                                <xsl:when test="ccda:addr/@use='HP' or ccda:addr/@use='H'">home</xsl:when>
                                <xsl:when test="ccda:addr/@use='WP'">work</xsl:when>
                                <xsl:when test="ccda:addr/@use='TMP'">temp</xsl:when>
                                <xsl:when test="ccda:addr/@use='OLD' or ccda:addr/@use='BAD'">old</xsl:when>
                                <xsl:otherwise><xsl:value-of select="ccda:addr/@use"/></xsl:otherwise>
                            </xsl:choose>"
                        </xsl:if>
                        <xsl:variable name="formattedAddress">
                            <xsl:call-template name="format-address">
                                <xsl:with-param name="addr" select="ccda:addr"/>
                            </xsl:call-template>
                        </xsl:variable>

                        <xsl:if test="normalize-space($formattedAddress) != ''">
                            , "text": "<xsl:value-of select="$formattedAddress"/>"
                        </xsl:if>
                        <xsl:if test="ccda:addr/ccda:streetAddressLine">
                            , "line": [
                                <xsl:for-each select="ccda:addr/ccda:streetAddressLine">
                                    "<xsl:value-of select="."/>"
                                    <xsl:if test="position() != last()">, </xsl:if>
                                </xsl:for-each>
                            ]
                        </xsl:if>
                        <xsl:if test="normalize-space(ccda:addr/ccda:city)">
                            , "city": "<xsl:value-of select="normalize-space(ccda:addr/ccda:city)"/>"
                        </xsl:if>
                        <xsl:if test="normalize-space(ccda:addr/ccda:county)">
                            , "district": "<xsl:value-of select="normalize-space(ccda:addr/ccda:county)"/>"
                        </xsl:if>
                        <xsl:if test="normalize-space(ccda:addr/ccda:state)">
                            , "state": "<xsl:value-of select="normalize-space(ccda:addr/ccda:state)"/>"
                        </xsl:if>
                        <xsl:if test="normalize-space(ccda:addr/ccda:postalCode)">
                            , "postalCode": "<xsl:value-of select="normalize-space(ccda:addr/ccda:postalCode)"/>"
                        </xsl:if>
                        <xsl:if test="normalize-space(ccda:addr/ccda:country)">
                            , "country": "<xsl:value-of select="normalize-space(ccda:addr/ccda:country)"/>"
                        </xsl:if>
                        <xsl:if test="ccda:addr/ccda:useablePeriod">
                            ,"period": {
                                <xsl:if test="string(ccda:addr/ccda:useablePeriod/ccda:low/@value)">
                                    "start": "<xsl:call-template name="formatDateTime">
                                                  <xsl:with-param name="dateTime" select="ccda:addr/ccda:useablePeriod/ccda:low/@value"/>
                                              </xsl:call-template>"
                                </xsl:if>
                                <xsl:if test="string(ccda:addr/ccda:useablePeriod/ccda:high/@value)">
                                    <xsl:if test="string(ccda:addr/ccda:useablePeriod/ccda:low/@value)">, </xsl:if>
                                    "end": "<xsl:call-template name="formatDateTime">
                                                <xsl:with-param name="dateTime" select="ccda:addr/ccda:useablePeriod/ccda:high/@value"/>
                                            </xsl:call-template>"
                                </xsl:if>
                            }
                        </xsl:if>
                    } 
        </xsl:if>
      },
      "request" : {
        "method" : "POST",
        "url" : "<xsl:value-of select='$baseFhirUrl'/>/Location/<xsl:value-of select="$locationResourceId"/>"
      }
    }
  </xsl:if>
  </xsl:template>
  
  <!-- Location Template from Encounters section -->
  <xsl:template name="EncountersLocationResource" match="/ccda:ClinicalDocument/ccda:component/ccda:structuredBody/ccda:component/ccda:section[@ID='encounters']/ccda:entry[position()=1]/ccda:encounter/ccda:participant/ccda:participantRole">
  <xsl:if test="not(/ccda:ClinicalDocument/ccda:componentOf/ccda:encompassingEncounter/ccda:location/ccda:healthCareFacility/ccda:location/ccda:name) and string($locationResourceId)">
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
          ,"name": "<xsl:value-of select="normalize-space(ccda:playingEntity/ccda:name)"/>"
        </xsl:if>

        <xsl:if test="ccda:addr[not(@nullFlavor)]">
            , "address": 
                    {
                        <xsl:if test="string(ccda:addr/@use)">
                            "use": "<xsl:choose>
                                <xsl:when test="ccda:addr/@use='HP' or ccda:addr/@use='H'">home</xsl:when>
                                <xsl:when test="ccda:addr/@use='WP'">work</xsl:when>
                                <xsl:when test="ccda:addr/@use='TMP'">temp</xsl:when>
                                <xsl:when test="ccda:addr/@use='OLD' or ccda:addr/@use='BAD'">old</xsl:when>
                                <xsl:otherwise><xsl:value-of select="ccda:addr/@use"/></xsl:otherwise>
                            </xsl:choose>"
                        </xsl:if>
                        <xsl:variable name="formattedAddress">
                            <xsl:call-template name="format-address">
                                <xsl:with-param name="addr" select="ccda:addr"/>
                            </xsl:call-template>
                        </xsl:variable>

                        <xsl:if test="normalize-space($formattedAddress) != ''">
                            , "text": "<xsl:value-of select="$formattedAddress"/>"
                        </xsl:if>
                        <xsl:if test="ccda:addr/ccda:streetAddressLine">
                            , "line": [
                                <xsl:for-each select="ccda:addr/ccda:streetAddressLine">
                                    "<xsl:value-of select="."/>"
                                    <xsl:if test="position() != last()">, </xsl:if>
                                </xsl:for-each>
                            ]
                        </xsl:if>
                        <xsl:if test="normalize-space(ccda:addr/ccda:city)">
                            , "city": "<xsl:value-of select="normalize-space(ccda:addr/ccda:city)"/>"
                        </xsl:if>
                        <xsl:if test="normalize-space(ccda:addr/ccda:county)">
                            , "district": "<xsl:value-of select="normalize-space(ccda:addr/ccda:county)"/>"
                        </xsl:if>
                        <xsl:if test="normalize-space(ccda:addr/ccda:state)">
                            , "state": "<xsl:value-of select="normalize-space(ccda:addr/ccda:state)"/>"
                        </xsl:if>
                        <xsl:if test="normalize-space(ccda:addr/ccda:postalCode)">
                            , "postalCode": "<xsl:value-of select="normalize-space(ccda:addr/ccda:postalCode)"/>"
                        </xsl:if>
                        <xsl:if test="normalize-space(ccda:addr/ccda:country)">
                            , "country": "<xsl:value-of select="normalize-space(ccda:addr/ccda:country)"/>"
                        </xsl:if>
                        <xsl:if test="ccda:addr/ccda:useablePeriod">
                            ,"period": {
                                <xsl:if test="string(ccda:addr/ccda:useablePeriod/ccda:low/@value)">
                                    "start": "<xsl:call-template name="formatDateTime">
                                                  <xsl:with-param name="dateTime" select="ccda:addr/ccda:useablePeriod/ccda:low/@value"/>
                                              </xsl:call-template>"
                                </xsl:if>
                                <xsl:if test="string(ccda:addr/ccda:useablePeriod/ccda:high/@value)">
                                    <xsl:if test="string(ccda:addr/ccda:useablePeriod/ccda:low/@value)">, </xsl:if>
                                    "end": "<xsl:call-template name="formatDateTime">
                                                <xsl:with-param name="dateTime" select="ccda:addr/ccda:useablePeriod/ccda:high/@value"/>
                                            </xsl:call-template>"
                                </xsl:if>
                            }
                        </xsl:if>
                    } 
        </xsl:if>
      },
      "request" : {
        "method" : "POST",
        "url" : "<xsl:value-of select='$baseFhirUrl'/>/Location/<xsl:value-of select="$locationResourceId"/>"
      }
    }
  </xsl:if>
  </xsl:template>
  
  <xsl:template name="mapScreeningCodeDisplay">
    <xsl:param name="screeningCode"/>
    <xsl:choose>
      <xsl:when test="$screeningCode = 'NYSAHCHRSN'">NYS Accountable Health Communities (AHC) Health-Related Social Needs Screening (HRSN) tool [Alternate]</xsl:when>
      <xsl:when test="$screeningCode = 'NYS-AHC-HRSN'">NYS Accountable Health Communities (AHC) Health-Related Social Needs Screening (HRSN) tool</xsl:when>
      <xsl:when test="$screeningCode = '96777-8'">Accountable health communities (AHC) health-related social needs screening (HRSN) tool</xsl:when>
      <xsl:when test="$screeningCode = '97023-6'">Accountable health communities (AHC) health-related social needs (HRSN) supplemental questions</xsl:when> 
      <xsl:when test="$screeningCode = '100698-0'">Social Determinants of Health screening report Document</xsl:when>    
      <xsl:otherwise>
        <xsl:text/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <xsl:template name="mapScreeningCodeSystem">
    <xsl:param name="screeningCode"/>
    <xsl:choose>
      <xsl:when test="$screeningCode = 'NYSAHCHRSN' or $screeningCode = 'NYS-AHC-HRSN'">http://test.shinny.org/us/ny/hrsn/CodeSystem/NYS-HRSN-Questionnaire</xsl:when>
      <xsl:when test="$screeningCode = '96777-8' or $screeningCode = '97023-6' or $screeningCode = '100698-0'">http://loinc.org</xsl:when>
      <xsl:otherwise>
        <xsl:text>http://loinc.org</xsl:text>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <!-- Template to format an address -->
  <xsl:template name="format-address">
      <xsl:param name="addr"/>

      <!-- Extract components -->
      <xsl:variable name="street">
          <xsl:for-each select="$addr/ccda:streetAddressLine[not(@nullFlavor) and normalize-space(.) != '']">
              <xsl:value-of select="normalize-space(.)"/>
              <xsl:if test="position() != last()">, </xsl:if>
          </xsl:for-each>
      </xsl:variable>

      <xsl:variable name="city"  select="normalize-space($addr/ccda:city[not(@nullFlavor) and normalize-space(.) != ''])"/>
      <xsl:variable name="state" select="normalize-space($addr/ccda:state[not(@nullFlavor) and normalize-space(.) != ''])"/>
      <xsl:variable name="zip"   select="normalize-space($addr/ccda:postalCode[not(@nullFlavor) and normalize-space(.) != ''])"/>

      <!-- Build the final string -->
      <xsl:variable name="fullAddress">
          <xsl:if test="string-length(normalize-space($street)) &gt; 0">
              <xsl:value-of select="$street"/>
          </xsl:if>

          <xsl:if test="$city != ''">
              <xsl:if test="normalize-space($street) != ''">, </xsl:if>
              <xsl:value-of select="$city"/>
          </xsl:if>

          <xsl:if test="$state != ''">
              <xsl:if test="$city != '' or normalize-space($street) != ''">, </xsl:if>
              <xsl:value-of select="$state"/>
          </xsl:if>

          <xsl:if test="$zip != ''">
              <xsl:text> </xsl:text>
              <xsl:value-of select="$zip"/>
          </xsl:if>
      </xsl:variable>

      <!-- Output trimmed -->
      <xsl:value-of select="normalize-space($fullAddress)"/>
  </xsl:template>

</xsl:stylesheet>