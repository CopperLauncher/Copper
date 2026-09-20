package net.kdt.pojavlaunch.prefs.screens;


import net.kdt.pojavlaunch.prefs.BackButtonPreference;
import net.kdt.pojavlaunch.fragments.SettingsHostFragment;
import androidx.preference.PreferenceScreen;
import android.content.res.Configuration;
import android.content.pm.ActivityInfo;
import net.kdt.pojavlaunch.utils.AnimationManager;
import net.kdt.pojavlaunch.utils.ThemeColors;
import android.Manifest;
import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import net.kdt.pojavlaunch.LauncherActivity;
import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.ThemeManager;

/**
 * Preference for the main screen, any sub-screen should inherit this class for consistent behavior,
 * overriding only onCreatePreferences
 */
public class LauncherPreferenceFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {
    protected Runnable mVisibilityUpdater = () -> {};
    private boolean mRecreatePending;

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        // The list of categories in the left pane of the two-pane settings draws on the host background
        if(!isSettingsListPane()) view.setBackgroundColor(ThemeColors.surface(view.getContext()));
        super.onViewCreated(view, savedInstanceState);
        AnimationManager.applyListAnimation(getListView());
    }

    @Override
    public void onCreatePreferences(Bundle b, String str) {
        mVisibilityUpdater = this::updateVisibility;
        addPreferencesFromResource(R.xml.pref_main);
        // The right pane of the two-pane settings has its own back button
        if(isSettingsListPane()) removeBackButton();
        setupNotificationRequestPreference();
    }

    /** @return whether this is the list of categories inside the left pane of the two-pane settings */
    private boolean isSettingsListPane() {
        // Only the category list itself, not the categories that extend this fragment
        return getClass() == LauncherPreferenceFragment.class
                && getParentFragment() instanceof SettingsHostFragment
                && getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    private void removeBackButton() {
        PreferenceScreen screen = getPreferenceScreen();
        for(int i = 0; i < screen.getPreferenceCount(); i++) {
            Preference preference = screen.getPreference(i);
            if(preference instanceof BackButtonPreference) {
                screen.removePreference(preference);
                return;
            }
        }
    }

    private void updateVisibility(){
        requirePreference("notification_permission_request").setVisible(!getLauncherActivity().checkForPermission(33, Manifest.permission.POST_NOTIFICATIONS));
    }

    private void setupNotificationRequestPreference() {
        Preference mRequestNotificationPermissionPreference = requirePreference("notification_permission_request");
        Activity activity = getActivity();
        if(activity instanceof LauncherActivity) {
            mRequestNotificationPermissionPreference.setOnPreferenceClickListener(preference -> {
                ((LauncherActivity) activity).askForPermission(33, Manifest.permission.POST_NOTIFICATIONS);
                return true;
            });
        }else{
            mRequestNotificationPermissionPreference.setVisible(false);
        }
        updateVisibility();
    }

    @Override
    public void onResume() {
        super.onResume();
        SharedPreferences sharedPreferences = getPreferenceManager().getSharedPreferences();
        if(sharedPreferences != null) sharedPreferences.registerOnSharedPreferenceChangeListener(this);
        mVisibilityUpdater.run();
    }

    @Override
    public void onPause() {
        SharedPreferences sharedPreferences = getPreferenceManager().getSharedPreferences();
        if(sharedPreferences != null) sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String s) {
        LauncherPreferences.loadPreferences(getContext());
        if(ThemeManager.PREF_THEME_MODE.equals(s)) {
            ThemeManager.applyThemeMode(p.getString(s, "system"));
        }else if(ThemeManager.PREF_COLOR_SOURCE.equals(s) || ThemeManager.PREF_CUSTOM_COLOR.equals(s)
                || AnimationManager.PREF_TYPES.equals(s)) {
            scheduleRecreate();
        }else if(ThemeManager.PREF_FORCE_LANDSCAPE.equals(s)) {
            Activity activity = getActivity();
            if(activity == null) return;
            if(p.getBoolean(s, false)) {
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            }else if(activity instanceof LauncherActivity) {
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
            }
        }
    }

    /** Recreates the activity to apply a new theme, only once even if multiple keys changed together */
    private void scheduleRecreate() {
        Activity activity = getActivity();
        if(activity == null || mRecreatePending) return;
        mRecreatePending = true;
        activity.getWindow().getDecorView().post(activity::recreate);
    }

    protected Preference requirePreference(CharSequence key) {
        Preference preference = findPreference(key);
        if(preference != null) return preference;
        throw new IllegalStateException("Preference "+key+" is null");
    }
    @SuppressWarnings("unchecked")
    protected <T extends Preference> T requirePreference(CharSequence key, Class<T> preferenceClass) {
        Preference preference = requirePreference(key);
        if(preferenceClass.isInstance(preference)) return (T)preference;
        throw new IllegalStateException("Preference "+key+" is not an instance of "+preferenceClass.getSimpleName());
    }
    protected LauncherActivity getLauncherActivity(){
        return ((LauncherActivity) getActivity());
    }
}
