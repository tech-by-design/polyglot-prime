export class ModalAide {
    constructor(modalId) {
        this.modalId = modalId;
        this.modalContentClass = `${modalId}-content`;
        this.modalClass = `${modalId}`;
        this.closeClass = `${modalId}-close`;
        this.jsonViewerId = `${modalId}-json`;
    }

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

    viewJsonValue(value) {
        this.setupJsonViewerModal();
        document.querySelector(`#${this.jsonViewerId}`).data = value;
        document.getElementById(this.modalId).style.display = 'block';
    }

    viewFetchedJsonValue(url) {
        fetch(url)
            .then(response => response.json())
            .then(data => {
                this.viewJsonValue(data);
            })
            .catch(error => {
                console.error(`Error fetching data from ${url}`, error);
            });
    }
}

export default ModalAide;
