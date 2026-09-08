package com.kdt.mcgui;

import android.content.*;
import android.graphics.*;
import android.util.*;
import android.view.*;
import android.view.animation.*;

import androidx.core.content.res.ResourcesCompat;

import git.artdeell.mojo.R;

/**
 * Ported from Copper-Android's MineButton: rounded, theme-accent-colored
 * background (mine_button_background.xml resolves ?attr/copperAccent, so
 * this button now reacts to the colour theme presets from ThemeManager)
 * with a small press-scale animation, replacing Mojo's static pixel-art
 * 9-patch button background.
 */
public class MineButton extends androidx.appcompat.widget.AppCompatButton {

	public MineButton(Context ctx) {
		this(ctx, null);
	}

	public MineButton(Context ctx, AttributeSet attrs) {
		super(ctx, attrs);
		init();
	}

	public void init() {
		setTypeface(ResourcesCompat.getFont(getContext(), R.font.noto_sans_bold));
		setBackground(ResourcesCompat.getDrawable(getResources(),
				R.drawable.mine_button_background, getContext().getTheme()));
		setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen._13ssp));
		setTextColor(Color.WHITE);

		setOnTouchListener((v, event) -> {
			if (event.getAction() == MotionEvent.ACTION_DOWN) {
				animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).start();
			} else if (event.getAction() == MotionEvent.ACTION_UP
					|| event.getAction() == MotionEvent.ACTION_CANCEL) {
				animate().scaleX(1f).scaleY(1f).setDuration(100).start();
			}
			return false;
		});
	}

}
