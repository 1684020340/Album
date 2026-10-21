package com.livephoto.viewer;

import android.app.Activity;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** 幻灯片：苹果「照片」式自动播放，实况照片会播放动态，画面缓慢推近。 */
public class SlideshowActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private List<LivePhotoItem> items = new ArrayList<LivePhotoItem>();
    private int index;
    private ImageView image;
    private MotionPlayerView player;
    private TextView caption;
    private FrameLayout root;
    private boolean paused = false;
    private long token = 0;

    private static final long SLIDE_MS = 4200;

    private final Runnable advance = new Runnable() {
        @Override
        public void run() {
            next();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        items = Library.snapshot();
        if (items.isEmpty()) {
            finish();
            return;
        }
        String month = getIntent().getStringExtra("month");
        if (month != null) {
            List<LivePhotoItem> filtered = new ArrayList<LivePhotoItem>();
            java.util.Calendar cal = java.util.Calendar.getInstance();
            for (LivePhotoItem it : items) {
                cal.setTimeInMillis(it.dateMs <= 0 ? System.currentTimeMillis() : it.dateMs);
                String key = cal.get(java.util.Calendar.YEAR) + "-" + (cal.get(java.util.Calendar.MONTH) + 1);
                if (month.equals(key)) filtered.add(it);
            }
            if (!filtered.isEmpty()) items = filtered;
        }
        long id = getIntent().getLongExtra("id", -1);
        int pos = getIntent().getIntExtra("pos", 0);
        if (id > 0) {
            for (int i = 0; i < items.size(); i++) {
                if (items.get(i).id == id) {
                    pos = i;
                    break;
                }
            }
        }
        index = Math.max(0, Math.min(items.size() - 1, pos));

        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);
        setContentView(root);

        image = new ImageView(this);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(image, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        player = new MotionPlayerView(this);
        player.setMode(MotionPlayerView.MODE_FIT);
        player.setAlpha(0f);
        root.addView(player, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        caption = Ui.text(this, "", 13, 0xCCFFFFFF, false);
        caption.setShadowLayer(Ui.dp(this, 6), 0, Ui.dp(this, 1), 0x99000000);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.gravity = Gravity.BOTTOM | Gravity.LEFT;
        clp.setMargins(Ui.dp(this, 20), 0, 0, Ui.dp(this, 30));
        root.addView(caption, clp);

        root.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                paused = !paused;
                if (paused) {
                    token++;
                    ui.removeCallbacks(advance);
                    player.stop();
                    player.animate().alpha(0f).setDuration(150).start();
                    image.animate().alpha(1f).setDuration(150).start();
                } else {
                    showSlide();
                }
            }
        });

        showSlide();
    }

    private void next() {
        if (paused) return;
        index++;
        if (index >= items.size()) index = 0;
        showSlide();
    }

    private void showSlide() {
        if (index < 0 || index >= items.size()) return;
        final LivePhotoItem it = items.get(index);
        paused = false;
        final long myToken = ++token;
        ui.removeCallbacks(advance);
        player.stop();
        player.setAlpha(0f);
        image.animate().cancel();
        image.setAlpha(0f);
        image.setScaleX(1f);
        image.setScaleY(1f);
        caption.setText((index + 1) + " / " + items.size() + "   " + it.dateText()
                + (it.live() ? "   LIVE" : ""));

        ThumbLoader.get(this).loadFull(it, Math.max(Ui.screenWidth(this), Ui.screenHeight(this)),
                new ThumbLoader.LoadCallback() {
                    @Override
                    public void onBitmap(Bitmap b) {
                        if (myToken != token) return;
                        if (b != null) image.setImageBitmap(b);
                        image.animate().alpha(1f).setDuration(500).start();
                        image.animate().scaleX(1.09f).scaleY(1.09f)
                                .setDuration(SLIDE_MS + 1200).start();
                    }
                });

        // 实况照片：播一下动态
        if (it.live()) {
            if (it.videoUri != null) {
                playMotion(it.videoUri, myToken);
            } else if (it.motionPhoto) {
                MotionVideo.get(this, it, new MotionVideo.Callback() {
                    @Override
                    public void onReady(File f) {
                        if (f == null || myToken != token) return;
                        playMotion(Uri.fromFile(f), myToken);
                    }
                });
            }
        }
        ui.postDelayed(advance, SLIDE_MS);
    }

    private void playMotion(Uri uri, final long myToken) {
        ui.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (myToken != token || paused) return;
                player.play(uri, false, true);
                player.setAlpha(1f);
            }
        }, 700);
        ui.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (myToken != token || paused) return;
                player.animate().alpha(0f).setDuration(250).start();
                image.animate().alpha(1f).setDuration(250).start();
                ui.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (myToken == token) player.stop();
                    }
                }, 300);
            }
        }, 2600);
    }

    @Override
    protected void onPause() {
        super.onPause();
        paused = true;
        token++;
        ui.removeCallbacks(advance);
        player.stop();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }
}
