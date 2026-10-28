import { readFileSync, writeFileSync, readdirSync, statSync, existsSync, mkdirSync, unlinkSync } from "fs";
import { createHash } from "crypto";
import { shell } from "electron";
import { basename, join } from "path";
import Properties from "./Properties";
import { MetaData } from "../interfaces/MetaData";
import { getLogger } from "../utils/logger";
import { FILE_EXTENSIONS, URLS } from "../constants";
import ExtractMetaData from "../utils/ExtractMetaData";
import RegistryMetaData from "../interfaces/RegistryMetaData";
import Helpers from "../utils/Helpers";

class ModManager {
    private static logger = getLogger("ModManager");

    public static getPluginContent(pluginName: string): string | null {
        const pluginPath = join(Properties.pluginsPath, pluginName);
        if (!existsSync(pluginPath)) return null;
        return readFileSync(pluginPath, "utf-8");
    }

    public static getInstalledThemes(): string[] {
        const dirPath = Properties.themesPath;
        if (!existsSync(dirPath)) return [];
        return readdirSync(dirPath).filter(f => statSync(join(dirPath, f)).isFile());
    }

    public static getInstalledPlugins(): string[] {
        const dirPath = Properties.pluginsPath;
        if (!existsSync(dirPath)) return [];
        return readdirSync(dirPath).filter(f => statSync(join(dirPath, f)).isFile());
    }

    public static isThemeInstalled(fileName: string): boolean {
        return this.getInstalledThemes().includes(fileName);
    }

    public static isPluginInstalled(fileName: string): boolean {
        return this.getInstalledPlugins().includes(fileName);
    }

    public static openFolder(folderPath: string): void {
        shell.openPath(folderPath).then(error => {
            if (error) this.logger.error(`Failed to open folder ${folderPath}: ${error}`);
        });
    }

    private static async fetchList(url: string): Promise<{ plugins: RegistryMetaData[]; themes: RegistryMetaData[] }> {
        const response = await fetch(url);
        if (!response.ok) throw new Error(`Failed to load mod list: ${response.status}`);
        return response.json();
    }

    // dx.bi's checked list first, then community mods it doesn't have, marked unchecked.
    // Their sha256 is dropped so nothing from the community list can pass as checked.
    public static async fetchMods(): Promise<{ plugins: RegistryMetaData[]; themes: RegistryMetaData[] }> {
        const [ours, community] = await Promise.all([
            this.fetchList(URLS.REGISTRY),
            this.fetchList(URLS.COMMUNITY_REGISTRY).catch(() => ({ plugins: [], themes: [] })),
        ]);
        const key = (e: RegistryMetaData) => `${e.name}|${e.author}`.toLowerCase();
        const known = new Set([...ours.plugins, ...ours.themes].map(key));
        const unchecked = (list: RegistryMetaData[] = []) =>
            list.filter(e => e?.name && e.download && !known.has(key(e))).map(e => ({ ...e, sha256: "", unchecked: true }));
        return {
            plugins: [...ours.plugins, ...unchecked(community.plugins)],
            themes: [...ours.themes, ...unchecked(community.themes)],
        };
    }

    private static async registryEntries(): Promise<RegistryMetaData[]> {
        const { plugins, themes } = await this.fetchList(URLS.REGISTRY);
        return [...plugins, ...themes];
    }

    private static sha256(data: Buffer | string): string {
        return createHash("sha256").update(data).digest("hex");
    }

    // allowUnchecked: the user agreed to install a mod that isn't in dx.bi's list. A listed mod is always hash-checked.
    public static async downloadMod(modLink: string, type: "plugin" | "theme", allowUnchecked = false): Promise<string> {
        this.logger.info(`Downloading ${type} from: ${modLink}`);
        const entry = (await this.registryEntries()).find(e => e.download === modLink);
        if (!entry?.sha256 && !allowUnchecked) throw new Error("It isn't in dx.bi's mod list.");

        const response = await fetch(modLink);
        if (!response.ok) throw new Error(`Failed to download: ${response.status}`);

        const buffer = Buffer.from(await response.arrayBuffer());
        if (entry?.sha256 && this.sha256(buffer) !== entry.sha256) throw new Error("The download doesn't match the copy in dx.bi's mod list, so it wasn't installed.");

        const saveDir = type === "plugin" ? Properties.pluginsPath : Properties.themesPath;
        if (!existsSync(saveDir)) mkdirSync(saveDir, { recursive: true });

        const filename = basename(new URL(modLink).pathname) || `${type}-${Date.now()}`;
        const filePath = join(saveDir, filename);

        writeFileSync(filePath, buffer);

        this.logger.info(`Downloaded ${type} saved to: ${filePath}`);
        return filePath;
    }

    public static deleteModFile(fileName: string, type: "plugin" | "theme"): void {
        const targetPath = join(type === "plugin" ? Properties.pluginsPath : Properties.themesPath, fileName);
        if (existsSync(targetPath)) {
            unlinkSync(targetPath);
            this.logger.info(`Deleted ${type} file: ${fileName}`);
        }
    }

    public static saveModFile(fileName: string, type: "plugin" | "theme", content: string): void {
        const targetPath = join(type === "plugin" ? Properties.pluginsPath : Properties.themesPath, fileName);
        writeFileSync(targetPath, content, 'utf-8');
    }

    public static async checkUpdateData(itemFile: string): Promise<{
        hasUpdate: boolean;
        newContent?: string;
        newMetaData?: MetaData;
        installedMetaData?: MetaData;
        registryVerified?: boolean;
        updateUrl?: string;
    } | null> {
        const type = itemFile.endsWith(FILE_EXTENSIONS.THEME) ? "theme" : "plugin";
        const itemPath = join(type === "theme" ? Properties.themesPath : Properties.pluginsPath, itemFile);
        
        const installedMetaData = ExtractMetaData.extractMetadataFromFile(itemPath) as MetaData | null;
        if (!installedMetaData?.updateUrl || installedMetaData.updateUrl === "none") return null;

        try {
            const request = await fetch(installedMetaData.updateUrl);
            if (request.status !== 200) return null;

            const responseText = await request.text();
            const extractedMetaData = ExtractMetaData.extractMetadataFromText(responseText) as MetaData | null;
            
            if (!extractedMetaData) {
                this.logger.warn(`Failed to check for updates for the ${type} ${installedMetaData.name}. The provided updateUrl leads to content with invalid metadata.`);
                return null;
            }

            if (Helpers.isNewerVersion(extractedMetaData.version, installedMetaData.version)) {
                this.logger.info(`New update found for plugin ${installedMetaData.name} (v${installedMetaData.version} -> v${extractedMetaData.version})`)

                const hash = this.sha256(responseText);
                const registryVerified = (await this.registryEntries().catch(() => [])).some(e => e.sha256 === hash);

                return {
                    hasUpdate: true,
                    newContent: responseText,
                    newMetaData: extractedMetaData,
                    installedMetaData,
                    registryVerified,
                    updateUrl: installedMetaData.updateUrl
                };
            }
            return { hasUpdate: false, installedMetaData };
        } catch (error) {
            this.logger.error(`Error checking updates for ${itemFile}: ${(error as Error).message}`);
            return null;
        }
    }
}
    
export default ModManager;