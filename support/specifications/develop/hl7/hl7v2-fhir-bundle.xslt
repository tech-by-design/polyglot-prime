<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0"
                xmlns:ccda="urn:hl7-org:v3"
                xmlns:fhir="http://hl7.org/fhir"
                xmlns:sdtc="urn:hl7-org:sdtc"
                exclude-result-prefixes="sdtc">

  <xsl:output method="text"/>
  
  <xsl:param name="currentTimestamp"/>
  <xsl:param name="patientCIN"/>
  <xsl:param name="encounterType"/>
  <xsl:param name="organizationNPI"/>
  <xsl:param name="organizationTIN"/>
  <xsl:param name="facilityID"/>
  <xsl:param name="OrganizationName"/>
  <xsl:variable name="patientRoleId" select="//ccda:patientRole/ccda:id[not(@assigningAuthorityName)]/@extension"/>
  <xsl:variable name="patientResourceName" 
  select="normalize-space(concat(
		  //PID/PID.5/PID.5.1, &quot; &quot;, 
		  //PID/PID.5/PID.5.2, &quot; &quot;, 
		  //PID/PID.5/PID.5.3, &quot;  &quot;, 
		  //PID/PID.9/PID.9.1, &quot; &quot;, 
		  //PID/PID.9/PID.9.2, &quot; &quot;, 
		  //PID/PID.9/PID.9.3
		))"/>
  <!-- <xsl:variable name="bundleTimestamp" select="/ccda:ClinicalDocument/ccda:effectiveTime/@value"/> -->
  <!-- <xsl:variable name="bundleTimestamp" select="//OBX[OBX.3/OBX.3.2 = 'AHC-HRSN Patient Consent'][1]/OBX.14/OBX.14.1"/> -->
  <xsl:variable name="bundleTimestamp" select="MSH/MSH.7/MSH.7.1"/>

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
    , "timestamp": "<xsl:call-template name='formatDateTime'>
                        <xsl:with-param name='dateTime' select='$bundleTimestamp'/>
                    </xsl:call-template>"
  </xsl:if>,
  "entry": [
	  <xsl:variable name="consent"><xsl:call-template name="ConsentFromOBX"/></xsl:variable>
	  <xsl:if test="normalize-space($consent)">
		<xsl:value-of select="$consent"/>
		<xsl:text>,</xsl:text>
	  </xsl:if>

	  <xsl:variable name="encounter"><xsl:call-template name="EncounterFromPV"/></xsl:variable>
	  <xsl:if test="normalize-space($encounter)">
		<xsl:value-of select="$encounter"/>
		<xsl:text>,</xsl:text>
	  </xsl:if>

	<xsl:if test="string(//XON)">
	  <xsl:variable name="org"><xsl:call-template name="OrganizationFromXON"/></xsl:variable>
	  <xsl:if test="normalize-space($org)">
		<xsl:value-of select="$org"/>
		<xsl:text>,</xsl:text>
	  </xsl:if>
	</xsl:if>
	  
	  <xsl:variable name="pid"><xsl:call-template name="PatientFromPID"/></xsl:variable>
	  <xsl:if test="normalize-space($pid)">
		<xsl:value-of select="$pid"/>
		<xsl:text>,</xsl:text>
	  </xsl:if>

	  <xsl:variable name="sog"><xsl:call-template name="SexualOrientationFromOBX"/></xsl:variable>
	  <xsl:if test="normalize-space($sog)">
		<xsl:value-of select="$sog"/>
		<xsl:text>,</xsl:text>
	  </xsl:if>

	  <!-- Observation entries -->
	  <xsl:for-each select="//OBX[string(OBX.5/OBX.5.1) and OBX.5/OBX.5.1 != 'UNK' and string(OBX.3/OBX.3.1) and OBX.3/OBX.3.1 != 'UNK' and OBX.3/OBX.3.1 != '76690-7']">
	  <xsl:variable name="obs">
		<xsl:call-template name="ObservationFromXON"/>
	  </xsl:variable>
	  <xsl:if test="normalize-space($obs)">
		<xsl:if test="position() != 1">,</xsl:if>
		<xsl:value-of select="$obs"/>
	  </xsl:if>
	</xsl:for-each>
	]

}
</xsl:template>

