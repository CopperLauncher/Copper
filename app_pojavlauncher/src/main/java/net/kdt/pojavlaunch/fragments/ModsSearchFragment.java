package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.math.MathUtils;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.kdt.mcgui.ProgressLayout;

import git.artdeell.mojo.R;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;
import net.kdt.pojavlaunch.modloaders.modpacks.ModItemAdapter;
import net.kdt.pojavlaunch.modloaders.modpacks.api.CommonApi;
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModLoader;
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModpackApi;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ContentType;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModItem;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchFilters;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchResult;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.utils.DownloadUtils;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * "Browse Content" screen: search Modrinth + CurseForge for mods/
 * resourcepacks/shaderpacks and install them (with their required
 * dependencies) into the currently selected instance.
 *
 * This is a new fragment rather than a straight port of Copper-Android's
 * ModsSearchFragment (built on Amethyst's own ModItem/ModDetail/CommonApi),
 * but it now reuses Mojo's real CommonApi/ModrinthApi/CurseforgeApi for
 * search + version details - see ContentInstallApi below - rather than a
 * hand-rolled API client. Those three classes gained a handful of small,
 * additive changes (content-type-aware search, per-version dependency
 * lists on ModDetail) to support this; see their own commit for what
 * changed and why it doesn't affect the existing modpack-search flow that
 * also depends on them. ContentInstallApi itself only overrides
 * installModpack(), since installing a single mod/resourcepack/shaderpack
 * file (+ its dependencies) is different enough from unzipping a whole
 * modpack that reusing CommonApi's installModpack() wasn't an option.
 */
public class ModsSearchFragment extends Fragment implements ModItemAdapter.SearchResultCallback {

    public static final String TAG = "ModsSearchFragment";

    public static final String ARG_CONTENT_TYPE = "content_type";
    public static final String ARG_PRESET_MC_VERSION = "preset_mc_version";

    private final RecyclerView.OnScrollListener mOverlayPositionListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            mOverlay.setY(MathUtils.clamp(mOverlay.getY() - dy, -mOverlay.getHeight(), mOverlayTopCache));
        }
    };

    private View mOverlay;
    private float mOverlayTopCache;
    private EditText mSearchEditText;
    private ImageButton mFilterButton;
    private RecyclerView mRecyclerview;
    private ModItemAdapter mModItemAdapter;
    private ProgressBar mSearchProgressBar;
    private TextView mStatusTextView;
    private ColorStateList mDefaultTextColor;
    private ContentInstallApi mModpackApi;
    private ContentType mContentType = ContentType.MOD;

    private final SearchFilters mSearchFilters = new SearchFilters();

    public ModsSearchFragment() {
        super(R.layout.fragment_mod_search);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        Bundle args = getArguments();
        String typeName = args != null ? args.getString(ARG_CONTENT_TYPE, null) : null;
        mContentType = typeName != null ? ContentType.valueOf(typeName) : ContentType.MOD;
        mSearchFilters.isModpack = false;
        mSearchFilters.contentType = mContentType;
        if (args != null) mSearchFilters.mcVersion = args.getString(ARG_PRESET_MC_VERSION, null);
        mModpackApi = new ContentInstallApi(context, mContentType, mSearchFilters);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mModItemAdapter = new ModItemAdapter(getResources(), mModpackApi, this);
        ProgressKeeper.addTaskCountListener(mModItemAdapter);
        mOverlayTopCache = getResources().getDimension(R.dimen.fragment_padding_medium);

        mOverlay = view.findViewById(R.id.search_mod_overlay);
        mSearchEditText = view.findViewById(R.id.search_mod_edittext);
        mSearchProgressBar = view.findViewById(R.id.search_mod_progressbar);
        mRecyclerview = view.findViewById(R.id.search_mod_list);
        mStatusTextView = view.findViewById(R.id.search_mod_status_text);
        mFilterButton = view.findViewById(R.id.search_mod_filter);
        // The local-import flow lives on Manage Content instead (see
        // ManageModsFragment.mImportLauncher); hide the button this layout
        // shares with Mojo's own modpack search screen.
        View importButton = view.findViewById(R.id.mineButton_import_local_modpack);
        importButton.setVisibility(View.GONE);

        mDefaultTextColor = mStatusTextView.getTextColors();

        mRecyclerview.setLayoutManager(new LinearLayoutManager(getContext()));
        mRecyclerview.setAdapter(mModItemAdapter);
        mRecyclerview.addOnScrollListener(mOverlayPositionListener);

        mSearchEditText.setOnEditorActionListener((v, actionId, event) -> {
            searchMods(mSearchEditText.getText().toString());
            mSearchEditText.clearFocus();
            return false;
        });

        mOverlay.post(() -> {
            int overlayHeight = mOverlay.getHeight();
            mRecyclerview.setPadding(mRecyclerview.getPaddingLeft(),
                    mRecyclerview.getPaddingTop() + overlayHeight,
                    mRecyclerview.getPaddingRight(),
                    mRecyclerview.getPaddingBottom());
        });
        mFilterButton.setOnClickListener(v -> new ContentFilterDialog()
                .show(getParentFragmentManager(), "content_filters"));

        searchMods(null);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ProgressKeeper.removeTaskCountListener(mModItemAdapter);
        mRecyclerview.removeOnScrollListener(mOverlayPositionListener);
    }

    @Override
    public void onSearchFinished() {
        mSearchProgressBar.setVisibility(View.GONE);
        mStatusTextView.setVisibility(View.GONE);
    }

    @Override
    public void onSearchError(int error) {
        mSearchProgressBar.setVisibility(View.GONE);
        mStatusTextView.setVisibility(View.VISIBLE);
        switch (error) {
            case ERROR_INTERNAL:
                mStatusTextView.setTextColor(Color.RED);
                mStatusTextView.setText(R.string.search_modpack_error);
                break;
            case ERROR_NO_RESULTS:
                mStatusTextView.setTextColor(mDefaultTextColor);
                mStatusTextView.setText(R.string.search_modpack_no_result);
                break;
        }
    }

    private void searchMods(String name) {
        mSearchProgressBar.setVisibility(View.VISIBLE);
        mSearchFilters.name = name == null ? "" : name;
        mModItemAdapter.performSearchQuery(mSearchFilters);
    }

    /**
     * Wraps Mojo's real CommonApi (Modrinth + CurseForge) for search/details,
     * without touching Mojo's shared ModrinthApi/CurseforgeApi/CommonApi
     * (see the class javadoc above for why) other than the small additive
     * changes made to them for this feature (see ModDetail/ModrinthApi/
     * CurseforgeApi) - this class only wraps them.
     */
    private static class ContentInstallApi implements ModpackApi {
        private final CommonApi mCommonApi;
        private final ContentType mContentType;
        private final SearchFilters mSearchFilters;

        ContentInstallApi(Context context, ContentType contentType, SearchFilters searchFilters) {
            mCommonApi = new CommonApi(context.getString(R.string.curseforge_api_key));
            mContentType = contentType;
            mSearchFilters = searchFilters;
        }

        @Override
        public SearchResult searchMod(SearchFilters searchFilters, SearchResult previousPageResult) {
            // CommonApi already searches Modrinth + CurseForge (when a real API key is
            // configured) and fuses/paginates both, so just delegate straight to it -
            // it reads searchFilters.contentType itself (see ModrinthApi/CurseforgeApi).
            return mCommonApi.searchMod(searchFilters, previousPageResult);
        }

        @Override
        public ModDetail getModDetails(ModItem item) {
            return mCommonApi.getModDetails(item);
        }

        @Override
        public ModLoader installModpack(ModDetail modDetail, int selectedVersion) throws IOException {
            installSingleFile(modDetail, selectedVersion, new HashSet<>());
            return null; // no mod loader is involved in installing a single mod/resourcepack/shaderpack
        }

        @Override
        public ModLoader installLocalModpack(String modpackName, File modpackFile, String icon) throws IOException {
            throw new IOException("Local install isn't supported here - use Manage Content's import instead.");
        }

        /**
         * Downloads modDetail's selectedVersion into this instance's content folder, then
         * walks its "required" dependencies (see ModDetail.Dependency) and recursively
         * installs each one the same way. visitedProjectIds prevents installing the same
         * project twice in one call (dependency cycles, or two mods sharing a dependency)
         * and is shared across the whole recursive walk.
         */
        private void installSingleFile(ModDetail modDetail, int selectedVersion, Set<String> visitedProjectIds) throws IOException {
            String selfKey = modDetail.apiSource + ":" + modDetail.id;
            if (!visitedProjectIds.add(selfKey)) return;

            String url = modDetail.versionUrls[selectedVersion];
            File contentDir = getContentDir();
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
                    ModDetail depDetail = mCommonApi.getModDetails(depItem);
                    if (depDetail == null || depDetail.versionUrls.length == 0) continue;
                    installSingleFile(depDetail, pickBestVersionIndex(depDetail), visitedProjectIds);
                } catch (Exception e) {
                    // Best-effort: a dependency we couldn't resolve or install shouldn't
                    // roll back the main file that already downloaded successfully.
                }
            }
        }

        /** Prefers a version matching the active Minecraft-version filter; falls back to
         *  the newest version (index 0 - both Modrinth and CurseForge return newest-first). */
        private int pickBestVersionIndex(ModDetail detail) {
            if (mSearchFilters.mcVersion != null && !mSearchFilters.mcVersion.isEmpty()) {
                for (int i = 0; i < detail.mcVersionNames.length; i++) {
                    if (mSearchFilters.mcVersion.equals(detail.mcVersionNames[i])) return i;
                }
            }
            return 0;
        }

        /** Appends " (1)", " (2)", etc. before the extension if a file of that name already exists. */
        private File uniqueDestination(File dir, String name) {
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

        private File getContentDir() {
            Instance instance = Instances.loadSelectedInstance();
            File gameDir = instance != null ? instance.getGameDirectory() : new File(Tools.DIR_GAME_NEW);
            return new File(gameDir, mContentType.folderName);
        }
    }
}
