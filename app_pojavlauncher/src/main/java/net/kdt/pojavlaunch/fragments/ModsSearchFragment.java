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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.kdt.mcgui.ProgressLayout;

import git.artdeell.mojo.R;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;
import net.kdt.pojavlaunch.modloaders.modpacks.ModItemAdapter;
import net.kdt.pojavlaunch.modloaders.modpacks.api.ApiHandler;
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModLoader;
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModpackApi;
import net.kdt.pojavlaunch.modloaders.modpacks.models.Constants;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ContentType;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModItem;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchFilters;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchResult;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.utils.DownloadUtils;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

/**
 * "Browse Content" screen: search Modrinth for mods/resourcepacks/shaderpacks
 * and install them into the currently selected instance.
 *
 * This is a new implementation rather than a straight port of
 * Copper-Android's ModsSearchFragment: that version was built on Amethyst's
 * richer ModItem/ModDetail/CommonApi (which also support CurseForge,
 * dependency auto-installing, and per-version switching), and those classes
 * are shared with Mojo's *existing*, working modpack-creation flow
 * (SearchModFragment / CommonApi / ModrinthApi / CurseforgeApi). Rather than
 * risk that flow by changing those shared classes, this fragment follows the
 * same structure/UI as Mojo's own SearchModFragment (which already searches
 * Modrinth modpacks) and adds a small self-contained ModpackApi
 * implementation (ModsInstallApi, below) that talks to Modrinth directly for
 * the three individual content types. Net effect: CurseForge and multi-
 * version dependency installs aren't part of this port's Browse Content
 * screen (Modrinth-only, single-file installs).
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
    private ModsInstallApi mModpackApi;
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
        mModpackApi = new ModsInstallApi(mContentType);
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
     * Small, self-contained ModpackApi implementation that talks to Modrinth
     * directly for a single ContentType (mod/resourcepack/shaderpack),
     * without touching Mojo's shared ModrinthApi/CommonApi (see the class
     * javadoc above for why). Loader filtering isn't supported - only the
     * Minecraft-version facet is applied, matching ContentFilterDialog.
     */
    private static class ModsInstallApi implements ModpackApi {
        private static final String MODRINTH_API = "https://api.modrinth.com/v2";
        private final ApiHandler mApiHandler = new ApiHandler(MODRINTH_API);
        private final ContentType mContentType;

        ModsInstallApi(ContentType contentType) {
            mContentType = contentType;
        }

        @Override
        public SearchResult searchMod(SearchFilters searchFilters, SearchResult previousPageResult) {
            int previousOffset = previousPageResult instanceof OffsetSearchResult
                    ? ((OffsetSearchResult) previousPageResult).offset : 0;
            if (previousPageResult != null && previousOffset >= previousPageResult.totalResultCount) {
                OffsetSearchResult empty = new OffsetSearchResult();
                empty.results = new ModItem[0];
                empty.totalResultCount = previousPageResult.totalResultCount;
                empty.offset = previousOffset;
                return empty;
            }

            HashMap<String, Object> params = new HashMap<>();
            StringBuilder facets = new StringBuilder("[");
            facets.append(String.format("[\"project_type:%s\"]", mContentType.modrinthType));
            if (searchFilters.mcVersion != null && !searchFilters.mcVersion.isEmpty()) {
                facets.append(String.format(",[\"versions:%s\"]", searchFilters.mcVersion));
            }
            facets.append("]");
            params.put("facets", facets.toString());
            params.put("query", searchFilters.name);
            params.put("limit", 50);
            params.put("index", "relevance");
            if (previousPageResult != null) params.put("offset", previousOffset);

            JsonObject response = mApiHandler.get("search", params, JsonObject.class);
            if (response == null) return null;
            JsonArray hits = response.getAsJsonArray("hits");
            if (hits == null) return null;

            ModItem[] items = new ModItem[hits.size()];
            for (int i = 0; i < hits.size(); i++) {
                JsonObject hit = hits.get(i).getAsJsonObject();
                items[i] = new ModItem(
                        Constants.SOURCE_MODRINTH,
                        false,
                        hit.get("project_id").getAsString(),
                        hit.get("title").getAsString(),
                        hit.get("description").getAsString(),
                        hit.get("icon_url").isJsonNull() ? null : hit.get("icon_url").getAsString());
            }

            OffsetSearchResult result = new OffsetSearchResult();
            result.results = items;
            result.offset = previousOffset + hits.size();
            result.totalResultCount = response.get("total_hits").getAsInt();
            return result;
        }

        @Override
        public ModDetail getModDetails(ModItem item) {
            JsonArray response = mApiHandler.get(
                    String.format("project/%s/version", item.id), JsonArray.class);
            if (response == null) return null;

            String[] names = new String[response.size()];
            String[] mcNames = new String[response.size()];
            String[] urls = new String[response.size()];
            String[] hashes = new String[response.size()];

            for (int i = 0; i < response.size(); i++) {
                JsonObject version = response.get(i).getAsJsonObject();
                names[i] = version.get("name").getAsString();
                mcNames[i] = version.get("game_versions").getAsJsonArray().get(0).getAsString();
                JsonObject file = version.getAsJsonArray("files").get(0).getAsJsonObject();
                urls[i] = file.get("url").getAsString();
                JsonObject hashesObj = file.getAsJsonObject("hashes");
                hashes[i] = (hashesObj != null && hashesObj.has("sha1"))
                        ? hashesObj.get("sha1").getAsString() : null;
            }

            return new ModDetail(item, names, mcNames, urls, hashes);
        }

        @Override
        public ModLoader installModpack(ModDetail modDetail, int selectedVersion) throws IOException {
            String url = modDetail.versionUrls[selectedVersion];
            File contentDir = getContentDir();
            if (!contentDir.isDirectory() && !contentDir.mkdirs()) {
                throw new IOException("could not create content directory");
            }
            String fileName = url.substring(url.lastIndexOf('/') + 1);
            File destination = new File(contentDir, fileName);
            ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 0, R.string.global_waiting);
            DownloadUtils.downloadFile(url, destination);
            ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
            return null; // no mod loader is involved in installing a single mod/resourcepack/shaderpack
        }

        @Override
        public ModLoader installLocalModpack(String modpackName, File modpackFile, String icon) throws IOException {
            throw new IOException("Local install isn't supported here - use Manage Content's import instead.");
        }

        private File getContentDir() {
            Instance instance = Instances.loadSelectedInstance();
            File gameDir = instance != null ? instance.getGameDirectory() : new File(Tools.DIR_GAME_NEW);
            return new File(gameDir, mContentType.folderName);
        }
    }

    /** SearchResult that also tracks the Modrinth pagination offset. */
    private static class OffsetSearchResult extends SearchResult {
        int offset;
    }
}
