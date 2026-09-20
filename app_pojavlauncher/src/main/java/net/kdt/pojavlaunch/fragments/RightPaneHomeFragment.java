package net.kdt.pojavlaunch.fragments;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.Tools;

/**
 * Default content of the right pane of the two-pane landscape main menu.
 */
public class RightPaneHomeFragment extends Fragment {
    public static final String TAG = "RightPaneHomeFragment";

    public RightPaneHomeFragment() {
        super(R.layout.fragment_right_pane_home);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        View newsButton = view.findViewById(R.id.news_button_pane);
        newsButton.setOnClickListener(v -> Tools.openURL(requireActivity(), Tools.URL_HOME));
        // Hidden feature, same as the wiki button of the portrait main menu
        newsButton.setOnLongClickListener(v -> {
            Tools.swapFragment(requireActivity(), GamepadMapperFragment.class, GamepadMapperFragment.TAG, null);
            return true;
        });
        view.findViewById(R.id.social_media_button_pane).setOnClickListener(
                v -> Tools.openURL(requireActivity(), getString(R.string.social_media_invite)));
    }
}
