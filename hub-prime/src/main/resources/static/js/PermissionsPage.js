class PermissionsPage {
    constructor() {
        this.allRoles = [];
        this.init();
    }

    init() {
        this.bindEvents();

        if (document.readyState === "loading") {
            document.addEventListener("DOMContentLoaded", () => this.loadRoles());
        } else {
            this.loadRoles();
        }
    }

    bindEvents() {
        document.addEventListener('change', e => {
            if (e.target.classList.contains('screen-checkbox')) {
                this.handleScreenCheckboxChange(e.target);
            }
        });
    }

    handleScreenCheckboxChange(checkbox) {
        this.applySelectionState(checkbox, checkbox.checked);
        this.updateSelectedCount();
    }

    applySelectionState(checkbox, checked) {
        checkbox.checked = checked;

        const parentItem = checkbox.closest('.screen-tree-item');
        if (!parentItem) return;

        const descendants = Array.from(parentItem.querySelectorAll('.screen-checkbox'));
        descendants.forEach(item => {
            if (item !== checkbox) {
                item.checked = checked;
            }
        });

        this.updateParentState(checkbox);
    }

    updateParentState(checkbox) {
        let currentCheckbox = this.findParentCheckbox(checkbox);

        while (currentCheckbox) {
            const parentItem = currentCheckbox.closest('.screen-tree-item');
            if (!parentItem) break;

            const childCheckboxes = Array.from(parentItem.querySelectorAll('.screen-checkbox'))
                .filter(item => item !== currentCheckbox);
            currentCheckbox.checked = childCheckboxes.some(item => item.checked);
            currentCheckbox = this.findParentCheckbox(currentCheckbox);
        }
    }

    findParentCheckbox(checkbox) {
        const parentScreenId = checkbox.dataset.parentScreenId;
        if (!parentScreenId) return null;

        return document.querySelector(`.screen-checkbox[data-screen-id="${parentScreenId}"]`);
    }

    updateRoleDescription() {
        const select = document.getElementById("roleSelect");
        const desc = document.getElementById("roleDescription");

        if (!select || !desc) return;

        const selectedRoleId = select.value;
        const roleObj = this.allRoles.find(r => String(r.role_id) === String(selectedRoleId));
        const text = roleObj && roleObj.role_description ? roleObj.role_description : "";

        if (text) {
            desc.style.display = "block";
            desc.innerText = text;
        } else {
            desc.style.display = "none";
            desc.innerText = "";
        }
    }

    async loadRoles() {
        try {
            const response = await fetch("/api/getRoles", {
                method: "GET",
                headers: { "Content-Type": "application/json" }
            });

            if (!response.ok) throw new Error("Failed to fetch roles");

            this.allRoles = await response.json();

            const select = document.getElementById("roleSelect");
            if (!select) return;

            select.innerHTML = "";

            this.allRoles.forEach(role => {
                const opt = document.createElement("option");
                opt.value = role.role_id;
                opt.textContent = role.role_name;
                select.appendChild(opt);
            });

            if (this.allRoles.length > 0) {
                select.value = this.allRoles[0].role_id;
                await this.loadPermissions(this.allRoles[0].role_id);
            }
        } catch (error) {
            console.error("Error loading roles:", error);
            this.showToast("Failed to load roles", true);
        }
    }

    async onRoleChange() {
        const select = document.getElementById("roleSelect");
        if (!select) return;
        await this.loadPermissions(select.value);
    }

    async loadPermissions(roleId) {
        try {
            const response = await fetch(`/api/permissions/${roleId}`, {
                method: "GET",
                headers: { "Content-Type": "application/json" }
            });

            if (!response.ok) throw new Error("Failed to fetch permissions");

            const data = await response.json();
            this.renderMenus(data);
            this.updateRoleDescription();
        } catch (error) {
            console.error("Error fetching permissions:", error);
            this.showToast("Failed to load permissions", true);
        }
    }

    groupMenus(data) {
        const menuMap = {};
        const screensById = new Map();

        data.menu_screens.forEach(item => {
            const screen = {
                scr_id: item.scr_id,
                scr_name: item.scr_name,
                scr_code: item.scr_code,
                parent_scr_id: item.parent_scr_id,
                mnu_id: item.mnu_id,
                has_permission: item.has_permission,
                children: []
            };
            screensById.set(screen.scr_id, screen);
        });

        data.menu_screens.forEach(item => {
            const screen = screensById.get(item.scr_id);
            if (!screen) return;

            if (!menuMap[item.mnu_id]) {
                menuMap[item.mnu_id] = {
                    mnu_id: item.mnu_id,
                    mnu_name: item.mnu_name,
                    mnu_code: item.mnu_code,
                    screens: []
                };
            }

            const parentScrId = item.parent_scr_id;
            if (parentScrId && screensById.has(parentScrId)) {
                const parentScreen = screensById.get(parentScrId);
                if (parentScreen) {
                    parentScreen.children.push(screen);
                }
            } else {
                menuMap[item.mnu_id].screens.push(screen);
            }
        });

        return Object.values(menuMap);
    }

    renderScreenTree(screens, depth = 0, forceChecked = false) {
        return screens.map(screen => {
            const childrenHtml = screen.children && screen.children.length
                ? this.renderScreenTree(screen.children, depth + 1)
                : "";
            const indentStyle = depth > 0 ? `style="margin-left: ${depth * 16}px;"` : "";

            return `
                <div class="screen-tree-item">
                    <div class="screen-item" ${indentStyle}>
                        <input
                            type="checkbox"
                            class="screen-checkbox"
                            data-screen-id="${screen.scr_id}"
                            data-menu-id="${screen.mnu_id}"
                            data-parent-screen-id="${screen.parent_scr_id || ''}"
                            ${screen.has_permission ? 'checked' : ''} 
                            >
                        ${screen.scr_name}
                    </div>
                    ${childrenHtml}
                </div>
            `;
        }).join('');
    }

    renderMenus(data) {
        const menus = this.groupMenus(data);
        const container = document.getElementById("menuContainer");
        if (!container) return; 

        container.innerHTML = "";

        menus.forEach(menu => {
            const isDashboardMenu = menu.mnu_code === "mnu_dashboard";
            const totalScreens = menu.screens.reduce((count, screen) => count + 1 + (screen.children ? screen.children.length : 0), 0);
            const selectedScreens = menu.screens.reduce((count, screen) => {
                const screenCount = (screen.has_permission ? 1 : 0) + (screen.children ? screen.children.filter(child => child.has_permission).length : 0);
                return count + screenCount;
            }, 0); 
            const screensHtml = this.renderScreenTree(menu.screens, 0, isDashboardMenu);

            container.insertAdjacentHTML('beforeend', `
                <div class="menu-card">
                    <div class="menu-header" onclick="toggleMenu(this, event)">
                        <div class="menu-left">
                            <div class="toggle-area">
                                <input type="checkbox"
                               onchange="toggleGroup(this)" >
                            </div>
                            <span class="menu-name">
                                ${menu.mnu_name}
                            </span>
                            <span class="menu-count">
                                ${selectedScreens} / ${totalScreens} Selected
                            </span>
                        </div>
                        <div class="expand-icon">
                            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                                <polyline points="9 18 15 12 9 6"></polyline>
                            </svg>
                        </div>
                    </div>
                    <div class="menu-body">
                        ${screensHtml}
                    </div>
                </div>
            `);
        });

        this.updateSelectedCount();
    }

    async savePermissions() {
        const select = document.getElementById("roleSelect");
        const roleId = select ? select.value : null;
        if (!roleId) return;

        const checkboxes = document.querySelectorAll(".screen-checkbox");
        const data = Array.from(checkboxes).map(cb => ({
            scr_id: parseInt(cb.dataset.screenId),
            mnu_id: parseInt(cb.dataset.menuId),
            has_permission: cb.checked
        }));

        const hasChecked = data.some(item => item.has_permission);
        if (!hasChecked) {
            this.showToast("At least one screen must be selected", true);
            return;
        }

        try {
            const response = await fetch(`/api/permissions/${roleId}`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(data)
            });

            const resData = await response.json().catch(() => ({}));
            if (!response.ok || resData.status === "error") {
                throw new Error(resData.message || "Failed to save permissions");
            }
            this.showToast("Permissions saved successfully!");
        } catch (error) {
            console.error("Error saving permissions:", error);
            this.showToast(error.message || "Failed to save permissions", true);
        }
    }

    showToast(message, isError = false) {
        const oldToast = document.getElementById("toast-notification");
        if (oldToast) oldToast.remove();

        const toast = document.createElement("div");
        toast.id = "toast-notification";
        toast.className = `fixed bottom-5 right-5 px-6 py-3 rounded-lg shadow-lg text-white font-medium transition-all duration-300 transform translate-y-10 opacity-0 z-50 ${isError ? 'bg-red-600' : 'bg-green-600'}`;
        toast.innerText = message;
        document.body.appendChild(toast);

        setTimeout(() => {
            toast.classList.remove("translate-y-10", "opacity-0");
        }, 10);

        setTimeout(() => {
            toast.classList.add("translate-y-10", "opacity-0");
            setTimeout(() => toast.remove(), 300);
        }, 3000);
    }

    toggleMenu(header, event) {
        if (event.target.type === 'checkbox' || event.target.tagName === 'LABEL') {
            return;
        }

        const card = header.closest('.menu-card');
        const body = header.nextElementSibling;

        if (!card || !body) return;

        if (body.style.display === 'block') {
            body.style.display = 'none';
            card.classList.remove('active');
        } else {
            body.style.display = 'block';
            card.classList.add('active');
        }
    }

    toggleGroup(checkbox) {
        if (window.event) {
            window.event.stopPropagation();
        }

        const header = checkbox.closest('.menu-header');
        const card = checkbox.closest('.menu-card');
        const body = header ? header.nextElementSibling : null;

        if (!card || !body) return;

        body.style.display = 'block';
        card.classList.add('active');

        const children = body.querySelectorAll('.screen-checkbox');
        children.forEach(c => {
            this.applySelectionState(c, checkbox.checked);
        });

        this.updateSelectedCount();
    }

    selectAllScreens() {
        document.querySelectorAll('.screen-checkbox').forEach(c => {
            this.applySelectionState(c, true);
        });
        this.updateSelectedCount();
    }

    clearAllScreens() {
        document.querySelectorAll('.screen-checkbox').forEach(c => {
            if (!c.disabled) {
                this.applySelectionState(c, false);
            }
        });
        this.updateSelectedCount();
    }

    updateSelectedCount() {
        document.querySelectorAll('.menu-card').forEach(menu => {
            const total = menu.querySelectorAll('.screen-checkbox').length;
            const selected = menu.querySelectorAll('.screen-checkbox:checked').length;

            const countElement = menu.querySelector('.menu-count');
            if (countElement) {
                countElement.innerText = `${selected} / ${total} Selected`;
            }

            const parentCheckbox = menu.querySelector('.toggle-area input[type="checkbox"]');
            if (parentCheckbox) {
                parentCheckbox.checked = selected === total && total > 0;
                parentCheckbox.indeterminate = selected > 0 && selected < total;
            }
        });
    }
}

const permissionsPage = new PermissionsPage();
window.permissionsPage = permissionsPage;
window.toggleMenu = (header, event) => permissionsPage.toggleMenu(header, event);
window.toggleGroup = (checkbox) => permissionsPage.toggleGroup(checkbox);
window.selectAllScreens = () => permissionsPage.selectAllScreens();
window.clearAllScreens = () => permissionsPage.clearAllScreens();
window.savePermissions = () => permissionsPage.savePermissions();
window.onRoleChange = () => permissionsPage.onRoleChange();