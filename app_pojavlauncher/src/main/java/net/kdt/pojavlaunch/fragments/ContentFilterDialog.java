package net.kdt.pojavlaunch.fragments;

import android.app.Dialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import git.artdeell.mojo.R;

import net.kdt.pojavlaunch.instances.Instances;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.profiles.VersionSelectorDialog;

/**
 * Lets the user pick a Minecraft version to filter "Manage Content" update
 * checks and "Browse Content" searches by, for the currently selected
 * instance. Saved per-instance under the "mod_filters" preferences file that
 * ManageModsFragment reads from.
 *
 * Ported and simplified from Copper-Android's ContentFilterDialog: that
 * version also let the user pick a mod loader (fabric/forge/quilt/neoforge).
 * Mojo's existing dialog_mod_filters.xml (reused here — it already backs the
 * app's own modpack-search filter dialog) only has a Minecraft-version
 * picker, so the loader filter was left out rather than adding a second
 * layout just for this screen.
 */
public class ContentFilterDialog extends DialogFragment {

    private static final String PREF_FILE      = "mod_filters";
    private static final String KEY_MC_VERSION = "mc_version_";

    private String mSelectedVersion = "";

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_mod_filters, null, false);

        TextView versionText = view.findViewById(R.id.search_mod_selected_mc_version_textview);
        Button versionButton = view.findViewById(R.id.search_mod_mc_version_button);
        Button applyButton   = view.findViewById(R.id.search_mod_apply_filters);

        String instanceKey = LauncherPreferences.DEFAULT_PREF
                .getString(LauncherPreferences.PREF_KEY_CURRENT_INSTANCE, "default");
        SharedPreferences prefs = requireContext()
                .getSharedPreferences(PREF_FILE, android.content.Context.MODE_PRIVATE);
        mSelectedVersion = prefs.getString(KEY_MC_VERSION + instanceKey, "");

        if (mSelectedVersion.isEmpty()) {
            InstanceVersionResolver.Info info =
                    InstanceVersionResolver.resolve(Instances.loadSelectedInstance());
            if (info.mcVersion != null) mSelectedVersion = info.mcVersion;
        }
        versionText.setText(mSelectedVersion);

        versionButton.setOnClickListener(v -> VersionSelectorDialog.open(requireContext(), true,
                (version, isSnapshot) -> {
                    mSelectedVersion = version;
                    versionText.setText(version);
                }));

        applyButton.setOnClickListener(v -> {
            prefs.edit().putString(KEY_MC_VERSION + instanceKey, mSelectedVersion).apply();
            dismiss();
        });

        return new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setView(view)
                .create();
    }
}
