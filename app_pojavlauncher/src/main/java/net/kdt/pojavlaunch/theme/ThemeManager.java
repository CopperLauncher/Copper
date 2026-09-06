package net.kdt.pojavlaunch.theme;

import androidx.annotation.NonNull;
import androidx.annotation.StyleRes;

import git.artdeell.mojo.R;

import net.kdt.pojavlaunch.prefs.LauncherPreferences;

/**
 * Ported from CopperLauncher/Copper-Android (net.kdt.pojavlaunch.theme.ThemeManager).
 *
 * Note: the upstream Amethyst-based ThemeManager also supported deriving a preset
 * automatically from a user-picked custom background image (via the Palette API,
 * hooked into RightPaneHomeFragment's custom background feature). Mojo's home
 * screen (MainMenuFragment) has no background-image layer to hook into, so that
 * piece was intentionally left out of this port - everything else (preset
 * selection, the gradient toggle, and applying the theme to Activities/preference
 * screens) is ported as-is.
 */
public class ThemeManager {

    private static final String KEY_THEME    = "launcher_theme";
    public  static final String KEY_GRADIENT = "enable_bg_gradient";

    public static final Preset[] PRESETS = {
        new Preset("Default (Copper)",  R.style.AppTheme,              R.style.AppTheme_Gradient),
        new Preset("Midnight Blue",     R.style.AppTheme_MidnightBlue, R.style.AppTheme_MidnightBlue_Gradient),
        new Preset("Forest Green",      R.style.AppTheme_ForestGreen,  R.style.AppTheme_ForestGreen_Gradient),
        new Preset("Crimson",           R.style.AppTheme_Crimson,      R.style.AppTheme_Crimson_Gradient),
        new Preset("Amethyst",          R.style.AppTheme_Amethyst,     R.style.AppTheme_Amethyst_Gradient),
        new Preset("Arctic",            R.style.AppTheme_Arctic,       R.style.AppTheme_Arctic_Gradient),
    };

    public static void applyPreset(@NonNull Preset preset) {
        LauncherPreferences.DEFAULT_PREF.edit()
            .putInt(KEY_THEME, preset.styleRes)
            .apply();
    }

    public static void resetToDefault() {
        applyPreset(PRESETS[0]);
    }

    /**
     * Apply the current theme's bgMainDrawable to a preference fragment's root view.
     * Called from LauncherPreferenceFragment.onViewCreated().
     */
    public static void applyToPrefView(@NonNull android.view.View view) {
        android.util.TypedValue tv = new android.util.TypedValue();
        view.getContext().getTheme().resolveAttribute(R.attr.bgMainDrawable, tv, true);
        if (tv.type >= android.util.TypedValue.TYPE_FIRST_COLOR_INT
                && tv.type <= android.util.TypedValue.TYPE_LAST_COLOR_INT) {
            view.setBackgroundColor(tv.data);
        } else if (tv.resourceId != 0) {
            view.setBackgroundResource(tv.resourceId);
        }
    }

    /**
     * Call in Activity.onCreate() BEFORE setContentView().
     * Returns the flat or gradient style depending on the gradient toggle.
     */
    @StyleRes
    public static int getSavedTheme() {
        int base = LauncherPreferences.DEFAULT_PREF.getInt(KEY_THEME, R.style.AppTheme);
        boolean gradient = LauncherPreferences.DEFAULT_PREF.getBoolean(KEY_GRADIENT, false);

        // Normalise: always find the matching flat preset style
        // (guards against an old gradient style ID being stored in prefs)
        Preset matched = PRESETS[0];
        for (Preset p : PRESETS) {
            if (p.styleRes == base || p.gradientStyleRes == base) {
                matched = p;
                break;
            }
        }

        return gradient ? matched.gradientStyleRes : matched.styleRes;
    }

    public static final class Preset {
        public final String name;
        public final int styleRes;
        public final int gradientStyleRes;
        public Preset(String name, int styleRes, int gradientStyleRes) {
            this.name             = name;
            this.styleRes         = styleRes;
            this.gradientStyleRes = gradientStyleRes;
        }
    }
}
