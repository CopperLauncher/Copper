package net.kdt.pojavlaunch.fragments;

import net.kdt.pojavlaunch.JVersionList;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.instances.Instance;

/**
 * Infers an instance's plain Minecraft version (e.g. "1.20.1") and mod loader
 * (fabric/forge/quilt/neoforge/"") from its {@code versionId}. Used to
 * auto-fill the Manage Content filter with sensible defaults the first time
 * it's opened for an instance, instead of leaving it blank.
 *
 * Ported from Copper-Android's InstanceVersionResolver; adapted from
 * Amethyst's Profile/MinecraftProfile model to Mojo's Instance model
 * (profile.lastVersionId -> instance.versionId).
 */
final class InstanceVersionResolver {

    private InstanceVersionResolver() {}

    static final class Info {
        /** May be null if it couldn't be determined at all. */
        final String mcVersion;
        /** "", "fabric", "forge", "quilt", or "neoforge". */
        final String loader;

        Info(String mcVersion, String loader) {
            this.mcVersion = mcVersion;
            this.loader = loader;
        }
    }

    static Info resolve(Instance instance) {
        try {
            if (instance == null || instance.versionId == null || instance.versionId.isEmpty()) {
                return new Info(null, "");
            }
            String versionId = instance.versionId;
            String loader = loaderFromVersionId(versionId);
            String mcVersion = cleanMcVersion(versionId, loader);
            return new Info(mcVersion, loader);
        } catch (Exception e) {
            return new Info(null, "");
        }
    }

    private static String loaderFromVersionId(String versionId) {
        if (versionId.startsWith("fabric-loader-")) return "fabric";
        if (versionId.startsWith("quilt-loader-"))  return "quilt";
        if (versionId.startsWith("neoforge-"))       return "neoforge";
        if (versionId.contains("-forge-"))           return "forge";
        return "";
    }

    /** Strips the loader naming convention off a version id (see ModLoader.getVersionId())
     *  to get the plain Minecraft version underneath — e.g. "fabric-loader-0.15.11-1.20.1"
     *  → "1.20.1". Falls back to the version JSON's own inheritsFrom (which every mod
     *  loader install here sets to the vanilla version it's built on), and finally to the
     *  raw id itself if neither approach works (e.g. NeoForge ids don't embed the MC
     *  version at all, so this relies entirely on inheritsFrom for that loader). */
    private static String cleanMcVersion(String versionId, String loader) {
        switch (loader) {
            case "fabric": {
                String rest = versionId.substring("fabric-loader-".length());
                int dash = rest.indexOf('-');
                if (dash > 0) return rest.substring(dash + 1);
                break;
            }
            case "quilt": {
                String rest = versionId.substring("quilt-loader-".length());
                int dash = rest.indexOf('-');
                if (dash > 0) return rest.substring(dash + 1);
                break;
            }
            case "forge": {
                int idx = versionId.indexOf("-forge-");
                if (idx > 0) return versionId.substring(0, idx);
                break;
            }
            default:
                break;
        }

        try {
            JVersionList.Version info = Tools.getVersionInfo(versionId);
            if (info != null && info.inheritsFrom != null && !info.inheritsFrom.isEmpty()) {
                return info.inheritsFrom;
            }
        } catch (Exception ignored) {}

        return loader.isEmpty() ? versionId : null;
    }
}
