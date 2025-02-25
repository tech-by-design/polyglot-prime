<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0"
    xmlns:hl7="urn:hl7-org:v3"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xmlns="urn:hl7-org:v3"
    xmlns:voc="urn:hl7-org:v3/voc"
    xsi:schemaLocation="urn:hl7-org:v3 CCDA/XSD_TechBd/CDA.xsd"
    exclude-result-prefixes="hl7">

    <xsl:output method="xml" indent="yes"/>
    <xsl:strip-space elements="*"/>

    <!-- Root template -->
    <xsl:template match="/hl7:ClinicalDocument">
        <xsl:copy>
            <xsl:copy-of select="@*"/>
            
            <!-- Keep necessary elements -->
            <xsl:copy-of select="hl7:realmCode | hl7:typeId | hl7:templateId | hl7:id | hl7:code | hl7:title"/>
            <xsl:copy-of select="hl7:effectiveTime | hl7:confidentialityCode | hl7:languageCode"/>
            <xsl:copy-of select="hl7:recordTarget/hl7:patientRole"/>
            <xsl:copy-of select="hl7:author"/>
            <xsl:copy-of select="hl7:custodian"/>
            <xsl:copy-of select="hl7:authorization/hl7:consent"/>
            <xsl:copy-of select="hl7:componentOf/hl7:encompassingEncounter"/>

            <component>
                <structuredBody>
                    <!-- Extract and place the single Sexual Orientation entry -->
                    <xsl:variable name="sexualOrientationEntry" select="//hl7:section[hl7:code[@code='29762-2']]/hl7:entry[hl7:observation/hl7:code[@code='76690-7']]" />
                    <xsl:if test="$sexualOrientationEntry">
                        <component>
                            <sexualOrientation>
                                <xsl:copy-of select="$sexualOrientationEntry" />
                            </sexualOrientation>
                        </component>
                    </xsl:if>

                    <!-- Extract and place all other observations -->
                    <xsl:variable name="observations" select="//hl7:section[hl7:code[@code='29762-2']]/hl7:entry[not(hl7:observation/hl7:code[@code='76690-7']) and hl7:observation/hl7:code[@codeSystemName='LOINC' or @codeSystemName='SNOMED' or @codeSystemName='SNOMED CT']]" />
                    <xsl:if test="$observations">
                        <component>
                            <observations>
                                <xsl:copy-of select="$observations" />
                            </observations>
                        </component>
                    </xsl:if>
                </structuredBody>
            </component>
        </xsl:copy>
    </xsl:template>

</xsl:stylesheet>