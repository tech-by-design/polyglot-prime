<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0"
    xmlns:hl7="urn:hl7-org:v3"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xmlns="urn:hl7-org:v3"
    xmlns:voc="urn:hl7-org:v3/voc"
    xsi:schemaLocation="urn:hl7-org:v3 ../ccda-techbd-schema-files/CDA.xsd"
    exclude-result-prefixes="hl7">

    <!-- Define output method -->
    <xsl:output method="xml" indent="yes" encoding="UTF-8"/>

    <!-- Root template -->
    <xsl:template match="/hl7:ClinicalDocument">
        <ClinicalDocument>
            <!-- Extract Header Information -->
            <header>
                <typeId>
                    <root><xsl:value-of select="hl7:typeId/@root"/></root>
                    <codeSystem><xsl:value-of select="hl7:typeId/@extension"/></codeSystem>
                </typeId>
                <templateId>
                    <root><xsl:value-of select="hl7:templateId/@root"/></root>
                </templateId>
                <id>
                    <root><xsl:value-of select="hl7:id/@root"/></root>
                </id>
                <title>
                    <xsl:value-of select="hl7:title"/>
                </title>
                <code>
                    <code><xsl:value-of select="hl7:code/@code"/></code>
                    <codeSystem><xsl:value-of select="hl7:code/@codeSystem"/></codeSystem>
                    <codeSystemName><xsl:value-of select="hl7:code/@codeSystemName"/></codeSystemName>
                    <displayName><xsl:value-of select="hl7:code/@displayName"/></displayName>
                </code>
                <effectiveTime>
                    <value><xsl:value-of select="hl7:effectiveTime/@value"/></value>
                </effectiveTime>
                <confidentialityCode>
                    <code><xsl:value-of select="hl7:confidentialityCode/@code"/></code>
                    <codeSystem><xsl:value-of select="hl7:confidentialityCode/@codeSystem"/></codeSystem>
                </confidentialityCode>
            </header>
            <!-- Extract Patient Information -->
            <recordTarget>
                <patientRole>
                    <xsl:for-each select="hl7:recordTarget/hl7:patientRole">
                        <id>
                            <root><xsl:value-of select="hl7:id/@root"/></root>
                            <extension><xsl:value-of select="hl7:id/@extension"/></extension>
                        </id>
                        <addr>
                            <streetAddressLine><xsl:value-of select="hl7:addr/hl7:streetAddressLine"/></streetAddressLine>
                            <city><xsl:value-of select="hl7:addr/hl7:city"/></city>
                            <state><xsl:value-of select="hl7:addr/hl7:state"/></state>
                            <county><xsl:value-of select="hl7:addr/hl7:county"/></county>
                            <postalCode><xsl:value-of select="hl7:addr/hl7:postalCode"/></postalCode>
                        </addr>
                        <telecom>
                            <use><xsl:value-of select="hl7:telecom/@use"/></use>
                            <value><xsl:value-of select="hl7:telecom/@value"/></value>
                        </telecom>
                        <patient>
                            <name>
                                <prefix><xsl:value-of select="hl7:patient/hl7:name/hl7:prefix"/></prefix>
                                <given><xsl:value-of select="hl7:patient/hl7:name/hl7:given"/></given>
                                <family><xsl:value-of select="hl7:patient/hl7:name/hl7:family"/></family>
                                <suffix><xsl:value-of select="hl7:patient/hl7:name/hl7:suffix"/></suffix>
                            </name>
                            <administrativeGenderCode>
                                <code><xsl:value-of select="hl7:patient/hl7:administrativeGenderCode/@code"/></code>
                            </administrativeGenderCode>
                            <birthTime>
                                <value><xsl:value-of select="hl7:patient/hl7:birthTime/@value"/></value>
                            </birthTime>
                            <raceCode>
                                <code><xsl:value-of select="hl7:patient/hl7:raceCode/@code"/></code>
                                <displayName><xsl:value-of select="hl7:patient/hl7:raceCode/@displayName"/></displayName>
                                <codeSystem><xsl:value-of select="hl7:patient/hl7:raceCode/@codeSystem"/></codeSystem>
                                <codeSystemName><xsl:value-of select="hl7:patient/hl7:raceCode/@codeSystemName"/></codeSystemName>
                            </raceCode>
                            <ethnicGroupCode>
                                <code><xsl:value-of select="hl7:patient/hl7:ethnicGroupCode/@code"/></code>
                                <displayName><xsl:value-of select="hl7:patient/hl7:ethnicGroupCode/@displayName"/></displayName>
                                <codeSystem><xsl:value-of select="hl7:patient/hl7:ethnicGroupCode/@codeSystem"/></codeSystem>
                                <codeSystemName><xsl:value-of select="hl7:patient/hl7:ethnicGroupCode/@codeSystemName"/></codeSystemName>
                            </ethnicGroupCode>
                            <languageCommunication>
                                <languageCode>
                                    <code><xsl:value-of select="hl7:patient/hl7:languageCommunication/hl7:languageCode/@code"/></code>
                                </languageCode>
                                <preferenceInd>
                                    <value><xsl:value-of select="hl7:patient/hl7:languageCommunication/hl7:preferenceInd/@value"/></value>
                                </preferenceInd>
                            </languageCommunication>
                        </patient>
                    </xsl:for-each>
                </patientRole>
            </recordTarget>

            <!-- Extract Author Information -->
            <author>
                <xsl:for-each select="hl7:author">
                    <time>
                        <value><xsl:value-of select="hl7:time/@value"/></value>
                    </time>
                    <assignedAuthor>
                        <id>
                            <root><xsl:value-of select="hl7:assignedAuthor/hl7:id/@root"/></root>
                            <extension><xsl:value-of select="hl7:assignedAuthor/hl7:id/@extension"/></extension>
                        </id>
                        <representedOrganization>
                            <id>
                                <root><xsl:value-of select="hl7:assignedAuthor/hl7:representedOrganization/hl7:id/@root"/></root>
                                <extension><xsl:value-of select="hl7:assignedAuthor/hl7:representedOrganization/hl7:id/@extension"/></extension>
                            </id>
                            <name>
                                <xsl:value-of select="hl7:assignedAuthor/hl7:representedOrganization/hl7:name"/>
                            </name>
                            <telecom>
                                <use><xsl:value-of select="hl7:assignedAuthor/hl7:representedOrganization/hl7:telecom/@use"/></use>
                                <value><xsl:value-of select="hl7:assignedAuthor/hl7:representedOrganization/hl7:telecom/@value"/></value>
                            </telecom>
                            <addr>
                                <streetAddressLine><xsl:value-of select="hl7:assignedAuthor/hl7:representedOrganization/hl7:addr/hl7:streetAddressLine"/></streetAddressLine>
                                <city><xsl:value-of select="hl7:assignedAuthor/hl7:representedOrganization/hl7:addr/hl7:city"/></city>
                                <state><xsl:value-of select="hl7:assignedAuthor/hl7:representedOrganization/hl7:addr/hl7:state"/></state>
                                <postalCode><xsl:value-of select="hl7:assignedAuthor/hl7:representedOrganization/hl7:addr/hl7:postalCode"/></postalCode>
                                <county><xsl:value-of select="hl7:assignedAuthor/hl7:representedOrganization/hl7:addr/hl7:county"/></county>
                            </addr>
                        </representedOrganization>
                    </assignedAuthor>
                </xsl:for-each>
            </author>

            <!-- Extract Custodian Information -->
            <custodian>
                <xsl:for-each select="hl7:custodian/hl7:assignedCustodian/hl7:representedCustodianOrganization">
                    <assignedCustodian>
                        <representedCustodianOrganization>
                            <id>
                                <root><xsl:value-of select="hl7:id/@root"/></root>
                            </id>
                            <name>
                                <xsl:value-of select="hl7:name"/>
                            </name>
                        </representedCustodianOrganization>
                    </assignedCustodian>
                </xsl:for-each>
            </custodian>

            <!-- Extract Consent Information -->
            <authorization>
                <xsl:for-each select="hl7:authorization/hl7:consent">
                    <consent>                    
                        <code>
                            <code><xsl:value-of select="hl7:code/@code"/></code>
                            <displayName><xsl:value-of select="hl7:code/@displayName"/></displayName>
                            <codeSystem><xsl:value-of select="hl7:code/@codeSystem"/></codeSystem>
                        </code>
                        <statusCode>
                            <code><xsl:value-of select="hl7:statusCode/@code"/></code>
                        </statusCode>
                        <effectiveTime>
                            <value><xsl:value-of select="hl7:effectiveTime/@value"/></value>
                        </effectiveTime>
                        <entry>
                            <act>
                                <classCode><xsl:value-of select="hl7:entry/hl7:act/@classCode"/></classCode>
                                <moodCode><xsl:value-of select="hl7:entry/hl7:act/@moodCode"/></moodCode>
                                <code>
                                    <code><xsl:value-of select="hl7:entry/hl7:act/hl7:code/@code"/></code>
                                    <displayName><xsl:value-of select="hl7:entry/hl7:act/hl7:code/@displayName"/></displayName>
                                    <codeSystem><xsl:value-of select="hl7:entry/hl7:act/hl7:code/@codeSystem"/></codeSystem>
                                </code>
                            </act>
                        </entry>
                        <policy>
                            <id><xsl:value-of select="hl7:policy/@id"/></id>
                        </policy>
                        <provision>
                            <type><xsl:value-of select="hl7:provision/@type"/></type>
                        </provision>
                    </consent>
                </xsl:for-each>                
            </authorization>

            <!-- Extract Encounter Details -->
            <componentOf>
                <xsl:for-each select="hl7:componentOf/hl7:encompassingEncounter">
                    <encompassingEncounter>
                        <id>
                            <root><xsl:value-of select="hl7:id/@root"/></root>
                            <extension><xsl:value-of select="hl7:id/@extension"/></extension>
                        </id>
                        <code>
                            <value><xsl:value-of select="hl7:code/@code"/></value>
                            <displayName><xsl:value-of select="hl7:code/@displayName"/></displayName>
                            <codeSystem><xsl:value-of select="hl7:code/@codeSystem"/></codeSystem>
                            <originalText><xsl:value-of select="hl7:code/hl7:originalText"/></originalText>
                        </code>
                        <effectiveTime>
                            <low>
                                <value><xsl:value-of select="hl7:effectiveTime/hl7:low/@value"/></value>
                            </low>
                            <high>
                                <value><xsl:value-of select="hl7:effectiveTime/hl7:high/@value"/></value>
                            </high>
                        </effectiveTime>
                        <locationDetails>
                            <healthCareFacility>
                                <id> 
                                    <extension><xsl:value-of select="hl7:location/hl7:healthCareFacility/hl7:id/@extension"/></extension>
                                    <root><xsl:value-of select="hl7:location/hl7:healthCareFacility/hl7:id/@root"/></root>
                                </id>
                                <code> 
                                    <code><xsl:value-of select="hl7:location/hl7:healthCareFacility/hl7:code/@code"/></code>
                                    <codeSystem><xsl:value-of select="hl7:location/hl7:healthCareFacility/hl7:code/@codeSystem"/></codeSystem>
                                    <displayName><xsl:value-of select="hl7:location/hl7:healthCareFacility/hl7:code/@displayName"/></displayName>
                                </code>
                                <locationAddr>
                                    <addr>
                                        <streetAddressLine><xsl:value-of select="hl7:location/hl7:healthCareFacility/hl7:location/hl7:addr/hl7:streetAddressLine"/></streetAddressLine>
                                        <city><xsl:value-of select="hl7:location/hl7:healthCareFacility/hl7:location/hl7:addr/hl7:city"/></city>
                                        <state><xsl:value-of select="hl7:location/hl7:healthCareFacility/hl7:location/hl7:addr/hl7:state"/></state>
                                        <postalCode><xsl:value-of select="hl7:location/hl7:healthCareFacility/hl7:location/hl7:addr/hl7:postalCode"/></postalCode>
                                        <country><xsl:value-of select="hl7:location/hl7:healthCareFacility/hl7:location/hl7:addr/hl7:country"/></country>
                                    </addr>
                                </locationAddr>
                                <serviceProviderOrganization>
                                    <name><xsl:value-of select="hl7:location/hl7:healthCareFacility/hl7:serviceProviderOrganization/hl7:name"/></name>
                                    <telecom>
                                        <use><xsl:value-of select="hl7:location/hl7:healthCareFacility/hl7:serviceProviderOrganization/hl7:telecom/@use"/></use>
                                        <value><xsl:value-of select="hl7:location/hl7:healthCareFacility/hl7:serviceProviderOrganization/hl7:telecom/@value"/></value>
                                    </telecom>
                                    <addr>
                                        <streetAddressLine><xsl:value-of select="hl7:location/hl7:healthCareFacility/hl7:serviceProviderOrganization/hl7:addr/hl7:streetAddressLine"/></streetAddressLine>
                                        <city><xsl:value-of select="hl7:location/hl7:healthCareFacility/hl7:serviceProviderOrganization/hl7:addr/hl7:city"/></city>
                                        <state><xsl:value-of select="hl7:location/hl7:healthCareFacility/hl7:serviceProviderOrganization/hl7:addr/hl7:state"/></state>
                                        <postalCode><xsl:value-of select="hl7:location/hl7:healthCareFacility/hl7:serviceProviderOrganization/hl7:addr/hl7:postalCode"/></postalCode>
                                        <country><xsl:value-of select="hl7:location/hl7:healthCareFacility/hl7:serviceProviderOrganization/hl7:addr/hl7:country"/></country>
                                    </addr>
                                </serviceProviderOrganization>
                            </healthCareFacility>
                        </locationDetails>
                    </encompassingEncounter>
                </xsl:for-each>
            </componentOf>

            <!-- Extract Observations -->
            <component>
                <structuredBody>
                    <xsl:for-each select="hl7:component/hl7:structuredBody/hl7:component/hl7:section">
                       <!-- <xsl:if test="hl7:code/@code = '29762-2'">   Social History -->
                            <component>
                                <Section>
                                    <!-- Extract TemplateId, Title, and Text if present -->
                                    <xsl:if test="hl7:templateId">
                                        <templateId>
                                            <root><xsl:value-of select="hl7:templateId/@root"/></root>
                                        </templateId>
                                    </xsl:if>
                                    <xsl:if test="hl7:title">
                                        <title>
                                            <xsl:value-of select="hl7:title"/>
                                        </title>
                                    </xsl:if>
                                    <xsl:if test="hl7:text">
                                        <text>
                                            <xsl:value-of select="hl7:text"/>
                                        </text>
                                    </xsl:if>

                                    <!-- Process observation Entries -->
                                    <xsl:for-each select="hl7:entry/hl7:observation">
                                        <xsl:if test = "hl7:code/@codeSystemName = 'LOINC' or 
                                                        hl7:code/@codeSystemName = 'SNOMED' or 
                                                        hl7:code/@codeSystemName = 'SNOMED CT'">
                                            <entry>
                                                <observation>
                                                    <templateId>
                                                        <root><xsl:value-of select="hl7:templateId/@root"/></root>
                                                    </templateId>
                                                    <id>
                                                        <root><xsl:value-of select="hl7:id/@root"/></root>
                                                    </id>
                                                    <code>
                                                        <code><xsl:value-of select="hl7:code/@code"/></code>
                                                        <displayName><xsl:value-of select="hl7:code/@displayName"/></displayName>
                                                        <codeSystem><xsl:value-of select="hl7:code/@codeSystem"/></codeSystem>
                                                        <codeSystemName><xsl:value-of select="hl7:code/@codeSystemName"/></codeSystemName>
                                                        <originalText><xsl:value-of select="hl7:code/hl7:originalText"/></originalText>
                                                    </code>
                                                    <statusCode>
                                                        <code><xsl:value-of select="hl7:statusCode/@code"/></code>
                                                    </statusCode>
                                                    <effectiveTime>
                                                        <value><xsl:value-of select="hl7:effectiveTime/@value"/></value>
                                                    </effectiveTime>
                                                    <interpretationCode>
                                                        <code><xsl:value-of select="hl7:interpretationCode/@code"/></code>
                                                        <displayName><xsl:value-of select="hl7:interpretationCode/@displayName"/></displayName>
                                                        <codeSystem><xsl:value-of select="hl7:interpretationCode/@codeSystem"/></codeSystem>
                                                        <codeSystemName><xsl:value-of select="hl7:interpretationCode/@codeSystemName"/></codeSystemName>
                                                    </interpretationCode>
                                                    <value>
                                                        <type><xsl:value-of select="hl7:value/@xsi:type"/></type>
                                                        <code><xsl:value-of select="hl7:value/@code"/></code>
                                                        <displayName><xsl:value-of select="hl7:value/@displayName"/></displayName>
                                                        <codeSystem><xsl:value-of select="hl7:value/@codeSystem"/></codeSystem>
                                                    </value>

                                                    <!-- Include Subject Information only if exists -->
                                                    <xsl:if test="hl7:subject">
                                                        <subject>
                                                            <xsl:if test="hl7:subject/hl7:relatedSubject/hl7:subject">
                                                                <relatedSubject>
                                                                    <subject>
                                                                        <name><xsl:value-of select="hl7:subject/hl7:relatedSubject/hl7:subject/hl7:name"/></name>
                                                                        <administrativeGenderCode>
                                                                            <code><xsl:value-of select="hl7:subject/hl7:relatedSubject/hl7:subject/hl7:administrativeGenderCode/@code"/></code>
                                                                        </administrativeGenderCode>
                                                                        <birthTime>
                                                                            <value><xsl:value-of select="hl7:subject/hl7:relatedSubject/hl7:subject/hl7:birthTime/@value"/></value>
                                                                        </birthTime>
                                                                    </subject>
                                                                </relatedSubject>
                                                            </xsl:if>
                                                        </subject>
                                                    </xsl:if>

                                                    <!-- Handle Nested Entry Relationships -->
                                                    <entryRelationship>
                                                        <xsl:for-each select="hl7:entryRelationship/hl7:observation">
                                                            <observation>
                                                                <id>
                                                                    <root><xsl:value-of select="hl7:id/@root"/></root>
                                                                </id>
                                                                <code>
                                                                    <code><xsl:value-of select="hl7:code/@code"/></code>
                                                                    <displayName><xsl:value-of select="hl7:code/@displayName"/></displayName>
                                                                    <codeSystem><xsl:value-of select="hl7:code/@codeSystem"/></codeSystem>
                                                                    <codeSystemName><xsl:value-of select="hl7:code/@codeSystemName"/></codeSystemName>
                                                                </code>
                                                            </observation>
                                                        </xsl:for-each>
                                                    </entryRelationship>
                                                </observation>
                                            </entry>
                                        </xsl:if>
                                    </xsl:for-each>
                                </Section>
                            </component>
                     <!--   </xsl:if> -->
                    </xsl:for-each>
                </structuredBody>
            </component>
        </ClinicalDocument>
    </xsl:template>

    <!-- Identity transform to handle unmatched nodes -->
    <xsl:template match="@*|node()">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()"/>
        </xsl:copy>
    </xsl:template>
</xsl:stylesheet>