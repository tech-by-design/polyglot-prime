## SHIN-NY FHIR IG test fixtures

- `happy-path` directory contains FHIR JSON Bundles that should "run clean" and generate no validation warnings or errors from the FHIR validation engines
- `unhappy-path` directory contains FHIR JSON Bundles that should deterministically generate validation errors based on "broken" content 

#### A note about the Implementation Guide
The FHIR v4 Implementation Guide you provided primarily defines the "SHINNY Bundle Profile," which is an extension of the base FHIR Bundle. This profile introduces additional constraints and rules for working with bundles in specific healthcare contexts. Here’s a summary of its key contents:

**StructureDefinition:**
The SHINNY Bundle Profile extends the base FHIR Bundle resource, imposing additional constraints to ensure interoperability and adherence to specific healthcare use cases.
The profile is in the draft stage and is identified by the URL http://localhost:8000/ImplementationGuide/HRSN.

**Constraints:**
The profile defines several constraints on the Bundle resource, such as ensuring that the total element is only present when the bundle is a search set or history, and specific constraints on the use of entry.request and entry.response elements depending on the type of bundle.
The profile also includes custom constraints like ensuring a relationship exists between a Patient and an Encounter or Location within the bundle.

**Mappings:**
The SHINNY Bundle Profile includes mappings to HL7 v2, HL7 v3 (RIM), CDA (R2), and the FiveWs pattern.
Additional Extensions and Elements:

The profile introduces elements like Bundle.meta, which includes metadata about the resource, and Bundle.link, which provides links related to the bundle.
The profile also supports extensions and modifier extensions that can represent additional implementation-specific information.

**Usage:**
This guide is aimed at ensuring that the resources within the bundle adhere to specific rules and relationships, particularly in environments where specific organizational or encounter-related relationships must be maintained.
The guide is detailed and provides explicit rules for managing and structuring healthcare data within bundles, making it crucial for developers and implementers working with FHIR in healthcare applications.

### "Happy Path" fixtures

Contains FHIR JSON Bundles that should "run clean" and generate no validation warnings or errors from the FHIR validation engines.

#### _Happy Path_ fixture naming conventions

The fixture file naming is as follows:

```<first-name>_<lstn-ame>_<family-name>_<bundle-id>.json```

For example:

For a fixture with the following data:

```json
{
  "id": "4d678b50-d57b-97cd-d8f5-97209d6b2e6d",
  .....
        "name": [{
                "use": "official",
                "family": "Kihn564",
                "given": ["Angele108", "Keitha498"],
                "prefix": ["Mrs."]
            }
        ]
  ..... 
}
```
The fixture file name will be: *Angele108_Keitha498_Kihn564_4d678b50-d57b-97cd-d8f5-97209d6b2e6d.json*

#### How to generate _Happy Path_ fixtures...
```
git clone https://github.com/synthetichealth/synthea.git
cd synthea
./gradlew build check test
```

Create a folder named `ig` in the synthea root folder and copy the implementatation guide json file to `ig` folder

```
mkdir ig
cd ig
wget -O StructureDefinition-SHINNYBundleProfile.json http://localhost:8000/ImplementationGuide/HRSN/StructureDefinition-SHINNYBundleProfile.json
cd ..
./run_synthea -ig ig/
```

The generated files will be in the `output/fhir` folder and copy the json with  file name format ```<first-name>_<lstn-ame>_<family-name>_<bundle-id>.json``` into  `happy-path` folder.

```
polyglot-prime
└── hub-prime
    └── src
        └── test
            └── resources
                └── org
                    └── techbd
                        └── fixtures
                            └── happy-path

```
### "Unhappy Path" fixtures

Contains FHIR JSON Bundles that should deterministically generate validation errors based on "broken" content.

#### _Unhappy Path_ fixture naming conventions

```<first-name>_<lstn-ame>_<family-name>_<bundle-id>.json```

For example:

For a fixture with the following data:

```json
{
  "id": "4d678b50-d57b-97cd-d8f5-97209d6b2e6d",
  .....
        "name": [{
                "use": "official",
                "family": "Kihn564",
                "given": ["Angele108", "Keitha498"],
                "prefix": ["Mrs."]
            }
        ]
  ..... 
}
```
The fixture file name will be: *Angele108_Keitha498_Kihn564_4d678b50-d57b-97cd-d8f5-97209d6b2e6d.json*

#### How to generate _Unhappy Path_ fixtures...

```
git clone https://github.com/synthetichealth/synthea.git
cd synthea
./gradlew build check test
```

Create a folder named `ig` in the synthea root folder and copy the implementatation guide json file to `ig` folder

```
mkdir ig
cd ig
wget -O StructureDefinition-SHINNYBundleProfile.json http://localhost:8000/ImplementationGuide/HRSN/StructureDefinition-SHINNYBundleProfile.json
cd ..
./run_synthea -ig ig/
```

The generated files will be in the `output/fhir` folder.

In order to make the Synthea generated output file invalid, follow the steps
1. Open ChatGpt 4
2. Upload the IG file, prompting - *Read this FHIR v4 Implementation Guide, to make the system learn the IG content.*
3. Go to the `output/fhir` folder and locate the file with the naming format - `<first-name>_<lstn-ame>_<family-name>_<bundle-id>.json`
4. Upload this file also to the Chat GPT, prompting - *Use this attachment as valid Synthea generated FHIR JSON which should pass the IG validation. Generate for me another JSON file based on this attachment with 10 errors introduced that would allow me to do a deterministic test of the IG validation using HAPI validation engine. and update the name of the file to summarize the nature of the errors you introduced.*

Download the file generated by Chat GPT and upload the same into  `unhappy-path` folder.

```
polyglot-prime
└── hub-prime
    └── src
        └── test
            └── resources
                └── org
                    └── techbd
                        └── fixtures
                            └── unhappy-path

```

