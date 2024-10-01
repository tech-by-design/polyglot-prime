/**
 * @class ModalAide
 * @classdesc A general-purpose helper class for creating and managing modal 
 * popups, particularly for displaying JSON data. This class facilitates the
 * creation of modal elements dynamically and provides methods to view JSON
 * content within the modal.
 */
export class ModalAide {
    /**
     * Constructs a new ModalAide instance.
     * @param {string} modalId - The unique identifier for the modal.
     */
    constructor(modalId) {
       // this.modalId = modalId;
       // this.modalContentClass = `${modalId}-content`;
       // this.modalClass = `${modalId}`;
       // this.closeClass = `${modalId}-close`;
       // this.jsonViewerId = `${modalId}-json`;
        this.fhirViewerId = 'fhir-viewer';
        this.fhirViewerModalId = 'fhir-viewer-modal';  
        this.fhirViewerModalContentClass = 'fhir-viewer-content';
        this.fhirViewerCloseClass = 'fhir-viewer-close';   
        
        this.modalId = 'json-viewer-modal';
        this.modalClass = 'json-viewer-modal';
        this.modalContentClass = 'json-viewer-modal-content';
        this.closeClass = 'json-viewer-modal-close';
        this.rowContainerClass = 'json-viewer-row-container';
        this.rowClass = 'json-viewer-row';
        this.jsonViewerId = 'json-viewer';
    }

    /**
     * Sets up the JSON viewer modal. This method assumes that the json-viewer
     * library is loaded. If the modal does not exist in the DOM, it creates
     * the necessary HTML and CSS for the modal.
     */
    setupJsonViewerModal() {
        // this method assume that https://github.com/alenaksu/json-viewer has been loaded
        // <script src="https://unpkg.com/@alenaksu/json-viewer@2.0.0/dist/json-viewer.bundle.js"></script>
        // TODO: automatically detect if json-viewer.bundle.js is missing and load it
        if (!document.getElementById(this.modalId)) {
            const style = document.createElement('style');
            style.id = this.modalId + "-styles";
            style.innerHTML = `
                .${this.modalClass} {
                    display: none;
                    position: fixed;
                    z-index: 1000;
                    left: 0;
                    top: 0;
                    width: 100%;
                    height: 100%;
                    overflow: auto;
                    background-color: rgba(0, 0, 0, 0.5);
                }

                .${this.modalContentClass} {
                    background-color: #fff;
                    margin: 15% auto;
                    padding: 20px;
                    border: 1px solid #888;
                    width: 80%;
                }

                .${this.closeClass} {
                    color: #aaa;
                    float: right;
                    font-size: 28px;
                    font-weight: bold;
                }

                .${this.closeClass}:hover,
                .${this.closeClass}:focus {
                    color: black;
                    text-decoration: none;
                    cursor: pointer;
                }
            `;
            document.head.appendChild(style);

            const modalHtml = `
                <div id="${this.modalId}" class="${this.modalClass}">
                    <div class="${this.modalContentClass}">
                        <span class="${this.closeClass}">&times;</span>
                        <json-viewer id="${this.jsonViewerId}"></json-viewer>
                    </div>
                </div>
            `;
            document.body.insertAdjacentHTML('beforeend', modalHtml);

            document.querySelector(`.${this.closeClass}`).onclick = () => {
                document.getElementById(this.modalId).style.display = 'none';
            };

            window.onclick = (event) => {
                if (event.target == document.getElementById(this.modalId)) {
                    document.getElementById(this.modalId).style.display = 'none';
                }
            };
        }
    }
   
    /**
     * Displays a JSON value within the modal.
     * @param {Object} value - The JSON object to be displayed in the modal.
     */
    viewJsonValue(value) {
        this.setupJsonViewerModal();
        document.querySelector(`#${this.jsonViewerId}`).data = value;
        if(value.length == 1){
            document.querySelector(`#${this.jsonViewerId}`).expandAll();
        }
        document.getElementById(this.modalId).style.display = 'block';
    }

    /**
     * Fetches JSON data from a specified URL and displays it within the modal.
     * @param {string} url - The URL to fetch JSON data from.
     */
    viewFetchedJsonValue(url) {
        fetch(url)
            .then(response => response.json())
            .then(data => this.viewJsonValue(data))
            .catch(error => console.error(`Error fetching data from ${url}`, error));
    }

