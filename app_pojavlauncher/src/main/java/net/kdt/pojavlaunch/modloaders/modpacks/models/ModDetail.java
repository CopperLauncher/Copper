package net.kdt.pojavlaunch.modloaders.modpacks.models;


import androidx.annotation.NonNull;

import java.util.Arrays;

public class ModDetail extends ModItem {
    /* A cheap way to map from the front facing name to the underlying id */
    public String[] versionNames;
    public String [] mcVersionNames;
    public String[] versionUrls;
    /* SHA 1 hashes, null if a hash is unavailable */
    public String[] versionHashes;
    /** Per-version dependency lists (index-aligned with the arrays above). Added for
     *  automatic dependency installs in the mod/resourcepack/shaderpack "Browse Content"
     *  screen; empty (never null) for callers that don't provide any, so existing code
     *  that only ever used the 5-arg constructor keeps working unchanged. */
    public Dependency[][] dependencies;

    public ModDetail(ModItem item, String[] versionNames, String[] mcVersionNames, String[] versionUrls, String[] hashes) {
        this(item, versionNames, mcVersionNames, versionUrls, hashes, emptyDependencies(versionNames.length));
    }

    public ModDetail(ModItem item, String[] versionNames, String[] mcVersionNames, String[] versionUrls,
                      String[] hashes, Dependency[][] dependencies) {
        super(item.apiSource, item.isModpack, item.id, item.title, item.description, item.imageUrl);
        this.versionNames = versionNames;
        this.mcVersionNames = mcVersionNames;
        this.versionUrls = versionUrls;
        this.versionHashes = hashes;
        this.dependencies = dependencies != null ? dependencies : emptyDependencies(versionNames.length);

        // Add the mc version to the version model
        for (int i=0; i<versionNames.length; i++){
            if (!versionNames[i].contains(mcVersionNames[i]))
                versionNames[i] += " - " + mcVersionNames[i];
        }
    }

    private static Dependency[][] emptyDependencies(int count) {
        Dependency[][] empty = new Dependency[count][];
        Arrays.fill(empty, new Dependency[0]);
        return empty;
    }

    /** One dependency of a specific version, as reported by Modrinth ("project_id" +
     *  "dependency_type") or CurseForge ("modId" + "relationType", mapped to the same
     *  three type strings). */
    public static class Dependency {
        public static final String TYPE_REQUIRED = "required";
        public static final String TYPE_OPTIONAL = "optional";
        public static final String TYPE_INCOMPATIBLE = "incompatible";

        public final int apiSource;
        public final String projectId;
        public final String type;

        public Dependency(int apiSource, String projectId, String type) {
            this.apiSource = apiSource;
            this.projectId = projectId;
            this.type = type;
        }
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
