package net.kdt.pojavlaunch.modloaders.modpacks;

import com.kdt.mcgui.ProgressLayout;

import git.artdeell.mojo.R;

import net.kdt.pojavlaunch.modloaders.modpacks.api.CommonApi;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModItem;
import net.kdt.pojavlaunch.utils.DownloadUtils;

import java.io.File;
import java.io.IOException;
import java.util.Set;

/**
 * Downloads a single mod/resourcepack/shaderpack file (as opposed to a whole
 * modpack) into a content folder, then recursively installs its "required"
 * dependencies the same way. Shared between ModsSearchFragment's Browse
 * Content screen (fresh installs) and InstalledModAdapter's "switch version"
 * feature on the Manage Content screen (replacing an already-installed
 * file), so both get automatic dependency installs without duplicating the
 * download/recursion logic.
 */
public final class ContentInstaller {

    private ContentInstaller() {}

    /**
     * @param commonApi        used to resolve dependency projects to their ModDetail
     * @param contentDir       where to place the downloaded file(s) - dependencies are
     *                         assumed to belong in the same folder as what depends on them
     *                         (true for mods; resource/shader packs essentially never have
     *                         cross-project dependencies in practice)
     * @param modDetail        the item + version list to install from
     * @param selectedVersion  index into modDetail's version arrays
     * @param preferredMcVersion used to pick the best matching version of a dependency;
     *                         may be null, in which case the newest version is used
     * @param visitedProjectIds accumulates (apiSource:projectId) keys already installed in
     *                         this call, so shared dependencies or dependency cycles only
     *                         install once - pass a fresh empty Set for a new top-level install
     */
    public static void installWithDependencies(CommonApi commonApi, File contentDir, ModDetail modDetail,
                                                 int selectedVersion, String preferredMcVersion,
                                                 Set<String> visitedProjectIds) throws IOException {
        String selfKey = modDetail.apiSource + ":" + modDetail.id;
        if (!visitedProjectIds.add(selfKey)) return;

        String url = modDetail.versionUrls[selectedVersion];
        if (!contentDir.isDirectory() && !contentDir.mkdirs()) {
            throw new IOException("could not create content directory");
        }
        String fileName = url.substring(url.lastIndexOf('/') + 1);
        File destination = uniqueDestination(contentDir, fileName);
        ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 0, R.string.global_waiting);
        try {
            DownloadUtils.downloadFile(url, destination);
        } finally {
            ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
        }

        if (modDetail.dependencies == null || selectedVersion >= modDetail.dependencies.length) return;
        for (ModDetail.Dependency dep : modDetail.dependencies[selectedVersion]) {
            if (dep == null || dep.projectId == null) continue;
            if (!ModDetail.Dependency.TYPE_REQUIRED.equals(dep.type)) continue;
            String depKey = dep.apiSource + ":" + dep.projectId;
            if (visitedProjectIds.contains(depKey)) continue;

            try {
                ModItem depItem = new ModItem(dep.apiSource, false, dep.projectId, dep.projectId, "", null);
                ModDetail depDetail = commonApi.getModDetails(depItem);
                if (depDetail == null || depDetail.versionUrls.length == 0) continue;
                int depVersionIndex = pickBestVersionIndex(depDetail, preferredMcVersion);
                installWithDependencies(commonApi, contentDir, depDetail, depVersionIndex,
                        preferredMcVersion, visitedProjectIds);
            } catch (Exception e) {
                // Best-effort: a dependency we couldn't resolve or install shouldn't roll
                // back the main file that already downloaded successfully.
            }
        }
    }

    /** Prefers a version matching preferredMcVersion; falls back to the newest version
     *  (index 0 - both Modrinth and CurseForge return newest-first). */
    public static int pickBestVersionIndex(ModDetail detail, String preferredMcVersion) {
        if (preferredMcVersion != null && !preferredMcVersion.isEmpty()) {
            for (int i = 0; i < detail.mcVersionNames.length; i++) {
                if (preferredMcVersion.equals(detail.mcVersionNames[i])) return i;
            }
        }
        return 0;
    }

    /** Appends " (1)", " (2)", etc. before the extension if a file of that name already exists. */
    public static File uniqueDestination(File dir, String name) {
        File candidate = new File(dir, name);
        if (!candidate.exists()) return candidate;
        String base = name, ext = "";
        int dot = name.lastIndexOf('.');
        if (dot >= 0) { base = name.substring(0, dot); ext = name.substring(dot); }
        int count = 1;
        do {
            candidate = new File(dir, base + " (" + count + ")" + ext);
            count++;
        } while (candidate.exists());
        return candidate;
    }
}