    /**
     * Sets up the FHIR viewer modal. This method creates the necessary HTML 
     * and CSS for the FHIR viewer modal if it does not exist in the DOM.
     */
    setupFhirViewerModal() {
        if (!document.getElementById(this.fhirViewerModalId)) {
            const style = document.createElement('style');
            style.id = `${this.fhirViewerModalId}-styles`;
            style.innerHTML = `
                .${this.fhirViewerModalId} {
                        display: none;
                        position: fixed;
                        z-index: 1000;
                        left: 0;
                        top: 0;
                        width: 100%;
                        height: 100%;
                        overflow: auto;
                        background-color: rgba(0, 0, 0, 0.5);
                    }

                    .${this.fhirViewerModalContentClass} {
                        background-color: #fff;
                        margin: 15% auto;
                        padding: 20px;
                        border: 1px solid #888;
                        width: 80%;
                    }

                    .${this.fhirViewerCloseClass} {
                        color: #aaa;
                        float: right;
                        font-size: 28px;
                        font-weight: bold;
                        cursor: pointer;
                    }

                    .${this.fhirViewerCloseClass}:hover,
                    .${this.fhirViewerCloseClass}:focus {
                        color: black;
                        text-decoration: none;
                        cursor: pointer;
                    }
                `;
                document.head.appendChild(style);

                const modalHtml = `
                    <div id="${this.fhirViewerModalId}" class="${this.fhirViewerModalId}">
                        <div class="${this.fhirViewerModalContentClass}">
                            <span id="fhir-viewer-close" class="${this.fhirViewerCloseClass}">&times;</span>
                            <fhir-viewer id="${this.fhirViewerId}"></fhir-viewer>
                        </div>
                    </div>
                `;
                document.body.insertAdjacentHTML('beforeend', modalHtml);

                // Add event listeners for closing the modal
                document.getElementById('fhir-viewer-close').addEventListener('click', this.closeFhirViewer.bind(this));
                window.addEventListener('click', (event) => {
                    if (event.target.id === this.fhirViewerModalId) {
                        this.closeFhirViewer();
                    }
                });
            }
    }

    /**
     * Shows the FHIR viewer modal with the specified URL.
     * @param {string} fhirUrl - The URL to be displayed in the FHIR viewer.
     */
    showFhirViewer(fhirUrl) {
        this.setupFhirViewerModal();
        const fhirViewer = document.getElementById(this.fhirViewerId);
        fhirViewer.setAttribute('src', fhirUrl);
        document.getElementById(this.fhirViewerModalId).style.display = 'block';
    }

    /**
     * Closes the FHIR viewer modal.
     */
    closeFhirViewer() {
        const modal = document.getElementById(this.fhirViewerModalId);
        if (modal) {
            modal.style.display = 'none';
        }
    }
    
    
    setupJsonViewerModalCustom() {
        // Assuming https://github.com/alenaksu/json-viewer is already loaded
        if (!document.getElementById(this.modalId)) {
            const style = document.createElement('style');
            style.id = this.modalId + "-styles";
            style.innerHTML = `
                .${this.modalClass} {
                    display: none;
                    position: fixed;
                    z-index: 1000;
                    left: 0;
                    top: 0;
                    width: 100%;
                    height: 100%;
                    overflow: auto;
                    background-color: rgba(0, 0, 0, 0.5);
                }
    
                .${this.modalContentClass} {
                    background-color: #fff;
                    margin: 15% auto;
                    padding: 20px;
                    border: 1px solid #888;
                    width: 80%;
                }
    
                .${this.closeClass} {
                    color: #aaa;
                    float: right;
                    font-size: 28px;
                    font-weight: bold;
                }
    
                .${this.closeClass}:hover,
                .${this.closeClass}:focus {
                    color: black;
                    text-decoration: none;
                    cursor: pointer;
                }
    
                .${this.rowClass} {
                    padding: 10px;
                }
    
                .${this.rowClass}:nth-child(even) {
                    background-color: #f9f9f9;
                }
    
                .${this.rowClass}:nth-child(odd) {
                    background-color: #fff;
                }
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

                .table th {
                    background-color: #f2f2f2;
                    font-weight: normal;
                    color: #555;
                }

                .table th, .table td {
                    padding: 0.75rem;
                    text-align: left;
                    border-bottom: 1px solid #ddd;
                }
                     .${this.closeClass}:hover,
            .${this.closeClass}:focus {
                color: black;
                text-decoration: none;
                cursor: pointer;
            }

            .level-0 {
                font-weight: bold;
            }

            .level-1 {
                margin-left: 20px;
                color: #333;
            }

            .level-2 {
                margin-left: 40px;
                color: #666;
            }

            .level-3 {
                margin-left: 60px;
                color: #999;
            }
               .level-0 {
                     display: none;  
                }
            `;
            document.head.appendChild(style);
    
            const modalHtml = `
                <div id="${this.modalId}" class="${this.modalClass}">
                    <div class="${this.modalContentClass}">
                        <span class="${this.closeClass}">&times;</span> 
                        <div id="${this.jsonViewerId}" class="${this.rowContainerClass}"></div>
                    </div>
                </div>
            `;
            document.body.insertAdjacentHTML('beforeend', modalHtml);
    
            document.querySelector(`.${this.closeClass}`).onclick = () => {
                document.getElementById(this.modalId).style.display = 'none';
            };
    
            window.onclick = (event) => {
                if (event.target == document.getElementById(this.modalId)) {
                    document.getElementById(this.modalId).style.display = 'none';
                }
            };
        }
    }
    
