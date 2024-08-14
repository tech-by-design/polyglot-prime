export class Helpers {

    // Function to format date as DD-MM-YYYY
    formatDate(date) {
        let day = String(date.getDate()).padStart(2, '0');
        let month = String(date.getMonth() + 1).padStart(2, '0'); // Months are zero-based
        let year = date.getFullYear();

        return `${month}/${day}/${year}`;
    }

    // Function to get date range, e.g., last 7 days
    getDateRange(daysAgo = 7) {
        let today = new Date();
        let pastDate = new Date(today);
        pastDate.setDate(today.getDate() - daysAgo);

        return {
            todayFormatted: this.formatDate(today),
            pastDateFormatted: this.formatDate(pastDate),
        };
    }

    // Function to inject date range text into an element
    injectDateRangeText(elementId, templateText) {
        const { todayFormatted, pastDateFormatted } = this.getDateRange();
        const text = templateText.replace('{startDate}', pastDateFormatted).replace('{endDate}', todayFormatted);
        document.getElementById(elementId).innerHTML = text;
    }

    // Example of a validation function
    validateEmail(email) {
        const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
        return emailRegex.test(email);
    }

    // Example of a function to validate if a string is not empty
    validateNotEmpty(value) {
        return value && value.trim().length > 0;
    }

    // Add more utility and validation functions as needed
}

export default Helpers;
