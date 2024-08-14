export class FhirViewer extends HTMLElement {
    constructor() {
        super();
        this.attachShadow({ mode: 'open' });
        this.jsrender = window.jsrender;

        // Shadow DOM template and styles
        this.shadowRoot.innerHTML = `
            <style>
                .resource {
                    margin-bottom: 1rem;
                    font-family: Arial, sans-serif;
                }
                .header {
                    font-size: 1.25rem;
                    font-weight: bold;
                    color: #333;
                    margin-bottom: 0.5rem;
                }
                .table {
                    width: 100%;
                    border-collapse: collapse;
                }
                .table th, .table td {
                    padding: 0.75rem;
                    text-align: left;
                    border-bottom: 1px solid #ddd;
                }
                .table th {
                    background-color: #f2f2f2;
                    font-weight: normal;
                    color: #555;
                }
                .table tr:nth-child(even) {
                    background-color: #f9f9f9;
                }
                .table tr:hover {
                    background-color: #f1f1f1;
                }
            </style>
            <div id="loading-indicator" style="display: none;">Loading...</div>
            <div id="rendered-content"></div>
        `;

        this.templates = {
            patient: `
                <div class="resource patient">
                    <div class="header">Patient</div>
                    <table class="table">
                        <tbody>
                            <tr>
                                <th>Name</th>
                                <td>{{:name[0]?.given || 'N/A'}} {{:name[0]?.family || ''}}</td>
                            </tr>
                            <tr>
                                <th>ID</th>
                                <td>{{:id || 'N/A'}}</td>
                            </tr>
                            <tr>
                                <th>Gender</th>
                                <td>{{:gender || 'N/A'}}</td>
                            </tr>
                            <tr>
                                <th>Birth Date</th>
                                <td>{{:birthDate || 'N/A'}}</td>
                            </tr>
                        </tbody>
                    </table>
                </div>
            `,
            observation: `
                <div class="resource observation">
                    <div class="header">Observation</div>
                    <table class="table">
                        <tbody>
                            <tr>
                                <th>Observation</th>                              
                                <td> 
                                {{if code?.coding[0]}}
                                        {{:code?.coding[0]?.display || 'N/A'}}  
                                    {{else}}
                                        No observation provided
                                {{/if}}
                                </td>
                            </tr>
                            <tr>
                                <th>ID</th>
                                <td>{{:id || 'N/A'}}</td>
                            </tr>
                            <tr>
                                <th>Status</th>
                                <td>{{:status || 'N/A'}}</td>
                            </tr>
                            <tr>
                                <th>Value</th>
                                <td> 
                                    {{if valueCodeableConcept?.coding[0]}}
                                        {{:valueCodeableConcept?.coding[0]?.display || 'N/A'}}  
                                    {{else}}
                                        No value provided
                                    {{/if}}
                                </td>
                            </tr>
                        </tbody>
                    </table>
                </div>
            `,
            consent: `
                <div class="resource consent">
                    <div class="header">Consent</div>
                    <table class="table">
                        <tbody>
                            <tr>
                                <th>Consent ID</th>
                                <td>{{:id || 'N/A'}}</td>
                            </tr>
                            <tr>
                                <th>Status</th>
                                <td>{{:status || 'N/A'}}</td>
                            </tr>
                            <tr>
                                <th>Scope</th>
                                <td>{{:scope?.text || 'N/A'}}</td>
                            </tr>
                        </tbody>
                    </table>
                </div>
            `,
            organization: `
                <div class="resource organization">
                    <div class="header">Organization</div>
                    <table class="table">
                        <tbody>
                            <tr>
                                <th>Name</th>
                                <td>{{:name || 'N/A'}}</td>
                            </tr>
                            <tr>
                                <th>ID</th>
                                <td>{{:id || 'N/A'}}</td>
                            </tr>
                            <tr>
                                <th>Address</th>
                                <td>
                                    {{if address && address.length > 0}}
                                        {{:address[0]?.text || ''}}, {{:address[0]?.city || ''}}, {{:address[0]?.state || ''}} {{:address[0]?.postalCode || ''}}
                                    {{else}}
                                        No address provided
                                    {{/if}}
                                </td>
                            </tr>
                            <tr>
                                <th>Contact</th>
                                <td>
                                    {{if telecom && telecom.length > 0}}
                                        {{:telecom[0]?.value || 'N/A'}}
                                    {{else}}
                                        No contact provided
                                    {{/if}}
                                </td>
                            </tr>
                        </tbody>
                    </table>
                </div>
            `,
            encounter: `
                <div class="resource encounter">
                    <div class="header">Encounter</div>
                    <table class="table">
                        <tbody>
                            <tr>
                                <th>Encounter ID</th>
                                <td>{{:id || 'N/A'}}</td>
                            </tr>
                            <tr>
                                <th>Status</th>
                                <td>{{:status || 'N/A'}}</td>
                            </tr>
                            <tr>
                                <th>Type</th>
                                <td>
                                    {{if type && type[0]}}
                                        {{:type[0]?.text || 'N/A'}}
                                    {{else}}
                                        No type provided
                                    {{/if}}
                                </td>
                            </tr>
                            <tr>
                                <th>Subject</th>
                                <td>
                                    {{if subject}}
                                        {{:subject?.reference || 'N/A'}}
                                    {{else}}
                                        No subject provided
                                    {{/if}}
                                </td>
                            </tr>
                        </tbody>
                    </table>
                </div>
            `,
            questionnaireresponse: `
                <div class="resource questionnaireresponse">
                    <div class="header">Questionnaire Response</div>
                    <table class="table">
                        <tbody>
                            <tr>
                                <th>ID</th>
                                <td>{{:id || 'N/A'}}</td>
                            </tr>
                            <tr>
                                <th>Status</th>
                                <td>{{:status || 'N/A'}}</td>
                            </tr>
                            <tr>
                                <th>Authored</th>
                                <td>{{:authored || 'N/A'}}</td>
                            </tr>
                            <tr>
                                <th>Questionnaire</th>
                                <td>{{:questionnaire || 'N/A'}}</td>
                            </tr>
                        </tbody>
                    </table>
                </div>
            `
        };
    }

    static get observedAttributes() {
        return ['src'];
    }

    attributeChangedCallback(name, oldValue, newValue) {
        if (name === 'src' && oldValue !== newValue) {
            this.render(newValue);
        }
    }

    async render(fhirUrl) {
        const renderIn = this.shadowRoot.getElementById("rendered-content");
        const loadingIndicator = this.shadowRoot.getElementById("loading-indicator");

        loadingIndicator.style.display = 'block';
        renderIn.innerHTML = ''; // Clear previous content

        try {
            const response = await fetch(fhirUrl);
            if (!response.ok) {
                throw new Error(`HTTP error! status: ${response.status}`);
            }
            const data = await response.json();
            renderIn.innerHTML = ''; // Clear previous content
            data.forEach(entry => {
                const resource = entry.resource;
                const resourceType = resource.resourceType.toLowerCase();
                if (this.templates[resourceType]) {
                    try {
                        const template = this.jsrender.templates(this.templates[resourceType]);
                        const renderedHtml = template.render(resource);
                        renderIn.innerHTML += renderedHtml;
                    } catch (error) {
                        renderIn.innerHTML += `<p>Error rendering <code>${resourceType}</code>: ${error}<p>`;
                        console.error(`Error rendering ${resourceType}:`, error);
                    }
                } else {
                    renderIn.innerHTML += `<p>No template found for resource type: <code>${resourceType}</code>, add in <code>FhirViewer.templates</code>.<p>`;
                    console.warn(`No template found for resource type: ${resourceType}`);
                }
            });
        } catch (error) {
            renderIn.innerHTML += `<p>Error fetching ${fhirUrl}: ${error} (see console)<p>`;
            console.error('Error fetching FHIR data:', error);
        }finally {
            // Hide the loading indicator
            loadingIndicator.style.display = 'none';
        }
    }
}
