package com.kdt.mcgui;

import android.content.Context;
import android.util.AttributeSet;
import android.util.TypedValue;

import com.google.android.material.button.MaterialButton;

import git.artdeell.mojo.R;

/** Material 3 filled button, kept under its old name so existing layouts keep working. */
public class MineButton extends MaterialButton {

	public MineButton(Context ctx) {
		this(ctx, null);
	}

	public MineButton(Context ctx, AttributeSet attrs) {
		super(ctx, attrs);
		init();
	}

	public void init() {
		setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen._13ssp));
	}

}
