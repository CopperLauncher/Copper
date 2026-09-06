package net.kdt.pojavlaunch.modloaders.modpacks.models;

/**
 * The three kinds of installable content that "Browse Content" / "Manage Content"
 * support. Mods, resource packs, and shader packs all go through the same
 * search/install/update-check pipeline (ModsSearchFragment, ManageModsFragment,
 * InstalledModAdapter) — this enum centralizes the handful of things that
 * actually differ between them, so that pipeline doesn't need three separate
 * copies:
 *   - which folder the content lives in under the instance's game directory
 *   - what file extension its files use
 *   - the Modrinth `project_type` facet to search under
 *   - the CurseForge classId (category) to search under
 */
public enum ContentType {
    MOD("mods", ".jar", "mod", 6),
    RESOURCE_PACK("resourcepacks", ".zip", "resourcepack", 12),
    // https://api.curseforge.com/v1/categories?gameId=432 — "Shaders" category id.
    SHADER_PACK("shaderpacks", ".zip", "shader", 6552);

    /** Folder name under the instance's game directory (e.g. ".minecraft/mods"). */
    public final String folderName;
    /** File extension used for this content's files (e.g. ".jar", ".zip"). */
    public final String fileExtension;
    /** Modrinth `project_type` facet value. */
    public final String modrinthType;
    /** CurseForge classId under the Minecraft game (id 432). */
    public final int curseforgeClassId;

    ContentType(String folderName, String fileExtension, String modrinthType, int curseforgeClassId) {
        this.folderName = folderName;
        this.fileExtension = fileExtension;
        this.modrinthType = modrinthType;
        this.curseforgeClassId = curseforgeClassId;
    }

    /** Inverse of {@link #modrinthType} — used to round-trip the selection through a Bundle. */
    public static ContentType fromModrinthType(String type) {
        if (type == null) return MOD;
        switch (type) {
            case "resourcepack": return RESOURCE_PACK;
            case "shader":       return SHADER_PACK;
            default:             return MOD;
        }
    }
}
