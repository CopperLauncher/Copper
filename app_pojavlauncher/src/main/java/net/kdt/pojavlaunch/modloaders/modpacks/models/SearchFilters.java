package net.kdt.pojavlaunch.modloaders.modpacks.models;

import org.jetbrains.annotations.Nullable;

/**
 * Search filters, passed to APIs
 */
public class SearchFilters {
    /** Where to search: only Modrinth (the default), only CurseForge or both */
    public static final int SOURCE_MODRINTH = 0;
    public static final int SOURCE_CURSEFORGE = 1;
    public static final int SOURCE_BOTH = 2;

    public boolean isModpack;
    public String name;
    @Nullable public String mcVersion;
    /** One of the Constants.LOADER_* values, null for any mod loader */
    @Nullable public String loader;
    public int source = SOURCE_MODRINTH;

}
