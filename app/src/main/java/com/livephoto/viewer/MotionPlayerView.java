package com.livephoto.viewer;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.widget.FrameLayout;

/** 播放实况照片中的动态视频（MediaPlayer + TextureView）。 */
public class MotionPlayerView extends FrameLayout implements TextureView.SurfaceTextureListener {
    public static final int MODE_FIT = 0;
    public static final int MODE_CROP = 1;

    private final TextureView texture;
    private MediaPlayer player;
    private Surface surface;
    private Uri pendingUri;
    private boolean pendingLoop;
    private boolean pendingMute;
    private int videoW = 0;
    private int videoH = 0;
    private int mode = MODE_FIT;
    private boolean prepared = false;
    private Runnable onStarted;

    public MotionPlayerView(Context c) {
        super(c);
        setBackgroundColor(0xFF000000);
        texture = new TextureView(c);
        addView(texture, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        texture.setSurfaceTextureListener(this);
        setVisibility(INVISIBLE);
    }

    public void setMode(int m) {
        mode = m;
    }

    public void setOnStarted(Runnable r) {
        onStarted = r;
    }

    public boolean isPlayingMotion() {
        return prepared && player != null;
    }

    public void play(Uri uri, boolean loop, boolean mute) {
        if (uri == null) return;
        pendingUri = uri;
        pendingLoop = loop;
        pendingMute = mute;
        setVisibility(VISIBLE);
        setAlpha(1f);
        if (surface != null) startInternal();
    }

    private void startInternal() {
        releaseInternal();
        try {
            final MediaPlayer mp = new MediaPlayer();
            player = mp;
            AudioAttributes aa = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build();
            mp.setAudioAttributes(aa);
            if (pendingMute) mp.setVolume(0f, 0f);
            mp.setSurface(surface);
            mp.setLooping(pendingLoop);
            if ("file".equals(pendingUri.getScheme())) {
                mp.setDataSource(pendingUri.getPath());
            } else {
                mp.setDataSource(getContext(), pendingUri);
            }
            mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mediaPlayer) {
                    prepared = true;
                    videoW = mediaPlayer.getVideoWidth();
                    videoH = mediaPlayer.getVideoHeight();
                    layoutTexture();
                    mediaPlayer.start();
                    if (onStarted != null) onStarted.run();
                }
            });
            mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mediaPlayer, int what, int extra) {
                    prepared = false;
                    return true;
                }
            });
            mp.prepareAsync();
        } catch (Throwable t) {
            releaseInternal();
        }
    }

    public void stop() {
        releaseInternal();
        setVisibility(INVISIBLE);
    }

    private void releaseInternal() {
        prepared = false;
        if (player != null) {
            try {
                player.stop();
            } catch (Throwable ignored) {
            }
            try {
                player.reset();
            } catch (Throwable ignored) {
            }
            try {
                player.release();
            } catch (Throwable ignored) {
            }
            player = null;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        layoutTexture();
    }

    private void layoutTexture() {
        if (videoW <= 0 || videoH <= 0) return;
        int cw = getWidth();
        int ch = getHeight();
        if (cw <= 0 || ch <= 0) return;
        float scale;
        if (mode == MODE_CROP) {
            scale = Math.max(cw / (float) videoW, ch / (float) videoH);
        } else {
            scale = Math.min(cw / (float) videoW, ch / (float) videoH);
        }
        int w = Math.round(videoW * scale);
        int h = Math.round(videoH * scale);
        LayoutParams lp = (LayoutParams) texture.getLayoutParams();
        if (lp == null) lp = new LayoutParams(w, h);
        lp.width = w;
        lp.height = h;
        lp.gravity = Gravity.CENTER;
        texture.setLayoutParams(lp);
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture st, int width, int height) {
        surface = new Surface(st);
        if (pendingUri != null && getVisibility() == VISIBLE) {
            startInternal();
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture st, int width, int height) {
        layoutTexture();
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
        releaseInternal();
        if (surface != null) {
            surface.release();
            surface = null;
        }
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture st) {
    }

    @Override
    protected void onDetachedFromWindow() {
        releaseInternal();
        super.onDetachedFromWindow();
    }
}
