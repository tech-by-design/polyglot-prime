export class AGGridAide {
    constructor(gridOptions, gridDivStyles = { height: "750px" }) {
        this.gridOptions = gridOptions;
        this.gridDivStyles = gridDivStyles;
        this.setupDefaultStyles('agGridDefaultStyles');
    }

    setupDefaultStyles(styleId) {
        if (!document.getElementById(styleId)) {
            const style = document.createElement('style');
            style.id = styleId;
            style.innerHTML = `
                .ag-theme-alpine .ag-root-wrapper {
                    border: none;
                }
            `;
            document.head.appendChild(style);
        }
    }

    init(gridDivId) {
        const gridDiv = document.querySelector(`#${gridDivId}`);
        agGrid.createGrid(gridDiv, this.gridOptions);
        Object.assign(gridDiv.style, this.gridDivStyles);
    }

    static modalCellRenderer(onClick, modalAide) {
        return function (params) {
            if (params.value) {
                const link = document.createElement('a');
                link.href = '#';
                link.innerText = params.value;
                link.addEventListener('click', function (e) {
                    e.preventDefault();
                    onClick(params.value, modalAide);
                });
                return link;
            } else {
                return null;
            }
        }
    }

    static dateTimeValueFormatter() {
        return function (params) {
            if (params.value) {
                let milliseconds = params.value * 1000;
                let date = new Date(milliseconds);
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
                let formatter = new Intl.DateTimeFormat('en-US', options);
                return formatter.format(date);
            }
            return '';
        }
    }
}

export class AGGridAideBuilder {
    constructor() {
        this.gridOptions = {
            defaultColDef: {
                flex: 1,
                minWidth: 100,
                resizable: true,
                sortable: true,
                filter: true,
                enablePivot: true
            },
            columnDefs: [],
            sideBar: true,
            autoSizeStrategy: { type: "fitCellContents" },
            rowModelType: 'serverSide',
            serverSideDatasource: null,
        };
        this.gridDivStyles = { height: "750px" };
    }

    withColumnDefs(columnDefs) {
        this.gridOptions.columnDefs = columnDefs;
        return this;
    }

    withDefaultColDef(defaultColDef) {
        this.gridOptions.defaultColDef = defaultColDef;
        return this;
    }

    withSideBar(sideBar) {
        this.gridOptions.sideBar = sideBar;
        return this;
    }

    withServerSideDatasource(dataSourceUrl, withSecondaryColumns = null) {
        this.gridOptions.serverSideDatasource = {
            getRows: async (params) => {
                const jsonRequest = JSON.stringify(params.request, null, 2);
                try {
                    const response = await fetch(dataSourceUrl, {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: jsonRequest
                    });
                    if (response.ok) {
                        const result = await response.json();
                        if (withSecondaryColumns) {
                            try {
                                if (params.request.pivotMode && params.request.pivotCols.length > 0) {
                                    let secondaryColDefs = withSecondaryColumns(result.data, params.request.valueCols);
                                    params.api.updateGridOptions({ columnDefs: secondaryColDefs })
                                } else {
                                    // why is this being done?
                                    // params.api.updateGridOptions({ columnDefs: [] })
                                }
                            } catch (error) {
                                console.error("Error in updateSecondaryColumns:", error);
                            }
                        }
                        params.success({
                            rowData: result.data,
                        });
                    } else {
                        console.error(`[EnterpriseDatasource] Error: ${response.statusText}`);
                        params.fail();
                    }
                } catch (error) {
                    console.error(`[EnterpriseDatasource] Error: ${error.message}`);
                    params.fail();
                }
            }
        };
        return this;
    }

    withModalAide(modalAide) {
        this.modalAide = modalAide;
        return this;
    }

    withGridDivStyles(styles) {
        this.gridDivStyles = { ...this.gridDivStyles, ...styles };
        return this;
    }

    build() {
        return new AGGridAide(this.gridOptions, this.gridDivStyles);
    }
}
