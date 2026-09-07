package net.kdt.pojavlaunch.modloaders;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import git.artdeell.mojo.R;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.modloaders.modpacks.ContentInstaller;
import net.kdt.pojavlaunch.modloaders.modpacks.api.ApiHandler;
import net.kdt.pojavlaunch.modloaders.modpacks.api.CommonApi;
import net.kdt.pojavlaunch.modloaders.modpacks.imagecache.ImageReceiver;
import net.kdt.pojavlaunch.modloaders.modpacks.imagecache.ModIconCache;
import net.kdt.pojavlaunch.modloaders.modpacks.models.Constants;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ContentType;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModItem;
import net.kdt.pojavlaunch.utils.Murmur2;

import java.io.File;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

/**
 * RecyclerView adapter for the "Manage Content" screen — lists the mods/
 * resourcepacks/shaderpacks currently installed in an instance, and lets the
 * user enable/disable (rename to/from ".disabled"), delete, check-for-update,
 * or switch version for each one.
 *
 * Ported from Copper-Android's InstalledModAdapter, with the identification
 * strategy adapted to reuse Mojo's real API classes (see ContentInstallApi's
 * javadoc in ModsSearchFragment for why): an installed file's origin is
 * resolved once (Modrinth by SHA1, falling back to CurseForge by murmur2
 * fingerprint - same two-step lookup Copper-Android's version used for icon
 * resolution) into a plain ModItem, and from then on CommonApi.getModDetails()
 * does the rest, exactly like it already does for Browse Content. This also
 * means update-check and switch-version share one code path instead of two.
 */
public class InstalledModAdapter extends RecyclerView.Adapter<InstalledModAdapter.ViewHolder> {

    private static final String MODRINTH_API = "https://api.modrinth.com/v2";
    private static final String CURSEFORGE_API = "https://api.curseforge.com/v1";

    public interface ActionListener {
        void onModDeleted(InstalledMod mod);
        void onModUpdated(InstalledMod mod);
    }

    public static final class InstalledMod {
        public File file;
        public String displayName;
        public boolean enabled;
        public String iconUrl;

        public InstalledMod(File file) {
            this.file = file;
            this.enabled = !file.getName().endsWith(".disabled");
            String name = file.getName();
            if (name.endsWith(".disabled")) name = name.substring(0, name.length() - ".disabled".length());
            this.displayName = name;
        }
    }

    private final List<InstalledMod> mMods;
    private final ContentType mContentType;
    private final String mMcVersionFilter;
    private final ModIconCache mIconCache = new ModIconCache();
    private final ActionListener mListener;
    private final Context mAppContext;
    private final String mCurseforgeApiKey;

    public InstalledModAdapter(Context context, List<InstalledMod> mods, ContentType contentType,
                                String mcVersionFilter, ActionListener listener) {
        mAppContext = context.getApplicationContext();
        mMods = mods;
        mContentType = contentType;
        mMcVersionFilter = mcVersionFilter;
        mListener = listener;
        mCurseforgeApiKey = context.getString(R.string.curseforge_api_key);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_installed_mod, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        InstalledMod mod = mMods.get(position);

        holder.title.setText(mod.displayName);
        holder.toggle.setChecked(mod.enabled);
        holder.icon.setImageResource(R.drawable.ic_mod_placeholder);

        holder.toggle.setOnCheckedChangeListener(null);
        holder.toggle.setChecked(mod.enabled);
        holder.toggle.setOnCheckedChangeListener((btn, checked) -> setEnabled(mod, checked));

        holder.deleteButton.setOnClickListener(v -> {
            if (mod.file.delete() || mod.file.isDirectory()) {
                int idx = mMods.indexOf(mod);
                mMods.remove(mod);
                notifyItemRemoved(idx);
                if (mListener != null) mListener.onModDeleted(mod);
            }
        });

        holder.updateButton.setOnClickListener(v -> checkForUpdate(mod, holder));
        holder.switchVersionButton.setOnClickListener(v -> showSwitchVersionDialog(mod, holder));

        String iconTag = mod.file.getAbsolutePath();
        holder.icon.setTag(iconTag);
        mIconCache.getImage(new ImageReceiver() {
            @Override
            public void onImageAvailable(Bitmap image) {
                if (iconTag.equals(holder.icon.getTag()) && image != null) {
                    holder.icon.setImageBitmap(image);
                }
            }
        }, iconTag, mod.iconUrl);
    }

    @Override
    public int getItemCount() {
        return mMods.size();
    }

