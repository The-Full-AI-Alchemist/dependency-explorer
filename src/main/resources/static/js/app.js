(() => {
    const state = { projects: [], selected: null, direction: "dependencies", tree: null, searchTimer: null, toastTimer: null };
    const byId = (id) => document.getElementById(id);
    const projectList = byId("projectList");
    const searchInput = byId("searchInput");
    const searchResults = byId("searchResults");

    const iconFolder = '<svg viewBox="0 0 20 20" aria-hidden="true"><path d="M2.7 5.1h5.2l1.8 2h7.6v8.3H2.7V5.1Z"/><path d="M2.7 5.1h5.2l1.8 2"/></svg>';
    const iconChevron = '<svg viewBox="0 0 16 16" aria-hidden="true"><path d="m5 6 3 3 3-3"/></svg>';

    async function api(url, options = {}) {
        const response = await fetch(url, { headers: { "Content-Type": "application/json", ...(options.headers || {}) }, ...options });
        const data = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error(data.error || `Erreur ${response.status}`);
        return data;
    }

    function coordinate(item) {
        return [item.groupId, item.artifactId, item.packaging || item.type || "jar", item.classifier || "", item.version].join(":");
    }

    function renderProjects() {
        byId("projectCount").textContent = state.projects.length;
        byId("projectFooterCount").textContent = `${state.projects.length} projet${state.projects.length > 1 ? "s" : ""}`;
        byId("sidebarEmpty").hidden = state.projects.length > 0;
        projectList.innerHTML = "";
        for (const project of state.projects) {
            const key = coordinate(project);
            const button = document.createElement("button");
            button.type = "button";
            button.className = `project-item${state.selected?.key === key ? " active" : ""}`;
            button.innerHTML = `<span class="folder-symbol">${iconFolder}</span><span class="project-copy"><span class="project-name"></span><span class="project-path"></span></span><span class="project-badge">${project.directDependencyCount}</span>`;
            button.querySelector(".project-name").textContent = project.artifactId;
            button.querySelector(".project-path").textContent = project.pomPath.replace(/[/\\]pom\.xml$/i, "");
            button.title = `${project.groupId}:${project.artifactId}:${project.version}`;
            button.addEventListener("click", () => selectItem({ key, kind: "application", label: project.artifactId, ...project }));
            projectList.append(button);
        }
        byId("welcomePanel").hidden = state.projects.length > 0;
        if (!state.selected && state.projects.length) {
            const first = state.projects[0];
            selectItem({ key: coordinate(first), kind: "application", label: first.artifactId, ...first });
        } else if (state.selected) {
            renderProjectsSelection();
        }
    }

    function renderProjectsSelection() {
        [...projectList.children].forEach((button) => {
            button.classList.toggle("active", button.title === `${state.selected.groupId}:${state.selected.artifactId}:${state.selected.version}` && state.selected.kind === "application");
        });
    }

    async function loadProjects() {
        try {
            state.projects = await api("/api/projects");
            renderProjects();
        } catch (error) { showToast(error.message, true); }
    }

    async function selectItem(item) {
        state.selected = item;
        state.direction = "dependencies";
        byId("searchResults").hidden = true;
        renderProjectsSelection();
        await loadTree();
    }

    async function loadTree() {
        if (!state.selected) return;
        byId("welcomePanel").hidden = true;
        byId("detailView").hidden = true;
        byId("loadingState").hidden = false;
        try {
            const response = await api(`/api/tree?key=${encodeURIComponent(state.selected.key)}&direction=${state.direction}`);
            state.tree = response.tree;
            renderDetail();
        } catch (error) {
            showToast(error.message, true);
            byId("welcomePanel").hidden = state.projects.length > 0;
        } finally { byId("loadingState").hidden = true; }
    }

    function renderDetail() {
        const item = state.selected;
        byId("detailView").hidden = false;
        byId("breadcrumbTitle").textContent = item.label || item.artifactId;
        byId("selectedTitle").textContent = item.label || item.artifactId;
        byId("selectedKind").textContent = item.kind === "application" ? "PROJET MAVEN" : "DÉPENDANCE JAVA";
        byId("selectedCoordinate").textContent = `${item.groupId}:${item.artifactId}`;
        byId("selectedVersion").textContent = item.version || "—";
        byId("selectedIcon").classList.toggle("library", item.kind !== "application");
        byId("selectedIcon").querySelector("span").textContent = item.kind === "application" ? "⌘" : "▦";
        byId("viewDescription").textContent = state.direction === "dependencies" ? "Dépendances de cet élément" : "Éléments qui dépendent de celui-ci";
        document.querySelectorAll(".direction-button").forEach((button) => button.classList.toggle("active", button.dataset.direction === state.direction));
        const count = countNodes(state.tree);
        byId("treeCount").textContent = count;
        byId("treeCountLabel").textContent = count > 1 ? "éléments dans l’arbre" : "élément dans l’arbre";
        const root = byId("treeRoot");
        root.innerHTML = "";
        if (!state.tree.children?.length) {
            root.innerHTML = '<div class="tree-empty"><strong>Aucun élément à afficher</strong><p>Cette branche ne contient pas d’autres éléments indexés.</p></div>';
        } else {
            root.append(renderNode(state.tree, true));
        }
    }

    function countNodes(node) {
        return 1 + (node.children || []).reduce((sum, child) => sum + countNodes(child), 0);
    }

    function renderNode(node, isRoot = false) {
        const wrapper = document.createElement("div");
        wrapper.className = "tree-node";
        const row = document.createElement("div");
        const nodeKey = coordinate(node);
        row.className = `tree-row${nodeKey === state.selected.key ? " selected" : ""}`;
        const branch = document.createElement("span");
        branch.className = "tree-branch";
        const toggle = document.createElement("button");
        const hasChildren = Boolean(node.children?.length);
        toggle.className = `tree-toggle${hasChildren ? "" : " empty"}`;
        toggle.type = "button";
        toggle.innerHTML = hasChildren ? '<svg viewBox="0 0 12 12"><path d="m4 2 4 4-4 4"/></svg>' : "";
        toggle.setAttribute("aria-label", hasChildren ? "Réduire ou développer" : "Aucun enfant");
        branch.append(toggle);
        const glyph = document.createElement("span");
        glyph.className = `tree-file-icon${nodeKey === state.selected.key ? "" : " library"}`;
        glyph.textContent = nodeKey === state.selected.key ? "⌘" : "▦";
        const name = document.createElement("button");
        name.type = "button";
        name.className = "tree-name";
        name.textContent = node.artifactId || node.groupId;
        name.title = `${node.groupId}:${node.artifactId}:${node.version}`;
        const version = document.createElement("span");
        version.className = "tree-version";
        version.textContent = node.version || "";
        row.append(branch, glyph, name, version);
        if (node.scope) {
            const scope = document.createElement("span");
            scope.className = `scope-badge ${node.scope}`;
            scope.textContent = node.scope;
            row.append(scope);
        }
        wrapper.append(row);
        if (hasChildren) {
            const childList = document.createElement("div");
            childList.className = "tree-children";
            node.children.forEach((child) => childList.append(renderNode(child)));
            wrapper.append(childList);
            toggle.addEventListener("click", (event) => {
                event.stopPropagation();
                childList.classList.toggle("collapsed");
                toggle.innerHTML = childList.classList.contains("collapsed")
                    ? '<svg viewBox="0 0 12 12"><path d="m4 2 4 4-4 4"/></svg>'
                    : '<svg viewBox="0 0 12 12"><path d="m2 4 4 4 4-4"/></svg>';
            });
        }
        name.addEventListener("click", () => {
            if (nodeKey !== state.selected.key) {
                selectItem({ key: nodeKey, kind: "dependency", label: node.artifactId, groupId: node.groupId, artifactId: node.artifactId, type: node.type, classifier: node.classifier, version: node.version });
            }
        });
        return wrapper;
    }

    async function runSearch(query) {
        if (query.trim().length < 2) { searchResults.hidden = true; return; }
        try {
            const results = await api(`/api/search?q=${encodeURIComponent(query)}`);
            searchResults.innerHTML = "";
            if (!results.length) {
                searchResults.innerHTML = '<div class="no-results">Aucun projet ou composant trouvé.</div>';
            } else {
                const heading = document.createElement("div");
                heading.className = "search-section-title";
                heading.textContent = `${results.length} résultat${results.length > 1 ? "s" : ""}`;
                searchResults.append(heading);
                results.forEach((item) => {
                    const button = document.createElement("button");
                    button.type = "button";
                    button.className = "search-option";
                    button.innerHTML = `<span class="search-result-icon${item.kind === "dependency" ? " library" : ""}">${item.kind === "application" ? "⌘" : "▦"}</span><span class="search-result-copy"><span class="search-result-name"></span><span class="search-result-detail"></span></span><span class="result-kind">${item.kind === "application" ? "Projet" : "Librairie"}</span>`;
                    button.querySelector(".search-result-name").textContent = item.label;
                    button.querySelector(".search-result-detail").textContent = `${item.groupId}:${item.artifactId}:${item.version}`;
                    button.addEventListener("click", () => { searchInput.value = ""; selectItem(item); });
                    searchResults.append(button);
                });
            }
            searchResults.hidden = false;
        } catch (error) { showToast(error.message, true); }
    }

    function showToast(message, isError = false) {
        const toast = byId("toast");
        toast.textContent = message;
        toast.classList.toggle("error", isError);
        toast.classList.add("visible");
        clearTimeout(state.toastTimer);
        state.toastTimer = setTimeout(() => toast.classList.remove("visible"), 3800);
    }

    function openModal() {
        byId("scanError").hidden = true;
        byId("scanModal").hidden = false;
        byId("folderPath").focus();
    }
    function closeModal() { byId("scanModal").hidden = true; }

    document.querySelectorAll("#openScan, #welcomeScan, #emptyScan").forEach((button) => button.addEventListener("click", openModal));
    byId("closeScan").addEventListener("click", closeModal);
    byId("cancelScan").addEventListener("click", closeModal);
    byId("scanModal").addEventListener("click", (event) => { if (event.target === byId("scanModal")) closeModal(); });
    byId("refreshProjects").addEventListener("click", loadProjects);
    byId("scanForm").addEventListener("submit", async (event) => {
        event.preventDefault();
        byId("scanError").hidden = true;
        byId("scanProgress").hidden = false;
        byId("startScan").disabled = true;
        try {
            const result = await api("/api/scan", { method: "POST", body: JSON.stringify({ folderPath: byId("folderPath").value }) });
            await loadProjects();
            closeModal();
            if (result.failed) showToast(`${result.scanned} projet(s) indexé(s), ${result.failed} erreur(s).`, true);
            else if (result.scanned) showToast(`${result.scanned} projet(s) indexé(s) avec succès.`);
            else showToast("Aucun pom.xml trouvé dans ce dossier.");
        } catch (error) {
            byId("scanError").textContent = error.message;
            byId("scanError").hidden = false;
        } finally {
            byId("scanProgress").hidden = true;
            byId("startScan").disabled = false;
        }
    });
    byId("searchInput").addEventListener("input", () => {
        clearTimeout(state.searchTimer);
        state.searchTimer = setTimeout(() => runSearch(searchInput.value), 220);
    });
    byId("searchInput").addEventListener("keydown", (event) => {
        if (event.key === "Escape") searchResults.hidden = true;
        if (event.key === "Enter" && !searchResults.hidden) searchResults.querySelector(".search-option")?.click();
    });
    document.addEventListener("click", (event) => {
        if (!byId("searchWrap").contains(event.target)) searchResults.hidden = true;
    });
    document.addEventListener("keydown", (event) => {
        if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === "k") { event.preventDefault(); searchInput.focus(); }
    });
    document.querySelectorAll(".direction-button").forEach((button) => button.addEventListener("click", () => {
        if (state.direction !== button.dataset.direction) { state.direction = button.dataset.direction; loadTree(); }
    }));
    byId("expandAll").addEventListener("click", () => {
        document.querySelectorAll(".tree-children").forEach((children) => children.classList.remove("collapsed"));
        document.querySelectorAll(".tree-toggle:not(.empty)").forEach((toggle) => { toggle.innerHTML = '<svg viewBox="0 0 12 12"><path d="m2 4 4 4 4-4"/></svg>'; });
    });
    byId("copyCoordinate").addEventListener("click", async () => {
        try { await navigator.clipboard.writeText(state.selected.key); showToast("Coordonnées copiées."); }
        catch { showToast("Impossible de copier les coordonnées.", true); }
    });

    loadProjects();
})();
