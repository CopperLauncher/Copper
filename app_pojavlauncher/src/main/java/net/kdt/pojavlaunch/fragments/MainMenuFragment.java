package net.kdt.pojavlaunch.fragments;

import net.kdt.pojavlaunch.utils.AnimationManager;
import static net.kdt.pojavlaunch.Tools.openPath;
import static net.kdt.pojavlaunch.Tools.shareLog;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.kdt.mcgui.mcVersionSpinner;

import net.kdt.pojavlaunch.CustomControlsActivity;
import git.artdeell.mojo.R;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.contracts.OpenDocumentWithExtension;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.instances.Instance;
import net.kdt.pojavlaunch.instances.Instances;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.utils.FileUtils;

import java.io.File;

/**
 * The main menu. In portrait it is a single column and every screen replaces it.
 * In landscape it is a two-pane layout: the actions stay in the left sidebar and every screen
 * opened with {@link Tools#swapFragment} shows up in the right pane, which has its own back stack.
 */
public class MainMenuFragment extends Fragment {
    public static final String TAG = "MainMenuFragment";

    private mcVersionSpinner mVersionSpinner;
    /* The two-pane views, null in portrait */
    private FrameLayout mRightPane;
    private View mBottomBar;
    /* Intercepts back when the right pane shows something above the home screen */
    private OnBackPressedCallback mRightPaneBackCallback;

    private final ActivityResultLauncher<Object> mModInstallerLauncher =
            registerForActivityResult(new OpenDocumentWithExtension("jar"), (data)->{
                if(data != null) Tools.launchModInstaller(requireContext(), data);
            });

    private final FragmentManager.OnBackStackChangedListener mBackStackListener = () -> {
        mRightPaneBackCallback.setEnabled(isRightPaneActive());
        updateBottomBar();
    };

    public MainMenuFragment(){
        super(R.layout.fragment_launcher);
    }

    // ─── Two-pane helpers ────────────────────────────────────────────────────

    /** @return whether the two-pane landscape layout is active */
    public boolean isTwoPane() {
        return mRightPane != null;
    }

    /** @return whether the right pane shows a screen above its home screen */
    public boolean isRightPaneActive() {
        return isTwoPane() && getChildFragmentManager().getBackStackEntryCount() > 0;
    }

