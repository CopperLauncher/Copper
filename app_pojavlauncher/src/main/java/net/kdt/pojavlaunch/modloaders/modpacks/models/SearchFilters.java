package net.kdt.pojavlaunch.modloaders.modpacks.models;

import org.jetbrains.annotations.Nullable;

/**
 * Search filters, passed to APIs
 */
public class SearchFilters {
    public boolean isModpack;
    public String name;
    @Nullable public String mcVersion;

    /**
     * Which kind of content is being searched for (mods/resourcepacks/shaderpacks).
     * Added for the "Browse Content" screen ported from Copper-Android; unused
     * (defaults to MOD) by the existing modpack-search flow.
     */
    public ContentType contentType = ContentType.MOD;
}
