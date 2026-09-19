package net.kdt.pojavlaunch.prefs.screens;

import android.os.Bundle;

import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;

import net.kdt.pojavlaunch.utils.AnimationManager;
import net.kdt.pojavlaunch.utils.ThemeManager;

import java.util.HashSet;
import java.util.Set;

import git.artdeell.mojo.R;

/**
 * Theme, color, orientation and animation settings of the launcher.
 */
public class LauncherPreferenceAppearanceFragment extends LauncherPreferenceFragment {

    @Override
    public void onCreatePreferences(Bundle b, String str) {
        addPreferencesFromResource(R.xml.pref_appearance);
        setupCustomColorVisibility();
        setupAnimationPreference();
    }

    /** The custom color is only relevant, and only shown, when the custom color source is selected */
    private void setupCustomColorVisibility() {
        ListPreference sourcePreference = requirePreference(ThemeManager.PREF_COLOR_SOURCE, ListPreference.class);
        Preference customColorPreference = requirePreference(ThemeManager.PREF_CUSTOM_COLOR);
        customColorPreference.setVisible(ThemeManager.SOURCE_CUSTOM.equals(sourcePreference.getValue()));
        sourcePreference.setOnPreferenceChangeListener((preference, newValue) -> {
            customColorPreference.setVisible(ThemeManager.SOURCE_CUSTOM.equals(newValue));
            return true;
        });
    }

    private void setupAnimationPreference() {
        MultiSelectListPreference animationPreference =
                requirePreference(AnimationManager.PREF_TYPES, MultiSelectListPreference.class);

        // "None" excludes every other type: picking it clears the rest, picking anything else clears it
        animationPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            @SuppressWarnings("unchecked")
            Set<String> selected = new HashSet<>((Set<String>) newValue);
            if (selected.contains(AnimationManager.TYPE_NONE) && selected.size() > 1) {
                Set<String> previous = ((MultiSelectListPreference) preference).getValues();
                if (previous.contains(AnimationManager.TYPE_NONE)) {
                    selected.remove(AnimationManager.TYPE_NONE);
                } else {
                    selected.clear();
                    selected.add(AnimationManager.TYPE_NONE);
                }
                ((MultiSelectListPreference) preference).setValues(selected);
                return false;
            }
            return true;
        });

        animationPreference.setSummaryProvider((Preference.SummaryProvider<MultiSelectListPreference>) preference -> {
            Set<String> values = preference.getValues();
            CharSequence[] entries = preference.getEntries();
            CharSequence[] entryValues = preference.getEntryValues();
            if (values.isEmpty() || values.contains(AnimationManager.TYPE_NONE)) return entries[0];
            StringBuilder summary = new StringBuilder();
            for (int i = 0; i < entryValues.length; i++) {
                if (!values.contains(entryValues[i].toString())) continue;
                if (summary.length() > 0) summary.append(", ");
                summary.append(entries[i]);
            }
            return summary.toString();
        });
    }
}
