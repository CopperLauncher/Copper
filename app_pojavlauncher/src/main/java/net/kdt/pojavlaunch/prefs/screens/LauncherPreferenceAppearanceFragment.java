package net.kdt.pojavlaunch.prefs.screens;

import android.app.AlertDialog;
import android.os.Bundle;

import androidx.preference.SwitchPreferenceCompat;

import git.artdeell.mojo.R;

import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.theme.ThemeManager;

/**
 * "Launcher appearance" settings screen, ported from Copper-Android's
 * LauncherPreferenceAppearanceFragment. Only the theming half of the upstream
 * screen is ported (colour presets + gradient toggle) - force-landscape and
 * the custom background picker were left out of scope for this port.
 */
public class LauncherPreferenceAppearanceFragment extends LauncherPreferenceFragment {

    @Override
    public void onCreatePreferences(Bundle b, String str) {
        addPreferencesFromResource(R.xml.pref_launcher_appearance);
        setupColourTheme();
    }

    // ── Colour theme ──────────────────────────────────────────────────────────

    private void setupColourTheme() {
        requirePreference("colour_theme_presets", androidx.preference.Preference.class)
                .setOnPreferenceClickListener(p -> {
                    showPresetDialog();
                    return true;
                });

        SwitchPreferenceCompat gradientPref =
                requirePreference("enable_bg_gradient", SwitchPreferenceCompat.class);
        gradientPref.setOnPreferenceChangeListener((preference, newValue) -> {
            // Save explicitly before recreate — the framework saves after listener returns
            LauncherPreferences.DEFAULT_PREF.edit()
                .putBoolean(ThemeManager.KEY_GRADIENT, Boolean.TRUE.equals(newValue))
                .commit(); // commit() not apply() — must be synchronous before recreate
            requireActivity().recreate();
            return true;
        });

        requirePreference("colour_theme_reset", androidx.preference.Preference.class)
                .setOnPreferenceClickListener(p -> {
                    ThemeManager.resetToDefault();
                    requireActivity().recreate();
                    return true;
                });
    }

    private void showPresetDialog() {
        ThemeManager.Preset[] presets = ThemeManager.PRESETS;
        String[] labels = new String[presets.length];
        for (int i = 0; i < presets.length; i++) labels[i] = presets[i].name;

        new AlertDialog.Builder(requireContext())
            .setTitle(R.string.preference_colour_presets_title)
            .setItems(labels, (dialog, which) -> {
                ThemeManager.applyPreset(presets[which]);
                requireActivity().recreate();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }
}
