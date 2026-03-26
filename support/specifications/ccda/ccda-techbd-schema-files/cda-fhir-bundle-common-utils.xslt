<?xml version="1.0" encoding="UTF-8"?>
<!-- Version : 0.1.0 -->
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0"
                xmlns:ccda="urn:hl7-org:v3"
                xmlns:fhir="http://hl7.org/fhir"
                xmlns:sdtc="urn:hl7-org:sdtc"
                xmlns:exsl="http://exslt.org/common"
                extension-element-prefixes="exsl"
                exclude-result-prefixes="sdtc ccda exsl">

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
        <xsl:when test="$cleanCode = 'aborted'">entered-in-error</xsl:when>
        <xsl:when test="$cleanCode = 'cancelled'">entered-in-error</xsl:when>
        <xsl:when test="$cleanCode = 'nullified'">entered-in-error</xsl:when>
        <!-- <xsl:when test="$cleanCode = 'active'">preliminary</xsl:when> -->
        <!-- <xsl:when test="$cleanCode = 'held'">registered</xsl:when> -->
        <!-- <xsl:when test="$cleanCode = 'suspended'">registered</xsl:when> -->
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
        <!-- given -->
        <xsl:variable name="given_trimmed">
          <xsl:call-template name="string-trim">
            <xsl:with-param name="text" select="$selectedName/ccda:given"/>
          </xsl:call-template>
        </xsl:variable>

        <!-- middle name extension -->
        <xsl:if test="string($given_trimmed)">
            "extension": [{
                "url": "<xsl:value-of select='$baseFhirUrl'/>/StructureDefinition/middle-name",
                "valueString": "<xsl:value-of select="$given_trimmed"/>"
            }]
        </xsl:if>

        <!-- use -->
        <xsl:if test="normalize-space($selectedName/@use)">
            <xsl:if test="string($given_trimmed)">, </xsl:if>
            "use": "<xsl:choose>
                        <xsl:when test="normalize-space($selectedName/@use)='L'">official</xsl:when>
                        <xsl:when test="normalize-space($selectedName/@use)='P'">usual</xsl:when>
                        <xsl:otherwise>
                            <xsl:value-of select="normalize-space($selectedName/@use)"/>
                        </xsl:otherwise>
                    </xsl:choose>"
        </xsl:if>

        <!-- prefix -->
        <xsl:variable name="prefix_trimmed">
          <xsl:call-template name="string-trim">
            <xsl:with-param name="text" select="$selectedName/ccda:prefix"/>
          </xsl:call-template>
        </xsl:variable>

        <xsl:if test="string($prefix_trimmed)">
            <xsl:if test="string($given_trimmed) or normalize-space($selectedName/@use)">, </xsl:if>
            "prefix": ["<xsl:value-of select="$prefix_trimmed"/>"]
        </xsl:if>

        <!-- given -->
        <xsl:if test="string($given_trimmed)">
            <xsl:if test="normalize-space($selectedName/@use) or string($prefix_trimmed)">, </xsl:if>            
            "given": ["<xsl:value-of select="$given_trimmed"/>"]
        </xsl:if>

        <!-- family -->
        <xsl:variable name="family_trimmed">
          <xsl:call-template name="string-trim">
            <xsl:with-param name="text" select="$selectedName/ccda:family"/>
          </xsl:call-template>
        </xsl:variable>

        <xsl:if test="string($family_trimmed)">
            <xsl:if test="
                normalize-space($selectedName/@use) or
                string($prefix_trimmed) or
                string($given_trimmed)
            ">, </xsl:if>              
              "family": "<xsl:value-of select="$family_trimmed"/>"
        </xsl:if>

        <!-- suffix -->
        <xsl:variable name="suffix_trimmed">
          <xsl:call-template name="string-trim">
            <xsl:with-param name="text" select="$selectedName/ccda:suffix"/>
          </xsl:call-template>
        </xsl:variable>
        <xsl:if test="string($suffix_trimmed)">
            <xsl:if test="
                normalize-space($selectedName/@use) or
                string($prefix_trimmed) or
                string($given_trimmed) or
                string($family_trimmed)
            ">, </xsl:if>
            "suffix": ["<xsl:value-of select="$suffix_trimmed"/>"]
        </xsl:if>

        <!-- period -->
        <xsl:if test="
            (
              $selectedName/ccda:validTime/ccda:low
              and normalize-space($selectedName/ccda:validTime/ccda:low/@value)
              and not(
                $selectedName/ccda:validTime/ccda:low/@nullFlavor = 'UNK'
                or $selectedName/ccda:validTime/ccda:low/@nullFlavor = 'NA'
              )
            )
            or
            (
              $selectedName/ccda:validTime/ccda:high
              and normalize-space($selectedName/ccda:validTime/ccda:high/@value)
              and not(
                $selectedName/ccda:validTime/ccda:high/@nullFlavor = 'UNK'
                or $selectedName/ccda:validTime/ccda:high/@nullFlavor = 'NA'
              )
            )
        ">
            <xsl:if test="
                string($given_trimmed) or
                string($prefix_trimmed) or
                string($family_trimmed) or
                string($suffix_trimmed) or
                normalize-space($selectedName/@use)
            ">, </xsl:if>

            "period": {
                <xsl:if test="
                    $selectedName/ccda:validTime/ccda:low
                    and normalize-space($selectedName/ccda:validTime/ccda:low/@value)
                    and not(
                      $selectedName/ccda:validTime/ccda:low/@nullFlavor = 'UNK'
                      or $selectedName/ccda:validTime/ccda:low/@nullFlavor = 'NA'
                    )
                ">
                    "start": "<xsl:call-template name="formatDateTime">
                                  <xsl:with-param name="dateTime"
                                      select="normalize-space($selectedName/ccda:validTime/ccda:low/@value)"/>
                              </xsl:call-template>"
                    <xsl:if test="
                        $selectedName/ccda:validTime/ccda:high
                        and normalize-space($selectedName/ccda:validTime/ccda:high/@value)
                        and not(
                          $selectedName/ccda:validTime/ccda:high/@nullFlavor = 'UNK'
                          or $selectedName/ccda:validTime/ccda:high/@nullFlavor = 'NA'
                        )
                    ">,</xsl:if>
                </xsl:if>

                <xsl:if test="
                    $selectedName/ccda:validTime/ccda:high
                    and normalize-space($selectedName/ccda:validTime/ccda:high/@value)
                    and not(
                      $selectedName/ccda:validTime/ccda:high/@nullFlavor = 'UNK'
                      or $selectedName/ccda:validTime/ccda:high/@nullFlavor = 'NA'
                    )
                ">
                    "end": "<xsl:call-template name="formatDateTime">
                                <xsl:with-param name="dateTime"
                                    select="normalize-space($selectedName/ccda:validTime/ccda:high/@value)"/>
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
      <xsl:when test="$screeningCode = 'NYSAHCHRSN' or $screeningCode = 'NYS-AHC-HRSN'"><xsl:value-of select='$baseFhirUrl'/>/CodeSystem/NYS-HRSN-Questionnaire</xsl:when>
      <xsl:when test="$screeningCode = '96777-8' or $screeningCode = '97023-6' or $screeningCode = '100698-0'">http://loinc.org</xsl:when>
      <xsl:otherwise>
        <xsl:text>http://loinc.org</xsl:text>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <!-- "Function" to trim leading and trailing spaces -->  
  <xsl:template name="string-trim">
    <xsl:param name="text"/>

    <!-- trim leading spaces -->
    <xsl:choose>
      <xsl:when test="starts-with($text, ' ')">
        <xsl:call-template name="string-trim">
          <xsl:with-param name="text" select="substring($text, 2)"/>
        </xsl:call-template>
      </xsl:when>

      <!-- trim trailing spaces -->
      <xsl:when test="substring($text, string-length($text)) = ' '">
        <xsl:call-template name="string-trim">
          <xsl:with-param name="text"
            select="substring($text, 1, string-length($text) - 1)"/>
        </xsl:call-template>
      </xsl:when>

      <!-- Return result -->
      <xsl:otherwise>
        <xsl:value-of select="$text"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <xsl:template name="clean-telecom-value">
    <xsl:param name="value"/>

    <!-- Step 1: Trim first -->
    <xsl:variable name="trimmed">
      <xsl:call-template name="string-trim">
        <xsl:with-param name="text" select="$value"/>
      </xsl:call-template>
    </xsl:variable>

    <!-- Step 2: Lowercase copy for prefix checking -->
    <xsl:variable name="lower"
      select="translate($trimmed,
        'ABCDEFGHIJKLMNOPQRSTUVWXYZ',
        'abcdefghijklmnopqrstuvwxyz')" />

    <!-- Step 3: Remove known prefixes -->
    <xsl:choose>

      <!-- tel: -->
      <xsl:when test="starts-with($lower, 'tel:')">
        <xsl:call-template name="string-trim">
          <xsl:with-param name="text" select="substring($trimmed, 5)"/>
        </xsl:call-template>
      </xsl:when>

      <!-- fax: -->
      <xsl:when test="starts-with($lower, 'fax:')">
        <xsl:call-template name="string-trim">
          <xsl:with-param name="text" select="substring($trimmed, 5)"/>
        </xsl:call-template>
      </xsl:when>

      <!-- mailto: -->
      <xsl:when test="starts-with($lower, 'mailto:')">
        <xsl:call-template name="string-trim">
          <xsl:with-param name="text" select="substring($trimmed, 8)"/>
        </xsl:call-template>
      </xsl:when>

      <!-- pager: -->
      <xsl:when test="starts-with($lower, 'pager:')">
        <xsl:call-template name="string-trim">
          <xsl:with-param name="text" select="substring($trimmed, 7)"/>
        </xsl:call-template>
      </xsl:when>

      <!-- sms: -->
      <xsl:when test="starts-with($lower, 'sms:')">
        <xsl:call-template name="string-trim">
          <xsl:with-param name="text" select="substring($trimmed, 5)"/>
        </xsl:call-template>
      </xsl:when>

      <!-- no prefix -->
      <xsl:otherwise> <xsl:value-of select="$trimmed"/> </xsl:otherwise>

    </xsl:choose>
  </xsl:template>

  <!-- Render address array if there are any addresses without nullFlavor -->
  <xsl:template name="build-address-object">
    <xsl:param name="addr"/>
    <xsl:param name="resource_name"/>
    {
      <!-- Pre-calculate trimmed values -->
      <xsl:variable name="street">
        <xsl:for-each select="$addr/ccda:streetAddressLine[not(@nullFlavor) and normalize-space(.) != '']">
          <xsl:variable name="line">
            <xsl:call-template name="string-trim">
              <xsl:with-param name="text" select="."/>
            </xsl:call-template>
          </xsl:variable>

          <xsl:if test="string($line)">
            <xsl:value-of select="$line"/>
            <xsl:if test="position()!=last()">, </xsl:if>
          </xsl:if>
        </xsl:for-each>
      </xsl:variable>

      <xsl:variable name="city">
        <xsl:call-template name="string-trim">
          <xsl:with-param name="text" select="$addr/ccda:city[not(@nullFlavor) and normalize-space(.) != '']"/>
        </xsl:call-template>
      </xsl:variable>

      <xsl:variable name="district">
        <xsl:call-template name="string-trim">
          <xsl:with-param name="text" select="$addr/ccda:county"/>
        </xsl:call-template>
      </xsl:variable>

      <xsl:variable name="state">
        <xsl:call-template name="string-trim">
          <xsl:with-param name="text" select="$addr/ccda:state[not(@nullFlavor) and normalize-space(.) != '']"/>
        </xsl:call-template>
      </xsl:variable>

      <xsl:variable name="zip">
        <xsl:call-template name="string-trim">
          <xsl:with-param name="text" select="$addr/ccda:postalCode[not(@nullFlavor) and normalize-space(.) != '']"/>
        </xsl:call-template>
      </xsl:variable>

      <xsl:variable name="country">
        <xsl:call-template name="string-trim">
          <xsl:with-param name="text" select="$addr/ccda:country"/>
        </xsl:call-template>
      </xsl:variable>

      <!-- Build the final string -->
      <xsl:variable name="formattedAddress">
          <xsl:if test="string-length($street) &gt; 0">
              <xsl:value-of select="$street"/>
          </xsl:if>

          <xsl:if test="$city != ''">
              <xsl:if test="$street != ''">, </xsl:if>
              <xsl:value-of select="$city"/>
          </xsl:if>

          <xsl:if test="$state != ''">
              <xsl:if test="$city != '' or $street != ''">, </xsl:if>
              <xsl:value-of select="$state"/>
          </xsl:if>

          <xsl:if test="$zip != ''">
              <xsl:text> </xsl:text>
              <xsl:value-of select="$zip"/>
          </xsl:if>
      </xsl:variable>

      <!-- use -->
      <xsl:if test="$addr/@use">
        <xsl:variable name="use_trimmed" select="normalize-space($addr/@use)"/>
        "use": "<xsl:choose>
          <xsl:when test="$use_trimmed='WP' or $use_trimmed='DIR' or $use_trimmed='PUB'">work</xsl:when>
          <!-- <xsl:when test="$use_trimmed='BA'">billing</xsl:when> -->
          <xsl:when test="$use_trimmed='TMP'">temp</xsl:when>
          <xsl:when test="$use_trimmed='BAD'">old</xsl:when>
          <xsl:otherwise>
            <xsl:choose>
              <xsl:when test="$resource_name='Location' or $resource_name='Organization'">work</xsl:when>
              <xsl:otherwise>home</xsl:otherwise>
            </xsl:choose>
          </xsl:otherwise>
        </xsl:choose>"
        <xsl:if test="string($formattedAddress) or $addr/ccda:streetAddressLine or string($city) or string($district) or string($state) or string($zip) or string($country)">,</xsl:if>
      </xsl:if>

      <!-- text -->
      <xsl:if test="string($formattedAddress)">
        "text": "<xsl:value-of select="$formattedAddress"/>"
        <xsl:if test="$addr/ccda:streetAddressLine or string($city) or string($district) or string($state) or string($zip) or string($country)">,</xsl:if>
      </xsl:if>

      <!-- line -->
      <xsl:if test="$addr/ccda:streetAddressLine[not(@nullFlavor)]">
        "line": [
          <xsl:for-each select="$addr/ccda:streetAddressLine[not(@nullFlavor)]">
            "<xsl:call-template name="string-trim">
                <xsl:with-param name="text" select="."/>
              </xsl:call-template>"
            <xsl:if test="position()!=last()">,</xsl:if>
          </xsl:for-each>
        ]
        <xsl:if test="string($city) or string($district) or string($state) or string($zip) or string($country)">,</xsl:if>
      </xsl:if>

      <!-- city -->
      <xsl:if test="string($city)">
        "city": "<xsl:value-of select="$city"/>"
        <xsl:if test="string($district) or string($state) or string($zip) or string($country)">,</xsl:if>
      </xsl:if>

      <!-- district -->
      <xsl:if test="string($district)">
        "district": "<xsl:value-of select="$district"/>"
        <xsl:if test="string($state) or string($zip) or string($country)">,</xsl:if>
      </xsl:if>

      <!-- state -->
      <xsl:if test="string($state)">
        "state": "<xsl:value-of select="$state"/>"
        <xsl:if test="string($zip) or string($country)">,</xsl:if>
      </xsl:if>

      <!-- postalCode -->
      <xsl:if test="string($zip)">
        "postalCode": "<xsl:value-of select="$zip"/>"
        <xsl:if test="string($country)">,</xsl:if>
      </xsl:if>

      <!-- country -->
      <xsl:if test="string($country)">
        "country": "<xsl:value-of select="$country"/>"
      </xsl:if>

      <!-- period -->
      <xsl:variable name="periodLow" select="$addr/ccda:useablePeriod/ccda:low/@value"/>
      <xsl:variable name="periodHigh" select="$addr/ccda:useablePeriod/ccda:high/@value"/>

      <xsl:if test="string($periodLow) or string($periodHigh)">
        , "period": {
          <xsl:if test="string($periodLow)">
            "start": "<xsl:call-template name="formatDateTime">
                        <xsl:with-param name="dateTime" select="$periodLow"/>
                      </xsl:call-template>"
            <xsl:if test="string($periodHigh)">,</xsl:if>
          </xsl:if>

          <xsl:if test="string($periodHigh)">
            "end": "<xsl:call-template name="formatDateTime">
                      <xsl:with-param name="dateTime" select="$periodHigh"/>
                    </xsl:call-template>"
          </xsl:if>
        }
      </xsl:if>
    }
  </xsl:template>

  <!-- Gives an array of address objects if there are any addresses without nullFlavor, used for Patient Address and Organization Address. -->
  <xsl:template name="build-address-array">
    <xsl:param name="addresses"/>
    <xsl:param name="resource_name"/>
    <xsl:if test="$addresses[not(@nullFlavor)]">
      , "address": [
        <xsl:for-each select="$addresses[not(@nullFlavor)]">
          <xsl:call-template name="build-address-object">
            <xsl:with-param name="addr" select="."/>
            <xsl:with-param name="resource_name" select="$resource_name"/>
          </xsl:call-template>
          <xsl:if test="position() != last()">,</xsl:if>
        </xsl:for-each>
      ]
    </xsl:if>
  </xsl:template>

  <!-- Gives an address object if there are any addresses without nullFlavor, used for Location address. -->
  <xsl:template name="build-address-object-only">
    <xsl:param name="addresses"/>
    <xsl:param name="resource_name"/>
    <xsl:if test="$addresses[not(@nullFlavor)]">
      , "address":        
          <xsl:call-template name="build-address-object">
            <xsl:with-param name="addr" select="$addresses[not(@nullFlavor)][1]"/>
            <xsl:with-param name="resource_name" select="$resource_name"/>
          </xsl:call-template>
    </xsl:if>
  </xsl:template>

  <xsl:template name="mapProcedureStatus">
    <xsl:param name="statusCode"/>
            
    <!-- Convert value to lowercase for case-insensitive matching -->
    <xsl:variable name="cleanCode"
        select="translate(normalize-space(string($statusCode)),
                          'ABCDEFGHIJKLMNOPQRSTUVWXYZ',
                          'abcdefghijklmnopqrstuvwxyz')" />
    <xsl:choose>
        <xsl:when test="$cleanCode = 'completed'">completed</xsl:when>
        <xsl:when test="$cleanCode = 'active'">in-progress</xsl:when>
        <xsl:when test="$cleanCode = 'aborted'">stopped</xsl:when>
        <xsl:when test="$cleanCode = 'suspended'">on-hold</xsl:when>
        <xsl:when test="$cleanCode = 'nullified'">entered-in-error</xsl:when>
        <xsl:otherwise>unknown</xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <xsl:template name="mapQuestionnaireStatus">
    <xsl:param name="statusCode"/>
    <xsl:choose>
        <xsl:when test="$statusCode = 'completed'">active</xsl:when>
        <xsl:when test="$statusCode = 'final'">active</xsl:when>
        <xsl:when test="$statusCode = 'active'">active</xsl:when>
        <xsl:when test="$statusCode = 'aborted'">retired</xsl:when>
        <xsl:when test="$statusCode = 'cancelled'">retired</xsl:when>
        <xsl:when test="$statusCode = 'held'">draft</xsl:when>
        <xsl:when test="$statusCode = 'suspended'">draft</xsl:when>
        <xsl:when test="$statusCode = 'nullified'">retired</xsl:when>
        <xsl:otherwise>unknown</xsl:otherwise>
    </xsl:choose>
  </xsl:template>

</xsl:stylesheet>