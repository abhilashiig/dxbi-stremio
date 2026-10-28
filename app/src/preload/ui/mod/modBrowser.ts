import { ipcRenderer, shell } from "electron";
import ModManager from "../../../core/ModManager";
import Helpers from "../../../utils/Helpers";
import { getModsTabTemplate } from "../../../components/marketplace-tab/marketplaceTab";
import { getModItemTemplate } from "../../../components/marketplace-item/marketplaceItem";
import { getBackButton } from "../../../components/back-btn/backBtn";
import { SELECTORS, CLASSES, IPC_CHANNELS } from "../../../constants";
import { modController } from "./modController";

export function setupBrowseModsButton(): void {
    Helpers.waitForElm('#browsePluginsThemesBtn').then(() => {
        const btn = document.getElementById("browsePluginsThemesBtn");
        btn?.addEventListener("click", browseMods);
    }).catch(() => {});
}

function setupSearchBar(): void {
    const searchInput = document.querySelector(SELECTORS.SEARCH_INPUT) as HTMLInputElement;
    const addonsContainer = document.querySelector(SELECTORS.ADDONS_LIST_CONTAINER);
    
    if (!searchInput || !addonsContainer) return;
    
    searchInput.addEventListener("input", () => {
        const filter = searchInput.value.trim().toLowerCase();
        const modItems = addonsContainer.querySelectorAll(SELECTORS.ADDON_CONTAINER);
        
        modItems.forEach((item) => {
            const name = item.querySelector(SELECTORS.NAME_CONTAINER)?.textContent?.toLowerCase() || "";
            const description = item.querySelector(SELECTORS.DESCRIPTION_ITEM)?.textContent?.toLowerCase() || "";
            const type = item.querySelector(SELECTORS.TYPES_CONTAINER)?.textContent?.toLowerCase() || "";
            
            const match = name.includes(filter) || description.includes(filter) || type.includes(filter);
            (item as HTMLElement).style.display = match ? "" : "none";
        });
    });
}

export async function browseMods(): Promise<void> {
    const settingsContent = document.querySelector(SELECTORS.SETTINGS_CONTENT);
    if (!settingsContent) return;
    
    settingsContent.innerHTML = getModsTabTemplate();
    const mods = await ModManager.fetchMods();
    const modsList = document.getElementById("mods-list");
    if (!modsList) return;
    
    for (const plugin of mods.plugins as any[]) {
        const installed = ModManager.isPluginInstalled(Helpers.getFileNameFromUrl(plugin.download));
        const template = await getModItemTemplate(plugin, "Plugin", installed);
        modsList.insertAdjacentHTML("beforeend", template);
    }

    for (const theme of mods.themes as any[]) {
        const installed = ModManager.isThemeInstalled(Helpers.getFileNameFromUrl(theme.download));
        const template = await getModItemTemplate(theme, "Theme", installed);
        modsList.insertAdjacentHTML("beforeend", template);
    }
    
    const actionBtns = document.querySelectorAll(".modActionBtn");
    actionBtns.forEach((btn) => {
        btn.addEventListener("click", async () => {
            const link = btn.getAttribute("data-link");
            const type = btn.getAttribute("data-type")?.toLowerCase() as "plugin" | "theme";
            if (!link || !type) return;

            if (btn.getAttribute("title") === "Install") {
                const unchecked = btn.getAttribute("data-unchecked") === "true";
                if (unchecked) {
                    const name = btn.closest(SELECTORS.ADDON_CONTAINER)?.querySelector(SELECTORS.NAME_CONTAINER)?.textContent || "This mod";
                    const choice = await ipcRenderer.invoke(
                        IPC_CHANNELS.SHOW_ALERT, "warning", "Install unchecked mod?",
                        `${name} isn't in dx.bi's mod list, so it hasn't been checked. Mods run code inside the app and could read your Stremio account.\n\nOnly install it if you trust the author. It's recommended to read the source code first.`,
                        ["Install anyway", "Cancel", "View Source Code"]
                    );
                    if (choice === 2) shell.openExternal(link);
                    if (choice !== 0) return;
                }
                try {
                    await ModManager.downloadMod(link, type, unchecked);
                } catch (err) {
                    ipcRenderer.invoke(IPC_CHANNELS.SHOW_ALERT, "error", "Install failed", (err as Error).message, ["OK"]);
                    return;
                }
                btn.classList.remove(CLASSES.INSTALL_BUTTON);
                btn.classList.add(CLASSES.UNINSTALL_BUTTON);
                btn.setAttribute("title", "Uninstall");
                if (btn.childNodes[1]) btn.childNodes[1].textContent = "Uninstall";
            } else {
                modController.removeModUI(Helpers.getFileNameFromUrl(link), type);
                btn.classList.remove(CLASSES.UNINSTALL_BUTTON);
                btn.classList.add(CLASSES.INSTALL_BUTTON);
                btn.setAttribute("title", "Install");
                if (btn.childNodes[1]) btn.childNodes[1].textContent = "Install";
            }
        });
    });
    
    setupSearchBar();
    
    const horizontalNavs = document.querySelectorAll(SELECTORS.HORIZONTAL_NAV);
    if (horizontalNavs[1]) {
        horizontalNavs[1].innerHTML = getBackButton();
        document.getElementById("back-btn")?.addEventListener("click", () => {
            location.hash = '#/';
            setTimeout(() => location.hash = '#/settings', 0);
        });
    }
}