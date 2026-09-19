package net.kdt.pojavlaunch.utils;

import android.app.Application;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.DynamicColorsOptions;

/**
 * Applies the user's theme mode (system / light / dark) and Material You dynamic color choice.
 */
public final class ThemeManager {
    public static final String PREF_THEME_MODE = "theme_mode";
    public static final String PREF_DYNAMIC_COLOR = "dynamic_color";

    private ThemeManager() {}

    /** Call once from Application#onCreate */
    public static void init(Application application) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(application);
        applyThemeMode(preferences.getString(PREF_THEME_MODE, "system"));
        DynamicColors.applyToActivitiesIfAvailable(application,
                new DynamicColorsOptions.Builder()
                        .setPrecondition((activity, theme) -> PreferenceManager
                                .getDefaultSharedPreferences(activity)
                                .getBoolean(PREF_DYNAMIC_COLOR, true))
                        .build());
    }

    /** Sets the night mode. AppCompat recreates the visible activities when the mode changes. */
    public static void applyThemeMode(String mode) {
        int nightMode;
        if ("light".equals(mode)) nightMode = AppCompatDelegate.MODE_NIGHT_NO;
        else if ("dark".equals(mode)) nightMode = AppCompatDelegate.MODE_NIGHT_YES;
        else nightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        AppCompatDelegate.setDefaultNightMode(nightMode);
    }
}