<!-- Patient Template -->
  <xsl:template name="PatientFromPID">
    {
      "fullUrl": "<xsl:value-of select='$baseFhirUrl'/>/Patient/<xsl:value-of select='$patientResourceId'/>",
      "resource": {
        "resourceType": "Patient",
        "id": "<xsl:value-of select='$patientResourceId'/>",
        "meta": {
          "lastUpdated": "<xsl:value-of select='$currentTimestamp'/>",
          "profile": ["<xsl:value-of select='$patientMetaProfileUrlFull'/>"]
        }
        <xsl:if test="//PID/PID.15">
		  ,"language": 
				"<xsl:choose>
					<xsl:when test='//PID/PID.15/PID.15.1 = &quot;001&quot; or not(string(//PID/PID.15/PID.15.1))'>en</xsl:when>
					<xsl:otherwise><xsl:value-of select='PID/PID.15/PID.15.1'/></xsl:otherwise>
				</xsl:choose>"

		</xsl:if>

        <!--If there is Official Name, print it, otherwise print first occuring name-->
        <xsl:if test="//PID/PID.5/PID.5.1">
            , "name": [
                <xsl:call-template name="generateNameJson">
				  <xsl:with-param name="nameNode" select="//PID.5"/>
				</xsl:call-template>
            ]
        </xsl:if>
        <xsl:if test="not(//PID/PID.5/PID.5.1) and //PID/PID.9/PID.9.1">
            , "name": [
                <xsl:call-template name="generateNameJson">
					<xsl:with-param name="nameNode" select="//PID.9"/>
				  </xsl:call-template>
            ]
        </xsl:if>

        , "gender": "<xsl:choose>
			<xsl:when test='//PID.8 = "" or //PID.8 = "U"'>
				<xsl:call-template name="getNullFlavorDisplay">
					<xsl:with-param name="nullFlavor" select="'UNK'"/>
				</xsl:call-template>
			</xsl:when>
			<xsl:when test='//PID.8 = "M"'>male</xsl:when>
			<xsl:when test='//PID.8 = "F"'>female</xsl:when>
			<xsl:when test='//PID.8 = "O"'>other</xsl:when>
			<xsl:otherwise><xsl:value-of select='//PID.8'/></xsl:otherwise>
		</xsl:choose>"

        <xsl:if test="string(//PID.7/PID.7.1)">
		  , "birthDate": "<xsl:choose>
						   <xsl:when test="string-length(//PID.7/PID.7.1) >= 8">
							 <xsl:value-of select="concat(substring(//PID.7/PID.7.1, 1, 4), '-', substring(//PID.7/PID.7.1, 5, 2), '-', substring(//PID.7/PID.7.1, 7, 2))"/>
						   </xsl:when>
						   <xsl:otherwise>
							 <xsl:value-of select="//PID.7/PID.7.1"/>
						   </xsl:otherwise>
						 </xsl:choose>"
		</xsl:if>
        <xsl:if test="//PID.11[PID.11.1 or PID.11.3 or PID.11.4 or PID.11.5]">
			  , "address": [
				<xsl:for-each select="//PID.11[PID.11.1 or PID.11.3 or PID.11.4 or PID.11.5]">
				  {
                        <!-- <xsl:if test="@use">
                            "use": "<xsl:choose>
                                <xsl:when test="@use='HP' or @use='H'">home</xsl:when>
                                <xsl:when test="@use='WP'">work</xsl:when>
                                <xsl:when test="@use='TMP'">temp</xsl:when>
                                <xsl:when test="@use='OLD' or @use='BAD'">old</xsl:when>
                                <xsl:otherwise><xsl:value-of select="@use"/></xsl:otherwise>
                            </xsl:choose>",
                        </xsl:if> -->
						<!-- text -->
						<xsl:if test="PID.11.1 or PID.11.2 or PID.11.3 or PID.11.4 or PID.11.5 or PID.11.6">
						  "text": "<xsl:value-of select="normalize-space(concat(PID.11.1, ' ', PID.11.2, ' ', PID.11.3, ' ', PID.11.4, ' ', PID.11.5, ' ', PID.11.6))"/>",
						</xsl:if>
                        <!-- line -->
						<xsl:if test="PID.11.1 or PID.11.2">
						  "line": [
							"<xsl:value-of select="normalize-space(concat(PID.11.1, ' ', PID.11.2))"/>"
						  ],
						</xsl:if>
                        <!-- city -->
						<xsl:if test="PID.11.3">
						  "city": "<xsl:value-of select="PID.11.3"/>",
						</xsl:if>
                        <!-- district -->
						<xsl:if test="PID.11.9">
						  "district": "<xsl:value-of select="PID.11.9"/>",
						</xsl:if>
                        <!-- state -->
						<xsl:if test="PID.11.4">
						  "state": "<xsl:value-of select="PID.11.4"/>",
						</xsl:if>
                        <!-- postalCode -->
						<xsl:if test="PID.11.5">
						  "postalCode": "<xsl:value-of select="PID.11.5"/>"
						</xsl:if>
                    } <xsl:if test="position() != last()">,</xsl:if>
                </xsl:for-each>
            ]
        </xsl:if>

		<xsl:if test="//PID.13[normalize-space(PID.13.1)] or //PID.14[normalize-space(PID.14.1)] or //PID.40[normalize-space(PID.40.1)]">
		  , "telecom": [
			<xsl:for-each select="//PID.13[normalize-space(PID.13.1)] | //PID.14[normalize-space(PID.14.1)] | //PID.40[normalize-space(PID.40.1)]">
			  {
				<!-- system -->
				<xsl:if test="*[3]">
				  "system": "<xsl:choose>
							  <xsl:when test="*[3]='PH' or *[3]='TEL'">phone</xsl:when>
							  <xsl:when test="*[3]='EM' or *[3]='MAIL'">email</xsl:when>
							  <xsl:otherwise>other</xsl:otherwise>
							</xsl:choose>",
				</xsl:if>

				<!-- use -->
				<xsl:if test="*[2]">
				  "use": "<xsl:choose>
						   <xsl:when test="*[2]='WPN' or *[2]='WP'">work</xsl:when>
						   <xsl:when test="*[2]='PRN' or *[2]='H'">home</xsl:when>
						   <xsl:when test="*[2]='NET' or *[2]='MC'">mobile</xsl:when>
						   <xsl:when test="*[2]='TMP'">temp</xsl:when>
						   <xsl:when test="*[2]='BAD'">old</xsl:when>
						   <xsl:otherwise><xsl:value-of select="*[2]"/></xsl:otherwise>
						 </xsl:choose>",
				</xsl:if>

				<!-- value -->
				"value": "<xsl:value-of select="*[1]"/>"
			  }<xsl:if test="position() != last()">,</xsl:if>
			</xsl:for-each>
		  ]
		</xsl:if>
        
      <xsl:if test="ccda:patient/ccda:raceCode or ccda:patient/ccda:ethnicGroupCode or ccda:patient/ccda:administrativeGenderCode/@code">
      , "extension": [
        <!-- Declare OMB code sets -->
        <xsl:variable name="ombRaceCodes" select="'1002-5 2028-9 2054-5 2076-8 2106-3 UNK ASKU'" />
        <xsl:variable name="ombEthnicityCodes" select="'2135-2 2186-5 UNK ASKU'" />

        <!-- RACE extension -->
        <xsl:if test="ccda:patient/ccda:raceCode">
          {
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
                    <xsl:when test="@code">urn:oid:<xsl:value-of select="@codeSystem"/></xsl:when>
                    <xsl:otherwise></xsl:otherwise>
                    <!-- <xsl:otherwise>http://terminology.hl7.org/CodeSystem/v3-NullFlavor</xsl:otherwise> -->
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
            ],
            "url": "http://hl7.org/fhir/us/core/StructureDefinition/us-core-race"
          },
        </xsl:if>

        <!-- ETHNICITY extension -->
        <xsl:if test="ccda:patient/ccda:ethnicGroupCode">
          {
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
                    <!-- <xsl:otherwise>http://terminology.hl7.org/CodeSystem/v3-NullFlavor</xsl:otherwise> -->
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
            ],
            "url": "http://hl7.org/fhir/us/core/StructureDefinition/us-core-ethnicity"
          }
        </xsl:if>

        <!-- GenderCode extension -->
        <xsl:if test="string(ccda:patient/ccda:administrativeGenderCode/@code)">
        ,{
            "url": "http://terminology.hl7.org/CodeSystem/v3-AdministrativeGender", 
            "valueCode": "<xsl:call-template name='mapAdministrativeGenderCode'>
                              <xsl:with-param name='genderCode' select='ccda:patient/ccda:administrativeGenderCode/@code'/>
                          </xsl:call-template>"
        }
        </xsl:if>
      ]
      </xsl:if>

      <xsl:variable name="cinId" select="$patientCIN"/>

		<!-- 19 -->
      <!-- <xsl:variable name="ssnId" select="(ccda:id[@root='2.16.840.1.113883.4.1'] | 
                                          ccda:id[
                                            string-length(@extension) = 11 and 
                                            substring(@extension,4,1) = '-' and 
                                            substring(@extension,7,1) = '-' and 
                                            translate(concat(substring(@extension,1,3), substring(@extension,5,2), substring(@extension,8,4)), '0123456789', '') = ''
                                          ])[1]/@extension"/> -->
		<xsl:variable name="ssnId" select="//PID.19/PID.19.1"/>

      <!-- MRN: Take first id element that is NOT SSN -->
      <!-- <xsl:variable name="mrnId" select="(ccda:id[
                                          not(@root='2.16.840.1.113883.4.1') and
                                          not(string-length(@extension) = 11 and 
                                              substring(@extension,4,1) = '-' and 
                                              substring(@extension,7,1) = '-' and 
                                              translate(concat(substring(@extension,1,3), substring(@extension,5,2), substring(@extension,8,4)), '0123456789', '') = '')
                                        ])[1]/@extension"/> -->
		<xsl:variable name="mrnId" select="//PID.3/PID.3.1"/>

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

      <xsl:if test="string(//PID.30/PID.30.1)">
		  , "deceasedBoolean": <xsl:choose>
			  <xsl:when test="normalize-space(//PID.30/PID.30.1) = 'Y'">true</xsl:when>
			  <xsl:otherwise>false</xsl:otherwise>
			</xsl:choose>
		</xsl:if>

      
      <xsl:variable name="mappedCode">
		  <xsl:call-template name="mapMaritalStatusCode">
			<xsl:with-param name="statusCode" select="//PID.16/PID.16.1"/>
		  </xsl:call-template>
		</xsl:variable>

		<!-- Output maritalStatus only if mappedCode is non-empty -->
		<xsl:if test="string($mappedCode)">
		  , "maritalStatus": {
			"coding": [{
			  "system": "http://terminology.hl7.org/CodeSystem/v3-MaritalStatus",
			  "code": "<xsl:value-of select='$mappedCode'/>",
			  "display": "<xsl:call-template name='mapMaritalStatus'>
							<xsl:with-param name='statusCode' select='//PID.16/PID.16.1'/>
						  </xsl:call-template>"
			}]
		  }
		</xsl:if>
		
		<xsl:if test="//NK1">
		  , "contact": [
			<xsl:for-each select="//NK1">
			  {
				<!-- Relationship: NK1.7 -->
				<xsl:choose>
				  <xsl:when test="string(NK1.7/NK1.7.1)">
					"relationship": [{
					  "coding": [{
						"system": "http://terminology.hl7.org/CodeSystem/v2-0063",
						"code": "<xsl:value-of select='NK1.7/NK1.7.1'/>",
						"display": "<xsl:value-of select='NK1.7/NK1.7.2'/>"
					  }]
					}],
				  </xsl:when>
				  <xsl:when test="string(NK1.3/NK1.3.1)">
					"relationship": [{
					  "coding": [{
						"system": "http://terminology.hl7.org/CodeSystem/v2-0063",
						"code": "<xsl:value-of select='NK1.3/NK1.3.1'/>",
						"display": "<xsl:value-of select='NK1.3/NK1.3.2'/>"
					  }]
					}],
				  </xsl:when>
				</xsl:choose>

				<!-- Name: NK1.2 -->
				<xsl:if test="string(NK1.2/NK1.2.1) or string(NK1.2/NK1.2.2)">
				  "name": {
					<xsl:if test="string(NK1.2/NK1.2.1)">
					  "family": "<xsl:value-of select='NK1.2/NK1.2.1'/>"<xsl:if test="string(NK1.2/NK1.2.2)">,</xsl:if>
					</xsl:if>
					<xsl:if test="string(NK1.2/NK1.2.2)">
					  "given": ["<xsl:value-of select='NK1.2/NK1.2.2'/>"]
					</xsl:if>
				  },
				</xsl:if>

				<!-- Telecom: NK1.5 (phone), NK1.6 (business phone), NK1.40 (mobile) -->
				<xsl:if test="string(NK1.5) or string(NK1.6) or string(NK1.40)">
				  "telecom": [
					<xsl:if test="string(NK1.5)">
					  { "system": "phone", "value": "<xsl:value-of select='NK1.5'/>" }<xsl:if test="string(NK1.6) or string(NK1.40)">,</xsl:if>
					</xsl:if>
					<xsl:if test="string(NK1.6)">
					  { "system": "phone", "value": "<xsl:value-of select='NK1.6'/>" }<xsl:if test="string(NK1.40)">,</xsl:if>
					</xsl:if>
					<xsl:if test="string(NK1.40)">
					  { "system": "phone", "value": "<xsl:value-of select='NK1.40'/>" }
					</xsl:if>
				  ],
				</xsl:if>

				<!-- Address: NK1.4 -->
				<xsl:if test="NK1.4">
				  "address": {
					<xsl:if test="string(NK1.4/NK1.4.1)">
					  "line": ["<xsl:value-of select='NK1.4/NK1.4.1'/>"]
					</xsl:if>
					<xsl:if test="string(NK1.4/NK1.4.3)">
					  , "city": "<xsl:value-of select='NK1.4/NK1.4.3'/>"
					</xsl:if>
					<xsl:if test="string(NK1.4/NK1.4.4)">
					  , "state": "<xsl:value-of select='NK1.4/NK1.4.4'/>"
					</xsl:if>
					<xsl:if test="string(NK1.4/NK1.4.5)">
					  , "postalCode": "<xsl:value-of select='NK1.4/NK1.4.5'/>"
					</xsl:if>
				  }
				</xsl:if>
			  }<xsl:if test="position() != last()">,</xsl:if>
			</xsl:for-each>
		  ]
		</xsl:if>
      
      <xsl:if test="//PID/PID.15">
		  ,"communication": [
			{
			  "language": {
				"coding": [
				  {
					"system": "urn:ietf:bcp:47",
					"code": "<xsl:choose>
							   <xsl:when test='//PID/PID.15/PID.15.1 = &quot;001&quot; or not(string(//PID/PID.15/PID.15.1))'>en</xsl:when>
							   <xsl:otherwise><xsl:value-of select='PID/PID.15/PID.15.1'/></xsl:otherwise>
							 </xsl:choose>"
				  }
				]
			  },
			  "preferred": true
			}
		  ]
		</xsl:if>    
    }
    , "request" : {
        "method" : "POST",
        "url" : "<xsl:value-of select='$baseFhirUrl'/>/Patient/<xsl:value-of select='$patientResourceId'/>"
      }
  }
  </xsl:template>