    /**
     * Displays a JSON value within the modal.
     * @param {Object} value - The JSON object to be displayed in the modal.
     */
    viewJsonValueCustom(value) {
        this.setupJsonViewerModalCustom();
        const container = document.querySelector(`#${this.jsonViewerId}`);
        container.innerHTML = this.convertJsonToHtmlRowsCustom(value);
        document.getElementById(this.modalId).style.display = 'block';
    }
    
    /**
     * Converts a JSON object into HTML rows.
     * @param {Object} json - The JSON object to convert.
     * @return {string} The HTML string of rows.
     */
    formatStringCamel(input) {
        return input
            .replace(/_/g, ' ')  // Replace underscores with spaces
            .replace(/\b\w/g, char => char.toUpperCase());  // Capitalize the first letter of each word
    }

    convertJsonToHtmlRowsCustom(json, level = 0) {
        let html = '<div class="resource observation"><div class="header">Error Information</div><table class="table"><tbody>';
        
        // Use an arrow function to maintain the 'this' context
        const parseObject = (obj, level) => {
            const levelClass = `level-${level}`;
            for (const [key, value] of Object.entries(obj)) {
                let displayValue = value;

                // Check if the key is 'request_created_at' and value is a number
                if (key === 'date' && typeof value === 'number') {
                    // Convert Unix timestamp to a readable date
                    let milliseconds = value * 1000; // Convert seconds to milliseconds
                    let date = new Date(milliseconds);

                    // Format options
                    let options = {
                        timeZone: 'America/New_York',
                        year: 'numeric',
                        month: '2-digit',
                        day: '2-digit',
                        hour: '2-digit',
                        minute: '2-digit',
                        second: '2-digit',
                        hour12: false
                    };

                    // Create a formatter
                    let formatter = new Intl.DateTimeFormat('en-US', options);
                    displayValue = formatter.format(date);
                }

                // Use the formatStringCamel method
                let key_val = this.formatStringCamel(key);

                // Check if the value is an object      
                if (typeof value === 'object' && value !== null) {
                    html += `<tr class="${levelClass}"><th><b>${key_val}</b></th><td>`;
                    parseObject(value, level + 1);
                    html += `</td></tr>`;
                } else {
                    if(key == "sat_interaction_http_request_id"){
                        html += ``;      
                    }else{
                        html += `<tr class="${levelClass}"><th>${key_val}</th><td>${displayValue}</td></tr>`;
                    }
                }
            }
        };
    
        parseObject(json, level);
        if (Array.isArray(json) && json.length === 0) {
            html += '<div class="h-48 flex justify-center items-center text-base">No Data Found</div></tbody></table></div>';
        }else{
            html += '</tbody></table><div style="text-align: right; margin-top: 10px;"><a href="https://github.com/tech-by-design/polyglot-prime/issues" target="_blank" style="margin-right: 58px;"><button type="button" style="background-color: #2296dd; color: white; border: none; padding: 10px 20px; border-radius: 5px;">Send to Support </button></a></div></div>';
        }
        return html;
    }
    
    /**
     * Fetches JSON data from a specified URL and displays it within the modal.
     * @param {string} url - The URL to fetch JSON data from.
     */
    viewFetchedJsonValueCustom(url) {
        fetch(url)
            .then(response => response.json())
            .then(data => this.viewJsonValueCustom(data))
            .catch(error => console.error(`Error fetching data from ${url}`, error));
    }     

    formatStringCamel(str) {
        return str
            .replace(/_/g, ' ')  // Replace underscores with spaces
            .replace(/\b\w/g, char => char.toUpperCase());  // Capitalize the first letter of each word
    }
    
}

export default ModalAide;
