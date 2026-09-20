package net.kdt.pojavlaunch.modloaders.modpacks.models;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;

public class ModDetail extends ModItem {
    /* A cheap way to map from the front facing name to the underlying id */
    public String[] versionNames;
    public String [] mcVersionNames;
    public String[] versionUrls;
    /* SHA 1 hashes, null if a hash is unavailable */
    public String[] versionHashes;
    /* Every Minecraft version each version supports, null if unknown */
    @Nullable public String[][] gameVersions;
    /* Every mod loader each version supports (lowercase, see Constants.LOADER_*), null if unknown */
    @Nullable public String[][] loaders;

    public ModDetail(ModItem item, String[] versionNames, String[] mcVersionNames, String[] versionUrls, String[] hashes) {
        this(item, versionNames, mcVersionNames, versionUrls, hashes, null, null);
    }

    public ModDetail(ModItem item, String[] versionNames, String[] mcVersionNames, String[] versionUrls, String[] hashes,
                     @Nullable String[][] gameVersions, @Nullable String[][] loaders) {
        super(item.apiSource, item.isModpack, item.id, item.title, item.description, item.imageUrl);
        this.versionNames = versionNames;
        this.mcVersionNames = mcVersionNames;
        this.versionUrls = versionUrls;
        this.versionHashes = hashes;
        this.gameVersions = gameVersions;
        this.loaders = loaders;

        // Add the mc version to the version model
        for (int i=0; i<versionNames.length; i++){
            if (mcVersionNames[i] != null && !versionNames[i].contains(mcVersionNames[i]))
                versionNames[i] += " - " + mcVersionNames[i];
        }
    }

    /**
     * Get the versions that match the search filters. Only these should be offered to the user.
     * @param mcVersion the Minecraft version the version has to support, null or empty for any
     * @param loader the mod loader the version has to use, null or empty for any
     * @return the indices into the version arrays of this object, in their original order
     */
    public int[] getMatchingVersions(@Nullable String mcVersion, @Nullable String loader) {
        boolean filterMc = mcVersion != null && !mcVersion.isEmpty();
        boolean filterLoader = loader != null && !loader.isEmpty();
        int[] matching = new int[versionNames.length];
        int count = 0;
        for (int i = 0; i < versionNames.length; i++) {
            if (filterMc && !contains(gameVersions, i, mcVersion)) continue;
            if (filterLoader && !contains(loaders, i, loader)) continue;
            matching[count++] = i;
        }
        return Arrays.copyOf(matching, count);
    }

    private static boolean contains(@Nullable String[][] values, int index, String wanted) {
        if (values == null || index >= values.length || values[index] == null) return false;
        for (String value : values[index]) {
            if (wanted.equalsIgnoreCase(value)) return true;
        }
        return false;
    }

    @NonNull
    @Override
    public String toString() {
        return "ModDetail{" +
                "versionNames=" + Arrays.toString(versionNames) +
                ", mcVersionNames=" + Arrays.toString(mcVersionNames) +
                ", versionIds=" + Arrays.toString(versionUrls) +
                ", id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", description='" + description + '\'' +
                ", imageUrl='" + imageUrl + '\'' +
                ", apiSource=" + apiSource +
                ", isModpack=" + isModpack +
                '}';
    }
}