<!-- Sexual orientation Observation Template -->
  <xsl:template name="SexualOrientationFromOBX">
  <xsl:for-each select="//OBX[OBX.3/OBX.3.1 = '76690-7' and string-length(OBX.5/OBX.5.1) > 0]">
    <xsl:if test="position() != 1">
      <xsl:text>,</xsl:text>
    </xsl:if>
      {
        "fullUrl" : "<xsl:value-of select='$baseFhirUrl'/>/Observation/<xsl:value-of select='$sexualOrientationResourceId'/>",
        "resource": {
          "resourceType": "Observation",
          "id": "<xsl:value-of select='$sexualOrientationResourceId'/>",
          "meta": {
            "lastUpdated": "<xsl:value-of select='$currentTimestamp'/>",
            "profile": ["<xsl:value-of select='$observationSexualOrientationMetaProfileUrlFull'/>"]
          },
          "status": "<xsl:call-template name='mapObservationStatus'>
                       <xsl:with-param name='statusCode' select='OBX.11'/>
                     </xsl:call-template>",
          "code": {
            "coding": [{
              "system": "http://loinc.org",
              "code": "<xsl:value-of select='OBX.3/OBX.3.1'/>",
              "display": "<xsl:value-of select='OBX.3/OBX.3.2'/>"
            }],
            "text": "<xsl:choose>
                       <xsl:when test='string(OBX.3/OBX.3.9)'>
                         <xsl:value-of select='OBX.3/OBX.3.9'/>
                       </xsl:when>
                       <xsl:otherwise>
                         <xsl:value-of select='OBX.3/OBX.3.2'/>
                       </xsl:otherwise>
                     </xsl:choose>"
          },

          <xsl:choose>

			<!-- If value is unknown or empty -->
			<xsl:when test="OBX.5/OBX.5.1 = 'UNK' or OBX.5/OBX.5.1 = 'OTH'">
			  "valueCodeableConcept": {
				"coding": [{
				  "system": "http://terminology.hl7.org/CodeSystem/v3-NullFlavor",
				  "code": "<xsl:value-of select='OBX.5/OBX.5.1'/>",
				  "display": "<xsl:value-of select='OBX.5/OBX.5.2'/>"
				}]
			  }
			</xsl:when>
			
			<xsl:otherwise>
              "valueCodeableConcept" : {
                "coding" : [{
                  "system" : "http://snomed.info/sct",
                  "code" : "<xsl:value-of select='OBX.5/OBX.5.1'/>",
                  "display" : "<xsl:value-of select='OBX.5/OBX.5.2'/>"
                }]
              }
            </xsl:otherwise>

		  </xsl:choose>

          , "subject": {
            "reference": "Patient/<xsl:value-of select='$patientResourceId'/>",
            "display" : "<xsl:value-of select='$patientResourceName'/>"
          }
          <xsl:if test="string(OBX.14/OBX.14.1) or $currentTimestamp">
            , "effectiveDateTime": "<xsl:choose>
              <xsl:when test="string(OBX.14/OBX.14.1)">
                <xsl:call-template name='formatDateTime'>
                  <xsl:with-param name='dateTime' select='OBX.14/OBX.14.1'/>
                </xsl:call-template>
              </xsl:when>
              <xsl:otherwise>
                <xsl:value-of select='$currentTimestamp'/>
              </xsl:otherwise>
            </xsl:choose>"
          </xsl:if>
        },
        "request": {
          "method": "POST",
          "url": "<xsl:value-of select='$baseFhirUrl'/>/Observation/<xsl:value-of select='$sexualOrientationResourceId'/>"
        }
      }
  </xsl:for-each>
