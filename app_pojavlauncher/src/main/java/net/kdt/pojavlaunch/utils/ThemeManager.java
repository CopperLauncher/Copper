package net.kdt.pojavlaunch.utils;

import android.app.Activity;
import android.app.Application;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.utilities.Hct;

import git.artdeell.mojo.R;

/**
 * Applies the user's theme mode (system / light / dark) and color source:
 * <ul>
 *     <li>dynamic: Material You wallpaper colors, Android 12+ only</li>
 *     <li>default: the static Copper palette</li>
 *     <li>custom: a Material 3 palette generated from a user picked color, works on every Android version</li>
 * </ul>
 * The custom palettes are pre-generated theme overlays, one for each 10 degrees of HCT hue.
 * Material's tonal spot scheme only depends on the hue of its source color, so picking the closest
 * overlay gives the same result as generating the scheme at runtime, within a 5 degree hue error.
 */
public final class ThemeManager {
    public static final String PREF_THEME_MODE = "theme_mode";
    public static final String PREF_COLOR_SOURCE = "color_source";
    public static final String PREF_CUSTOM_COLOR = "theme_custom_color";
    public static final String PREF_FORCE_LANDSCAPE = "force_landscape";

    public static final String SOURCE_DYNAMIC = "dynamic";
    public static final String SOURCE_DEFAULT = "default";
    public static final String SOURCE_CUSTOM = "custom";

    public static final int DEFAULT_CUSTOM_COLOR = 0xFFB87333;

    private static final int HUE_STEP = 10;

    private ThemeManager() {}

    /** Call once from Application#onCreate */
    public static void init(Application application) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(application);
        applyThemeMode(preferences.getString(PREF_THEME_MODE, "system"));
        application.registerActivityLifecycleCallbacks(new ColorCallbacks());
    }

    /** Sets the night mode. AppCompat recreates the visible activities when the mode changes. */
    public static void applyThemeMode(String mode) {
        int nightMode;
        if ("light".equals(mode)) nightMode = AppCompatDelegate.MODE_NIGHT_NO;
        else if ("dark".equals(mode)) nightMode = AppCompatDelegate.MODE_NIGHT_YES;
        else nightMode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        AppCompatDelegate.setDefaultNightMode(nightMode);
    }

    /** @return the HCT hue (0-360) of the color, this is what the generated palette is based on */
    public static double getHue(int color) {
        return Hct.fromInt(color).getHue();
    }

    /** Applies the color source selected in the preferences onto the activity theme */
    public static void applyColors(@NonNull Activity activity) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        String source = preferences.getString(PREF_COLOR_SOURCE, SOURCE_DYNAMIC);
        if (SOURCE_DYNAMIC.equals(source)) {
            if (DynamicColors.isDynamicColorAvailable()) {
                DynamicColors.applyToActivityIfAvailable(activity);
            }
        } else if (SOURCE_CUSTOM.equals(source)) {
            int color = preferences.getInt(PREF_CUSTOM_COLOR, DEFAULT_CUSTOM_COLOR);
            int overlay = getOverlayForColor(activity, color);
            if (overlay != 0) activity.getTheme().applyStyle(overlay, true);
        }
    }

    /** Locks the activity to landscape when the user asked for it, otherwise leaves it alone */
    public static void applyForcedOrientation(@NonNull Activity activity) {
        boolean force = PreferenceManager.getDefaultSharedPreferences(activity)
                .getBoolean(PREF_FORCE_LANDSCAPE, false);
        if (force) activity.setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
    }

    private static int getOverlayForColor(Activity activity, int color) {
        int count = 360 / HUE_STEP;
        int index = (int) Math.round(getHue(color) / HUE_STEP) % count;
        TypedArray overlays = activity.getResources().obtainTypedArray(R.array.theme_hue_overlays);
        try {
            return overlays.getResourceId(index, 0);
        } finally {
            overlays.recycle();
        }
    }

    private static class ColorCallbacks implements Application.ActivityLifecycleCallbacks {
        @Override
        public void onActivityPreCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
            applyColors(activity);
            AnimationManager.applyToActivity(activity);
        }

        @Override
        public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
            // Before Android 10 there is no pre-created callback, but this still runs before setContentView.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                applyColors(activity);
                AnimationManager.applyToActivity(activity);
            }
        }

        @Override public void onActivityStarted(@NonNull Activity activity) {}
        @Override public void onActivityResumed(@NonNull Activity activity) {}
        @Override public void onActivityPaused(@NonNull Activity activity) {}
        @Override public void onActivityStopped(@NonNull Activity activity) {}
        @Override public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}
        @Override public void onActivityDestroyed(@NonNull Activity activity) {}
    }
}
