package net.kdt.pojavlaunch.fragments;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import git.artdeell.mojo.R;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ContentType;

/**
 * "Browse Content" / "Manage Content" entry screen: lets the user pick which
 * kind of content (mods, resource packs, shader packs) they want to work
 * with, plus a filter button, then opens the corresponding screen.
 *
 * Ported from Copper-Android's ContentPickerFragment. Adapted from
 * Amethyst's two-pane layout (this fragment lived in the left pane, opening
 * the chosen screen into a right pane docked alongside it inside
 * MainMenuFragment) to Mojo's single-pane {@code container_fragment} + back
 * stack — Mojo's MainMenuFragment has no split-pane layout to dock into, so
 * this screen is opened full-screen and simply swaps to the next one.
 */
public class ContentPickerFragment extends Fragment {

    public static final String TAG = "ContentPickerFragment";

    /** Bundle key: "browse" or "manage" — which destination screen to open per content type. */
    public static final String ARG_MODE = "mode";
    public static final String MODE_BROWSE = "browse";
    public static final String MODE_MANAGE = "manage";

    public ContentPickerFragment() {
        super(R.layout.fragment_content_picker);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Bundle args = getArguments();
        String mode = args != null ? args.getString(ARG_MODE, MODE_BROWSE) : MODE_BROWSE;

        ImageButton backButton = view.findViewById(R.id.content_picker_back);
        backButton.setOnClickListener(v -> requireActivity().onBackPressed());

        view.findViewById(R.id.content_picker_mods)
                .setOnClickListener(v -> openDestination(mode, ContentType.MOD));
        view.findViewById(R.id.content_picker_resourcepacks)
                .setOnClickListener(v -> openDestination(mode, ContentType.RESOURCE_PACK));
        view.findViewById(R.id.content_picker_shaderpacks)
                .setOnClickListener(v -> openDestination(mode, ContentType.SHADER_PACK));

        view.findViewById(R.id.content_picker_filters).setOnClickListener(v ->
                new ContentFilterDialog().show(getParentFragmentManager(), "content_filters"));
    }

    private void openDestination(String mode, ContentType contentType) {
        Bundle args = new Bundle();
        args.putString(ManageModsFragment.ARG_CONTENT_TYPE, contentType.name());

        if (MODE_MANAGE.equals(mode)) {
            Tools.swapFragment(requireActivity(), ManageModsFragment.class,
                    ManageModsFragment.TAG + ":" + contentType.name(), args);
        } else {
            args.putString(ModsSearchFragment.ARG_CONTENT_TYPE, contentType.name());
            Tools.swapFragment(requireActivity(), ModsSearchFragment.class,
                    ModsSearchFragment.TAG + ":" + contentType.name(), args);
        }
    }
}