</xsl:template>

<!-- Observation Template -->
  <xsl:template name="ObservationFromXON">
    <!-- <xsl:if test="string(ccda:observation/ccda:code/@code) != '76690-7' and string(ccda:observation/ccda:value/@code) != 'UNK' or string(ccda:observation/ccda:value/@code) != ''">  -->
    <!-- {"OBX/OBX.5/OBX.5.1" : "<xsl:value-of select= 'OBX.5/OBX.5.1'/>",
	"OBX/OBX.3/OBX.3.1" : "<xsl:value-of select= 'OBX.3/OBX.3.1'/>"} -->
	<xsl:if test="string(OBX.5/OBX.5.1) 
          and OBX.5/OBX.5.1 != 'UNK' 
          and string(OBX.3/OBX.3.1) 
          and OBX.3/OBX.3.1 != 'UNK'
          ">
      
      <!--The observation resource will be generated only for the question codes present in the list specified in 'mapObservationCategoryCodes'-->
      <xsl:variable name="questionCode" select="OBX.3/OBX.3.1"/>
      <xsl:variable name="categoryCode">	  
              <xsl:call-template name="mapObservationCategoryCodes">
                <xsl:with-param name="questionCode" select="$questionCode"/>
              </xsl:call-template>
            </xsl:variable>
      <xsl:if test="string($categoryCode)">

          <xsl:variable name="observationResourceId">
            <xsl:call-template name="generateFixedLengthResourceId">
              <xsl:with-param name="prefixString" select="concat(generate-id(questionCode), position())"/>
              <xsl:with-param name="sha256ResourceId" select="$observationResourceSha256Id"/>
            </xsl:call-template>
          </xsl:variable>
          {
            "fullUrl": "<xsl:value-of select='$baseFhirUrl'/>/Observation/<xsl:value-of select='$observationResourceId'/>",
            "resource": {
              "resourceType": "Observation",
              "id": "<xsl:value-of select='$observationResourceId'/>",
              "meta": {
                "lastUpdated": "<xsl:value-of select='$currentTimestamp'/>",
                "profile": ["<xsl:value-of select='$observationMetaProfileUrlFull'/>"]
              },
              "status": "<xsl:call-template name='mapObservationStatus'>
                            <xsl:with-param name='statusCode' select='OBX.11'/>
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
                  }]
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
              "code": {
                "coding": [
                  {
                    "system": "http://loinc.org",
                    "code": "<xsl:value-of select='OBX.3/OBX.3.1'/>",
                    "display": "<xsl:value-of select='OBX.3/OBX.3.2'/>"
                  }
                ]
                <xsl:choose>
				  <xsl:when test="string(OBX.3/OBX.3.9)">
					<xsl:text>,</xsl:text>
					"text": "<xsl:value-of select='OBX.3/OBX.3.9'/>"
				  </xsl:when>
				  <xsl:when test="string(OBX.3/OBX.3.2)">
					<xsl:text>,</xsl:text>
					"text": "<xsl:value-of select='OBX.3/OBX.3.2'/>"
				  </xsl:when>
				</xsl:choose>
              },
              <!-- https://test.shinny.org/change_log.html#v150 
                 According to v1.5.0 change log, add component element for the question code '96778-6' -->
              <xsl:choose>
			  <xsl:when test="string(OBX.3/OBX.3.1) = '96778-6'">
				"component": [
				  {
					"code": {
					  "coding": [
						{
						  "system": "http://loinc.org",
						  "code": "<xsl:value-of select='OBX.3/OBX.3.1'/>",
						  "display": "<xsl:value-of select='OBX.3/OBX.3.2'/>"
						}
					  ]
					  <xsl:choose>
						<xsl:when test="string(OBX.3/OBX.3.9)">
						  <xsl:text>,</xsl:text> "text": "<xsl:value-of select='OBX.3/OBX.3.9'/>"
						</xsl:when>
						<xsl:when test="string(OBX.3/OBX.3.2)">
						  <xsl:text>,</xsl:text> "text": "<xsl:value-of select='OBX.3/OBX.3.2'/>"
						</xsl:when>
					  </xsl:choose>
					},
					"valueCodeableConcept": {
					  "coding": [{
						"system": "http://loinc.org",
						"code": "<xsl:value-of select='OBX.5/OBX.5.1'/>",
						"display": "<xsl:value-of select='OBX.5/OBX.5.2'/>"
					  }]
					  <xsl:choose>
						<xsl:when test="string(OBX.5/OBX.5.9)">
						  <xsl:text>,</xsl:text> "text": "<xsl:value-of select='OBX.5/OBX.5.9'/>"
						</xsl:when>
						<xsl:when test="string(OBX.5/OBX.5.2)">
						  <xsl:text>,</xsl:text> "text": "<xsl:value-of select='OBX.5/OBX.5.2'/>"
						</xsl:when>
					  </xsl:choose>
					}
				  }
				],
			  </xsl:when>
			  <xsl:when test="string(OBX.3/OBX.3.1) = '95614-4'">
				"valueCodeableConcept": {
					"coding": [{
					  "system": "http://unitsofmeasure.org",
					  "display": "{Number}"
					}]
					<xsl:text>,</xsl:text> "text": "<xsl:value-of select='OBX.5/OBX.5.1'/>"
				  },
			  </xsl:when>
			  <xsl:otherwise>
				  "valueCodeableConcept": {
					"coding": [{
					  "system": "http://loinc.org",
					  "code": "<xsl:value-of select='OBX.5/OBX.5.1'/>",
					  "display": "<xsl:value-of select='OBX.5/OBX.5.2'/>"
					}]
					<xsl:choose>
					  <xsl:when test="string(OBX.5/OBX.5.9)">
						<xsl:text>,</xsl:text> "text": "<xsl:value-of select='OBX.5/OBX.5.9'/>"
					  </xsl:when>
					  <xsl:when test="string(OBX.5/OBX.5.2)">
						<xsl:text>,</xsl:text> "text": "<xsl:value-of select='OBX.5/OBX.5.2'/>"
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
              <xsl:if test="string(OBX.14/OBX.14.1) or $currentTimestamp">
				  <xsl:text>,</xsl:text>
				  "effectiveDateTime": "<xsl:choose>
					<xsl:when test="string(OBX.14/OBX.14.1)">
					  <xsl:call-template name='formatDateTime'>
						<xsl:with-param name='dateTime' select='OBX.14/OBX.14.1'/>
					  </xsl:call-template>
					</xsl:when>
					<xsl:otherwise>
					  <xsl:value-of select='$currentTimestamp'/>
					</xsl:otherwise>
				  </xsl:choose>"
				</xsl:if>
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

  <!-- Organization Template -->
<xsl:template name="OrganizationFromXON">
    {
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
            <xsl:choose>

              <!-- NPI -->
              <xsl:when test="$organizationNPI">
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
              </xsl:when>

              <!-- TAX -->
              <xsl:when test="$organizationTIN">
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
              </xsl:when>
            </xsl:choose>
          ],
        </xsl:if>
		
        "name": "<xsl:choose>
           <xsl:when test='normalize-space(//MSH/MSH.6)'>
             <xsl:value-of select='//MSH/MSH.6'/>
           </xsl:when>
           <xsl:otherwise>
             <xsl:value-of select='$OrganizationName'/>
           </xsl:otherwise>
         </xsl:choose>"

        <xsl:if test="//ORC.23">
		  , "telecom": [
			<xsl:for-each select="//ORC.23">
			  {
				<xsl:if test="string(//ORC.23.1)">
				  "value": "<xsl:value-of select='//ORC.23.1'/>"
				</xsl:if>
				<xsl:if test="string(//ORC.23.3)">
				  <xsl:if test="string(//ORC.23.1)">, </xsl:if>
				  "system": "<xsl:choose>
					<xsl:when test="//ORC.23.3 = 'PH'">phone</xsl:when>
					<xsl:when test="//ORC.23.3 = 'FX'">fax</xsl:when>
					<xsl:when test="//ORC.23.3 = 'Internet'">email</xsl:when>
					<xsl:otherwise>other</xsl:otherwise>
				  </xsl:choose>"
				</xsl:if>
				<xsl:if test="string(//ORC.23.2)">
				  <xsl:if test="string(//ORC.23.1) or string(//ORC.23.3)">, </xsl:if>
				  "use": "<xsl:choose>
					<xsl:when test="//ORC.23.2 = 'WP'">work</xsl:when>
					<xsl:when test="//ORC.23.2 = 'H'">home</xsl:when>
					<xsl:when test="//ORC.23.2 = 'TMP'">temp</xsl:when>
					<xsl:when test="//ORC.23.2 = 'MC' or ORC.23.2 = 'PG'">mobile</xsl:when>
					<xsl:otherwise><xsl:value-of select='//ORC.23.2'/></xsl:otherwise>
				  </xsl:choose>"
				</xsl:if>
			  }<xsl:if test="position() != last()">,</xsl:if>
			</xsl:for-each>
		  ]
		</xsl:if>
        <xsl:if test="//ORC.22">
		  , "address": [
			<xsl:for-each select="//ORC.22">
			  {
				<xsl:variable name="comma" select="false()" />
				<xsl:if test="string(ORC.22.7)">
				  "use": "<xsl:choose>
							<xsl:when test="ORC.22.7 = 'H' or ORC.22.7 = 'HP'">home</xsl:when>
							<xsl:when test="ORC.22.7 = 'WP'">work</xsl:when>
							<xsl:when test="ORC.22.7 = 'TMP'">temp</xsl:when>
							<xsl:when test="ORC.22.7 = 'OLD' or ORC.22.7 = 'BAD'">old</xsl:when>
							<xsl:otherwise><xsl:value-of select="ORC.22.7"/></xsl:otherwise>
						 </xsl:choose>"
				  <xsl:text>,</xsl:text>
				</xsl:if>

				<xsl:if test="string(ORC.22.1) or string(ORC.22.2) or string(ORC.22.3) or string(ORC.22.4) or string(ORC.22.5) or string(ORC.22.6)">
				  "text": "<xsl:value-of select="normalize-space(concat(ORC.22.1, ' ', ORC.22.2, ' ', ORC.22.3, ' ', ORC.22.4, ' ', ORC.22.5, ' ', ORC.22.6))"/>"
				  <xsl:if test="string(ORC.22.1) or string(ORC.22.2)">,<xsl:text/></xsl:if>
				</xsl:if>

				<xsl:if test="string(ORC.22.1) or string(ORC.22.2)">
				  "line": [
					<xsl:if test="string(ORC.22.1)">
					  "<xsl:value-of select='ORC.22.1'/>"<xsl:if test="string(ORC.22.2)">,</xsl:if>
					</xsl:if>
					<xsl:if test="string(ORC.22.2)">
					  "<xsl:value-of select='ORC.22.2'/>"
					</xsl:if>
				  ]
				  <xsl:if test="string(ORC.22.3) or string(ORC.22.4) or string(ORC.22.5) or string(ORC.22.6) or string(ORC.22.9)">,<xsl:text/></xsl:if>
				</xsl:if>

				<xsl:if test="string(ORC.22.3)">
				  "city": "<xsl:value-of select='ORC.22.3'/>"<xsl:if test="string(ORC.22.4) or string(ORC.22.5) or string(ORC.22.6) or string(ORC.22.9)">,<xsl:text/></xsl:if>
				</xsl:if>

				<xsl:if test="string(ORC.22.9)">
				  "district": "<xsl:value-of select='ORC.22.9'/>"<xsl:if test="string(ORC.22.4) or string(ORC.22.5) or string(ORC.22.6)">,<xsl:text/></xsl:if>
				</xsl:if>

				<xsl:if test="string(ORC.22.4)">
				  "state": "<xsl:value-of select='ORC.22.4'/>"<xsl:if test="string(ORC.22.5) or string(ORC.22.6)">,<xsl:text/></xsl:if>
				</xsl:if>

				<xsl:if test="string(ORC.22.5)">
				  "postalCode": "<xsl:value-of select='ORC.22.5'/>"<xsl:if test="string(ORC.22.6)">,<xsl:text/></xsl:if>
				</xsl:if>

				<xsl:if test="string(ORC.22.6)">
				  "country": "<xsl:value-of select='ORC.22.6'/>"
				</xsl:if>
			  }<xsl:if test="position() != last()">,</xsl:if>
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


<!-- Encounter Template -->
  <xsl:template name="EncounterFromPV">
  {
    "fullUrl": "<xsl:value-of select='$baseFhirUrl'/>/Encounter/<xsl:value-of select='$encounterResourceId'/>",
    "resource": {
      "resourceType": "Encounter",
      "id": "<xsl:value-of select='$encounterResourceId'/>",
      "meta": {
        "lastUpdated": "<xsl:value-of select='$currentTimestamp'/>",
        "profile": [
          "<xsl:value-of select='$encounterMetaProfileUrlFull'/>"
        ]
      },
      "status": "<xsl:call-template name='mapEncounterStatusFromHL7'/>"

      <xsl:if test="string(//PV1/PV1.2/PV1.2.1)">
		  <xsl:text>,</xsl:text>
		  "class": {
			"system": "http://terminology.hl7.org/CodeSystem/v3-ActCode",
			<xsl:choose>
			  <xsl:when test="//PV1/PV1.2/PV1.2.1 = 'I'">
				"code": "IMP",
				"display": "inpatient encounter"
			  </xsl:when>
			  <xsl:when test="//PV1/PV1.2/PV1.2.1 = 'E'">
				"code": "EMER",
				"display": "emergency"
			  </xsl:when>
			  <xsl:when test="//PV1/PV1.2/PV1.2.1 = 'O'">
				"code": "AMB",
				"display": "ambulatory"
			  </xsl:when>
			  <xsl:when test="//PV1/PV1.2/PV1.2.1 = 'P'">
				"code": "PRENC",
				"display": "pre-admission"
			  </xsl:when>
			  <xsl:otherwise>
				"code": "<xsl:value-of select='//PV1/PV1.2/PV1.2.1'/>",
				"display": "<xsl:value-of select='//PV1/PV1.2/PV1.2.2'/>"
			  </xsl:otherwise>
			</xsl:choose>
		  }
		</xsl:if>


	 <xsl:variable name="type" select="$encounterType"/>
      <xsl:if test="$type">
        <xsl:text>,</xsl:text>
        "type": [
          <xsl:if test="$type">
            {
			  "coding": [{
				"system": "http://snomed.info/sct",
				"code": "<xsl:value-of select='$type'/>",
				"display": "<xsl:choose>
							  <xsl:when test='$type = "405672008"'>Direct questioning (procedure)</xsl:when>
							  <xsl:when test='$type = "23918007"'>History taking, self-administered, by computer terminal</xsl:when>
							  <xsl:otherwise><xsl:value-of select='//PV1/PV1.4/PV1.4.2'/></xsl:otherwise>
							</xsl:choose>"
			  }],
			  "text": "<xsl:choose>
						 <xsl:when test='$type = "405672008"'>Direct questioning (procedure)</xsl:when>
						 <xsl:when test='$type = "23918007"'>History taking, self-administered, by computer terminal</xsl:when>
						 <xsl:otherwise><xsl:value-of select='//PV1/PV1.4/PV1.4.9'/></xsl:otherwise>
					   </xsl:choose>"
			}
          </xsl:if>
        ]
      </xsl:if>

      <xsl:if test="string(//PV1/PV1.44/PV1.44.1) or string(//PV1/PV1.45/PV1.45.1)">
        <xsl:text>,</xsl:text>
        "period": {
          <xsl:if test="string(//PV1/PV1.44/PV1.44.1)">
            "start": "<xsl:call-template name='formatDateTime'>
                        <xsl:with-param name='dateTime' select='//PV1/PV1.44/PV1.44.1'/>
                      </xsl:call-template>"
            <xsl:if test="string(//PV1/PV1.45/PV1.45.1)">,</xsl:if>
          </xsl:if>
          <xsl:if test="string(//PV1/PV1.45/PV1.45.1)">
            "end": "<xsl:call-template name='formatDateTime'>
                      <xsl:with-param name='dateTime' select='//PV1/PV1.45/PV1.45.1'/>
                    </xsl:call-template>"
          </xsl:if>
        }
      </xsl:if>

      <xsl:text>,</xsl:text>
      "subject": {
        "reference": "Patient/<xsl:value-of select='$patientResourceId'/>",
        "display": "<xsl:value-of select='normalize-space(concat(
		  //PID/PID.5/PID.5.1, &quot; &quot;, 
		  //PID/PID.5/PID.5.2, &quot; &quot;, 
		  //PID/PID.5/PID.5.3, &quot;  &quot;, 
		  //PID/PID.9/PID.9.1, &quot; &quot;, 
		  //PID/PID.9/PID.9.2, &quot; &quot;, 
		  //PID/PID.9/PID.9.3
		))'/>"

      }

		<xsl:if test="string(//OBR.32/OBR.32.1)">
			<xsl:text>,</xsl:text>
			"participant": [{
			  "type": [{
				"coding": [{
				  "system": "http://terminology.hl7.org/CodeSystem/participant-type",
				  "code": "<xsl:choose>
						   <xsl:when test='normalize-space(//OBR.32/OBR.32.1)'>
							 <xsl:value-of select='substring-before(//OBR.32/OBR.32.1, "&amp;")'/>
						   </xsl:when>
						   <xsl:when test='normalize-space(//OBR.34/OBR.34.1)'>
							 <xsl:value-of select='substring-before(//OBR.34/OBR.34.1, "&amp;")'/>
						   </xsl:when>
						 </xsl:choose>",

				"display": "<xsl:choose>
					  <xsl:when test='normalize-space(OBR.32/OBR.32.1)'>
              <xsl:variable name='fv32' select='OBR.32/OBR.32.1'/>
              <xsl:variable name='after32' select='substring-after($fv32, "&amp;")'/>
              <xsl:variable name='sub2_32' select='substring-before($after32, "&amp;")'/>
              <xsl:variable name='sub3_32' select='substring-after($after32, "&amp;")'/>
              <xsl:value-of select='normalize-space(concat($sub3_32, " ", $sub2_32))'/>
					  </xsl:when>

					  <xsl:when test='normalize-space(OBR.34/OBR.34.1)'>
              <xsl:variable name='fv34' select='OBR.34/OBR.34.1'/>
              <xsl:variable name='after34' select='substring-after($fv34, "&amp;")'/>
              <xsl:variable name='sub2_34' select='substring-before($after34, "&amp;")'/>
              <xsl:variable name='sub3_34' select='substring-after($after34, "&amp;")'/>
              <xsl:value-of select='normalize-space(concat($sub3_34, " ", $sub2_34))'/>
					  </xsl:when>
					</xsl:choose>"
				}]
			  }],
			  "individual": {
				"reference": "Practitioner/<xsl:value-of select='//ROL/ROL.4/ROL.4.1'/>",
				"display": "<xsl:value-of select='normalize-space(concat(//ROL/ROL.4/ROL.4.3, &quot; &quot;, //ROL/ROL.4/ROL.4.2))'/>"
			  }
			}]
		</xsl:if>

      <xsl:if test="string(//PV1/PV1.3/PV1.3.1)">
        <xsl:text>,</xsl:text>
        "location": [{
          "location": {
            "reference": "Location/<xsl:value-of select='//PV1/PV1.3/PV1.3.1'/>",
        <xsl:variable name="pv1Field" select="PV1.3"/>
        <xsl:variable name="comp4" select="tokenize($pv1Field, '\^')[4]"/>
        <xsl:variable name="comp7" select="tokenize($pv1Field, '\^')[7]"/>
        <xsl:variable name="subcomp4_1" select="substring-before($comp4, '&amp;')"/>

        "display": "<xsl:choose>
                <xsl:when test='normalize-space($subcomp4_1)'>
                <xsl:value-of select='$subcomp4_1'/>
                </xsl:when>
                <xsl:otherwise>
                <xsl:value-of select='$comp7'/>
                </xsl:otherwise>
					   </xsl:choose>"
          }
        }]
      </xsl:if>

      <xsl:if test="string(//PL.1) or string(//PL.6)">
	  <xsl:text>,</xsl:text>
	  "serviceProvider": {
		<xsl:if test="string(//PL.1)">
		  "reference": "Organization/<xsl:value-of select='//PL.1'/>"
		  <xsl:if test="string(//PL.6)">,</xsl:if>
		</xsl:if>
		<xsl:if test="string(//PL.6)">
		  "display": "<xsl:value-of select='//PL.6'/>"
		</xsl:if>
	  }
	</xsl:if>
    },
    "request": {
      "method": "POST",
      "url": "<xsl:value-of select='$baseFhirUrl'/>/Encounter/<xsl:value-of select='$encounterResourceId'/>"
    }
  }
