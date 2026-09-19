package net.kdt.pojavlaunch.utils;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;

import com.google.android.material.transition.MaterialSharedAxis;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import git.artdeell.mojo.R;

/**
 * Central place that decides which kinds of animations the launcher plays.
 * The user picks the kinds (or none) in the launcher appearance settings.
 * <ul>
 *     <li>{@link #TYPE_SCREENS}: activity and fragment transitions</li>
 *     <li>{@link #TYPE_DIALOGS}: dialogs and popups</li>
 *     <li>{@link #TYPE_BUTTONS}: button press effects</li>
 *     <li>{@link #TYPE_CONTENT}: lists and screen content entering</li>
 * </ul>
 */
public final class AnimationManager {
    public static final String PREF_TYPES = "animation_types";

    public static final String TYPE_NONE = "none";
    public static final String TYPE_SCREENS = "screens";
    public static final String TYPE_DIALOGS = "dialogs";
    public static final String TYPE_BUTTONS = "buttons";
    public static final String TYPE_CONTENT = "content";

    private static final Set<String> DEFAULT_TYPES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(TYPE_SCREENS, TYPE_DIALOGS, TYPE_BUTTONS, TYPE_CONTENT)));

    private AnimationManager() {}

    /** @return whether animations of this type should be played */
    public static boolean isEnabled(@NonNull Context context, @NonNull String type) {
        if (systemAnimationsDisabled(context)) return false;
        Set<String> types = PreferenceManager.getDefaultSharedPreferences(context)
                .getStringSet(PREF_TYPES, DEFAULT_TYPES);
        return types != null && !types.contains(TYPE_NONE) && types.contains(type);
    }

    /** Respect the "remove animations" accessibility setting */
    private static boolean systemAnimationsDisabled(Context context) {
        try {
            return Settings.Global.getFloat(context.getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Applies the animation related theme overlays and window animations to a not yet created activity.
     * Must be called before the content view is set.
     */
    public static void applyToActivity(@NonNull Activity activity) {
        Resources.Theme theme = activity.getTheme();
        if (isEnabled(activity, TYPE_DIALOGS)) {
            theme.applyStyle(R.style.ThemeOverlay_Copper_Animations_Dialogs, true);
        }
        if (isEnabled(activity, TYPE_BUTTONS)) {
            theme.applyStyle(R.style.ThemeOverlay_Copper_Animations_Buttons, true);
        }
        activity.getWindow().setWindowAnimations(isEnabled(activity, TYPE_SCREENS)
                ? R.style.Animation_Copper_Activity
                : R.style.Animation_Copper_ActivityNone);
    }

    /** Slide + fade fragment transitions for every fragment shown by this activity */
    public static void installFragmentTransitions(@NonNull FragmentActivity activity) {
        activity.getSupportFragmentManager().registerFragmentLifecycleCallbacks(
                new FragmentManager.FragmentLifecycleCallbacks() {
                    @Override
                    public void onFragmentPreCreated(@NonNull FragmentManager fm, @NonNull Fragment f,
                                                     @Nullable android.os.Bundle savedInstanceState) {
                        if (f instanceof DialogFragment) return;
                        if (!isEnabled(activity, TYPE_SCREENS)) return;
                        f.setEnterTransition(new MaterialSharedAxis(MaterialSharedAxis.X, true));
                        f.setExitTransition(new MaterialSharedAxis(MaterialSharedAxis.X, true));
                        f.setReenterTransition(new MaterialSharedAxis(MaterialSharedAxis.X, false));
                        f.setReturnTransition(new MaterialSharedAxis(MaterialSharedAxis.X, false));
                    }
                }, true);
    }

    /** Makes the items of a list enter one after the other the first time it is laid out */
    public static void applyListAnimation(@Nullable ViewGroup list) {
        if (list == null) return;
        Context context = list.getContext();
        if (isEnabled(context, TYPE_CONTENT)) {
            list.setLayoutAnimation(AnimationUtils.loadLayoutAnimation(context, R.anim.copper_layout_list));
        } else {
            list.setLayoutAnimation(null);
        }
    }

    /** Makes the direct children of a layout fade and slide in one after the other */
    public static void staggerIn(@Nullable ViewGroup parent) {
        if (parent == null || !isEnabled(parent.getContext(), TYPE_CONTENT)) return;
        float offset = 16 * parent.getResources().getDisplayMetrics().density;
        int shown = 0;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child.getVisibility() != View.VISIBLE) continue;
            child.setAlpha(0f);
            child.setTranslationY(offset);
            child.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(Math.min(shown, 12) * 35L)
                    .setDuration(280)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
            shown++;
        }
    }
}
