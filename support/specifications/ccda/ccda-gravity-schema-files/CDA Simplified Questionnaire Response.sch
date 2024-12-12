<?xml version="1.0" encoding="utf-8" standalone="yes"?>
<!--

THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL LANTANA CONSULTING GROUP LLC, OR ANY OF THEIR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
Schematron generated from Trifolia on 11/25/2024
-->
<sch:schema xmlns:voc="http://www.lantanagroup.com/voc" xmlns:svs="urn:ihe:iti:svs:2008" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xmlns:sdtc="urn:hl7-org:sdtc" xmlns="urn:hl7-org:v3" xmlns:cda="urn:hl7-org:v3" xmlns:sch="http://purl.oclc.org/dsdl/schematron">
  <sch:ns prefix="voc" uri="http://www.lantanagroup.com/voc" />
  <sch:ns prefix="svs" uri="urn:ihe:iti:svs:2008" />
  <sch:ns prefix="xsi" uri="http://www.w3.org/2001/XMLSchema-instance" />
  <sch:ns prefix="sdtc" uri="urn:hl7-org:sdtc" />
  <sch:ns prefix="cda" uri="urn:hl7-org:v3" />
  <sch:phase id="errors">
    <sch:active pattern="p-urn-oid-2.16.840.1.113883.19.1000.3.1-errors" />
    <sch:active pattern="p-urn-oid-2.16.840.1.113883.19.1000.2.2-errors" />
    <sch:active pattern="p-urn-oid-2.16.840.1.113883.19.1000.1-errors" />
    <sch:active pattern="p-urn-oid-2.16.840.1.113883.19.1000.3.2-errors" />
    <sch:active pattern="p-urn-oid-2.16.840.1.113883.19.1000.3.3-errors" />
    <sch:active pattern="p-urn-oid-2.16.840.1.113883.19.1000.3.4-errors" />
    <sch:active pattern="p-urn-oid-2.16.840.1.113883.19.1000.3.5-errors" />
    <sch:active pattern="p-urn-oid-2.16.840.1.113883.19.1000.3.6-errors" />
    <sch:active pattern="p-urn-oid-2.16.840.1.113883.19.1000.3.7-errors" />
    <sch:active pattern="p-urn-oid-2.16.840.1.113883.19.1000.3.8-errors" />
    <sch:active pattern="p-urn-oid-2.16.840.1.113883.19.1000.3.9-errors" />
    <sch:active pattern="p-urn-oid-2.16.840.1.113883.19.1000.3.10-errors" />
    <sch:active pattern="p-urn-oid-2.16.840.1.113883.19.1000.3.11-errors" />
    <sch:active pattern="p-urn-oid-2.16.840.1.113883.19.1000.3.12-errors" />
    <sch:active pattern="p-urn-oid-2.16.840.1.113883.19.1000.3.14-errors" />
    <sch:active pattern="p-urn-oid-2.16.840.1.113883.19.1000.2.1-errors" />
    <sch:active pattern="p-urn-oid-2.16.840.1.113883.19.1000.2.3-errors" />
    <sch:active pattern="p-urn-oid-2.16.840.1.113883.19.1000.2.4-errors" />
    <sch:active pattern="p-urn-oid-2.16.840.1.113883.19.1000.2.5-errors" />
    <sch:active pattern="p-urn-oid-2.16.840.1.113883.19.1000.2.6-errors" />
    <sch:active pattern="p-urn-oid-2.16.840.1.113883.19.1000.2.7-errors" />
    <sch:active pattern="p-urn-oid-2.16.840.1.113883.19.1000.3.15-errors" />
  </sch:phase>
  <sch:phase id="warnings">
    <sch:active pattern="p-urn-oid-2.16.840.1.113883.19.1000.3.1-warnings" />
    <sch:active pattern="p-urn-oid-2.16.840.1.113883.19.1000.2.2-warnings" />
    <sch:active pattern="p-urn-oid-2.16.840.1.113883.19.1000.1-warnings" />
    <sch:active pattern="p-urn-oid-2.16.840.1.113883.19.1000.3.2-warnings" />
    <sch:active pattern="p-urn-oid-2.16.840.1.113883.19.1000.3.3-warnings" />
    <sch:active pattern="p-urn-oid-2.16.840.1.113883.19.1000.3.4-warnings" />
    <sch:active pattern="p-urn-oid-2.16.840.1.113883.19.1000.3.5-warnings" />
    <sch:active pattern="p-urn-oid-2.16.840.1.113883.19.1000.3.6-warnings" />
    <sch:active pattern="p-urn-oid-2.16.840.1.113883.19.1000.3.7-warnings" />
    <sch:active pattern="p-urn-oid-2.16.840.1.113883.19.1000.3.8-warnings" />
    <sch:active pattern="p-urn-oid-2.16.840.1.113883.19.1000.3.9-warnings" />
    <sch:active pattern="p-urn-oid-2.16.840.1.113883.19.1000.3.10-warnings" />
    <sch:active pattern="p-urn-oid-2.16.840.1.113883.19.1000.3.11-warnings" />
    <sch:active pattern="p-urn-oid-2.16.840.1.113883.19.1000.3.12-warnings" />
    <sch:active pattern="p-urn-oid-2.16.840.1.113883.19.1000.3.14-warnings" />
    <sch:active pattern="p-urn-oid-2.16.840.1.113883.19.1000.2.1-warnings" />
    <sch:active pattern="p-urn-oid-2.16.840.1.113883.19.1000.2.3-warnings" />
    <sch:active pattern="p-urn-oid-2.16.840.1.113883.19.1000.2.4-warnings" />
    <sch:active pattern="p-urn-oid-2.16.840.1.113883.19.1000.2.5-warnings" />
    <sch:active pattern="p-urn-oid-2.16.840.1.113883.19.1000.2.6-warnings" />
    <sch:active pattern="p-urn-oid-2.16.840.1.113883.19.1000.2.7-warnings" />
    <sch:active pattern="p-urn-oid-2.16.840.1.113883.19.1000.3.15-warnings" />
  </sch:phase>
  <sch:pattern id="p-urn-oid-2.16.840.1.113883.19.1000.3.1-errors">
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.3.1-errors-abstract" abstract="true">
      <sch:assert id="a-5560-32826" test="count(cda:templateId)=1">SHALL contain exactly one [1..1] templateId (CONF:5560-32826).</sch:assert>
      <sch:assert id="a-5560-32827" test="cda:templateId[@root='2.16.840.1.113883.19.1000.3.1']">This templateId SHALL contain exactly one [1..1] @root="2.16.840.1.113883.19.1000.3.1" (CONF:5560-32827).</sch:assert>
      <sch:assert id="a-5560-32828" test="count(cda:code[@code='71802-3'][@codeSystem='2.16.840.1.113883.6.1' or @nullFlavor])=1">SHALL contain exactly one [1..1] code="71802-3" Housing status (CodeSystem: LOINC urn:oid:2.16.840.1.113883.6.1) (CONF:5560-32828).</sch:assert>
      <sch:assert id="a-5560-32829" test="count(cda:value[@xsi:type='CO' and @code=document('voc.xml')/voc:systems/voc:system[@valueSetOid='2.16.840.1.113883.19.1000.4.1']/voc:code/@value or @nullFlavor])=1">SHALL contain exactly one [1..1] value with @xsi:type="CO", where the code SHALL be selected from ValueSet LOINC LL5876-9 | AHC HRSN - Living Situation urn:oid:2.16.840.1.113883.19.1000.4.1 STATIC 2024-05-13 (CONF:5560-32829).</sch:assert>
    </sch:rule>
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.3.1-errors" context="cda:observation[cda:templateId[@root='2.16.840.1.113883.19.1000.3.1']]">
      <sch:extends rule="r-urn-oid-2.16.840.1.113883.19.1000.3.1-errors-abstract" />
    </sch:rule>
  </sch:pattern>
  <sch:pattern id="p-urn-oid-2.16.840.1.113883.19.1000.2.2-errors">
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.2.2-errors-abstract" abstract="true">
      <sch:assert id="a-5560-11" test="count(cda:entry[count(cda:observation[cda:templateId[@root='2.16.840.1.113883.19.1000.3.1']])=1])=1">SHALL contain exactly one [1..1] entry (CONF:5560-11) such that it SHALL contain exactly one [1..1] LOINC 71802-3 | Housing status (identifier: urn:oid:2.16.840.1.113883.19.1000.3.1) (CONF:5560-32825).</sch:assert>
      <sch:assert id="a-5560-12" test="count(cda:templateId)=1">SHALL contain exactly one [1..1] templateId (CONF:5560-12).</sch:assert>
      <sch:assert id="a-5560-23" test="cda:templateId[@root='2.16.840.1.113883.19.1000.2.2']">This templateId SHALL contain exactly one [1..1] @root="2.16.840.1.113883.19.1000.2.2" (CONF:5560-23).</sch:assert>
      <sch:assert id="a-5560-32884" test="count(cda:entry[count(cda:observation[cda:templateId[@root='2.16.840.1.113883.19.1000.3.2']])=1])=1">SHALL contain exactly one [1..1] entry (CONF:5560-32884) such that it SHALL contain exactly one [1..1] LOINC 96778-6 | Problems with place where you live (identifier: urn:oid:2.16.840.1.113883.19.1000.3.2) (CONF:5560-32885).</sch:assert>
      <sch:assert id="a-5560-32886" test="count(cda:entry[count(cda:observation[cda:templateId[@root='2.16.840.1.113883.19.1000.3.3']])=1])=1">SHALL contain exactly one [1..1] entry (CONF:5560-32886) such that it SHALL contain exactly one [1..1] LOINC 96779-4 | Has the electric, gas, oil, or water company threatened to shut off services in your home in past 12 months (identifier: urn:oid:2.16.840.1.113883.19.1000.3.3) (CONF:5560-32887).</sch:assert>
    </sch:rule>
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.2.2-errors" context="cda:section[cda:templateId[@root='2.16.840.1.113883.19.1000.2.2']]">
      <sch:extends rule="r-urn-oid-2.16.840.1.113883.19.1000.2.2-errors-abstract" />
    </sch:rule>
  </sch:pattern>
  <sch:pattern id="p-urn-oid-2.16.840.1.113883.19.1000.1-errors">
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.1-errors-abstract" abstract="true">
      <sch:assert id="a-5560-13" test="count(cda:component)=1">SHALL contain exactly one [1..1] component (CONF:5560-13).</sch:assert>
      <sch:assert id="a-5560-14" test="cda:component[count(cda:structuredBody)=1]">This component SHALL contain exactly one [1..1] structuredBody (CONF:5560-14).</sch:assert>
      <sch:assert id="a-5560-15" test="cda:component/cda:structuredBody[count(cda:component[count(cda:section[cda:templateId[@root='2.16.840.1.113883.19.1000.2.1']])=1])=1]">This structuredBody SHALL contain exactly one [1..1] component (CONF:5560-15) such that it SHALL contain exactly one [1..1] Details about source questionnaire section (identifier: urn:oid:2.16.840.1.113883.19.1000.2.1) (CONF:5560-16).</sch:assert>
      <sch:assert id="a-5560-17" test="count(cda:templateId)=1">SHALL contain exactly one [1..1] templateId (CONF:5560-17).</sch:assert>
      <sch:assert id="a-5560-18" test="count(cda:code[@code='temp1'][@codeSystem='2.16.840.1.113883.6.1' or @nullFlavor])=1">SHALL contain exactly one [1..1] code="temp1" AHC-HRSN modified screening tool response (CodeSystem: LOINC urn:oid:2.16.840.1.113883.6.1) (CONF:5560-18).</sch:assert>
      <sch:assert id="a-5560-19" test="count(cda:recordTarget)=1">SHALL contain exactly one [1..1] recordTarget (CONF:5560-19).</sch:assert>
      <sch:assert id="a-5560-20" test="count(cda:author)=1">SHALL contain exactly one [1..1] author (CONF:5560-20).</sch:assert>
      <sch:assert id="a-5560-21" test="count(cda:custodian)=1">SHALL contain exactly one [1..1] custodian (CONF:5560-21).</sch:assert>
      <sch:assert id="a-5560-22" test="cda:templateId[@root='2.16.840.1.113883.19.1000.1']">This templateId SHALL contain exactly one [1..1] @root="2.16.840.1.113883.19.1000.1" (CONF:5560-22).</sch:assert>
      <sch:assert id="a-5560-32932" test="cda:recordTarget[count(cda:patientRole)=1]">This recordTarget SHALL contain exactly one [1..1] patientRole (CONF:5560-32932).</sch:assert>
      <sch:assert id="a-5560-32933" test="cda:recordTarget/cda:patientRole[count(cda:patient)=1]">This patientRole SHALL contain exactly one [1..1] patient (CONF:5560-32933).</sch:assert>
      <sch:assert id="a-5560-32934" test="cda:author[count(cda:assignedAuthor)=1]">This author SHALL contain exactly one [1..1] assignedAuthor (CONF:5560-32934).</sch:assert>
      <sch:assert id="a-5560-32947" test="cda:recordTarget/cda:patientRole[count(cda:id)=1]">This patientRole SHALL contain exactly one [1..1] id (CONF:5560-32947).</sch:assert>
      <sch:assert id="a-5560-32948" test="cda:recordTarget/cda:patientRole/cda:patient[count(cda:name)=1]">This patient SHALL contain exactly one [1..1] name (CONF:5560-32948).</sch:assert>
      <sch:assert id="a-5560-32949" test="cda:author/cda:assignedAuthor[count(cda:id)=1]">This assignedAuthor SHALL contain exactly one [1..1] id (CONF:5560-32949).</sch:assert>
      <sch:assert id="a-5560-32950" test="cda:custodian[count(cda:assignedCustodian)=1]">This custodian SHALL contain exactly one [1..1] assignedCustodian (CONF:5560-32950).</sch:assert>
      <sch:assert id="a-5560-32951" test="cda:component/cda:structuredBody[count(cda:component[count(cda:section[cda:templateId[@root='2.16.840.1.113883.19.1000.2.2']])=1])=1]">This structuredBody SHALL contain exactly one [1..1] component (CONF:5560-32951) such that it SHALL contain exactly one [1..1] Housing / Utilities section (identifier: urn:oid:2.16.840.1.113883.19.1000.2.2) (CONF:5560-32952).</sch:assert>
      <sch:assert id="a-5560-32953" test="cda:component/cda:structuredBody[count(cda:component[count(cda:section[cda:templateId[@root='2.16.840.1.113883.19.1000.2.3']])=1])=1]">This structuredBody SHALL contain exactly one [1..1] component (CONF:5560-32953) such that it SHALL contain exactly one [1..1] Food Security section (identifier: urn:oid:2.16.840.1.113883.19.1000.2.3) (CONF:5560-32954).</sch:assert>
      <sch:assert id="a-5560-32955" test="cda:component/cda:structuredBody[count(cda:component[count(cda:section[cda:templateId[@root='2.16.840.1.113883.19.1000.2.4']])=1])=1]">This structuredBody SHALL contain exactly one [1..1] component (CONF:5560-32955) such that it SHALL contain exactly one [1..1] Transportation section (identifier: urn:oid:2.16.840.1.113883.19.1000.2.4) (CONF:5560-32956).</sch:assert>
      <sch:assert id="a-5560-32957" test="cda:component/cda:structuredBody[count(cda:component[count(cda:section[cda:templateId[@root='2.16.840.1.113883.19.1000.2.5']])=1])=1]">This structuredBody SHALL contain exactly one [1..1] component (CONF:5560-32957) such that it SHALL contain exactly one [1..1] Employment section (identifier: urn:oid:2.16.840.1.113883.19.1000.2.5) (CONF:5560-32958).</sch:assert>
      <sch:assert id="a-5560-32959" test="cda:component/cda:structuredBody[count(cda:component[count(cda:section[cda:templateId[@root='2.16.840.1.113883.19.1000.2.6']])=1])=1]">This structuredBody SHALL contain exactly one [1..1] component (CONF:5560-32959) such that it SHALL contain exactly one [1..1] Education section (identifier: urn:oid:2.16.840.1.113883.19.1000.2.6) (CONF:5560-32960).</sch:assert>
      <sch:assert id="a-5560-32961" test="cda:component/cda:structuredBody[count(cda:component[count(cda:section[cda:templateId[@root='2.16.840.1.113883.19.1000.2.7']])=1])=1]">This structuredBody SHALL contain exactly one [1..1] component (CONF:5560-32961) such that it SHALL contain exactly one [1..1] Interpersonal Safety section (identifier: urn:oid:2.16.840.1.113883.19.1000.2.7) (CONF:5560-32962).</sch:assert>
      <sch:assert id="a-5560-32972" test="not(cda:componentOf) or cda:componentOf[count(cda:encompassingEncounter)=1]">This componentOf SHALL contain exactly one [1..1] encompassingEncounter (CONF:5560-32972).</sch:assert>
      <sch:assert id="a-5560-32975" test="cda:componentOf/cda:encompassingEncounter[count(cda:effectiveTime)=1]">This encompassingEncounter SHALL contain exactly one [1..1] effectiveTime (CONF:5560-32975).</sch:assert>
      <sch:assert id="a-5560-32976" test="count(cda:effectiveTime)=1">SHALL contain exactly one [1..1] effectiveTime (CONF:5560-32976).</sch:assert>
      <sch:assert id="a-5560-32978" test="not(cda:authorization) or cda:authorization[count(cda:consent)=1]">This authorization SHALL contain exactly one [1..1] consent (CONF:5560-32978).</sch:assert>
      <sch:assert id="a-5560-32983" test="cda:custodian/cda:assignedCustodian[count(cda:representedCustodianOrganization)=1]">This assignedCustodian SHALL contain exactly one [1..1] representedCustodianOrganization (CONF:5560-32983).</sch:assert>
      <sch:assert id="a-5560-32985" test="not(cda:informationRecipient) or cda:informationRecipient[count(cda:intendedRecipient)=1]">The informationRecipient, if present, SHALL contain exactly one [1..1] intendedRecipient (CONF:5560-32985).</sch:assert>
      <sch:assert id="a-5560-32988" test="cda:custodian/cda:assignedCustodian/cda:representedCustodianOrganization[count(cda:id) &gt; 0]">This representedCustodianOrganization SHALL contain at least one [1..*] id (CONF:5560-32988).</sch:assert>
    </sch:rule>
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.1-errors" context="cda:ClinicalDocument[cda:templateId[@root='2.16.840.1.113883.19.1000.1']]">
      <sch:extends rule="r-urn-oid-2.16.840.1.113883.19.1000.1-errors-abstract" />
    </sch:rule>
  </sch:pattern>
  <sch:pattern id="p-urn-oid-2.16.840.1.113883.19.1000.3.2-errors">
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.3.2-errors-abstract" abstract="true">
      <sch:assert id="a-5560-32830" test="count(cda:templateId)=1">SHALL contain exactly one [1..1] templateId (CONF:5560-32830).</sch:assert>
      <sch:assert id="a-5560-32831" test="cda:templateId[@root='2.16.840.1.113883.19.1000.3.2']">This templateId SHALL contain exactly one [1..1] @root="2.16.840.1.113883.19.1000.3.2" (CONF:5560-32831).</sch:assert>
      <sch:assert id="a-5560-32832" test="count(cda:code[@code='96778-6'][@codeSystem='2.16.840.1.113883.6.1' or @nullFlavor])=1">SHALL contain exactly one [1..1] code="96778-6" Problems with place where you live (CodeSystem: LOINC urn:oid:2.16.840.1.113883.6.1) (CONF:5560-32832).</sch:assert>
      <sch:assert id="a-5560-32833" test="count(cda:value[@xsi:type='CO' and @code=document('voc.xml')/voc:systems/voc:system[@valueSetOid='2.16.840.1.113883.19.1000.4.2']/voc:code/@value or @nullFlavor]) &gt; 0">SHALL contain at least one [1..*] value with @xsi:type="CO", where the code SHALL be selected from ValueSet LL5877-7 | Habitat Issues urn:oid:2.16.840.1.113883.19.1000.4.2 STATIC 2024-05-13 (CONF:5560-32833).</sch:assert>
    </sch:rule>
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.3.2-errors" context="cda:observation[cda:templateId[@root='2.16.840.1.113883.19.1000.3.2']]">
      <sch:extends rule="r-urn-oid-2.16.840.1.113883.19.1000.3.2-errors-abstract" />
    </sch:rule>
  </sch:pattern>
  <sch:pattern id="p-urn-oid-2.16.840.1.113883.19.1000.3.3-errors">
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.3.3-errors-abstract" abstract="true">
      <sch:assert id="a-5560-32834" test="count(cda:templateId)=1">SHALL contain exactly one [1..1] templateId (CONF:5560-32834).</sch:assert>
      <sch:assert id="a-5560-32835" test="cda:templateId[@root='2.16.840.1.113883.19.1000.3.3']">This templateId SHALL contain exactly one [1..1] @root="2.16.840.1.113883.19.1000.3.3" (CONF:5560-32835).</sch:assert>
      <sch:assert id="a-5560-32836" test="count(cda:code[@code='96779-4'][@codeSystem='2.16.840.1.113883.6.1' or @nullFlavor])=1">SHALL contain exactly one [1..1] code="96779-4" Has the electric, gas, oil, or water company threatened to shut off services in your home in past 12 months (CodeSystem: LOINC urn:oid:2.16.840.1.113883.6.1) (CONF:5560-32836).</sch:assert>
      <sch:assert id="a-5560-32837" test="count(cda:value[@xsi:type='CO' and @code=document('voc.xml')/voc:systems/voc:system[@valueSetOid='2.16.840.1.113883.19.1000.4.3']/voc:code/@value or @nullFlavor])=1">SHALL contain exactly one [1..1] value with @xsi:type="CO", where the code SHALL be selected from ValueSet LL5878-5 | Utility shut off risk urn:oid:2.16.840.1.113883.19.1000.4.3 STATIC 2024-05-13 (CONF:5560-32837).</sch:assert>
    </sch:rule>
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.3.3-errors" context="cda:observation[cda:templateId[@root='2.16.840.1.113883.19.1000.3.3']]">
      <sch:extends rule="r-urn-oid-2.16.840.1.113883.19.1000.3.3-errors-abstract" />
    </sch:rule>
  </sch:pattern>
  <sch:pattern id="p-urn-oid-2.16.840.1.113883.19.1000.3.4-errors">
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.3.4-errors-abstract" abstract="true">
      <sch:assert id="a-5560-32838" test="count(cda:templateId)=1">SHALL contain exactly one [1..1] templateId (CONF:5560-32838).</sch:assert>
      <sch:assert id="a-5560-32839" test="cda:templateId[@root='2.16.840.1.113883.19.1000.3.4']">This templateId SHALL contain exactly one [1..1] @root="2.16.840.1.113883.19.1000.3.4" (CONF:5560-32839).</sch:assert>
      <sch:assert id="a-5560-32840" test="count(cda:code[@code='88122-7'][@codeSystem='2.16.840.1.113883.6.1' or @nullFlavor])=1">SHALL contain exactly one [1..1] code="88122-7" Within the past 12 months we worried whether our food would run out before we got money to buy more [U.S. FSS] (CodeSystem: LOINC urn:oid:2.16.840.1.113883.6.1) (CONF:5560-32840).</sch:assert>
      <sch:assert id="a-5560-32841" test="count(cda:value[@xsi:type='CO' and @code=document('voc.xml')/voc:systems/voc:system[@valueSetOid='2.16.840.1.113883.19.1000.4.4']/voc:code/@value or @nullFlavor])=1">SHALL contain exactly one [1..1] value with @xsi:type="CO", where the code SHALL be selected from ValueSet LL5586-4 | Often true | Sometimes true | Never true | DK urn:oid:2.16.840.1.113883.19.1000.4.4 STATIC 2024-05-13 (CONF:5560-32841).</sch:assert>
    </sch:rule>
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.3.4-errors" context="cda:observation[cda:templateId[@root='2.16.840.1.113883.19.1000.3.4']]">
      <sch:extends rule="r-urn-oid-2.16.840.1.113883.19.1000.3.4-errors-abstract" />
    </sch:rule>
  </sch:pattern>
  <sch:pattern id="p-urn-oid-2.16.840.1.113883.19.1000.3.5-errors">
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.3.5-errors-abstract" abstract="true">
      <sch:assert id="a-5560-32842" test="count(cda:templateId)=1">SHALL contain exactly one [1..1] templateId (CONF:5560-32842).</sch:assert>
      <sch:assert id="a-5560-32843" test="cda:templateId[@root='2.16.840.1.113883.19.1000.3.5']">This templateId SHALL contain exactly one [1..1] @root="2.16.840.1.113883.19.1000.3.5" (CONF:5560-32843).</sch:assert>
      <sch:assert id="a-5560-32844" test="count(cda:code[@code='88123-5'][@codeSystem='2.16.840.1.113883.6.1' or @nullFlavor])=1">SHALL contain exactly one [1..1] code="88123-5" Within the past 12 months the food we bought just didn't last and we didn't have money to get more [U.S. FSS] (CodeSystem: LOINC urn:oid:2.16.840.1.113883.6.1) (CONF:5560-32844).</sch:assert>
      <sch:assert id="a-5560-32845" test="count(cda:value[@xsi:type='CO' and @code=document('voc.xml')/voc:systems/voc:system[@valueSetOid='2.16.840.1.113883.19.1000.4.4']/voc:code/@value or @nullFlavor])=1">SHALL contain exactly one [1..1] value with @xsi:type="CO", where the code SHALL be selected from ValueSet LL5586-4 | Often true | Sometimes true | Never true | DK urn:oid:2.16.840.1.113883.19.1000.4.4 STATIC 2024-05-13 (CONF:5560-32845).</sch:assert>
    </sch:rule>
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.3.5-errors" context="cda:observation[cda:templateId[@root='2.16.840.1.113883.19.1000.3.5']]">
      <sch:extends rule="r-urn-oid-2.16.840.1.113883.19.1000.3.5-errors-abstract" />
    </sch:rule>
  </sch:pattern>
  <sch:pattern id="p-urn-oid-2.16.840.1.113883.19.1000.3.6-errors">
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.3.6-errors-abstract" abstract="true">
      <sch:assert id="a-5560-32846" test="count(cda:templateId)=1">SHALL contain exactly one [1..1] templateId (CONF:5560-32846).</sch:assert>
      <sch:assert id="a-5560-32847" test="cda:templateId[@root='2.16.840.1.113883.19.1000.3.6']">This templateId SHALL contain exactly one [1..1] @root="2.16.840.1.113883.19.1000.3.6" (CONF:5560-32847).</sch:assert>
      <sch:assert id="a-5560-32848" test="count(cda:code[@code='93030-5'][@codeSystem='2.16.840.1.113883.6.1' or @nullFlavor])=1">SHALL contain exactly one [1..1] code="93030-5" Has lack of transportation kept you from medical appointments, meetings, work, or from getting things needed for daily living (CodeSystem: LOINC urn:oid:2.16.840.1.113883.6.1) (CONF:5560-32848).</sch:assert>
      <sch:assert id="a-5560-32849" test="count(cda:value[@xsi:type='CO' and @code=document('voc.xml')/voc:systems/voc:system[@valueSetOid='2.16.840.1.113883.19.1000.4.5']/voc:code/@value or @nullFlavor])=1">SHALL contain exactly one [1..1] value with @xsi:type="CO", where the code SHALL be selected from ValueSet LL361-7 | Y/N urn:oid:2.16.840.1.113883.19.1000.4.5 STATIC 2024-05-13 (CONF:5560-32849).</sch:assert>
    </sch:rule>
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.3.6-errors" context="cda:observation[cda:templateId[@root='2.16.840.1.113883.19.1000.3.6']]">
      <sch:extends rule="r-urn-oid-2.16.840.1.113883.19.1000.3.6-errors-abstract" />
    </sch:rule>
  </sch:pattern>
  <sch:pattern id="p-urn-oid-2.16.840.1.113883.19.1000.3.7-errors">
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.3.7-errors-abstract" abstract="true">
      <sch:assert id="a-5560-32850" test="count(cda:templateId)=1">SHALL contain exactly one [1..1] templateId (CONF:5560-32850).</sch:assert>
      <sch:assert id="a-5560-32851" test="cda:templateId[@root='2.16.840.1.113883.19.1000.3.7']">This templateId SHALL contain exactly one [1..1] @root="2.16.840.1.113883.19.1000.3.7" (CONF:5560-32851).</sch:assert>
      <sch:assert id="a-5560-32852" test="count(cda:code[@code='96780-2'][@codeSystem='2.16.840.1.113883.6.1' or @nullFlavor])=1">SHALL contain exactly one [1..1] code="96780-2" Wants help finding or keeping work or a job (CodeSystem: LOINC urn:oid:2.16.840.1.113883.6.1) (CONF:5560-32852).</sch:assert>
      <sch:assert id="a-5560-32853" test="count(cda:value[@xsi:type='CO' and @code=document('voc.xml')/voc:systems/voc:system[@valueSetOid='2.16.840.1.113883.19.1000.4.5']/voc:code/@value or @nullFlavor])=1">SHALL contain exactly one [1..1] value with @xsi:type="CO", where the code SHALL be selected from ValueSet LL361-7 | Y/N urn:oid:2.16.840.1.113883.19.1000.4.5 STATIC 2024-05-13 (CONF:5560-32853).</sch:assert>
    </sch:rule>
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.3.7-errors" context="cda:observation[cda:templateId[@root='2.16.840.1.113883.19.1000.3.7']]">
      <sch:extends rule="r-urn-oid-2.16.840.1.113883.19.1000.3.7-errors-abstract" />
    </sch:rule>
  </sch:pattern>
  <sch:pattern id="p-urn-oid-2.16.840.1.113883.19.1000.3.8-errors">
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.3.8-errors-abstract" abstract="true">
      <sch:assert id="a-5560-32854" test="count(cda:templateId)=1">SHALL contain exactly one [1..1] templateId (CONF:5560-32854).</sch:assert>
      <sch:assert id="a-5560-32855" test="cda:templateId[@root='2.16.840.1.113883.19.1000.3.8']">This templateId SHALL contain exactly one [1..1] @root="2.16.840.1.113883.19.1000.3.8" (CONF:5560-32855).</sch:assert>
      <sch:assert id="a-5560-32856" test="count(cda:code[@code='96782-8'][@codeSystem='2.16.840.1.113883.6.1' or @nullFlavor])=1">SHALL contain exactly one [1..1] code="96782-8" Wants help with school or training (CodeSystem: LOINC urn:oid:2.16.840.1.113883.6.1) (CONF:5560-32856).</sch:assert>
      <sch:assert id="a-5560-32857" test="count(cda:value[@xsi:type='CO' and @code=document('voc.xml')/voc:systems/voc:system[@valueSetOid='2.16.840.1.113883.19.1000.4.5']/voc:code/@value or @nullFlavor])=1">SHALL contain exactly one [1..1] value with @xsi:type="CO", where the code SHALL be selected from ValueSet LL361-7 | Y/N urn:oid:2.16.840.1.113883.19.1000.4.5 STATIC 2024-05-13 (CONF:5560-32857).</sch:assert>
    </sch:rule>
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.3.8-errors" context="cda:observation[cda:templateId[@root='2.16.840.1.113883.19.1000.3.8']]">
      <sch:extends rule="r-urn-oid-2.16.840.1.113883.19.1000.3.8-errors-abstract" />
    </sch:rule>
  </sch:pattern>
  <sch:pattern id="p-urn-oid-2.16.840.1.113883.19.1000.3.9-errors">
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.3.9-errors-abstract" abstract="true">
      <sch:assert id="a-5560-32858" test="count(cda:templateId)=1">SHALL contain exactly one [1..1] templateId (CONF:5560-32858).</sch:assert>
      <sch:assert id="a-5560-32859" test="cda:templateId[@root='2.16.840.1.113883.19.1000.3.9']">This templateId SHALL contain exactly one [1..1] @root="2.16.840.1.113883.19.1000.3.9" (CONF:5560-32859).</sch:assert>
      <sch:assert id="a-5560-32860" test="count(cda:code[@code='95618-5'][@codeSystem='2.16.840.1.113883.6.1' or @nullFlavor])=1">SHALL contain exactly one [1..1] code="95618-5" Physically hurt you [HITS] (CodeSystem: LOINC urn:oid:2.16.840.1.113883.6.1) (CONF:5560-32860).</sch:assert>
      <sch:assert id="a-5560-32861" test="count(cda:value[@xsi:type='INT'])=1">SHALL contain exactly one [1..1] value with @xsi:type="INT" (CONF:5560-32861).</sch:assert>
      <sch:assert id="a-5560-32876" test="count(cda:value[@xsi:type='CO' and @code=document('voc.xml')/voc:systems/voc:system[@valueSetOid='2.16.840.1.113883.19.1000.4.6']/voc:code/@value or @nullFlavor])=1">SHALL contain exactly one [1..1] value with @xsi:type="CO", where the code SHALL be selected from ValueSet LL5622-7 | Nev, Rare, Some, Fair Oft, Freq urn:oid:2.16.840.1.113883.19.1000.4.6 STATIC 2024-05-13 (CONF:5560-32876).</sch:assert>
    </sch:rule>
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.3.9-errors" context="cda:observation[cda:templateId[@root='2.16.840.1.113883.19.1000.3.9']]">
      <sch:extends rule="r-urn-oid-2.16.840.1.113883.19.1000.3.9-errors-abstract" />
    </sch:rule>
  </sch:pattern>
  <sch:pattern id="p-urn-oid-2.16.840.1.113883.19.1000.3.10-errors">
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.3.10-errors-abstract" abstract="true">
      <sch:assert id="a-5560-32862" test="count(cda:templateId)=1">SHALL contain exactly one [1..1] templateId (CONF:5560-32862).</sch:assert>
      <sch:assert id="a-5560-32863" test="cda:templateId[@root='2.16.840.1.113883.19.1000.3.10']">This templateId SHALL contain exactly one [1..1] @root="2.16.840.1.113883.19.1000.3.10" (CONF:5560-32863).</sch:assert>
      <sch:assert id="a-5560-32864" test="count(cda:code[@code='95617-7'][@codeSystem='2.16.840.1.113883.6.1' or @nullFlavor])=1">SHALL contain exactly one [1..1] code="95617-7" Insult you or talk down to you [HITS] (CodeSystem: LOINC urn:oid:2.16.840.1.113883.6.1) (CONF:5560-32864).</sch:assert>
      <sch:assert id="a-5560-32865" test="count(cda:value[@xsi:type='INT'])=1">SHALL contain exactly one [1..1] value with @xsi:type="INT" (CONF:5560-32865).</sch:assert>
      <sch:assert id="a-5560-32877" test="count(cda:value[@xsi:type='CO' and @code=document('voc.xml')/voc:systems/voc:system[@valueSetOid='2.16.840.1.113883.19.1000.4.6']/voc:code/@value or @nullFlavor])=1">SHALL contain exactly one [1..1] value with @xsi:type="CO", where the code SHALL be selected from ValueSet LL5622-7 | Nev, Rare, Some, Fair Oft, Freq urn:oid:2.16.840.1.113883.19.1000.4.6 STATIC 2024-05-13 (CONF:5560-32877).</sch:assert>
    </sch:rule>
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.3.10-errors" context="cda:observation[cda:templateId[@root='2.16.840.1.113883.19.1000.3.10']]">
      <sch:extends rule="r-urn-oid-2.16.840.1.113883.19.1000.3.10-errors-abstract" />
    </sch:rule>
  </sch:pattern>
  <sch:pattern id="p-urn-oid-2.16.840.1.113883.19.1000.3.11-errors">
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.3.11-errors-abstract" abstract="true">
      <sch:assert id="a-5560-32866" test="count(cda:templateId)=1">SHALL contain exactly one [1..1] templateId (CONF:5560-32866).</sch:assert>
      <sch:assert id="a-5560-32867" test="cda:templateId[@root='2.16.840.1.113883.19.1000.3.11']">This templateId SHALL contain exactly one [1..1] @root="2.16.840.1.113883.19.1000.3.11" (CONF:5560-32867).</sch:assert>
      <sch:assert id="a-5560-32868" test="count(cda:code[@code='95616-9'][@codeSystem='2.16.840.1.113883.6.1' or @nullFlavor])=1">SHALL contain exactly one [1..1] code="95616-9" Threaten you with physical harm [HITS] (CodeSystem: LOINC urn:oid:2.16.840.1.113883.6.1) (CONF:5560-32868).</sch:assert>
      <sch:assert id="a-5560-32869" test="count(cda:value[@xsi:type='INT'])=1">SHALL contain exactly one [1..1] value with @xsi:type="INT" (CONF:5560-32869).</sch:assert>
      <sch:assert id="a-5560-32870" test="count(cda:value[@xsi:type='CO' and @code=document('voc.xml')/voc:systems/voc:system[@valueSetOid='2.16.840.1.113883.19.1000.4.6']/voc:code/@value or @nullFlavor])=1">SHALL contain exactly one [1..1] value with @xsi:type="CO", where the code SHALL be selected from ValueSet LL5622-7 | Nev, Rare, Some, Fair Oft, Freq urn:oid:2.16.840.1.113883.19.1000.4.6 STATIC 2024-05-13 (CONF:5560-32870).</sch:assert>
    </sch:rule>
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.3.11-errors" context="cda:observation[cda:templateId[@root='2.16.840.1.113883.19.1000.3.11']]">
      <sch:extends rule="r-urn-oid-2.16.840.1.113883.19.1000.3.11-errors-abstract" />
    </sch:rule>
  </sch:pattern>
  <sch:pattern id="p-urn-oid-2.16.840.1.113883.19.1000.3.12-errors">
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.3.12-errors-abstract" abstract="true">
      <sch:assert id="a-5560-32871" test="count(cda:templateId)=1">SHALL contain exactly one [1..1] templateId (CONF:5560-32871).</sch:assert>
      <sch:assert id="a-5560-32872" test="cda:templateId[@root='2.16.840.1.113883.19.1000.3.12']">This templateId SHALL contain exactly one [1..1] @root="2.16.840.1.113883.19.1000.3.12" (CONF:5560-32872).</sch:assert>
      <sch:assert id="a-5560-32873" test="count(cda:code[@code='95615-1'][@codeSystem='2.16.840.1.113883.6.1' or @nullFlavor])=1">SHALL contain exactly one [1..1] code="95615-1" Scream or curse at you [HITS] (CodeSystem: LOINC urn:oid:2.16.840.1.113883.6.1) (CONF:5560-32873).</sch:assert>
      <sch:assert id="a-5560-32874" test="count(cda:value[@xsi:type='INT'])=1">SHALL contain exactly one [1..1] value with @xsi:type="INT" (CONF:5560-32874).</sch:assert>
      <sch:assert id="a-5560-32875" test="count(cda:value[@xsi:type='CO' and @code=document('voc.xml')/voc:systems/voc:system[@valueSetOid='2.16.840.1.113883.19.1000.4.6']/voc:code/@value or @nullFlavor])=1">SHALL contain exactly one [1..1] value with @xsi:type="CO", where the code SHALL be selected from ValueSet LL5622-7 | Nev, Rare, Some, Fair Oft, Freq urn:oid:2.16.840.1.113883.19.1000.4.6 STATIC 2024-05-13 (CONF:5560-32875).</sch:assert>
    </sch:rule>
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.3.12-errors" context="cda:observation[cda:templateId[@root='2.16.840.1.113883.19.1000.3.12']]">
      <sch:extends rule="r-urn-oid-2.16.840.1.113883.19.1000.3.12-errors-abstract" />
    </sch:rule>
  </sch:pattern>
  <sch:pattern id="p-urn-oid-2.16.840.1.113883.19.1000.3.14-errors">
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.3.14-errors-abstract" abstract="true">
      <sch:assert id="a-5560-32879" test="count(cda:reference)=1">SHALL contain exactly one [1..1] reference (CONF:5560-32879).</sch:assert>
      <sch:assert id="a-5560-32880" test="cda:reference[count(cda:externalAct)=1]">This reference SHALL contain exactly one [1..1] externalAct (CONF:5560-32880).</sch:assert>
      <sch:assert id="a-5560-32882" test="count(cda:code[@code='445536008'][@codeSystem='2.16.840.1.113883.6.96' or @nullFlavor])=1">SHALL contain exactly one [1..1] code="445536008" Assessment using assessment scale (procedure) (CodeSystem: SNOMED CT urn:oid:2.16.840.1.113883.6.96) (CONF:5560-32882).</sch:assert>
      <sch:assert id="a-5560-32883" test="cda:reference/cda:externalAct[count(cda:id)=1]">This externalAct SHALL contain exactly one [1..1] id (CONF:5560-32883).</sch:assert>
      <sch:assert id="a-5560-32963" test="count(cda:templateId)=1">SHALL contain exactly one [1..1] templateId (CONF:5560-32963).</sch:assert>
      <sch:assert id="a-5560-32964" test="cda:templateId[@root='2.16.840.1.113883.19.1000.3.14']">This templateId SHALL contain exactly one [1..1] @root="2.16.840.1.113883.19.1000.3.14" (CONF:5560-32964).</sch:assert>
    </sch:rule>
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.3.14-errors" context="cda:act[cda:templateId[@root='2.16.840.1.113883.19.1000.3.14']]">
      <sch:extends rule="r-urn-oid-2.16.840.1.113883.19.1000.3.14-errors-abstract" />
    </sch:rule>
  </sch:pattern>
  <sch:pattern id="p-urn-oid-2.16.840.1.113883.19.1000.2.1-errors">
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.2.1-errors-abstract" abstract="true">
      <sch:assert id="a-5560-32888" test="count(cda:entry)=1">SHALL contain exactly one [1..1] entry (CONF:5560-32888).</sch:assert>
      <sch:assert id="a-5560-32890" test="count(cda:templateId)=1">SHALL contain exactly one [1..1] templateId (CONF:5560-32890).</sch:assert>
      <sch:assert id="a-5560-32895" test="cda:templateId[@root='2.16.840.1.113883.19.1000.2.1']">This templateId SHALL contain exactly one [1..1] @root="2.16.840.1.113883.19.1000.2.1" (CONF:5560-32895).</sch:assert>
      <sch:assert id="a-5560-32896" test="cda:entry[count(cda:act[cda:templateId[@root='2.16.840.1.113883.19.1000.3.14']])=1]">This entry SHALL contain exactly one [1..1] SNOMED 445536008 | Assessment using assessment scale (identifier: urn:oid:2.16.840.1.113883.19.1000.3.14) (CONF:5560-32896).</sch:assert>
    </sch:rule>
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.2.1-errors" context="cda:section[cda:templateId[@root='2.16.840.1.113883.19.1000.2.1']]">
      <sch:extends rule="r-urn-oid-2.16.840.1.113883.19.1000.2.1-errors-abstract" />
    </sch:rule>
  </sch:pattern>
  <sch:pattern id="p-urn-oid-2.16.840.1.113883.19.1000.2.3-errors">
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.2.3-errors-abstract" abstract="true">
      <sch:assert id="a-5560-32897" test="count(cda:entry[count(cda:observation[cda:templateId[@root='2.16.840.1.113883.19.1000.3.4']])=1])=1">SHALL contain exactly one [1..1] entry (CONF:5560-32897) such that it SHALL contain exactly one [1..1] LOINC 88122-7 | Within the past 12 months we worried whether our food would run out before we got money to buy more [U.S. FSS] (identifier: urn:oid:2.16.840.1.113883.19.1000.3.4) (CONF:5560-32898).</sch:assert>
      <sch:assert id="a-5560-32899" test="count(cda:templateId)=1">SHALL contain exactly one [1..1] templateId (CONF:5560-32899).</sch:assert>
      <sch:assert id="a-5560-32900" test="count(cda:entry[count(cda:observation[cda:templateId[@root='2.16.840.1.113883.19.1000.3.5']])=1])=1">SHALL contain exactly one [1..1] entry (CONF:5560-32900) such that it SHALL contain exactly one [1..1] LOINC 88123-5 | Within the past 12 months the food we bought just didn't last and we didn't have money to get more [U.S. FSS] (identifier: urn:oid:2.16.840.1.113883.19.1000.3.5) (CONF:5560-32901).</sch:assert>
      <sch:assert id="a-5560-32904" test="cda:templateId[@root='2.16.840.1.113883.19.1000.2.3']">This templateId SHALL contain exactly one [1..1] @root="2.16.840.1.113883.19.1000.2.3" (CONF:5560-32904).</sch:assert>
    </sch:rule>
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.2.3-errors" context="cda:section[cda:templateId[@root='2.16.840.1.113883.19.1000.2.3']]">
      <sch:extends rule="r-urn-oid-2.16.840.1.113883.19.1000.2.3-errors-abstract" />
    </sch:rule>
  </sch:pattern>
  <sch:pattern id="p-urn-oid-2.16.840.1.113883.19.1000.2.4-errors">
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.2.4-errors-abstract" abstract="true">
      <sch:assert id="a-5560-32905" test="count(cda:entry)=1">SHALL contain exactly one [1..1] entry (CONF:5560-32905).</sch:assert>
      <sch:assert id="a-5560-32906" test="cda:entry[count(cda:observation[cda:templateId[@root='2.16.840.1.113883.19.1000.3.6']])=1]">This entry SHALL contain exactly one [1..1] LOINC 93030-5 | Has lack of transportation kept you from medical appointments, meetings, work, or from getting things needed for daily living (identifier: urn:oid:2.16.840.1.113883.19.1000.3.6) (CONF:5560-32906).</sch:assert>
      <sch:assert id="a-5560-32907" test="count(cda:templateId)=1">SHALL contain exactly one [1..1] templateId (CONF:5560-32907).</sch:assert>
      <sch:assert id="a-5560-32910" test="cda:templateId[@root='2.16.840.1.113883.19.1000.2.4']">This templateId SHALL contain exactly one [1..1] @root="2.16.840.1.113883.19.1000.2.4" (CONF:5560-32910).</sch:assert>
    </sch:rule>
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.2.4-errors" context="cda:section[cda:templateId[@root='2.16.840.1.113883.19.1000.2.4']]">
      <sch:extends rule="r-urn-oid-2.16.840.1.113883.19.1000.2.4-errors-abstract" />
    </sch:rule>
  </sch:pattern>
  <sch:pattern id="p-urn-oid-2.16.840.1.113883.19.1000.2.5-errors">
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.2.5-errors-abstract" abstract="true">
      <sch:assert id="a-5560-32911" test="count(cda:entry)=1">SHALL contain exactly one [1..1] entry (CONF:5560-32911).</sch:assert>
      <sch:assert id="a-5560-32912" test="cda:entry[count(cda:observation[cda:templateId[@root='2.16.840.1.113883.19.1000.3.7']])=1]">This entry SHALL contain exactly one [1..1] LOINC 96780-2 | Wants help finding or keeping work or a job (identifier: urn:oid:2.16.840.1.113883.19.1000.3.7) (CONF:5560-32912).</sch:assert>
      <sch:assert id="a-5560-32913" test="count(cda:templateId)=1">SHALL contain exactly one [1..1] templateId (CONF:5560-32913).</sch:assert>
      <sch:assert id="a-5560-32914" test="cda:templateId[@root='2.16.840.1.113883.19.1000.2.5']">This templateId SHALL contain exactly one [1..1] @root="2.16.840.1.113883.19.1000.2.5" (CONF:5560-32914).</sch:assert>
    </sch:rule>
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.2.5-errors" context="cda:section[cda:templateId[@root='2.16.840.1.113883.19.1000.2.5']]">
      <sch:extends rule="r-urn-oid-2.16.840.1.113883.19.1000.2.5-errors-abstract" />
    </sch:rule>
  </sch:pattern>
  <sch:pattern id="p-urn-oid-2.16.840.1.113883.19.1000.2.6-errors">
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.2.6-errors-abstract" abstract="true">
      <sch:assert id="a-5560-32915" test="count(cda:entry)=1">SHALL contain exactly one [1..1] entry (CONF:5560-32915).</sch:assert>
      <sch:assert id="a-5560-32916" test="cda:entry[count(cda:observation[cda:templateId[@root='2.16.840.1.113883.19.1000.3.8']])=1]">This entry SHALL contain exactly one [1..1] LOINC 96782-8 | Wants help with school or training (identifier: urn:oid:2.16.840.1.113883.19.1000.3.8) (CONF:5560-32916).</sch:assert>
      <sch:assert id="a-5560-32917" test="count(cda:templateId)=1">SHALL contain exactly one [1..1] templateId (CONF:5560-32917).</sch:assert>
      <sch:assert id="a-5560-32918" test="cda:templateId[@root='2.16.840.1.113883.19.1000.2.6']">This templateId SHALL contain exactly one [1..1] @root="2.16.840.1.113883.19.1000.2.6" (CONF:5560-32918).</sch:assert>
    </sch:rule>
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.2.6-errors" context="cda:section[cda:templateId[@root='2.16.840.1.113883.19.1000.2.6']]">
      <sch:extends rule="r-urn-oid-2.16.840.1.113883.19.1000.2.6-errors-abstract" />
    </sch:rule>
  </sch:pattern>
  <sch:pattern id="p-urn-oid-2.16.840.1.113883.19.1000.2.7-errors">
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.2.7-errors-abstract" abstract="true">
      <sch:assert id="a-5560-32919" test="count(cda:entry[count(cda:observation[cda:templateId[@root='2.16.840.1.113883.19.1000.3.9']])=1])=1">SHALL contain exactly one [1..1] entry (CONF:5560-32919) such that it SHALL contain exactly one [1..1] LOINC 95618-5 | Physically hurt you [HITS] (identifier: urn:oid:2.16.840.1.113883.19.1000.3.9) (CONF:5560-32920).</sch:assert>
      <sch:assert id="a-5560-32921" test="count(cda:templateId)=1">SHALL contain exactly one [1..1] templateId (CONF:5560-32921).</sch:assert>
      <sch:assert id="a-5560-32922" test="count(cda:entry[count(cda:observation[cda:templateId[@root='2.16.840.1.113883.19.1000.3.10']])=1])=1">SHALL contain exactly one [1..1] entry (CONF:5560-32922) such that it SHALL contain exactly one [1..1] LOINC 95617-7 | Insult you or talk down to you [HITS] (identifier: urn:oid:2.16.840.1.113883.19.1000.3.10) (CONF:5560-32923).</sch:assert>
      <sch:assert id="a-5560-32924" test="count(cda:entry[count(cda:observation[cda:templateId[@root='2.16.840.1.113883.19.1000.3.11']])=1])=1">SHALL contain exactly one [1..1] entry (CONF:5560-32924) such that it SHALL contain exactly one [1..1] LOINC 95616-9 | Threaten you with physical harm [HITS] (identifier: urn:oid:2.16.840.1.113883.19.1000.3.11) (CONF:5560-32925).</sch:assert>
      <sch:assert id="a-5560-32926" test="cda:templateId[@root='2.16.840.1.113883.19.1000.2.7']">This templateId SHALL contain exactly one [1..1] @root="2.16.840.1.113883.19.1000.2.7" (CONF:5560-32926).</sch:assert>
      <sch:assert id="a-5560-32927" test="count(cda:entry[count(cda:observation[cda:templateId[@root='2.16.840.1.113883.19.1000.3.12']])=1])=1">SHALL contain exactly one [1..1] entry (CONF:5560-32927) such that it SHALL contain exactly one [1..1] LOINC 95615-1 | Scream or curse at you [HITS] (identifier: urn:oid:2.16.840.1.113883.19.1000.3.12) (CONF:5560-32928).</sch:assert>
      <sch:assert id="a-5560-32929" test="count(cda:entry[count(cda:observation[cda:templateId[@root='2.16.840.1.113883.19.1000.3.15']])=1])=1">SHALL contain exactly one [1..1] entry (CONF:5560-32929) such that it SHALL contain exactly one [1..1] LOINC 95614-4 | Total score [HITS] (identifier: urn:oid:2.16.840.1.113883.19.1000.3.15) (CONF:5560-32930).</sch:assert>
    </sch:rule>
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.2.7-errors" context="cda:section[cda:templateId[@root='2.16.840.1.113883.19.1000.2.7']]">
      <sch:extends rule="r-urn-oid-2.16.840.1.113883.19.1000.2.7-errors-abstract" />
    </sch:rule>
  </sch:pattern>
  <sch:pattern id="p-urn-oid-2.16.840.1.113883.19.1000.3.15-errors">
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.3.15-errors-abstract" abstract="true">
      <sch:assert id="a-5560-32965" test="count(cda:templateId)=1">SHALL contain exactly one [1..1] templateId (CONF:5560-32965).</sch:assert>
      <sch:assert id="a-5560-32966" test="cda:templateId[@root='2.16.840.1.113883.19.1000.3.15']">This templateId SHALL contain exactly one [1..1] @root="2.16.840.1.113883.19.1000.3.15" (CONF:5560-32966).</sch:assert>
      <sch:assert id="a-5560-32967" test="count(cda:code[@code='95614-4'][@codeSystem='2.16.840.1.113883.6.1' or @nullFlavor])=1">SHALL contain exactly one [1..1] code="95614-4" Total score [HITS] (CodeSystem: LOINC urn:oid:2.16.840.1.113883.6.1) (CONF:5560-32967).</sch:assert>
      <sch:assert id="a-5560-32968" test="count(cda:value[@xsi:type='INT'])=1">SHALL contain exactly one [1..1] value with @xsi:type="INT" (CONF:5560-32968).</sch:assert>
    </sch:rule>
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.3.15-errors" context="cda:observation[cda:templateId[@root='2.16.840.1.113883.19.1000.3.15']]">
      <sch:extends rule="r-urn-oid-2.16.840.1.113883.19.1000.3.15-errors-abstract" />
    </sch:rule>
  </sch:pattern>
  <sch:pattern id="p-urn-oid-2.16.840.1.113883.19.1000.3.1-warnings">
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.3.1-warnings-abstract" abstract="true">
      <sch:assert test="."></sch:assert>
    </sch:rule>
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.3.1-warnings" context="cda:observation[cda:templateId[@root='2.16.840.1.113883.19.1000.3.1']]">
      <sch:extends rule="r-urn-oid-2.16.840.1.113883.19.1000.3.1-warnings-abstract" />
    </sch:rule>
  </sch:pattern>
  <sch:pattern id="p-urn-oid-2.16.840.1.113883.19.1000.2.2-warnings">
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.2.2-warnings-abstract" abstract="true">
      <sch:assert test="."></sch:assert>
    </sch:rule>
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.2.2-warnings" context="cda:section[cda:templateId[@root='2.16.840.1.113883.19.1000.2.2']]">
      <sch:extends rule="r-urn-oid-2.16.840.1.113883.19.1000.2.2-warnings-abstract" />
    </sch:rule>
  </sch:pattern>
  <sch:pattern id="p-urn-oid-2.16.840.1.113883.19.1000.1-warnings">
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.1-warnings-abstract" abstract="true">
      <sch:assert id="a-5560-32969" test="cda:recordTarget/cda:patientRole/cda:patient[count(cda:administrativeGenderCode)=1]">This patient SHOULD contain exactly one [1..1] administrativeGenderCode (CONF:5560-32969).</sch:assert>
      <sch:assert id="a-5560-32970" test="cda:recordTarget/cda:patientRole/cda:patient[count(cda:birthTime)=1]">This patient SHOULD contain exactly one [1..1] birthTime (CONF:5560-32970).</sch:assert>
      <sch:assert id="a-5560-32971" test="count(cda:componentOf)=1">SHOULD contain exactly one [1..1] componentOf (CONF:5560-32971).</sch:assert>
      <sch:assert id="a-5560-32973" test="cda:componentOf/cda:encompassingEncounter[count(cda:id)=1]">This encompassingEncounter SHOULD contain exactly one [1..1] id (CONF:5560-32973).</sch:assert>
      <sch:assert id="a-5560-32974" test="cda:componentOf/cda:encompassingEncounter[count(cda:code)=1]">This encompassingEncounter SHOULD contain exactly one [1..1] code (CONF:5560-32974).</sch:assert>
      <sch:assert id="a-5560-32977" test="count(cda:authorization)=1">SHOULD contain exactly one [1..1] authorization (CONF:5560-32977).</sch:assert>
      <sch:assert id="a-5560-32981" test="not(cda:author/cda:assignedAuthor/cda:assignedPerson) or cda:author/cda:assignedAuthor/cda:assignedPerson[count(cda:name)=1]">The assignedPerson, if present, SHOULD contain exactly one [1..1] name (CONF:5560-32981).</sch:assert>
      <sch:assert id="a-5560-32982" test="not(cda:author/cda:assignedAuthor/cda:representedOrganization) or cda:author/cda:assignedAuthor/cda:representedOrganization[count(cda:name)=1]">The representedOrganization, if present, SHOULD contain exactly one [1..1] name (CONF:5560-32982).</sch:assert>
      <sch:assert id="a-5560-32989" test="cda:custodian/cda:assignedCustodian/cda:representedCustodianOrganization[count(cda:name)=1]">This representedCustodianOrganization SHOULD contain exactly one [1..1] name (CONF:5560-32989).</sch:assert>
      <sch:assert id="a-5560-32990" test="cda:custodian/cda:assignedCustodian/cda:representedCustodianOrganization[count(cda:addr)=1]">This representedCustodianOrganization SHOULD contain exactly one [1..1] addr (CONF:5560-32990).</sch:assert>
      <sch:assert id="a-5560-32991" test="not(cda:informationRecipient/cda:intendedRecipient/cda:informationRecipient) or cda:informationRecipient/cda:intendedRecipient/cda:informationRecipient[count(cda:name) &gt; 0]">The informationRecipient, if present, SHOULD contain zero or more [0..*] name (CONF:5560-32991).</sch:assert>
      <sch:assert id="a-5560-32992" test="not(cda:informationRecipient/cda:intendedRecipient/cda:receivedOrganization) or cda:informationRecipient/cda:intendedRecipient/cda:receivedOrganization[count(cda:name)=1]">The receivedOrganization, if present, SHOULD contain exactly one [1..1] name (CONF:5560-32992).</sch:assert>
      <sch:assert id="a-5560-32993" test="not(cda:informationRecipient/cda:intendedRecipient/cda:receivedOrganization) or cda:informationRecipient/cda:intendedRecipient/cda:receivedOrganization[count(cda:addr)=1]">The receivedOrganization, if present, SHOULD contain exactly one [1..1] addr (CONF:5560-32993).</sch:assert>
    </sch:rule>
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.1-warnings" context="cda:ClinicalDocument[cda:templateId[@root='2.16.840.1.113883.19.1000.1']]">
      <sch:extends rule="r-urn-oid-2.16.840.1.113883.19.1000.1-warnings-abstract" />
    </sch:rule>
  </sch:pattern>
  <sch:pattern id="p-urn-oid-2.16.840.1.113883.19.1000.3.2-warnings">
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.3.2-warnings-abstract" abstract="true">
      <sch:assert test="."></sch:assert>
    </sch:rule>
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.3.2-warnings" context="cda:observation[cda:templateId[@root='2.16.840.1.113883.19.1000.3.2']]">
      <sch:extends rule="r-urn-oid-2.16.840.1.113883.19.1000.3.2-warnings-abstract" />
    </sch:rule>
  </sch:pattern>
  <sch:pattern id="p-urn-oid-2.16.840.1.113883.19.1000.3.3-warnings">
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.3.3-warnings-abstract" abstract="true">
      <sch:assert test="."></sch:assert>
    </sch:rule>
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.3.3-warnings" context="cda:observation[cda:templateId[@root='2.16.840.1.113883.19.1000.3.3']]">
      <sch:extends rule="r-urn-oid-2.16.840.1.113883.19.1000.3.3-warnings-abstract" />
    </sch:rule>
  </sch:pattern>
  <sch:pattern id="p-urn-oid-2.16.840.1.113883.19.1000.3.4-warnings">
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.3.4-warnings-abstract" abstract="true">
      <sch:assert test="."></sch:assert>
    </sch:rule>
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.3.4-warnings" context="cda:observation[cda:templateId[@root='2.16.840.1.113883.19.1000.3.4']]">
      <sch:extends rule="r-urn-oid-2.16.840.1.113883.19.1000.3.4-warnings-abstract" />
    </sch:rule>
  </sch:pattern>
  <sch:pattern id="p-urn-oid-2.16.840.1.113883.19.1000.3.5-warnings">
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.3.5-warnings-abstract" abstract="true">
      <sch:assert test="."></sch:assert>
    </sch:rule>
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.3.5-warnings" context="cda:observation[cda:templateId[@root='2.16.840.1.113883.19.1000.3.5']]">
      <sch:extends rule="r-urn-oid-2.16.840.1.113883.19.1000.3.5-warnings-abstract" />
    </sch:rule>
  </sch:pattern>
  <sch:pattern id="p-urn-oid-2.16.840.1.113883.19.1000.3.6-warnings">
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.3.6-warnings-abstract" abstract="true">
      <sch:assert test="."></sch:assert>
    </sch:rule>
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.3.6-warnings" context="cda:observation[cda:templateId[@root='2.16.840.1.113883.19.1000.3.6']]">
      <sch:extends rule="r-urn-oid-2.16.840.1.113883.19.1000.3.6-warnings-abstract" />
    </sch:rule>
  </sch:pattern>
  <sch:pattern id="p-urn-oid-2.16.840.1.113883.19.1000.3.7-warnings">
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.3.7-warnings-abstract" abstract="true">
      <sch:assert test="."></sch:assert>
    </sch:rule>
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.3.7-warnings" context="cda:observation[cda:templateId[@root='2.16.840.1.113883.19.1000.3.7']]">
      <sch:extends rule="r-urn-oid-2.16.840.1.113883.19.1000.3.7-warnings-abstract" />
    </sch:rule>
  </sch:pattern>
  <sch:pattern id="p-urn-oid-2.16.840.1.113883.19.1000.3.8-warnings">
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.3.8-warnings-abstract" abstract="true">
      <sch:assert test="."></sch:assert>
    </sch:rule>
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.3.8-warnings" context="cda:observation[cda:templateId[@root='2.16.840.1.113883.19.1000.3.8']]">
      <sch:extends rule="r-urn-oid-2.16.840.1.113883.19.1000.3.8-warnings-abstract" />
    </sch:rule>
  </sch:pattern>
  <sch:pattern id="p-urn-oid-2.16.840.1.113883.19.1000.3.9-warnings">
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.3.9-warnings-abstract" abstract="true">
      <sch:assert test="."></sch:assert>
    </sch:rule>
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.3.9-warnings" context="cda:observation[cda:templateId[@root='2.16.840.1.113883.19.1000.3.9']]">
      <sch:extends rule="r-urn-oid-2.16.840.1.113883.19.1000.3.9-warnings-abstract" />
    </sch:rule>
  </sch:pattern>
  <sch:pattern id="p-urn-oid-2.16.840.1.113883.19.1000.3.10-warnings">
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.3.10-warnings-abstract" abstract="true">
      <sch:assert test="."></sch:assert>
    </sch:rule>
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.3.10-warnings" context="cda:observation[cda:templateId[@root='2.16.840.1.113883.19.1000.3.10']]">
      <sch:extends rule="r-urn-oid-2.16.840.1.113883.19.1000.3.10-warnings-abstract" />
    </sch:rule>
  </sch:pattern>
  <sch:pattern id="p-urn-oid-2.16.840.1.113883.19.1000.3.11-warnings">
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.3.11-warnings-abstract" abstract="true">
      <sch:assert test="."></sch:assert>
    </sch:rule>
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.3.11-warnings" context="cda:observation[cda:templateId[@root='2.16.840.1.113883.19.1000.3.11']]">
      <sch:extends rule="r-urn-oid-2.16.840.1.113883.19.1000.3.11-warnings-abstract" />
    </sch:rule>
  </sch:pattern>
  <sch:pattern id="p-urn-oid-2.16.840.1.113883.19.1000.3.12-warnings">
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.3.12-warnings-abstract" abstract="true">
      <sch:assert test="."></sch:assert>
    </sch:rule>
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.3.12-warnings" context="cda:observation[cda:templateId[@root='2.16.840.1.113883.19.1000.3.12']]">
      <sch:extends rule="r-urn-oid-2.16.840.1.113883.19.1000.3.12-warnings-abstract" />
    </sch:rule>
  </sch:pattern>
  <sch:pattern id="p-urn-oid-2.16.840.1.113883.19.1000.3.14-warnings">
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.3.14-warnings-abstract" abstract="true">
      <sch:assert test="."></sch:assert>
    </sch:rule>
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.3.14-warnings" context="cda:act[cda:templateId[@root='2.16.840.1.113883.19.1000.3.14']]">
      <sch:extends rule="r-urn-oid-2.16.840.1.113883.19.1000.3.14-warnings-abstract" />
    </sch:rule>
  </sch:pattern>
  <sch:pattern id="p-urn-oid-2.16.840.1.113883.19.1000.2.1-warnings">
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.2.1-warnings-abstract" abstract="true">
      <sch:assert test="."></sch:assert>
    </sch:rule>
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.2.1-warnings" context="cda:section[cda:templateId[@root='2.16.840.1.113883.19.1000.2.1']]">
      <sch:extends rule="r-urn-oid-2.16.840.1.113883.19.1000.2.1-warnings-abstract" />
    </sch:rule>
  </sch:pattern>
  <sch:pattern id="p-urn-oid-2.16.840.1.113883.19.1000.2.3-warnings">
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.2.3-warnings-abstract" abstract="true">
      <sch:assert test="."></sch:assert>
    </sch:rule>
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.2.3-warnings" context="cda:section[cda:templateId[@root='2.16.840.1.113883.19.1000.2.3']]">
      <sch:extends rule="r-urn-oid-2.16.840.1.113883.19.1000.2.3-warnings-abstract" />
    </sch:rule>
  </sch:pattern>
  <sch:pattern id="p-urn-oid-2.16.840.1.113883.19.1000.2.4-warnings">
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.2.4-warnings-abstract" abstract="true">
      <sch:assert test="."></sch:assert>
    </sch:rule>
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.2.4-warnings" context="cda:section[cda:templateId[@root='2.16.840.1.113883.19.1000.2.4']]">
      <sch:extends rule="r-urn-oid-2.16.840.1.113883.19.1000.2.4-warnings-abstract" />
    </sch:rule>
  </sch:pattern>
  <sch:pattern id="p-urn-oid-2.16.840.1.113883.19.1000.2.5-warnings">
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.2.5-warnings-abstract" abstract="true">
      <sch:assert test="."></sch:assert>
    </sch:rule>
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.2.5-warnings" context="cda:section[cda:templateId[@root='2.16.840.1.113883.19.1000.2.5']]">
      <sch:extends rule="r-urn-oid-2.16.840.1.113883.19.1000.2.5-warnings-abstract" />
    </sch:rule>
  </sch:pattern>
  <sch:pattern id="p-urn-oid-2.16.840.1.113883.19.1000.2.6-warnings">
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.2.6-warnings-abstract" abstract="true">
      <sch:assert test="."></sch:assert>
    </sch:rule>
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.2.6-warnings" context="cda:section[cda:templateId[@root='2.16.840.1.113883.19.1000.2.6']]">
      <sch:extends rule="r-urn-oid-2.16.840.1.113883.19.1000.2.6-warnings-abstract" />
    </sch:rule>
  </sch:pattern>
  <sch:pattern id="p-urn-oid-2.16.840.1.113883.19.1000.2.7-warnings">
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.2.7-warnings-abstract" abstract="true">
      <sch:assert test="."></sch:assert>
    </sch:rule>
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.2.7-warnings" context="cda:section[cda:templateId[@root='2.16.840.1.113883.19.1000.2.7']]">
      <sch:extends rule="r-urn-oid-2.16.840.1.113883.19.1000.2.7-warnings-abstract" />
    </sch:rule>
  </sch:pattern>
  <sch:pattern id="p-urn-oid-2.16.840.1.113883.19.1000.3.15-warnings">
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.3.15-warnings-abstract" abstract="true">
      <sch:assert test="."></sch:assert>
    </sch:rule>
    <sch:rule id="r-urn-oid-2.16.840.1.113883.19.1000.3.15-warnings" context="cda:observation[cda:templateId[@root='2.16.840.1.113883.19.1000.3.15']]">
      <sch:extends rule="r-urn-oid-2.16.840.1.113883.19.1000.3.15-warnings-abstract" />
    </sch:rule>
  </sch:pattern>
</sch:schema>