<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0"
    xmlns:hl7="urn:hl7-org:v3"
    exclude-result-prefixes="hl7">

    <!-- Define output method -->
    <xsl:output method="xml" indent="yes" encoding="UTF-8"/>

    <!-- Root template -->
    <xsl:template match="/hl7:ClinicalDocument">
        <TransformedCCD>
            <!-- Extract Patient Information -->
            <Patient>
                <xsl:for-each select="hl7:recordTarget/hl7:patientRole">
                    <Name>
                        <xsl:value-of select="hl7:patient/hl7:name/hl7:given"/>
                        <xsl:text> </xsl:text>
                        <xsl:value-of select="hl7:patient/hl7:name/hl7:family"/>
                    </Name>
                    <Gender>
                        <xsl:value-of select="hl7:patient/hl7:administrativeGenderCode/@code"/>
                    </Gender>
                    <BirthDate>
                        <xsl:value-of select="hl7:patient/hl7:birthTime/@value"/>
                    </BirthDate>
                </xsl:for-each>
            </Patient>

            <!-- Extract Organization Information -->
            <Organization>
                <xsl:for-each select="hl7:author/hl7:assignedAuthor/hl7:representedOrganization">
                    <Name>
                        <xsl:value-of select="hl7:name"/>
                    </Name>
                    <Address>
                        <xsl:value-of select="hl7:addr/hl7:streetAddressLine"/>
                        <xsl:text>, </xsl:text>
                        <xsl:value-of select="hl7:addr/hl7:city"/>
                        <xsl:text>, </xsl:text>
                        <xsl:value-of select="hl7:addr/hl7:state"/>
                        <xsl:text> </xsl:text>
                        <xsl:value-of select="hl7:addr/hl7:postalCode"/>
                    </Address>
                </xsl:for-each>
            </Organization>

            <!-- Extract Custodian Information -->
            <Custodian>
                <xsl:for-each select="hl7:custodian/hl7:assignedCustodian/hl7:representedCustodianOrganization">
                    <Name>
                        <xsl:value-of select="hl7:name"/>
                    </Name>
                    <ID>
                        <xsl:value-of select="hl7:id/@root"/>
                    </ID>
                </xsl:for-each>
            </Custodian>

            <!-- Extract Encounter Details -->
            <Encounters>
                <xsl:for-each select="hl7:componentOf/hl7:encompassingEncounter">
                    <Encounter>
                        <ID>
                            <xsl:value-of select="hl7:id/@root"/>
                        </ID>
                        <Code>
                            <xsl:value-of select="hl7:code/@displayName"/>
                        </Code>
                        <StartDate>
                            <xsl:value-of select="hl7:effectiveTime/hl7:low/@value"/>
                        </StartDate>
                        <EndDate>
                            <xsl:value-of select="hl7:effectiveTime/hl7:high/@value"/>
                        </EndDate>
                    </Encounter>
                </xsl:for-each>
            </Encounters>

            <!-- Extract Observations -->
            <Observations>
                <xsl:for-each select="hl7:component/hl7:structuredBody/hl7:component/hl7:section/hl7:entry/hl7:observation">
                    <Observation>
                        <Code>
                            <xsl:value-of select="hl7:code/@displayName"/>
                        </Code>
                        <Value>
                            <xsl:value-of select="hl7:value/@displayName"/>
                        </Value>
                        <Date>
                            <xsl:value-of select="hl7:effectiveTime/@value"/>
                        </Date>
                    </Observation>
                </xsl:for-each>
            </Observations>
        </TransformedCCD>
    </xsl:template>

    <!-- Identity transform to handle unmatched nodes -->
    <xsl:template match="@*|node()">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()"/>
        </xsl:copy>
    </xsl:template>
</xsl:stylesheet>