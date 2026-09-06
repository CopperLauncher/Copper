package net.kdt.pojavlaunch.modloaders;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import git.artdeell.mojo.R;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.modloaders.modpacks.api.ApiHandler;
import net.kdt.pojavlaunch.modloaders.modpacks.imagecache.ImageReceiver;
import net.kdt.pojavlaunch.modloaders.modpacks.imagecache.ModIconCache;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ContentType;
import net.kdt.pojavlaunch.utils.DownloadUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * RecyclerView adapter for the "Manage Content" screen — lists the mods/
 * resourcepacks/shaderpacks currently installed in an instance, and lets the
 * user enable/disable (rename to/from ".disabled"), delete, or check-for-update
 * (against Modrinth) each one.
 *
 * Ported from Copper-Android's InstalledModAdapter. Trimmed down for this port:
 * no CurseForge lookups (Modrinth-only, matching the rest of this port), and no
 * "switch version" dialog - just install/update/delete/toggle.
 */
public class InstalledModAdapter extends RecyclerView.Adapter<InstalledModAdapter.ViewHolder> {

    private static final String MODRINTH_API = "https://api.modrinth.com/v2";

    public interface ActionListener {
        void onModDeleted(InstalledMod mod);
        void onModUpdated(InstalledMod mod);
    }

    public static final class InstalledMod {
        public File file;
        public String displayName;
        public boolean enabled;
        public String iconUrl;
        public String modrinthProjectId; // resolved lazily via version-file hash lookup, may be null

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

    public InstalledModAdapter(List<InstalledMod> mods, ContentType contentType,
                                String mcVersionFilter, ActionListener listener) {
        mMods = mods;
        mContentType = contentType;
        mMcVersionFilter = mcVersionFilter;
        mListener = listener;
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
        Context ctx = holder.itemView.getContext();

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

    /**
     * Looks up this file's SHA1 on Modrinth's version-file endpoint to find the
     * matching project, then checks whether a newer version exists for the
     * currently selected/filtered Minecraft version. On a hit, downloads and
     * replaces the file in place.
     */
    private void checkForUpdate(InstalledMod mod, ViewHolder holder) {
        PojavApplication.sExecutorService.execute(() -> {
            try {
                String sha1 = org.apache.commons.codec.binary.Hex.encodeHexString(
                        net.kdt.pojavlaunch.utils.HashUtils.fileHash(
                                java.security.MessageDigest.getInstance("SHA-1"), mod.file));
                HashMap<String, Object> query = new HashMap<>();
                query.put("algorithm", "sha1");
                ModrinthVersion version = ApiHandler.getFullUrl(
                        MODRINTH_API + "/version_file/" + sha1, query, ModrinthVersion.class);
                if (version == null || version.files == null || version.files.isEmpty()) return;

                java.util.HashMap<String, Object> query2 = new java.util.HashMap<>();
                if (mMcVersionFilter != null) query2.put("loaders", "[]");
                ModrinthVersion[] versions = ApiHandler.getFullUrl(
                        MODRINTH_API + "/project/" + version.project_id + "/version",
                        ModrinthVersion[].class);
                if (versions == null) return;

                ModrinthVersion best = null;
                for (ModrinthVersion candidate : versions) {
                    if (mMcVersionFilter != null && candidate.game_versions != null
                            && !java.util.Arrays.asList(candidate.game_versions).contains(mMcVersionFilter)) {
                        continue;
                    }
                    best = candidate;
                    break; // Modrinth returns newest-first
                }
                if (best == null || best.id.equals(version.id) || best.files == null || best.files.isEmpty()) {
                    return; // already up to date, or nothing compatible
                }

                ModrinthFile file = best.files.get(0);
                File tmp = File.createTempFile("update", mContentType.fileExtension, mod.file.getParentFile());
                DownloadUtils.downloadFile(file.url, tmp);
                if (!mod.file.delete()) throw new IOException("could not remove old file");
                File finalFile = new File(mod.file.getParentFile(), file.filename);
                if (!tmp.renameTo(finalFile)) throw new IOException("could not rename downloaded update");

                mod.file = finalFile;
                mod.displayName = file.filename;
                if (mListener != null) mListener.onModUpdated(mod);
                holder.itemView.post(() -> notifyItemChanged(holder.getBindingAdapterPosition()));
            } catch (Exception ignored) {
                // Best-effort: leave the existing file untouched on any failure.
            }
        });
    }

    static class ModrinthVersion {
        String id;
        String project_id;
        String[] game_versions;
        List<ModrinthFile> files;
    }

    static class ModrinthFile {
        String url;
        String filename;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final ImageView icon;
        final androidx.appcompat.widget.SwitchCompat toggle;
        final ImageView updateButton;
        final ImageView deleteButton;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.installed_mod_title);
            icon = itemView.findViewById(R.id.installed_mod_icon);
            toggle = itemView.findViewById(R.id.installed_mod_toggle);
            updateButton = itemView.findViewById(R.id.installed_mod_update);
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