</xsl:template>


<!-- Consent Template -->
  <xsl:template name="ConsentFromOBX">
  <xsl:variable name="consentOBX" select="//OBX[normalize-space(OBX.3/OBX.3.2) = 'AHC-HRSN Patient Consent'][1]"/>
  
   <!-- Define boolean: is consent given -->
  <xsl:variable name="isConsentGiven"
                select="contains(normalize-space($consentOBX/OBX.5/OBX.5.1), 'Patient Consents') or 
                        contains(normalize-space($consentOBX/OBX.5/OBX.5.1), 'Yes')"/>
						
  {
    "fullUrl": "<xsl:value-of select='$baseFhirUrl'/>/Consent/<xsl:value-of select='$consentResourceId'/>",
    "resource": {
      "resourceType": "Consent",
      "id": "<xsl:value-of select='$consentResourceId'/>",
      "meta": {
        "lastUpdated": "<xsl:value-of select='$currentTimestamp'/>",
        "profile": ["<xsl:value-of select='$consentMetaProfileUrlFull'/>"]
      },
      "status": "<xsl:choose>
        <xsl:when test='$isConsentGiven'>active</xsl:when>
        <xsl:otherwise>rejected</xsl:otherwise>
      </xsl:choose>",
      "scope": {
        "coding": [{
          "system": "http://terminology.hl7.org/CodeSystem/consentscope",
          "code": "treatment",
          "display": "Treatment"
        }],
        "text": "treatment"
      },
      "category": [
        {
          "coding": [{
            "system": "http://loinc.org",
            "code": "59284-0",
            "display": "Consent Document"
          }]
        },
        {
          "coding": [{
            "system": "http://terminology.hl7.org/CodeSystem/v3-ActCode",
            "code": "IDSCL"
          }]
        }
      ]
      <xsl:if test="$consentOBX/OBX.14/OBX.14.1 or $currentTimestamp">
        , "dateTime": "<xsl:choose>
          <xsl:when test='$consentOBX/OBX.14/OBX.14.1'>
            <xsl:call-template name="formatDateTime">
              <xsl:with-param name="dateTime" select="$consentOBX/OBX.14/OBX.14.1"/>
            </xsl:call-template>
          </xsl:when>
          <xsl:otherwise><xsl:value-of select="$currentTimestamp"/></xsl:otherwise>
        </xsl:choose>"
      </xsl:if>,
      "patient": {
        "reference": "Patient/<xsl:value-of select='$patientResourceId'/>"
      },
      "organization": [{
        "reference": "Organization/<xsl:value-of select='$organizationResourceId'/>"
      }],
      "provision": {
        "type": "<xsl:choose>
          <xsl:when test='$isConsentGiven'>permit</xsl:when>
          <xsl:otherwise>deny</xsl:otherwise>
        </xsl:choose>"
      },
      "policy": [{
        "authority": "urn:uuid:d1eaac1a-22b7-4bb6-9c62-cc95d6fdf1a5"
      }],
      "sourceAttachment": {
        "contentType": "application/pdf",
        "language": "en"
      }
    },
    "request": {
      "method": "POST",
      "url": "<xsl:value-of select='$baseFhirUrl'/>/Consent/<xsl:value-of select='$consentResourceId'/>"
    }
  }
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

