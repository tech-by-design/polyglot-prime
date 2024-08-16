## SHIN-NY FHIR IG test fixtures

- `happy-path` directory contains FHIR JSON Bundles that should "run clean" and generate no validation warnings or errors from the FHIR validation engines
- `unhappy-path` directory contains FHIR JSON Bundles that should deterministically generate validation errors based on "broken" content 

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
./run_synthea -ig ig/
```

The generated files will be in the output/fhir folder and copy the json with  file name format ```<first-name>_<lstn-ame>_<family-name>_<bundle-id>.json``` into  `happy-path` folder.

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
./run_synthea -ig ig/
```

The generated files will be in the output/fhir folder.
Edit the newly generated file and make it invalid against the IG and copy to unhappy path.
Copy the json with  file name format ```<first-name>_<lstn-ame>_<family-name>_<bundle-id>.json``` into  `unhappy-path` folder.

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