    private void setEnabled(InstalledMod mod, boolean enabled) {
        if (enabled == mod.enabled) return;
        File target;
        if (enabled) {
            String name = mod.file.getName();
            name = name.substring(0, name.length() - ".disabled".length());
            target = new File(mod.file.getParentFile(), name);
        } else {
            target = new File(mod.file.getParentFile(), mod.file.getName() + ".disabled");
        }
        if (mod.file.renameTo(target)) {
            mod.file = target;
            mod.enabled = enabled;
        }
    }

    /** Which content source an installed file was resolved to, and its project id there. */
    private static final class ResolvedSource {
        final int apiSource;
        final String projectId;
        final String sha1; // only ever set for the Modrinth path - used to detect "already up to date"
        ResolvedSource(int apiSource, String projectId, String sha1) {
            this.apiSource = apiSource;
            this.projectId = projectId;
            this.sha1 = sha1;
        }
    }

    /**
     * Identifies which project an installed file came from: tries Modrinth first (by SHA1,
     * via its version_file endpoint), then falls back to CurseForge (by murmur2 fingerprint,
     * via its fingerprints endpoint) if a real API key is configured. Returns null if neither
     * source recognises the file. Runs blocking network I/O - call off the main thread.
     */
    @Nullable
    private ResolvedSource resolveSource(File file) {
        try {
            String sha1 = org.apache.commons.codec.binary.Hex.encodeHexString(
                    net.kdt.pojavlaunch.utils.HashUtils.fileHash(MessageDigest.getInstance("SHA-1"), file));
            HashMap<String, Object> query = new HashMap<>();
            query.put("algorithm", "sha1");
            ModrinthVersion version = ApiHandler.getFullUrl(
                    MODRINTH_API + "/version_file/" + sha1, query, ModrinthVersion.class);
            if (version != null && version.project_id != null) {
                return new ResolvedSource(Constants.SOURCE_MODRINTH, version.project_id, sha1);
            }
        } catch (Exception ignored) {}

        if (mCurseforgeApiKey == null || mCurseforgeApiKey.isEmpty() || "DUMMY".equals(mCurseforgeApiKey)) {
            return null;
        }
        try {
            long fingerprint = Murmur2.hashFile(file);
            com.google.gson.JsonArray fingerprints = new com.google.gson.JsonArray();
            fingerprints.add(fingerprint);
            com.google.gson.JsonObject body = new com.google.gson.JsonObject();
            body.add("fingerprints", fingerprints);

            HashMap<String, String> headers = new HashMap<>();
            headers.put("x-api-key", mCurseforgeApiKey);
            headers.put("Content-Type", "application/json");
            headers.put("Accept", "application/json");

            String responseRaw = ApiHandler.postRaw(headers, CURSEFORGE_API + "/fingerprints", body.toString());
            if (responseRaw == null) return null;
            com.google.gson.JsonObject response = com.google.gson.JsonParser.parseString(responseRaw).getAsJsonObject();
            if (!response.has("data")) return null;
            com.google.gson.JsonObject data = response.getAsJsonObject("data");
            com.google.gson.JsonArray exactMatches = data.has("exactMatches") ? data.getAsJsonArray("exactMatches") : null;
            if (exactMatches == null || exactMatches.size() == 0) return null;

            com.google.gson.JsonObject match = exactMatches.get(0).getAsJsonObject();
            if (!match.has("file")) return null;
            com.google.gson.JsonObject file2 = match.getAsJsonObject("file");
            if (!file2.has("modId")) return null;
            return new ResolvedSource(Constants.SOURCE_CURSEFORGE, String.valueOf(file2.get("modId").getAsInt()), null);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Resolves the installed file's source/project, fetches its full version list via
     * CommonApi (Modrinth or CurseForge, whichever matched), and hands the result to
     * onResolved on the calling (background) thread. Shared by checkForUpdate() and
     * showSwitchVersionDialog(). Toasts and returns early on any failure.
     */
    private void resolveAndFetchDetails(InstalledMod mod, ViewHolder holder,
                                         ResolvedDetailsCallback onResolved) {
        PojavApplication.sExecutorService.execute(() -> {
            ResolvedSource source = resolveSource(mod.file);
            if (source == null) {
                holder.itemView.post(() -> Toast.makeText(mAppContext,
                        R.string.mod_source_not_found, Toast.LENGTH_SHORT).show());
                return;
            }
            try {
                CommonApi commonApi = new CommonApi(mCurseforgeApiKey);
                ModItem item = new ModItem(source.apiSource, false, source.projectId, source.projectId, "", null);
                ModDetail detail = commonApi.getModDetails(item);
                if (detail == null || detail.versionUrls.length == 0) {
                    holder.itemView.post(() -> Toast.makeText(mAppContext,
                            R.string.mod_source_not_found, Toast.LENGTH_SHORT).show());
                    return;
                }
                onResolved.onResolved(commonApi, detail, source);
            } catch (Exception e) {
                holder.itemView.post(() -> Toast.makeText(mAppContext,
                        R.string.mod_source_not_found, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private interface ResolvedDetailsCallback {
        void onResolved(CommonApi commonApi, ModDetail detail, ResolvedSource source);
    }

    /** Checks whether a newer compatible version exists and, if so, downloads it (along with
     *  any new required dependencies) and replaces the old file. */
    private void checkForUpdate(InstalledMod mod, ViewHolder holder) {
        resolveAndFetchDetails(mod, holder, (commonApi, detail, source) -> {
            int bestIndex = ContentInstaller.pickBestVersionIndex(detail, mMcVersionFilter);
            String bestHash = detail.versionHashes != null && bestIndex < detail.versionHashes.length
                    ? detail.versionHashes[bestIndex] : null;
            if (source.sha1 != null && source.sha1.equalsIgnoreCase(bestHash)) {
                holder.itemView.post(() -> Toast.makeText(mAppContext,
                        R.string.mod_already_up_to_date, Toast.LENGTH_SHORT).show());
                return;
            }
            installReplacement(mod, holder, commonApi, detail, bestIndex);
        });
    }

    /** Lets the user pick any version from the resolved project's full version list and
     *  installs it in place of the current file. */
    private void showSwitchVersionDialog(InstalledMod mod, ViewHolder holder) {
        resolveAndFetchDetails(mod, holder, (commonApi, detail, source) ->
                holder.itemView.post(() -> new androidx.appcompat.app.AlertDialog.Builder(holder.itemView.getContext())
                        .setTitle(R.string.switch_mod_version_title)
                        .setItems(detail.versionNames, (dialog, which) ->
                                PojavApplication.sExecutorService.execute(() ->
                                        installReplacement(mod, holder, commonApi, detail, which)))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()));
    }

    private void installReplacement(InstalledMod mod, ViewHolder holder, CommonApi commonApi,
                                     ModDetail detail, int versionIndex) {
        try {
            File contentDir = mod.file.getParentFile();
            String oldPath = mod.file.getAbsolutePath();
            File oldFile = mod.file;

            ContentInstaller.installWithDependencies(commonApi, contentDir, detail, versionIndex,
                    mMcVersionFilter, new HashSet<>());

            oldFile.delete();

            String url = detail.versionUrls[versionIndex];
            String newFileName = url.substring(url.lastIndexOf('/') + 1);
            File newFile = new File(contentDir, newFileName);

            mod.file = newFile;
            mod.displayName = newFileName;
            if (mListener != null) mListener.onModUpdated(mod);
            holder.itemView.post(() -> {
                Toast.makeText(mAppContext,
                        mAppContext.getString(R.string.mod_update_success, mod.displayName),
                        Toast.LENGTH_SHORT).show();
                notifyItemChanged(holder.getBindingAdapterPosition());
            });
        } catch (Exception e) {
            holder.itemView.post(() -> Toast.makeText(mAppContext, R.string.mod_update_failed, Toast.LENGTH_SHORT).show());
        }
    }

    static class ModrinthVersion {
        String id;
        String project_id;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final ImageView icon;
        final androidx.appcompat.widget.SwitchCompat toggle;
        final ImageView updateButton;
        final ImageView switchVersionButton;
        final ImageView deleteButton;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.installed_mod_title);
            icon = itemView.findViewById(R.id.installed_mod_icon);
            toggle = itemView.findViewById(R.id.installed_mod_toggle);
            updateButton = itemView.findViewById(R.id.installed_mod_update);
            switchVersionButton = itemView.findViewById(R.id.installed_mod_switch_version);
            deleteButton = itemView.findViewById(R.id.installed_mod_delete);
        }
    }

    public static List<InstalledMod> scan(File contentDir) {
        List<InstalledMod> mods = new ArrayList<>();
        File[] files = contentDir.listFiles();
        if (files == null) return mods;
        for (File f : files) {
            if (f.isDirectory()) continue;
            String name = f.getName();
            if (name.endsWith(".jar") || name.endsWith(".zip")
                    || name.endsWith(".jar.disabled") || name.endsWith(".zip.disabled")) {
                mods.add(new InstalledMod(f));
            }
        }
        return mods;
    }
}
