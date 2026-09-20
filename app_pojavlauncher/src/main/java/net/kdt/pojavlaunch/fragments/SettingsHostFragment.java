package net.kdt.pojavlaunch.fragments;

import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceFragment;
import net.kdt.pojavlaunch.prefs.screens.LauncherPreferenceVideoFragment;

/**
 * Two-pane landscape settings. The list of categories (pref_main) stays in the left pane,
 * and the category the user taps is shown in the right pane, the video settings by default.
 * <p>
 * It implements {@link PreferenceFragmentCompat.OnPreferenceStartFragmentCallback}, which the
 * preference fragments look up in their parent fragments first, to be the one opening the categories.
 */
public class SettingsHostFragment extends Fragment
        implements PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {
    public static final String TAG = "SettingsHostFragment";
    private static final String LEFT_PANE_TAG = "SETTINGS_LEFT_PANE";
    private static final String DEFAULT_RIGHT_PANE_TAG = "SETTINGS_RIGHT_PANE_DEFAULT";

    private OnBackPressedCallback mBackCallback;

    public SettingsHostFragment() {
        super(R.layout.fragment_settings_host);
    }

    private boolean isTwoPane() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBackCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                FragmentManager manager = getChildFragmentManager();
                if (manager.getBackStackEntryCount() > 0) manager.popBackStackImmediate();
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(this, mBackCallback);
        getChildFragmentManager().addOnBackStackChangedListener(
                () -> mBackCallback.setEnabled(getChildFragmentManager().getBackStackEntryCount() > 0));
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        FragmentManager manager = getChildFragmentManager();
        if (manager.findFragmentById(R.id.settings_left_pane_container) != null) return;
        // The default screen is not on the back stack, so leaving settings takes one back press
        manager.beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.settings_left_pane_container, LauncherPreferenceFragment.class, null, LEFT_PANE_TAG)
                .replace(R.id.settings_right_pane_container, LauncherPreferenceVideoFragment.class, null, DEFAULT_RIGHT_PANE_TAG)
                .commit();
    }

    @Override
    public boolean onPreferenceStartFragment(@NonNull PreferenceFragmentCompat caller,
                                             @NonNull Preference pref) {
        String fragmentClassName = pref.getFragment();
        if (fragmentClassName == null) return false;

        // Portrait has no right pane, the category takes the place of the list
        int container = isTwoPane() ? R.id.settings_right_pane_container : R.id.settings_left_pane_container;
        String tag = "SETTINGS_SCREEN:" + fragmentClassName;
        FragmentManager manager = getChildFragmentManager();
        int count = manager.getBackStackEntryCount();
        if (count > 0 && tag.equals(manager.getBackStackEntryAt(count - 1).getName())) return true;

        Fragment fragment = manager.getFragmentFactory().instantiate(
                requireContext().getClassLoader(), fragmentClassName);
        fragment.setArguments(pref.getExtras());
        manager.beginTransaction()
                .setReorderingAllowed(true)
                .replace(container, fragment, tag)
                .addToBackStack(tag)
                .commit();
        return true;
    }
}
