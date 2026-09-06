package net.kdt.pojavlaunch.fragments;

import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import git.artdeell.mojo.R;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.contracts.OpenDocumentWithExtension;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;
import net.kdt.pojavlaunch.modloaders.InstalledModAdapter;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ContentType;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Manages installed content (mods, resource packs, or shader packs — see
 * {@link #ARG_CONTENT_TYPE}) for the currently selected instance: list,
 * enable/disable, delete, import, and check for updates.
 *
 * Ported from Copper-Android's ManageModsFragment. Adapted from Amethyst's
 * Profile/MinecraftProfile model to Mojo's Instance model, and from the
 * two-pane (left/right container) navigation Copper-Android's home screen
 * uses to Mojo's single-pane {@code container_fragment} + back stack, since
 * Mojo's MainMenuFragment has no split-pane layout to dock into.
 */
public class ManageModsFragment extends Fragment {

    public static final String TAG = "ManageModsFragment";

    /** Bundle key: which kind of content this screen manages. Value is a
     *  ContentType enum name(); defaults to MOD when absent. */
    public static final String ARG_CONTENT_TYPE = "content_type";

    private static final String PREF_FILE      = "mod_filters";
    private static final String KEY_MC_VERSION = "mc_version_";
    private static final String KEY_LOADER     = "loader_";

    private InstalledModAdapter mAdapter;
    private ContentType mContentType = ContentType.MOD;
    private List<InstalledModAdapter.InstalledMod> mMods;

    // OpenDocumentWithExtension only filters by a single extension (and falls
    // back to "*/*" for one it doesn't recognise) - it can't filter by both
    // "jar" and "zip" at once, so this passes an empty string to get the
    // "all types" fallback, and relies on onImportFilePicked() to validate
    // the picked file's extension against mContentType.fileExtension itself.
    private final ActivityResultLauncher<Object> mImportLauncher =
            registerForActivityResult(new OpenDocumentWithExtension(""),
                    this::onImportFilePicked);

    public ManageModsFragment() {
        super(R.layout.fragment_manage_mods);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Bundle args = getArguments();
        String typeName = args != null ? args.getString(ARG_CONTENT_TYPE, null) : null;
        mContentType = typeName != null ? ContentType.valueOf(typeName) : ContentType.MOD;

        ImageButton backButton    = view.findViewById(R.id.manage_mods_back);
        ImageButton refreshButton = view.findViewById(R.id.manage_mods_refresh);
        ImageButton addButton     = view.findViewById(R.id.manage_mods_add);
        ImageButton importButton  = view.findViewById(R.id.manage_mods_import);
        TextView    title         = view.findViewById(R.id.manage_mods_title);
        RecyclerView recycler     = view.findViewById(R.id.manage_mods_recycler);
        View        emptyState    = view.findViewById(R.id.manage_mods_empty);

        backButton.setOnClickListener(v -> requireActivity().onBackPressed());
        refreshButton.setOnClickListener(v -> reload(recycler, emptyState));
        addButton.setOnClickListener(v -> openContentSearch());
        importButton.setOnClickListener(v -> mImportLauncher.launch(null));

        Instance instance = Instances.loadSelectedInstance();
        String instanceName = instance != null ? instance.name : "";
        String typeLabel = getString(contentTypeLabelRes());
        title.setText(instanceName.isEmpty() ? typeLabel : instanceName + " - " + typeLabel);

        if (emptyState instanceof TextView) {
            ((TextView) emptyState).setText(contentTypeEmptyLabelRes());
        }

        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        reload(recycler, emptyState);
    }

    private void reload(RecyclerView recycler, View emptyState) {
        File contentDir = getContentDir();
        mMods = InstalledModAdapter.scan(contentDir);
        String[] filter = resolveFilter();
        mAdapter = new InstalledModAdapter(mMods, mContentType, filter[0].isEmpty() ? null : filter[0],
                new InstalledModAdapter.ActionListener() {
                    @Override
                    public void onModDeleted(InstalledModAdapter.InstalledMod mod) {
                        toggleEmptyState(recycler, emptyState);
                    }

                    @Override
                    public void onModUpdated(InstalledModAdapter.InstalledMod mod) {
                        Toast.makeText(requireContext(),
                                getString(R.string.mod_update_success, mod.displayName),
                                Toast.LENGTH_SHORT).show();
                    }
                });
        recycler.setAdapter(mAdapter);
        toggleEmptyState(recycler, emptyState);
    }

    private void toggleEmptyState(RecyclerView recycler, View emptyState) {
        boolean isEmpty = mMods == null || mMods.isEmpty();
        recycler.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        emptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
    }

    private void openContentSearch() {
        String[] filter = resolveFilter();

        Bundle args = new Bundle();
        if (!filter[0].isEmpty()) args.putString(ModsSearchFragment.ARG_PRESET_MC_VERSION, filter[0]);
        args.putString(ModsSearchFragment.ARG_CONTENT_TYPE, mContentType.name());

        ModsSearchFragment fragment = new ModsSearchFragment();
        fragment.setArguments(args);
        Tools.swapFragment(requireActivity(), ModsSearchFragment.class,
                ModsSearchFragment.TAG + ":" + mContentType.name(), args);
    }

    /**
     * Handles a file picked via {@link #mImportLauncher}: validates its
     * extension against the content type this screen manages, copies it into
     * that content type's folder for the current instance, and reloads the list.
     */
    private void onImportFilePicked(Uri uri) {
        if (uri == null) return;

        String requiredExtension = mContentType.fileExtension;
        String pickedName = queryDisplayName(uri);
        if (pickedName == null || !pickedName.toLowerCase().endsWith(requiredExtension)) {
            Toast.makeText(requireContext(),
                    getString(R.string.content_import_wrong_type, requiredExtension),
                    Toast.LENGTH_LONG).show();
            return;
        }

        File contentDir = getContentDir();
        File destination = uniqueDestination(contentDir, pickedName);

        PojavApplication.sExecutorService.execute(() -> {
            boolean success = copyToFile(uri, contentDir, destination);
            if (getActivity() == null) return;
            requireActivity().runOnUiThread(() -> {
                if (success) {
                    View root = getView();
                    if (root != null) {
                        reload(root.findViewById(R.id.manage_mods_recycler),
                                root.findViewById(R.id.manage_mods_empty));
                    }
                    Toast.makeText(requireContext(),
                            getString(R.string.content_import_success, destination.getName()),
                            Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(requireContext(),
                            R.string.content_import_failed, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    @Nullable
    private String queryDisplayName(Uri uri) {
        try (Cursor cursor = requireContext().getContentResolver()
                .query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        } catch (Exception ignored) {}
        String path = uri.getLastPathSegment();
        return path != null ? path.substring(path.lastIndexOf('/') + 1) : null;
    }

    /** Appends " (1)", " (2)", etc. before the extension if a file of that name already exists. */
    private File uniqueDestination(File dir, String name) {
        File candidate = new File(dir, name);
        if (!candidate.exists()) return candidate;

        String base = name;
        String ext = "";
        int dot = name.lastIndexOf('.');
        if (dot >= 0) {
            base = name.substring(0, dot);
            ext = name.substring(dot);
        }
        int count = 1;
        do {
            candidate = new File(dir, base + " (" + count + ")" + ext);
            count++;
        } while (candidate.exists());
        return candidate;
    }

    private boolean copyToFile(Uri source, File contentDir, File destination) {
        if (!contentDir.isDirectory() && !contentDir.mkdirs()) return false;
        try (InputStream input = requireContext().getContentResolver().openInputStream(source);
             FileOutputStream output = new FileOutputStream(destination)) {
            if (input == null) return false;
            byte[] buffer = new byte[262144];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            output.flush();
            return true;
        } catch (IOException e) {
            destination.delete();
            return false;
        }
    }

    /**
     * The version filter to actually use: whatever's saved for this instance, or
     * — if nothing's been saved yet — whatever the instance itself is detected
     * as running. Index 0 is the MC version; loader detection is kept out of
     * this port (see ContentFilterDialog).
     */
    private String[] resolveFilter() {
        String instanceKey = LauncherPreferences.DEFAULT_PREF
                .getString(LauncherPreferences.PREF_KEY_CURRENT_INSTANCE, "default");
        SharedPreferences prefs = requireContext()
                .getSharedPreferences(PREF_FILE, android.content.Context.MODE_PRIVATE);
        String version = prefs.getString(KEY_MC_VERSION + instanceKey, "");

        if (version.isEmpty()) {
            InstanceVersionResolver.Info info =
                    InstanceVersionResolver.resolve(Instances.loadSelectedInstance());
            if (info.mcVersion != null) version = info.mcVersion;
        }
        return new String[]{version};
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private int contentTypeLabelRes() {
        switch (mContentType) {
            case RESOURCE_PACK: return R.string.mcl_content_resourcepacks;
            case SHADER_PACK:   return R.string.mcl_content_shaderpacks;
            default:            return R.string.mcl_content_mods;
        }
    }

    private int contentTypeEmptyLabelRes() {
        switch (mContentType) {
            case RESOURCE_PACK: return R.string.manage_resourcepacks_empty;
            case SHADER_PACK:   return R.string.manage_shaderpacks_empty;
            default:            return R.string.manage_mods_empty;
        }
    }

    private File getContentDir() {
        Instance instance = Instances.loadSelectedInstance();
        File gameDir = instance != null ? instance.getGameDirectory() : new File(Tools.DIR_GAME_NEW);
        return new File(gameDir, mContentType.folderName);
    }
}