    /**
     * Opens a screen in the right pane. Does nothing in portrait.
     * @return whether the screen was opened, false means the caller has to show it another way
     */
    public boolean openInPane(@NonNull Class<? extends Fragment> fragmentClass,
                              @Nullable String tag, @Nullable Bundle args) {
        if (!isTwoPane()) return false;
        String entryName = tag != null ? tag : fragmentClass.getName();
        FragmentManager manager = getChildFragmentManager();
        int count = manager.getBackStackEntryCount();
        // Already showing it, ignore the double tap
        if (count > 0 && entryName.equals(manager.getBackStackEntryAt(count - 1).getName())) return true;
        manager.beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.right_pane_container, fragmentClass, args, tag)
                .addToBackStack(entryName)
                .commit();
        return true;
    }

    /** Pops one screen off the right pane */
    public void popRightPane() {
        if (isRightPaneActive()) getChildFragmentManager().popBackStack();
    }

    /** Pops every screen off the right pane, so its home screen shows again */
    public void clearRightPane() {
        FragmentManager manager = getChildFragmentManager();
        if (manager.getBackStackEntryCount() == 0) return;
        manager.popBackStack(manager.getBackStackEntryAt(0).getId(), FragmentManager.POP_BACK_STACK_INCLUSIVE);
    }

    /** The bottom bar (instance and play) only belongs to the home screen, the other screens get the full height */
    private void updateBottomBar() {
        if (mBottomBar == null) return;
        boolean atHome = getChildFragmentManager().getBackStackEntryCount() == 0;
        mBottomBar.setVisibility(atHome ? View.VISIBLE : View.GONE);
    }

    // ─── Lifecycle ───────────────────────────────────────────────────────────

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // The owner is this fragment, so the callback is removed with it
        mRightPaneBackCallback = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                if (isRightPaneActive()) getChildFragmentManager().popBackStackImmediate();
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(this, mRightPaneBackCallback);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        // Portrait only
        Button mNewsButton = view.findViewById(R.id.news_button);
        Button mDiscordButton = view.findViewById(R.id.social_media_button);
        // Both
        Button mCustomControlButton = view.findViewById(R.id.custom_control_button);
        Button mInstallJarButton = view.findViewById(R.id.install_jar_button);
        Button mShareLogsButton = view.findViewById(R.id.share_logs_button);
        Button mOpenDirectoryButton = view.findViewById(R.id.open_files_button);

        ImageButton mEditProfileButton = view.findViewById(R.id.edit_profile_button);
        Button mPlayButton = view.findViewById(R.id.play_button);
        mVersionSpinner = view.findViewById(R.id.mc_version_spinner);

        mRightPane = view.findViewById(R.id.right_pane_container);
        mBottomBar = view.findViewById(R.id.bottom_bar);
        getChildFragmentManager().addOnBackStackChangedListener(mBackStackListener);

        FragmentManager childManager = getChildFragmentManager();
        if (isTwoPane()) {
            // Checked by presence and not by savedInstanceState, so a rotation keeps working
            if (childManager.findFragmentById(R.id.right_pane_container) == null) {
                childManager.beginTransaction()
                        .setReorderingAllowed(true)
                        // Not on the back stack, the home screen is the base and not a destination
                        .replace(R.id.right_pane_container, RightPaneHomeFragment.class, null,
                                RightPaneHomeFragment.TAG)
                        .commit();
            }
            mBackStackListener.onBackStackChanged();
        } else if (childManager.getBackStackEntryCount() > 0) {
            // The pane the screens were in is gone after rotating to portrait
            childManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        }

        ViewGroup staggerParent = isTwoPane()
                ? view.findViewById(R.id.left_sidebar_content)
                : (mNewsButton != null ? (ViewGroup) mNewsButton.getParent() : null);
        AnimationManager.staggerIn(staggerParent);

        if (mNewsButton != null) {
            mNewsButton.setOnClickListener(v -> Tools.openURL(requireActivity(), Tools.URL_HOME));
            mNewsButton.setOnLongClickListener((v)->{
                Tools.swapFragment(requireActivity(), GamepadMapperFragment.class, GamepadMapperFragment.TAG, null);
                return true;
            });
        }
        if (mDiscordButton != null)
            mDiscordButton.setOnClickListener(v -> Tools.openURL(requireActivity(), getString(R.string.social_media_invite)));

        mCustomControlButton.setOnClickListener(v -> startActivity(new Intent(requireContext(), CustomControlsActivity.class)));
        mInstallJarButton.setOnClickListener(v -> runInstallerWithConfirmation());
        mEditProfileButton.setOnClickListener(v -> mVersionSpinner.openProfileEditor(requireActivity()));

        mPlayButton.setOnClickListener(v -> ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true));

        mShareLogsButton.setOnClickListener((v) -> shareLog(requireContext()));

        mOpenDirectoryButton.setOnClickListener((v)-> openGameDirectory(v.getContext()));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        getChildFragmentManager().removeOnBackStackChangedListener(mBackStackListener);
        mRightPane = null;
        mBottomBar = null;
        mVersionSpinner = null;
    }

    private void openGameDirectory(Context context) {
        Instance instance = Instances.loadSelectedInstance();
        if(instance == null) {
            Toast.makeText(context, R.string.no_instance, Toast.LENGTH_LONG).show();
            return;
        }
        File gameDirectory = instance.getGameDirectory();
        if(FileUtils.ensureDirectorySilently(gameDirectory)) {
            openPath(context, gameDirectory, false);
        }else {
            Toast.makeText(context, R.string.gamedir_open_failed, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        ExtraCore.setValue(ExtraConstants.REFRESH_ACCOUNT_SPINNER, true);
        // Runs after the task listeners, which could have changed the bar
        if (mBottomBar != null) mBottomBar.post(this::updateBottomBar);
    }

    private void runInstallerWithConfirmation() {
        if (ProgressKeeper.getTaskCount() == 0) {
            mModInstallerLauncher.launch(null);
        } else Toast.makeText(requireContext(), R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
    }
}