<xsl:template name="mapEncounterStatusFromHL7">
  <xsl:choose>
    <xsl:when test="//PV1/PV1.44/PV1.44.1 and //PV1/PV1.45/PV1.45.1">finished</xsl:when>
    <xsl:when test="//PV1/PV1.44/PV1.44.1 and not(//PV1/PV1.45/PV1.45.1)">in-progress</xsl:when>
    <xsl:when test="//PV2/PV2.24/PV2.24.1 and not(//PV1/PV1.44/PV1.44.1)">planned</xsl:when>
    <xsl:otherwise>unknown</xsl:otherwise>
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

<xsl:template name="mapObservationStatus">
    <xsl:param name="statusCode"/>
    <xsl:choose>
        <xsl:when test="$statusCode = 'completed'">final</xsl:when>
        <xsl:when test="$statusCode = 'final'">final</xsl:when>
        <xsl:when test="$statusCode = 'active'">preliminary</xsl:when>
        <xsl:when test="$statusCode = 'aborted'">cancelled</xsl:when>
        <xsl:when test="$statusCode = 'cancelled'">cancelled</xsl:when>
        <xsl:when test="$statusCode = 'held'">registered</xsl:when>
        <xsl:when test="$statusCode = 'suspended'">registered</xsl:when>
        <xsl:when test="$statusCode = 'nullified'">entered-in-error</xsl:when>
        <xsl:otherwise>unknown</xsl:otherwise>
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


