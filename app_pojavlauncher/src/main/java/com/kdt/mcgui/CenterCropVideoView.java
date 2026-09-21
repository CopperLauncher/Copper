package com.kdt.mcgui;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.util.AttributeSet;
import android.view.Surface;
import android.view.TextureView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Plays a video file looped and muted, scaled like ImageView's center crop so that it fills the view.
 * It is a TextureView and not a SurfaceView, so that it follows the rounded corners of its parent.
 */
public class CenterCropVideoView extends TextureView implements TextureView.SurfaceTextureListener {
    public interface ErrorListener {
        void onVideoError();
    }

    private MediaPlayer mPlayer;
    private Surface mSurface;
    private String mPath;
    private boolean mPrepared;
    private boolean mShouldPlay = true;
    private int mVideoWidth, mVideoHeight;
    private ErrorListener mErrorListener;

    public CenterCropVideoView(Context context) {
        this(context, null);
    }

    public CenterCropVideoView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setSurfaceTextureListener(this);
    }

    public void setErrorListener(@Nullable ErrorListener listener) {
        mErrorListener = listener;
    }

    /** Starts playing the video, replacing the current one */
    public void setVideoPath(@NonNull String path) {
        mPath = path;
        releasePlayer();
        if (isAvailable()) startPlayer(getSurfaceTexture());
    }

    /** Stops the playback and frees the player. */
    public void release() {
        mPath = null;
        releasePlayer();
    }

    public void pausePlayback() {
        mShouldPlay = false;
        if (mPlayer != null && mPrepared && mPlayer.isPlaying()) mPlayer.pause();
    }

    public void resumePlayback() {
        mShouldPlay = true;
        if (mPlayer != null && mPrepared && !mPlayer.isPlaying()) mPlayer.start();
    }

    private void startPlayer(SurfaceTexture texture) {
        if (mPath == null || texture == null) return;
        releasePlayer();
        try {
            mSurface = new Surface(texture);
            mPlayer = new MediaPlayer();
            mPlayer.setDataSource(mPath);
            mPlayer.setSurface(mSurface);
            mPlayer.setLooping(true);
            mPlayer.setVolume(0f, 0f);
            mPlayer.setOnPreparedListener(player -> {
                mPrepared = true;
                mVideoWidth = player.getVideoWidth();
                mVideoHeight = player.getVideoHeight();
                updateCropTransform();
                if (mShouldPlay) player.start();
            });
            mPlayer.setOnErrorListener((player, what, extra) -> {
                releasePlayer();
                if (mErrorListener != null) mErrorListener.onVideoError();
                return true;
            });
            mPlayer.prepareAsync();
        } catch (Exception e) {
            releasePlayer();
            if (mErrorListener != null) mErrorListener.onVideoError();
        }
    }

    private void releasePlayer() {
        mPrepared = false;
        if (mPlayer != null) {
            try {
                mPlayer.release();
            } catch (Exception ignored) {}
            mPlayer = null;
        }
        if (mSurface != null) {
            mSurface.release();
            mSurface = null;
        }
    }

    /** A TextureView stretches the video to the view, this scales it back to its aspect ratio and crops the overflow */
    private void updateCropTransform() {
        int viewWidth = getWidth(), viewHeight = getHeight();
        if (mVideoWidth <= 0 || mVideoHeight <= 0 || viewWidth <= 0 || viewHeight <= 0) return;
        float viewRatio = (float) viewWidth / viewHeight;
        float videoRatio = (float) mVideoWidth / mVideoHeight;
        float scaleX = 1f, scaleY = 1f;
        if (videoRatio > viewRatio) scaleX = videoRatio / viewRatio;
        else scaleY = viewRatio / videoRatio;
        Matrix matrix = new Matrix();
        matrix.setScale(scaleX, scaleY, viewWidth / 2f, viewHeight / 2f);
        setTransform(matrix);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateCropTransform();
    }

    @Override
    public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
        if (mPath != null) startPlayer(surface);
    }

    @Override
    public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {
        updateCropTransform();
    }

    @Override
    public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
        releasePlayer();
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {}
}
