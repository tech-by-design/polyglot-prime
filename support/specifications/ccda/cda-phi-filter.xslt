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
    <xsl:template match="/">
        <xsl:apply-templates select="hl7:ClinicalDocument"/>
    </xsl:template>

    <xsl:template match="hl7:ClinicalDocument">
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
                    <xsl:apply-templates select="hl7:component/hl7:structuredBody/hl7:component"/>
                </structuredBody>
            </component>
        </xsl:copy>
    </xsl:template>

    <!-- Process each component only if it has relevant data -->
    <xsl:template match="hl7:component">
        <xsl:if test=".//hl7:entry">
            <component>
                <xsl:apply-templates select="hl7:section"/>
            </component>
        </xsl:if>
    </xsl:template>

    <!-- Process sections and replace them with appropriate tags -->
    <xsl:template match="hl7:section">
        <xsl:choose>
            <!-- Replace with <sexualOrientation> if it has Sexual Orientation observation -->
            <xsl:when test="hl7:entry/hl7:observation/hl7:code[@code='76690-7']">
                <sexualOrientation>
                    <xsl:copy-of select="hl7:templateId"/>
                    <xsl:apply-templates select="hl7:entry"/>
                </sexualOrientation>
            </xsl:when>

            <!-- Replace with <Questionnaire> if it has the correct templateId -->
            <xsl:when test="hl7:templateId[@root='2.16.840.1.113883.19.1000.2.1']">
                <Questionnaire>
                    <xsl:copy-of select="hl7:templateId"/>
                    <xsl:copy-of select="hl7:title"/>
                    <xsl:copy-of select="hl7:text"/>
                    <xsl:apply-templates select="hl7:entry"/>
                </Questionnaire>
            </xsl:when>

            <!-- Replace with <observations> for all other cases with LOINC/SNOMED codeSystem -->
            <xsl:when test="hl7:entry/hl7:observation/hl7:code[@codeSystemName='LOINC' or @codeSystemName='SNOMED' or @codeSystemName='SNOMED CT']">
                <observations>
                    <xsl:copy-of select="hl7:templateId"/>
                    <xsl:apply-templates select="hl7:entry"/>
                </observations>
            </xsl:when>
        </xsl:choose>
    </xsl:template>

    <!-- Process entry elements and copy observation -->
    <xsl:template match="hl7:entry">
        <entry>
            <xsl:copy-of select="hl7:observation"/>
        </entry>
    </xsl:template>

</xsl:stylesheet>