<xsl:template name="generateNameJson">
    <xsl:param name="nameNode"/>
    {
		<!-- valueString from given name (concatenation of given components) -->
		<xsl:if test="string($nameNode/PID.5.2) or string($nameNode/PID.5.3)">
		  "extension": [{
			"url": "<xsl:value-of select='$baseFhirUrl'/>/StructureDefinition/middle-name",
			"valueString": "<xsl:value-of select="normalize-space(concat($nameNode/PID.5.2, ' ', $nameNode/PID.5.3))"/>"
		  }],
		</xsl:if>

		<!-- use -->
		<xsl:if test="string($nameNode/PID.5.7)">
		  "use": "<xsl:choose>
					<xsl:when test="$nameNode/PID.5.7 = 'L'">official</xsl:when>
					<xsl:when test="$nameNode/PID.5.7 = 'P'">usual</xsl:when>
					<xsl:otherwise><xsl:value-of select="$nameNode/PID.5.7"/></xsl:otherwise>
				  </xsl:choose>",
		</xsl:if>

		<!-- prefix -->
		<xsl:if test="string($nameNode/PID.5.5)">
		  "prefix": ["<xsl:value-of select='$nameNode/PID.5.5'/>"],
		</xsl:if>

		<!-- given -->
		<xsl:if test="string($nameNode/PID.5.2) or string($nameNode/PID.5.3)">
		  "given": ["<xsl:value-of select='normalize-space(concat($nameNode/PID.5.2, &quot; &quot;, $nameNode/PID.5.3))'/>"]
		</xsl:if>

		<!-- family -->
		<xsl:if test="string($nameNode/PID.5.1)">
		  ,"family": "<xsl:value-of select='$nameNode/PID.5.1'/>"
		</xsl:if>

		<!-- suffix -->
		<xsl:if test="string($nameNode/PID.5.4)">
		  ,"suffix": ["<xsl:value-of select='$nameNode/PID.5.4'/>"]
		</xsl:if>
        <!-- <xsl:if test="($selectedName/ccda:validTime/ccda:low and not($selectedName/ccda:validTime/ccda:low/@nullFlavor = 'UNK' or $selectedName/ccda:validTime/ccda:low/@nullFlavor = 'NA')) or 
                  ($selectedName/ccda:validTime/ccda:high and not($selectedName/ccda:validTime/ccda:high/@nullFlavor = 'UNK' or $selectedName/ccda:validTime/ccda:high/@nullFlavor = 'NA'))">
          <xsl:if test="string($selectedName/ccda:given) or string($selectedName/ccda:prefix) or string($selectedName/ccda:family) or string($selectedName/ccda:suffix)">, </xsl:if>
          "period": {
              <xsl:if test="$selectedName/ccda:validTime/ccda:low and not($selectedName/ccda:validTime/ccda:low/@nullFlavor = 'UNK' or $selectedName/ccda:validTime/ccda:low/@nullFlavor = 'NA')">
                  "start": "<xsl:call-template name="formatDateTime">
                                <xsl:with-param name="dateTime" select="$selectedName/ccda:validTime/ccda:low/@value"/>
                            </xsl:call-template>"
                  <xsl:if test="$selectedName/ccda:validTime/ccda:high and not($selectedName/ccda:validTime/ccda:high/@nullFlavor = 'UNK' or $selectedName/ccda:validTime/ccda:high/@nullFlavor = 'NA')">,</xsl:if>
              </xsl:if>
              <xsl:if test="$selectedName/ccda:validTime/ccda:high and not($selectedName/ccda:validTime/ccda:high/@nullFlavor = 'UNK' or $selectedName/ccda:validTime/ccda:high/@nullFlavor = 'NA')">
                  "end": "<xsl:call-template name="formatDateTime">
                              <xsl:with-param name="dateTime" select="$selectedName/ccda:validTime/ccda:high/@value"/>
                          </xsl:call-template>"
              </xsl:if>
          }
        </xsl:if> -->
    }
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

