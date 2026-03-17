class PermissionsPage {
    constructor(roleSelectId, containerId) {
        this.roleSelect = document.getElementById(roleSelectId);
        this.container = document.getElementById(containerId);
        this.selectAllBtn = document.getElementById("select-all");
        this.deselectAllBtn = document.getElementById("deselect-all");
        this.saveBtn = document.getElementById("save-permissions");

        this.initEvents();
        this.loadRoles(); // fetch roles and load permissions for the first role
    }

    initEvents() {
        this.roleSelect.addEventListener("change", () => {
            this.loadPermissions(this.roleSelect.value);
        });

        this.selectAllBtn.addEventListener("click", () => this.toggleAll(true));
        this.deselectAllBtn.addEventListener("click", () => this.toggleAll(false));
        this.saveBtn.addEventListener("click", () => this.savePermissions());
    }

    async loadRoles() {
        try {
           const response = await fetch("/api/getRoles", {
                method: "GET",
                headers: { "Content-Type": "application/json" }
            });

            if (!response.ok) throw new Error("Failed to fetch roles");

            const roles = await response.json(); // array of {role_id, role_code, role_name}

            // Clear existing options
            this.roleSelect.innerHTML = "";

            // Add a default option
            const defaultOpt = document.createElement("option");
            defaultOpt.textContent = "Select a role";
            defaultOpt.disabled = true;
            defaultOpt.selected = true;
            this.roleSelect.appendChild(defaultOpt);

            // Populate roles
            roles.forEach(role => {
                const opt = document.createElement("option");
                opt.value = role.role_id;
                opt.textContent = role.role_name;
                this.roleSelect.appendChild(opt);
            });

            // Auto-load permissions for the first role
            if (roles.length > 0) {
                this.roleSelect.value = roles[0].role_id;
                this.loadPermissions(roles[0].role_id);
            }

        } catch (error) {
            console.error("Error loading roles:", error);
            this.roleSelect.innerHTML = `<option disabled>Error loading roles</option>`;
        }
    }

    async loadPermissions(roleId) {
        try {
            const response = await fetch(`/api/permissions/${roleId}`, {
                method: "GET",
                headers: { "Content-Type": "application/json" }
            });

            if (!response.ok) throw new Error("Network response was not ok");

            const data = await response.json();
            this.renderPermissions(data.menu_screens);
        } catch (error) {
            console.error("Error fetching permissions:", error);
            this.container.innerHTML = `<p class="text-center text-red-500 py-10">Failed to load permissions.</p>`;
        }
    }

renderPermissions(menuScreens) {
    this.container.innerHTML = "";

    const grouped = {};
    menuScreens.forEach(item => {
        if (!grouped[item.mnu_name]) grouped[item.mnu_name] = [];
        grouped[item.mnu_name].push(item);
    });

    for (const [menuName, screens] of Object.entries(grouped)) {
        const section = document.createElement("div");
        section.className = "mb-4";

        // ---- Main menu with checkbox ----
        const headerLabel = document.createElement("label");
        headerLabel.className = "flex items-center space-x-3 mb-2 font-semibold";

        const headerCheckbox = document.createElement("input");
        headerCheckbox.type = "checkbox";
        headerCheckbox.className = "menu-checkbox";

        const headerText = document.createElement("span");
        headerText.textContent = menuName;

        headerLabel.appendChild(headerCheckbox);
        headerLabel.appendChild(headerText);
        section.appendChild(headerLabel);

        // ---- Submenus ----
        const subCheckboxes = [];
        screens.forEach(screen => {
            const label = document.createElement("label");
            label.className = "flex items-center space-x-3 mb-1 ml-6";

            const checkbox = document.createElement("input");
            checkbox.type = "checkbox";
            checkbox.checked = (screen.has_permission === true || screen.has_permission === "true");
            checkbox.dataset.scrId = screen.scr_id;
            checkbox.dataset.mnuId = screen.mnu_id;
            checkbox.className = "submenu-checkbox";

            const text = document.createElement("span");
            text.textContent = screen.scr_name;

            label.appendChild(checkbox);
            label.appendChild(text);
            section.appendChild(label);

            subCheckboxes.push(checkbox);
        });

        // ---- Sync main menu with submenus ----
        // 1. Clicking main menu affects all submenus
        headerCheckbox.addEventListener("change", () => {
            subCheckboxes.forEach(cb => cb.checked = headerCheckbox.checked);
        });

        // 2. Clicking submenu may update main menu
        subCheckboxes.forEach(cb => {
            cb.addEventListener("change", () => {
                const anyChecked = subCheckboxes.some(sub => sub.checked);
                if (!anyChecked) {
                    headerCheckbox.checked = false; // uncheck only if none are selected
                } else {
                    headerCheckbox.checked = true; // stays checked even if one submenu is checked
                }
            });
        });

        // 3. Initialize header state when rendering
        headerCheckbox.checked = subCheckboxes.some(cb => cb.checked);

        this.container.appendChild(section);
    }
}

    toggleAll(state) {
        const checkboxes = this.container.querySelectorAll("input[type='checkbox']");
        checkboxes.forEach(cb => cb.checked = state);
    }

    async savePermissions() {
        const roleId = this.roleSelect.value;
        const checkboxes = this.container.querySelectorAll("input[type='checkbox']");

        const data = Array.from(checkboxes)
            .filter(cb => cb.dataset.scrId && cb.dataset.mnuId)
            .map(cb => ({
            scr_id: cb.dataset.scrId,
            mnu_id: cb.dataset.mnuId,
            has_permission: cb.checked
        }));

        try {
            const response = await fetch(`/api/permissions/${roleId}`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(data)
            });

            if (!response.ok) throw new Error("Failed to save permissions");

          this.showPopup("Permissions saved successfully!");  
      } catch (error) {
            console.error("Error saving permissions:", error);
           this.showPopup("Failed to save permissions", true);
        }
    }

    showPopup(message, isError = false) {
    // Remove existing popup if any
    const oldPopup = document.getElementById("popup-modal");
    if (oldPopup) oldPopup.remove();

    // Create overlay
    const overlay = document.createElement("div");
    overlay.id = "popup-modal";
    overlay.className = "fixed inset-0 flex items-center justify-center bg-black bg-opacity-50 z-50";

    // Create popup box
    const box = document.createElement("div");
    box.className = "bg-white rounded-lg shadow-lg p-6 text-center w-80";

    const msg = document.createElement("p");
    msg.textContent = message;
    msg.className = isError ? "text-red-600 mb-4 font-medium" : "text-green-600 mb-4 font-medium";

    const btn = document.createElement("button");
    btn.textContent = "OK";
    btn.className = "px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700";
    btn.addEventListener("click", () => overlay.remove());

    box.appendChild(msg);
    box.appendChild(btn);
    overlay.appendChild(box);
    document.body.appendChild(overlay);
}

}

document.addEventListener("DOMContentLoaded", () => {
    new PermissionsPage("role-select", "permissions-container");
});
