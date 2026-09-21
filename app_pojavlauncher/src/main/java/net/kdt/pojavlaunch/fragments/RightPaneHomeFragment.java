package net.kdt.pojavlaunch.fragments;

import android.graphics.drawable.AnimatedImageDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.kdt.mcgui.CenterCropVideoView;

import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.utils.LauncherBackground;

import java.io.File;

/**
 * Default content of the right pane of the two-pane landscape main menu.
 * It shows the custom background if there is one (an image, an animated GIF or a looping video)
 * with the Wiki and Discord buttons on top of it.
 */
public class RightPaneHomeFragment extends Fragment {
    public static final String TAG = "RightPaneHomeFragment";

    private ImageView mWallpaper;
    private CenterCropVideoView mVideo;
    /* Incremented to drop the result of a decode that is not needed anymore */
    private int mLoadId;

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

        mWallpaper = view.findViewById(R.id.right_pane_wallpaper);
        mVideo = view.findViewById(R.id.right_pane_wallpaper_video);
        mVideo.setErrorListener(this::clearBackground);
        loadBackground();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mVideo != null) mVideo.resumePlayback();
        setImageAnimation(true);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mVideo != null) mVideo.pausePlayback();
        setImageAnimation(false);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mLoadId++;
        if (mVideo != null) mVideo.release();
        setImageAnimation(false);
        mWallpaper = null;
        mVideo = null;
    }

    private void loadBackground() {
        int loadId = ++mLoadId;
        String kind = LauncherBackground.getKind(requireContext());
        if (kind == null) {
            clearBackground();
            return;
        }
        File file = LauncherBackground.getFile();
        if (LauncherBackground.KIND_VIDEO.equals(kind)) {
            mWallpaper.setImageDrawable(null);
            mWallpaper.setVisibility(View.GONE);
            mVideo.setVisibility(View.VISIBLE);
            mVideo.setVideoPath(file.getAbsolutePath());
            return;
        }
        mVideo.release();
        mVideo.setVisibility(View.GONE);
        // Decoding a big image or GIF takes a while, keep it off the UI thread
        PojavApplication.sExecutorService.execute(() -> {
            Drawable drawable = LauncherBackground.decodeImage(file);
            View root = getView();
            if (root == null) return;
            root.post(() -> {
                if (loadId != mLoadId || mWallpaper == null) return;
                if (drawable == null) {
                    clearBackground();
                    return;
                }
                mWallpaper.setImageDrawable(drawable);
                mWallpaper.setVisibility(View.VISIBLE);
                setImageAnimation(isResumed());
            });
        });
    }

    /** No background, or an unusable one: the plain pane shows */
    private void clearBackground() {
        if (mWallpaper == null || mVideo == null) return;
        setImageAnimation(false);
        mWallpaper.setImageDrawable(null);
        mWallpaper.setVisibility(View.GONE);
        mVideo.release();
        mVideo.setVisibility(View.GONE);
    }

    /** Animated images (GIF, animated WebP) are only played while the screen is shown */
    private void setImageAnimation(boolean play) {
        if (mWallpaper == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return;
        Drawable drawable = mWallpaper.getDrawable();
        if (!(drawable instanceof AnimatedImageDrawable)) return;
        AnimatedImageDrawable animated = (AnimatedImageDrawable) drawable;
        if (play) {
            animated.setRepeatCount(AnimatedImageDrawable.REPEAT_INFINITE);
            animated.start();
        } else {
            animated.stop();
        }
    }
}
