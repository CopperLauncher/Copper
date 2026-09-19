package net.kdt.pojavlaunch.utils;

import android.content.Context;
import android.graphics.Color;
import android.view.View;

import androidx.annotation.AttrRes;
import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import com.google.android.material.color.MaterialColors;

/**
 * Resolves Material 3 color roles from the current theme, so that custom views and Java code
 * follow both the light/dark mode and the Material You dynamic color palette.
 */
public final class ThemeColors {
    private ThemeColors() {}

    @ColorInt
    public static int get(@NonNull Context context, @AttrRes int attr) {
        return MaterialColors.getColor(context, attr, Color.MAGENTA);
    }

    @ColorInt
    public static int get(@NonNull View view, @AttrRes int attr) {
        return MaterialColors.getColor(view, attr, Color.MAGENTA);
    }

    @ColorInt
    public static int surface(@NonNull Context context) {
        return get(context, com.google.android.material.R.attr.colorSurface);
    }

    @ColorInt
    public static int surfaceContainer(@NonNull Context context) {
        return get(context, com.google.android.material.R.attr.colorSurfaceContainer);
    }

    @ColorInt
    public static int surfaceContainerHigh(@NonNull Context context) {
        return get(context, com.google.android.material.R.attr.colorSurfaceContainerHigh);
    }

    @ColorInt
    public static int primary(@NonNull Context context) {
        return get(context, androidx.appcompat.R.attr.colorPrimary);
    }

    @ColorInt
    public static int error(@NonNull Context context) {
        return get(context, androidx.appcompat.R.attr.colorError);
    }

    @ColorInt
    public static int onSurface(@NonNull Context context) {
        return get(context, com.google.android.material.R.attr.colorOnSurface);
    }
}
