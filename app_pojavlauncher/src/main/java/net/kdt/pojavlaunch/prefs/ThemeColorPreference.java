package net.kdt.pojavlaunch.prefs;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.SeekBar;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.google.android.material.color.utilities.Hct;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import net.kdt.pojavlaunch.utils.ThemeManager;

import git.artdeell.mojo.R;

import java.util.Locale;

/**
 * Preference that lets the user pick any color to build the launcher theme from.
 * Works on every Android version, unlike wallpaper based dynamic color.
 */
public class ThemeColorPreference extends Preference {
    private static final int[] HUE_COLORS = {
            0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF, 0xFFFF0000
    };

    public ThemeColorPreference(@NonNull Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ThemeColorPreference(@NonNull Context context) {
        super(context);
        init();
    }

    private void init() {
        setWidgetLayoutResource(R.layout.preference_color_widget);
    }

    private int getColor() {
        return getPersistedInt(ThemeManager.DEFAULT_CUSTOM_COLOR);
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        View swatch = holder.findViewById(R.id.theme_color_swatch);
        if (swatch != null) {
            swatch.setBackgroundTintList(android.content.res.ColorStateList.valueOf(getColor()));
        }
    }

    @Override
    public CharSequence getSummary() {
        return String.format(Locale.ROOT, "#%06X", getColor() & 0xFFFFFF);
    }

    @Override
    protected void onClick() {
        showDialog();
    }

    private void save(int color) {
        persistInt(color | 0xFF000000);
        notifyChanged();
    }

    private static int hueToColor(int hue) {
        return Color.HSVToColor(new float[]{hue, 1f, 1f});
    }

    private void showDialog() {
        Context context = getContext();
        View content = LayoutInflater.from(context).inflate(R.layout.dialog_theme_color, null);
        View previewPrimary = content.findViewById(R.id.theme_color_preview_primary);
        View previewContainer = content.findViewById(R.id.theme_color_preview_container);
        View previewTertiary = content.findViewById(R.id.theme_color_preview_tertiary);
        SeekBar hueBar = content.findViewById(R.id.theme_color_hue);
        EditText hexEdit = content.findViewById(R.id.theme_color_hex);

        float density = context.getResources().getDisplayMetrics().density;
        GradientDrawable rainbow = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, HUE_COLORS);
        rainbow.setCornerRadius(14 * density);
        rainbow.setSize(-1, (int) (14 * density));
        hueBar.setBackground(new android.graphics.drawable.InsetDrawable(rainbow, (int) (14 * density), (int) (7 * density), (int) (14 * density), (int) (7 * density)));

        boolean dark = (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;

        final int[] selected = {getColor() | 0xFF000000};
        final boolean[] updating = {false};

        Runnable refreshPreview = () -> {
            double hue = ThemeManager.getHue(selected[0]);
            setRounded(previewPrimary, Hct.from(hue, 36, dark ? 80 : 40).toInt(), density);
            setRounded(previewContainer, Hct.from(hue, 36, dark ? 30 : 90).toInt(), density);
            setRounded(previewTertiary, Hct.from((hue + 60) % 360, 24, dark ? 80 : 40).toInt(), density);
        };

        float[] hsv = new float[3];
        Color.colorToHSV(selected[0], hsv);
        hueBar.setProgress(Math.min(359, Math.round(hsv[0])));
        hexEdit.setText(String.format(Locale.ROOT, "#%06X", selected[0] & 0xFFFFFF));
        refreshPreview.run();

        hueBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                selected[0] = hueToColor(progress);
                updating[0] = true;
                hexEdit.setText(String.format(Locale.ROOT, "#%06X", selected[0] & 0xFFFFFF));
                updating[0] = false;
                refreshPreview.run();
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        hexEdit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (updating[0]) return;
                String text = s.toString().replace("#", "");
                if (text.length() != 6) return;
                try {
                    int color = Color.parseColor("#" + text) | 0xFF000000;
                    selected[0] = color;
                    float[] parsed = new float[3];
                    Color.colorToHSV(color, parsed);
                    hueBar.setProgress(Math.min(359, Math.round(parsed[0])));
                    refreshPreview.run();
                } catch (IllegalArgumentException ignored) {}
            }
        });

        new MaterialAlertDialogBuilder(context)
                .setTitle(getTitle())
                .setView(content)
                .setPositiveButton(android.R.string.ok, (d, w) -> save(selected[0]))
                .setNegativeButton(android.R.string.cancel, null)
                .setNeutralButton(R.string.preference_theme_color_reset,
                        (d, w) -> save(ThemeManager.DEFAULT_CUSTOM_COLOR))
                .show();
    }

    private static void setRounded(View view, int color, float density) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(16 * density);
        view.setBackground(drawable);
    }
}
