package net.kdt.pojavlaunch.prefs.screens;

import static android.text.InputType.TYPE_CLASS_NUMBER;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;

import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.SwitchPreference;

import git.artdeell.mojo.R;

import java.util.Objects;

/** Extra settings screen for the MobileGlues (MG-ES) renderer, reachable from the video settings */
public class LauncherPreferenceRendererSettingsFragment extends LauncherPreferenceFragment {
    EditTextPreference GLSLCachePreference;
    ListPreference MultiDrawEmulationPreference;
    SwitchPreference ComputeMultiDrawPreference;
    androidx.preference.Preference.SummaryProvider MultiDrawSummaryProvider;

    @Override
    public void onCreatePreferences(Bundle b, String str) {
        addPreferencesFromResource(R.xml.pref_renderer);
        GLSLCachePreference = requirePreference("mg_renderer_setting_glsl_cache_size", EditTextPreference.class);
        ComputeMultiDrawPreference = requirePreference("mg_renderer_multidrawCompute", SwitchPreference.class);
        MultiDrawEmulationPreference = requirePreference("mg_renderer_setting_multidraw", ListPreference.class);
        GLSLCachePreference.setOnBindEditTextListener((editText) -> {
            editText.setInputType(TYPE_CLASS_NUMBER);
            editText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                    // Nothing, its boilerplate
                }

                @Override
                public void afterTextChanged(Editable editable) {
                    // Nothing, its boilerplate
                }

                @Override
                public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                    // This is just to handle the summary not updating when its above max int value
                    // Horrible I know.
                    if (editText.getText().toString().isEmpty()) {
                        editText.setText("0");
                    }
                    if (Long.parseLong(editText.getText().toString()) > Integer.MAX_VALUE) {
                        editText.setError("Too big! Setting to maximum value");
                        editText.setText(String.valueOf(Integer.MAX_VALUE));
                    }

                }
            });
        });
        updateGLSLCacheSummary(); // Just updates the summary with the value when user opens the menu. Yes it's out of place.
        updateMultiDrawSummary(); // Same as above
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences p, String s) {
        super.onSharedPreferenceChanged(p, s);
        GLSLCachePreference = requirePreference("mg_renderer_setting_glsl_cache_size", EditTextPreference.class);
        updateGLSLCacheSummary();
        updateMultiDrawSummary();
    }

    private void updateMultiDrawSummary() {
        if (MultiDrawEmulationPreference != null) {
            if (MultiDrawEmulationPreference.getSummaryProvider() != null) {
                MultiDrawSummaryProvider = MultiDrawEmulationPreference.getSummaryProvider();
            }
            if (ComputeMultiDrawPreference.isChecked()) {
                MultiDrawEmulationPreference.setEnabled(false);
                MultiDrawEmulationPreference.setSummaryProvider(null);
                MultiDrawEmulationPreference.setSummary("(Experimental) Compute");
            } else {
                MultiDrawEmulationPreference.setEnabled(true);
                MultiDrawEmulationPreference.setSummaryProvider(MultiDrawSummaryProvider);
            }
        }
    }

    private void updateGLSLCacheSummary() {
        try {
            if (Objects.equals(Objects.requireNonNull(this.GLSLCachePreference).getText(), "") || Integer.parseInt(Objects.requireNonNull(this.GLSLCachePreference.getText())) == 0) {
                this.GLSLCachePreference.setSummary(getString(R.string.global_off));
            } else this.GLSLCachePreference.setSummary(this.GLSLCachePreference.getText() + " MB");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
