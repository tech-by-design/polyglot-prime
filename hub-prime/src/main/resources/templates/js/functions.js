export function deleteRow(schemaName, viewName, primaryKeyColumn, primaryKey) {
    return new Promise((resolve, reject) => {
        if (confirm(`Are you sure you want to delete this row?`)) {
            const url = schemaName ?
                `/api/ux/tabular/jooq/delete/${schemaName}/${viewName}/${primaryKeyColumn}/${primaryKey}` :
                `/api/ux/tabular/jooq/delete/${viewName}/${primaryKeyColumn}/${primaryKey}`;

            fetch(url, {
                method: 'DELETE',
                headers: {
                    'Content-Type': 'application/json'
                }
            })
                .then(response => {
                    if (!response.ok) {
                        return response.json().then(errData => {
                            throw new Error(errData.message || "Failed to delete row.");
                        });
                    }
                    return response.json();
                })
                .then(data => {
                    resolve(data); // Resolve the Promise with the server response
                })
                .catch(error => {
                    console.error('Error deleting row:', error);
                    reject(error); // Reject the Promise with the error
                });
        } else {
            reject(new Error("Deletion cancelled by user.")); // Reject if user cancels
        }
    });
}

export function editRow(schemaName, viewName, primaryKeyColumn, primaryKey, columnDefs) {
    return new Promise((resolve, reject) => {
        const url = schemaName ?
            `/api/ux/tabular/jooq/${schemaName}/${viewName}/${primaryKeyColumn}/${primaryKey}.json` :
            `/api/ux/tabular/jooq/${viewName}/${primaryKeyColumn}/${primaryKey}.json`;

        fetch(url)
            .then(response => {
                if (!response.ok) { // Check if the response is ok
                    return response.text().then(text => { // Get the error text
                        throw new Error(text || "Failed to fetch row data"); // Throw a more informative error
                    });
                }
                return response.json();
            })
            .then(data => {
                if (data.length > 0) {
                    resolve(data[0]); // Resolve the promise with the fetched data
                } else {
                    reject(new Error("No data found for the primary key")); // Reject if no data found
                }
            })
            .catch(error => {
                console.error('Error fetching row data:', error);
                reject(error); // Reject the promise with the error
            });
    });
}