<xsl:template name="mapAdministrativeGenderCode">
  <xsl:param name="genderCode"/>
  <xsl:choose>
    <xsl:when test="$genderCode = 'M' or $genderCode = 'Male' or $genderCode = 'male'">M</xsl:when>
    <xsl:when test="$genderCode = 'F' or $genderCode = 'Female' or $genderCode = 'female'">F</xsl:when>
    <xsl:otherwise>UNK</xsl:otherwise>
  </xsl:choose>
</xsl:template>

<xsl:template name="mapMaritalStatus">
    <xsl:param name="statusCode"/>
    <xsl:choose>
        <xsl:when test="$statusCode = 'M'">married</xsl:when>
        <xsl:when test="$statusCode = 'S'">Never Married</xsl:when>
        <xsl:when test="$statusCode = 'A'">Annulled</xsl:when>
        <xsl:when test="$statusCode = 'D'">Divorced</xsl:when>
        <xsl:when test="$statusCode = 'I'">Interlocutory</xsl:when>
        <xsl:when test="$statusCode = 'L'">Legally Separated</xsl:when>
        <xsl:when test="$statusCode = 'C'">Common Law</xsl:when>
        <xsl:when test="$statusCode = 'P'">Polygamous</xsl:when>
        <xsl:when test="$statusCode = 'T'">Domestic partner</xsl:when>
        <xsl:when test="$statusCode = 'U'">unmarried</xsl:when>
        <xsl:when test="$statusCode = 'W'">Widowed</xsl:when>
        <xsl:otherwise>unknown</xsl:otherwise>
    </xsl:choose>
</xsl:template>

<xsl:template name="mapMaritalStatusCode">
    <xsl:param name="statusCode"/>
    <xsl:choose>
  <xsl:when test='$statusCode = "M" or
                  $statusCode = "S" or
                  $statusCode = "A" or
                  $statusCode = "D" or
                  $statusCode = "I" or
                  $statusCode = "L" or
                  $statusCode = "C" or
                  $statusCode = "P" or
                  $statusCode = "T" or
                  $statusCode = "U" or
                  $statusCode = "W"'>
    <xsl:value-of select='$statusCode'/>
  </xsl:when>
  <xsl:otherwise/>
</xsl:choose>
</xsl:template>

</xsl:stylesheet>
