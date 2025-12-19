<?xml version="1.0" encoding="UTF-8"?>
<!-- Version : 0.1.1 -->
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0"
    xmlns:hl7="urn:hl7-org:v3"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xmlns="urn:hl7-org:v3"
    xmlns:voc="urn:hl7-org:v3/voc"
    exclude-result-prefixes="hl7">

    <xsl:output method="xml" indent="yes"/>
    <xsl:strip-space elements="*"/>

    <!-- top-level variable: true if any consent observation 59284-0 exists -->
    <xsl:variable name="hasConsent" select="count(//hl7:observation[hl7:code/@code='59284-0']) &gt; 0"/>

    <!-- Root template -->
    <xsl:template match="/hl7:ClinicalDocument">
        <!-- Copy xml-stylesheet if present -->
        <xsl:if test="processing-instruction('xml-stylesheet')">
            <xsl:processing-instruction name="xml-stylesheet">
                <xsl:value-of select="processing-instruction('xml-stylesheet')"/>
            </xsl:processing-instruction>
        </xsl:if>
        <xsl:text>&#10;</xsl:text>

        <xsl:element name="ClinicalDocument">
            <!-- Copy all attributes except xsi:schemaLocation -->
            <xsl:for-each select="@*">
                <xsl:if test="name() != 'xsi:schemaLocation'">
                    <xsl:attribute name="{name()}">
                        <xsl:value-of select="."/>
                    </xsl:attribute>
                </xsl:if>
            </xsl:for-each>

            <!-- Always set schemaLocation -->
            <xsl:attribute name="xsi:schemaLocation">
                <xsl:text>urn:hl7-org:v3 ../ccda-techbd-schema-files/CDA.xsd</xsl:text>
            </xsl:attribute>

            <xsl:copy-of select="hl7:realmCode | hl7:typeId | hl7:templateId | hl7:id | hl7:code | hl7:title"/>
            <xsl:copy-of select="hl7:effectiveTime | hl7:confidentialityCode | hl7:languageCode"/>
            <xsl:copy-of select="hl7:setId | hl7:versionNumber"/>
            <xsl:copy-of select="hl7:recordTarget"/>
            <xsl:copy-of select="hl7:author"/>
            <xsl:copy-of select="hl7:custodian"/>
            <xsl:copy-of select="hl7:legalAuthenticator"/>
            <xsl:copy-of select="hl7:documentationOf"/>

            <!-- Consent details are in Social History section with  Entry.observation.entryRelationship.observation.code = "105511-0" and answer in Entry.observation.entryRelationship.observation.value (Yes/No)  -->
            <xsl:variable name="consent" select="
                hl7:component/hl7:structuredBody/hl7:component
                /hl7:section[hl7:code[@code='29762-2']]
                /hl7:entry/hl7:observation/hl7:entryRelationship
                /hl7:observation[hl7:code/@code = '105511-0']
            "/>   
            <xsl:choose>
                <xsl:when test="$consent">
                    <xsl:variable name="consentDisplay">
                        <xsl:choose>
                            <!-- <xsl:when test="$consent/hl7:value/@displayName = 'Permit' or $consent/hl7:value/@code = 'LA33-6'">permit</xsl:when> -->
                            <xsl:when test="$consent/hl7:value/@code = 'LA33-6'">permit</xsl:when>
                            <xsl:otherwise>deny</xsl:otherwise>
                        </xsl:choose>
                    </xsl:variable>
                    <authorization>
                        <consent>
                            <id root="2.16.840.1.113883.3.227.2845.10.41.1.1"/>
                            <code code="105511-0" codeSystem="2.16.840.1.113883.6.1" codeSystemName="LOINC">
                                <xsl:attribute name="displayName"><xsl:value-of select="$consentDisplay"/></xsl:attribute>
                            </code>
                            <xsl:copy-of select="$consent/hl7:statusCode"/>
                        </consent>
                    </authorization>
                </xsl:when>
                <xsl:when test="hl7:authorization/hl7:consent[hl7:code[@code='59284-0']]">
                    <xsl:copy-of select="hl7:authorization"/>
                </xsl:when>
                <xsl:otherwise>
                    <authorization>
                        <consent>
                            <id root="2.16.840.1.113883.3.933"/>
                            <code code="59284-0" codeSystem="2.16.840.1.113883.6.1" displayName="deny"/>
                            <statusCode code="completed"/>
                        </consent>
                    </authorization>
                </xsl:otherwise>
            </xsl:choose>

            <xsl:copy-of select="hl7:componentOf"/>

            <!-- Structured Body -->
            <component>
                <structuredBody>
                    <!-- Encounter -->
                        <xsl:variable name="encounterEntry" select="hl7:component/hl7:structuredBody/hl7:component/hl7:section[hl7:code[@code='46240-8']]/hl7:entry[hl7:encounter]"/>
                        <xsl:if test="$encounterEntry">
                            <component>
                                <section ID="encounters">
                                    <xsl:copy-of select="//hl7:section[hl7:code[@code='46240-8']]/@*"/>
                                    <xsl:copy-of select="hl7:templateId |hl7:code | hl7:title"/>
                                    <xsl:copy-of select="$encounterEntry"/>
                                </section>
                            </component>
                        </xsl:if>
                    
                    <!-- Observations -->
                    <!-- Athena: observations variable (returns hl7:entry nodes only; supports organizer/component/observation) -->
                    <xsl:variable name="observations" select="hl7:component/hl7:structuredBody/hl7:component/hl7:section[hl7:code[@code='29762-2']]/hl7:entry[
                        hl7:observation/hl7:entryRelationship/hl7:observation[
                            (
                                hl7:code[
                                    (@codeSystemName = 'LOINC' or @codeSystemName = 'SNOMED' or @codeSystemName = 'SNOMED CT')
                                    and (not(@code = 'UNK') and string-length(@code) > 0)
                                ]
                                and (
                                    hl7:value/hl7:translation/@code = 'X-SDOH-FLO-1570000066-Patient-unable-to-answer' 
                                    or hl7:value/hl7:translation/@code = 'X-SDOH-FLO-1570000066-Patient-declined'
                                    or (
                                        hl7:value[
                                            not(@code = 'UNK')
                                            and string-length(@code) > 0
                                            and string-length(@nullFlavor) = 0
                                        ]
                                    )
                                )
                            )
                            or (
                                hl7:code[@code='95614-4']
                                and string-length(hl7:value/@value) > 0
                            )
                        ]
                    ]                    
                    "/>
                    <xsl:if test="$observations">
                        <component>
                            <section ID="observations">
                                <xsl:copy-of select="//hl7:section[hl7:code[@code='29762-2']]/hl7:templateId"/>
                                <xsl:copy-of select="//hl7:section[hl7:code[@code='29762-2']]/hl7:code"/>
                                <xsl:copy-of select="//hl7:section[hl7:code[@code='29762-2']]/hl7:title"/>
                                <xsl:copy-of select="//hl7:section[hl7:code[@code='29762-2']]/@*"/>
                                <xsl:copy-of select="$observations"/>
                            </section>
                        </component>
                    </xsl:if>

                    <!-- Sexual Orientation -->
                    <xsl:variable name="sexualOrientationEntry" select="hl7:component/hl7:structuredBody/hl7:component/hl7:section[hl7:code[@code='29762-2']]/hl7:entry[hl7:observation/hl7:code[@code='76690-7']]"/>
                    <xsl:if test="$sexualOrientationEntry">
                        <component>
                            <section ID="sexualOrientation">
                                <xsl:copy-of select="//hl7:section[hl7:code[@code='76690-7']]/@*"/>
                                <xsl:copy-of select="hl7:templateId |hl7:code | hl7:title"/>
                                <xsl:copy-of select="$sexualOrientationEntry"/>
                            </section>
                        </component>
                    </xsl:if>
                </structuredBody>
            </component>

        </xsl:element>
    </xsl:template>

    <!-- Top-level observation template: redaction when no consent -->
    <xsl:template match="hl7:observation">
        <xsl:choose>
            <!-- Always allow the explicit consent observation itself -->
            <xsl:when test="hl7:code/@code = '59284-0'">
                <xsl:copy-of select="."/>
            </xsl:when>

            <!-- If any consent observation exists anywhere in the document, allow all observations -->
            <xsl:when test="$hasConsent">
                <xsl:copy-of select="."/>
            </xsl:when>

            <!-- Otherwise redact PHI: keep only code/effectiveTime and attributes -->
            <xsl:otherwise>
                <xsl:copy>
                    <xsl:copy-of select="hl7:code | hl7:effectiveTime | @*"/>
                    <!-- intentionally omit hl7:value, hl7:performer, hl7:participant, hl7:entryRelationship content, etc. -->
                </xsl:copy>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:template>

</xsl:stylesheet>
