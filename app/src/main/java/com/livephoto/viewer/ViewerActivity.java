package com.livephoto.viewer;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.WallpaperManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 查看器：仿小米相册（白底、日期/时间、播放/锁定、动态照片、底部操作栏与更多面板）。 */
public class ViewerActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private List<LivePhotoItem> items = new ArrayList<LivePhotoItem>();
    private int index;

    private FrameLayout root;
    private ImageView image;
    private MotionPlayerView player;
    private LinearLayout topBar;
    private View chip;
    private TextView chipText;
    private LinearLayout bottomBar;
    private View moreOverlay;
    private IconView lockView;
    private IconView heartIcon;

    private boolean chromeVisible = true;
    private boolean locked = false;
    private boolean playing = false;
    private boolean preparing = false;
    private boolean videoPlaying = false;

    private float scale = 1f, tx = 0f, ty = 0f, lastX, lastY;
    private boolean pinching = false;
    private ScaleGestureDetector scaleDetector;
    private float downX, downY;
    private long downTime;
    private boolean moved = false;
    private boolean longPressFired = false;
    private float lastTapX, lastTapY;
    private long lastTapTime = 0;

    private final Runnable pressPlay = new Runnable() {
        @Override
        public void run() {
            if (!moved && !longPressFired) {
                longPressFired = true;
                startPlay(true);
            }
        }
    };
    private final Runnable chromeToggle = new Runnable() {
        @Override
        public void run() {
            setChrome(!chromeVisible);
        }
    };
    private final Runnable stopAfter = new Runnable() {
        @Override
        public void run() {
            if (playing) stopPlay();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        items = Library.snapshot();
        if (items.isEmpty()) {
            finish();
            return;
        }
        index = resolveIndex();
        if (index < 0) index = 0;
        if (index >= items.size()) index = items.size() - 1;

        root = new FrameLayout(this);
        root.setBackgroundColor(0xFFFFFFFF);
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

        topBar = buildTopBar();
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.gravity = Gravity.TOP;
        root.addView(topBar, tlp);

        chip = buildChip();
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.gravity = Gravity.TOP | Gravity.LEFT;
        clp.setMargins(Ui.dp(this, 18), Ui.dp(this, 74), 0, 0);
        root.addView(chip, clp);

        bottomBar = buildBottomBar();
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.gravity = Gravity.BOTTOM;
        blp.setMargins(Ui.dp(this, 0), 0, Ui.dp(this, 0), Ui.dp(this, 22));
        root.addView(bottomBar, blp);

        moreOverlay = buildMoreOverlay();
        root.addView(moreOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        scaleDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                if (locked || playing) return false;
                pinching = true;
                scale = clamp(scale * detector.getScaleFactor(), 1f, 6f);
                if (scale <= 1.002f) {
                    scale = 1f;
                    tx = 0f;
                    ty = 0f;
                }
                clampTranslation();
                applyTransform();
                return true;
            }

            @Override
            public void onScaleEnd(ScaleGestureDetector detector) {
                pinching = false;
            }
        });

        root.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                if (locked) return true;
                scaleDetector.onTouchEvent(e);
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getX();
                        downY = e.getY();
                        lastX = downX;
                        lastY = downY;
                        downTime = System.currentTimeMillis();
                        moved = false;
                        longPressFired = false;
                        pinching = false;
                        ui.postDelayed(pressPlay, 150);
                        break;
                    case MotionEvent.ACTION_POINTER_DOWN:
                        ui.removeCallbacks(pressPlay);
                        pinching = true;
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (Math.abs(e.getX() - downX) > Ui.dp(ViewerActivity.this, 14)
                                || Math.abs(e.getY() - downY) > Ui.dp(ViewerActivity.this, 14)) {
                            moved = true;
                            if (!playing) ui.removeCallbacks(pressPlay);
                        }
                        if (scale > 1.02f && !pinching && !playing && e.getPointerCount() == 1) {
                            tx += e.getX() - lastX;
                            ty += e.getY() - lastY;
                            clampTranslation();
                            applyTransform();
                        }
                        lastX = e.getX();
                        lastY = e.getY();
                        break;
                    case MotionEvent.ACTION_UP:
                        ui.removeCallbacks(pressPlay);
                        if (longPressFired) {
                            stopPlay();
                        } else {
                            float dx = e.getX() - downX;
                            float dy = e.getY() - downY;
                            long dt = System.currentTimeMillis() - downTime;
                            if (!moved && dt < 300) {
                                handleTap(e.getX(), e.getY());
                            } else if (Math.abs(dx) > Ui.dp(ViewerActivity.this, 54)
                                    && Math.abs(dx) > Math.abs(dy) * 1.2f
                                    && scale <= 1.02f && !videoPlaying) {
                                navigate(dx < 0 ? 1 : -1);
                            }
                        }
                        break;
                    case MotionEvent.ACTION_CANCEL:
                        ui.removeCallbacks(pressPlay);
                        if (playing || preparing) stopPlay();
                        break;
                    default:
                        break;
                }
                return true;
            }
        });

        showItem(0);
    }

    private int resolveIndex() {
        long id = getIntent().getLongExtra("id", -1);
        long size = getIntent().getLongExtra("size", -1);
        if (id > 0) {
            for (int i = 0; i < items.size(); i++) {
                LivePhotoItem it = items.get(i);
                if (it.id == id && (size <= 0 || it.imageSize == size)) return i;
            }
        }
        int pos = getIntent().getIntExtra("pos", 0);
        return Math.max(0, Math.min(items.size() - 1, pos));
    }

    private LivePhotoItem current() {
        if (index < 0 || index >= items.size()) return null;
        return items.get(index);
    }

    // ---------------- 顶部 ----------------

    private LinearLayout buildTopBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 6));

        IconView back = new IconView(this, IconView.BACK);
        back.setIconColor(0xFF101010);
        bar.addView(back, new LinearLayout.LayoutParams(Ui.dp(this, 42), Ui.dp(this, 42)));
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        Ui.margins(titles, this, 6, 0, 0, 0);
        bar.addView(titles, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        dateView = Ui.text(this, "", 19, Ui.L_TEXT, true);
        titles.addView(dateView);
        timeView = Ui.text(this, "", 12, Ui.L_TEXT2, false);
        Ui.margins(timeView, this, 0, 2, 0, 0);
        titles.addView(timeView);

        IconView play = new IconView(this, IconView.PLAY);
        play.setIconColor(0xFF101010);
        bar.addView(play, new LinearLayout.LayoutParams(Ui.dp(this, 42), Ui.dp(this, 42)));
        play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LivePhotoItem it = current();
                if (it == null) return;
                if (it.isVideo) toggleVideo();
                else if (it.live()) startPlay(true);
                else Toast.makeText(ViewerActivity.this, "这是普通照片", Toast.LENGTH_SHORT).show();
            }
        });

        lockView = new IconView(this, IconView.LOCK);
        lockView.setIconColor(0xFF101010);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(Ui.dp(this, 42), Ui.dp(this, 42));
        llp.setMargins(Ui.dp(this, 4), 0, 0, 0);
        bar.addView(lockView, llp);
        lockView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                locked = !locked;
                lockView.setIconColor(locked ? Ui.L_BLUE : 0xFF101010);
                Toast.makeText(ViewerActivity.this,
                        locked ? "已锁定：滑动/缩放已关闭" : "已解锁", Toast.LENGTH_SHORT).show();
            }
        });
        return bar;
    }

    private TextView dateView;
    private TextView timeView;

    private View buildChip() {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.HORIZONTAL);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setBackground(Ui.strokeRounded(this, 0xFFFFFFFF, Ui.L_LINE, 1, 16));
        b.setPadding(Ui.dp(this, 10), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6));
        IconView t = new IconView(this, IconView.TARGET);
        t.setIconColor(0xFF101010);
        b.addView(t, new LinearLayout.LayoutParams(Ui.dp(this, 16), Ui.dp(this, 16)));
        chipText = Ui.text(this, "动态照片", 12, Ui.L_TEXT, false);
        Ui.margins(chipText, this, 6, 0, 0, 0);
        b.addView(chipText);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LivePhotoItem it = current();
                if (it == null) return;
                if (it.isVideo) toggleVideo();
                else if (it.live()) startPlay(true);
            }
        });
        return b;
    }

    private LinearLayout buildBottomBar() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.HORIZONTAL);
        wrap.setGravity(Gravity.CENTER);
        wrap.setPadding(Ui.dp(this, 24), Ui.dp(this, 6), Ui.dp(this, 24), Ui.dp(this, 6));
        wrap.setBackground(Ui.lightPill(this, 30));
        wrap.setElevation(Ui.dp(this, 8));

        wrap.addView(barItem(IconView.SHARE, "发送", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                share();
            }
        }));
        wrap.addView(barItem(IconView.EDIT, "编辑", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                edit();
            }
        }));
        heartIcon = (IconView) ((ViewGroup) barItem(IconView.HEART, "收藏", false,
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        toggleHeart();
                    }
                })).getChildAt(0);
        wrap.addView(barItemHost(heartIcon, "收藏", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleHeart();
            }
        }));
        wrap.addView(barItem(IconView.TRASH, "删除", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmDelete();
            }
        }));
        wrap.addView(barItem(IconView.MORE, "更多", true, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleMore(true);
            }
        }));
        return wrap;
    }

    private View barItem(int icon, String label, boolean more, View.OnClickListener l) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        IconView iv = new IconView(this, icon);
        iv.setIconColor(0xFF101010);
        if (more) iv.setBackground(Ui.rounded(this, 0xFFF0F0F2, 18));
        box.addView(iv, new LinearLayout.LayoutParams(Ui.dp(this, 24), Ui.dp(this, 24)));
        TextView tv = Ui.text(this, label, 10, Ui.L_TEXT, false);
        tv.setGravity(Gravity.CENTER);
        Ui.margins(tv, this, 0, 4, 0, 0);
        box.addView(tv);
        box.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        box.setLayoutParams(lp);
        return box;
    }

    private View barItemHost(IconView iconView, String label, View.OnClickListener l) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        ViewGroup parent = (ViewGroup) iconView.getParent();
        if (parent != null) parent.removeView(iconView);
        box.addView(iconView, new LinearLayout.LayoutParams(Ui.dp(this, 24), Ui.dp(this, 24)));
        TextView tv = Ui.text(this, label, 10, Ui.L_TEXT, false);
        tv.setGravity(Gravity.CENTER);
        Ui.margins(tv, this, 0, 4, 0, 0);
        box.addView(tv);
        box.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        box.setLayoutParams(lp);
        return box;
    }

    // ---------------- 更多面板 ----------------

    private View buildMoreOverlay() {
        FrameLayout overlay = new FrameLayout(this);
        overlay.setVisibility(View.GONE);
        View dim = new View(this);
        dim.setBackgroundColor(0x66000000);
        overlay.addView(dim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        dim.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleMore(false);
            }
        });

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.lightCard(this, 20));
        card.setElevation(Ui.dp(this, 16));
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                Ui.dp(this, 232), ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.gravity = Gravity.BOTTOM | Gravity.RIGHT;
        clp.setMargins(0, 0, Ui.dp(this, 14), Ui.dp(this, 96));
        overlay.addView(card, clp);

        card.addView(menuRow("添加到", IconView.PLUS, false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleMore(false);
                addTo();
            }
        }));
        card.addView(menuRow("设置为壁纸", IconView.WALLPAPER, false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleMore(false);
                setWallpaper();
            }
        }));
        card.addView(menuRow("详情", IconView.INFO, false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleMore(false);
                showInfo();
            }
        }));
        card.addView(menuDivider());
        card.addView(menuRow("提取", IconView.EXTRACT, true, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleMore(false);
                showExtract();
            }
        }));
        card.addView(menuRow("生成PDF", IconView.PDF, false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleMore(false);
                exportPdf();
            }
        }));
        card.addView(menuRow("文档编辑", IconView.EDIT, false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleMore(false);
                edit();
            }
        }));
        card.addView(menuRow("隐私保护水印", IconView.SHIELD, false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleMore(false);
                watermark();
            }
        }));
        card.addView(menuRow("投屏", IconView.CAST, false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleMore(false);
                Toast.makeText(ViewerActivity.this,
                        "未检测到投屏设备（需要系统投屏服务）", Toast.LENGTH_SHORT).show();
            }
        }));
        card.addView(menuDivider());
        card.addView(menuRow("复制到剪贴板", IconView.COPY, false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleMore(false);
                copyToClipboard();
            }
        }));
        card.addView(menuRow("重命名", IconView.RENAME, false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleMore(false);
                rename();
            }
        }));
        return overlay;
    }

    private View menuRow(String text, int icon, boolean chevron, View.OnClickListener l) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 14), Ui.dp(this, 12));
        IconView iv = new IconView(this, icon);
        iv.setIconColor(0xFF3C3C43);
        row.addView(iv, new LinearLayout.LayoutParams(Ui.dp(this, 20), Ui.dp(this, 20)));
        TextView t = Ui.text(this, text, 14, Ui.L_TEXT, false);
        Ui.margins(t, this, 12, 0, 0, 0);
        row.addView(t, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        if (chevron) row.addView(Ui.text(this, "›", 18, Ui.L_TEXT3, false));
        row.setOnClickListener(l);
        return row;
    }

    private View menuDivider() {
        View v = new View(this);
        v.setBackgroundColor(Ui.L_LINE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        lp.setMargins(Ui.dp(this, 46), Ui.dp(this, 2), Ui.dp(this, 16), Ui.dp(this, 2));
        v.setLayoutParams(lp);
        return v;
    }

    private void toggleMore(boolean show) {
        moreOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    // ---------------- 内容 ----------------

    private void showItem(final int dir) {
        final LivePhotoItem it = current();
        if (it == null) return;
        stopPlay();
        resetZoom();
        image.animate().cancel();
        image.setAlpha(0f);
        if (dir != 0) image.setTranslationX(dir * Ui.screenWidth(this) * 0.3f);
        final int myIndex = index;
        ThumbLoader.get(this).loadFull(it, Math.max(Ui.screenWidth(this), Ui.screenHeight(this)),
                new ThumbLoader.LoadCallback() {
                    @Override
                    public void onBitmap(Bitmap b) {
                        if (myIndex != ViewerActivity.this.index) return;
                        if (b != null) image.setImageBitmap(b);
                        image.setAlpha(0f);
                        image.animate().alpha(1f).translationX(0f).setDuration(170).start();
                    }
                });

        SimpleDateFormat df = new SimpleDateFormat("yyyy年M月d日", Locale.CHINA);
        SimpleDateFormat tf = new SimpleDateFormat("HH:mm", Locale.CHINA);
        Date d = new Date(it.dateMs <= 0 ? System.currentTimeMillis() : it.dateMs);
        dateView.setText(df.format(d));
        timeView.setText(tf.format(d));

        boolean live = it.live();
        chip.setVisibility(live || it.isVideo ? View.VISIBLE : View.GONE);
        if (it.isVideo) {
            chipText.setText("视频 " + it.durationText());
        } else {
            chipText.setText("动态照片");
        }
        updateHeart();
        if (it.motionPhoto) MotionVideo.prepare(this, it);
        if (it.isVideo && it.videoUri != null && Prefs.videoAutoplayMuted(this)) {
            videoPlaying = true;
            image.animate().alpha(0f).setDuration(120).start();
            player.play(it.videoUri, Prefs.videoLoop(this), true);
            player.setAlpha(1f);
        }
    }

    private void navigate(int dir) {
        int ni = index + dir;
        if (ni < 0 || ni >= items.size()) return;
        if (playing) stopPlay();
        index = ni;
        image.animate().cancel();
        image.animate().translationX(-dir * Ui.screenWidth(this) * 0.3f)
                .alpha(0f).setDuration(130).start();
        ui.postDelayed(new Runnable() {
            @Override
            public void run() {
                image.setTranslationX(0f);
                showItem(0);
            }
        }, 90);
    }

    private void startPlay(boolean autoStop) {
        final LivePhotoItem it = current();
        if (it == null || !it.live() || !Prefs.longPressPlay(this)) return;
        if (playing) return;
        playing = true;
        final boolean mute = !Prefs.sound(this);
        if (it.videoUri != null) {
            playVideo(it.videoUri, mute);
        } else if (it.motionPhoto) {
            preparing = true;
            MotionVideo.get(this, it, new MotionVideo.Callback() {
                @Override
                public void onReady(File f) {
                    preparing = false;
                    if (!playing || current() != it) return;
                    if (f == null) {
                        Toast.makeText(ViewerActivity.this, "无法播放这段动态", Toast.LENGTH_SHORT).show();
                        stopPlay();
                        return;
                    }
                    playVideo(Uri.fromFile(f), mute);
                }
            });
        } else {
            playing = false;
            image.animate().alpha(1f).setDuration(120).start();
        }
        root.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        if (autoStop) {
            ui.removeCallbacks(stopAfter);
            ui.postDelayed(stopAfter, 3000);
        }
    }

    private void playVideo(Uri uri, boolean mute) {
        player.setVisibility(View.VISIBLE);
        player.setAlpha(0f);
        player.play(uri, false, mute);
        player.animate().alpha(1f).setDuration(120).start();
        image.animate().alpha(0f).setDuration(90).start();
    }

    private void toggleVideo() {
        final LivePhotoItem it = current();
        if (it == null || !it.isVideo || it.videoUri == null) return;
        if (videoPlaying) {
            videoPlaying = false;
            player.stop();
            player.setAlpha(0f);
            image.animate().alpha(1f).setDuration(150).start();
        } else {
            videoPlaying = true;
            image.animate().alpha(0f).setDuration(120).start();
            player.play(it.videoUri, Prefs.videoLoop(this), !Prefs.sound(this));
            player.setAlpha(1f);
        }
    }

    private void stopPlay() {
        ui.removeCallbacks(stopAfter);
        if (videoPlaying) {
            videoPlaying = false;
            player.stop();
            player.setAlpha(0f);
            image.animate().alpha(1f).setDuration(150).start();
            return;
        }
        if (!playing && !preparing) return;
        playing = false;
        preparing = false;
        player.stop();
        player.setAlpha(0f);
        image.animate().alpha(1f).setDuration(150).start();
    }

    private void handleTap(float x, float y) {
        long now = System.currentTimeMillis();
        boolean doubleTap = now - lastTapTime < 320
                && Math.hypot(x - lastTapX, y - lastTapY) < Ui.dp(this, 40);
        if (doubleTap) {
            ui.removeCallbacks(chromeToggle);
            lastTapTime = 0;
            toggleZoom(x, y);
        } else {
            lastTapTime = now;
            lastTapX = x;
            lastTapY = y;
            ui.removeCallbacks(chromeToggle);
            ui.postDelayed(chromeToggle, 280);
        }
    }

    private void setChrome(boolean show) {
        chromeVisible = show;
        topBar.animate().alpha(show ? 1f : 0f).setDuration(160).start();
        bottomBar.animate().alpha(show ? 1f : 0f).setDuration(160).start();
        chip.animate().alpha(show ? 1f : 0f).setDuration(160).start();
    }

    private void toggleZoom(float x, float y) {
        if (scale > 1.05f) {
            resetZoom();
            image.animate().scaleX(1f).scaleY(1f).translationX(0f).translationY(0f)
                    .setDuration(220).start();
            return;
        }
        float w = image.getWidth();
        float h = image.getHeight();
        if (w <= 0 || h <= 0) return;
        scale = 2.6f;
        tx = (w / 2f - x) * (scale - 1f);
        ty = (h / 2f - y) * (scale - 1f);
        clampTranslation();
        image.animate().scaleX(scale).scaleY(scale).translationX(tx).translationY(ty)
                .setDuration(220).start();
    }

    private void resetZoom() {
        scale = 1f;
        tx = 0f;
        ty = 0f;
        image.setScaleX(1f);
        image.setScaleY(1f);
        image.setTranslationX(0f);
        image.setTranslationY(0f);
    }

    private void clampTranslation() {
        float w = image.getWidth();
        float h = image.getHeight();
        float maxX = Math.max(0, w * (scale - 1f) / 2f);
        float maxY = Math.max(0, h * (scale - 1f) / 2f);
        if (tx > maxX) tx = maxX;
        if (tx < -maxX) tx = -maxX;
        if (ty > maxY) ty = maxY;
        if (ty < -maxY) ty = -maxY;
    }

    private void applyTransform() {
        image.setScaleX(scale);
        image.setScaleY(scale);
        image.setTranslationX(tx);
        image.setTranslationY(ty);
    }

    private float clamp(float v, float min, float max) {
        return v < min ? min : (v > max ? max : v);
    }

    // ---------------- 操作 ----------------

    private void updateHeart() {
        LivePhotoItem it = current();
        boolean fav = it != null && Favs.isFav(this, it);
        heartIcon.setIconType(fav ? IconView.HEART_FILLED : IconView.HEART);
        heartIcon.setIconColor(fav ? 0xFFFF3B30 : 0xFF101010);
    }

    private void toggleHeart() {
        LivePhotoItem it = current();
        if (it == null) return;
        boolean fav = !Favs.isFav(this, it);
        Favs.set(this, it, fav);
        updateHeart();
        Toast.makeText(this, fav ? "已收藏" : "已取消收藏", Toast.LENGTH_SHORT).show();
    }

    private void share() {
        LivePhotoItem it = current();
        if (it == null) return;
        Uri uri = ShareUtil.prepare(this, it, it.isVideo);
        if (uri == null) return;
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType(it.isVideo ? "video/*" : "image/*");
        i.putExtra(Intent.EXTRA_STREAM, uri);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(i, "发送"));
        } catch (Throwable t) {
            Toast.makeText(this, "发送失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void edit() {
        LivePhotoItem it = current();
        if (it == null) return;
        Intent i = new Intent(this, EditActivity.class);
        i.putExtra("id", it.id);
        i.putExtra("size", it.imageSize);
        startActivity(i);
    }

    private void confirmDelete() {
        final LivePhotoItem it = current();
        if (it == null) return;
        new AlertDialog.Builder(this)
                .setTitle("删除这张照片？")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        deleteItem(it);
                    }
                }).show();
    }

    private void deleteItem(LivePhotoItem it) {
        if (it.id > 0 && it.imageUri != null && Build.VERSION.SDK_INT >= 30) {
            try {
                List<Uri> uris = new ArrayList<Uri>();
                uris.add(it.imageUri);
                startIntentSenderForResult(
                        MediaStore.createDeleteRequest(getContentResolver(), uris)
                                .getIntentSender(), 77, null, 0, 0, 0);
                return;
            } catch (Throwable ignored) {
            }
        }
        if (it.imageFile != null) {
            //noinspection ResultOfMethodCallIgnored
            it.imageFile.delete();
        }
        Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 77 && resultCode == RESULT_OK) {
            Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void showInfo() {
        LivePhotoItem it = current();
        if (it == null) return;
        String text = it.infoText();
        try {
            android.media.ExifInterface exif;
            if (it.imageFile != null) {
                exif = new android.media.ExifInterface(it.imageFile.getAbsolutePath());
            } else {
                java.io.InputStream in = getContentResolver().openInputStream(it.imageUri);
                exif = new android.media.ExifInterface(in);
                in.close();
            }
            StringBuilder sb = new StringBuilder(text);
            String model = exif.getAttribute(android.media.ExifInterface.TAG_MODEL);
            String make = exif.getAttribute(android.media.ExifInterface.TAG_MAKE);
            if (model != null) sb.append("\n相机：").append(make == null ? "" : make + " ").append(model);
            String iso = exif.getAttribute(android.media.ExifInterface.TAG_ISO_SPEED_RATINGS);
            if (iso != null) sb.append("\nISO：").append(iso);
            String f = exif.getAttribute(android.media.ExifInterface.TAG_F_NUMBER);
            if (f != null) sb.append("\n光圈：f/").append(f);
            String exp = exif.getAttribute(android.media.ExifInterface.TAG_EXPOSURE_TIME);
            if (exp != null) sb.append("\n快门：").append(exp).append(" 秒");
            String lat = exif.getAttribute(android.media.ExifInterface.TAG_GPS_LATITUDE);
            String lon = exif.getAttribute(android.media.ExifInterface.TAG_GPS_LONGITUDE);
            if (lat != null && lon != null) {
                sb.append("\n位置：").append(lat).append(" , ").append(lon);
            }
            text = sb.toString();
        } catch (Throwable ignored) {
        }
        new AlertDialog.Builder(this)
                .setTitle("详情")
                .setMessage(text)
                .setPositiveButton("好", null)
                .show();
    }

    private void addTo() {
        final String[] actions = new String[]{"添加到个人收藏", "添加到我的卡证", "添加到已隐藏"};
        new AlertDialog.Builder(this)
                .setTitle("添加到")
                .setItems(actions, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        LivePhotoItem it = current();
                        if (it == null) return;
                        if (which == 0) {
                            Favs.set(ViewerActivity.this, it, true);
                            updateHeart();
                            Toast.makeText(ViewerActivity.this, "已添加到个人收藏", Toast.LENGTH_SHORT).show();
                        } else if (which == 1) {
                            Cards.set(ViewerActivity.this, it, true);
                            Toast.makeText(ViewerActivity.this, "已添加到我的卡证", Toast.LENGTH_SHORT).show();
                        } else {
                            Hidden.set(ViewerActivity.this, it, true);
                            Toast.makeText(ViewerActivity.this, "已隐藏", Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    }
                }).show();
    }

    private void showExtract() {
        new AlertDialog.Builder(this)
                .setTitle("提取")
                .setMessage("提取文字/主体需要 OCR 与 AI 模型，本机未内置。")
                .setPositiveButton("好", null)
                .show();
    }

    private void setWallpaper() {
        final LivePhotoItem it = current();
        if (it == null) return;
        Toast.makeText(this, "正在设置壁纸…", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean ok = false;
                try {
                    Bitmap b = ThumbLoader.decode(ViewerActivity.this, it.imageUri, it.imageFile,
                            Math.max(Ui.screenWidth(ViewerActivity.this), Ui.screenHeight(ViewerActivity.this)) * 2);
                    if (b != null) {
                        WallpaperManager wm = WallpaperManager.getInstance(ViewerActivity.this);
                        wm.setBitmap(b);
                        ok = true;
                    }
                } catch (Throwable ignored) {
                }
                final boolean success = ok;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(ViewerActivity.this,
                                success ? "已设置为壁纸" : "设置壁纸失败", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }

    private void exportPdf() {
        final LivePhotoItem it = current();
        if (it == null) return;
        Toast.makeText(this, "正在生成 PDF…", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean ok = false;
                try {
                    Bitmap b = ThumbLoader.decode(ViewerActivity.this, it.imageUri, it.imageFile, 2000);
                    if (b != null) {
                        PdfDocument doc = new PdfDocument();
                        int pw = 595, ph = 842;
                        PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(pw, ph, 1).create();
                        PdfDocument.Page page = doc.startPage(info);
                        Canvas c = page.getCanvas();
                        c.drawColor(Color.WHITE);
                        float scale = Math.min(pw / (float) b.getWidth(), ph / (float) b.getHeight());
                        float dw = b.getWidth() * scale, dh = b.getHeight() * scale;
                        c.drawBitmap(b, (pw - dw) / 2f, (ph - dh) / 2f, null);
                        doc.finishPage(page);
                        String name = "小瞳心_" + System.currentTimeMillis() + ".pdf";
                        ContentValues v = new ContentValues();
                        v.put(MediaStore.Downloads.DISPLAY_NAME, name);
                        v.put(MediaStore.Downloads.MIME_TYPE, "application/pdf");
                        v.put(MediaStore.Downloads.RELATIVE_PATH, "Download/小瞳心照片");
                        v.put(MediaStore.Downloads.IS_PENDING, 1);
                        Uri uri = getContentResolver().insert(
                                MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                        if (uri != null) {
                            OutputStream os = getContentResolver().openOutputStream(uri);
                            doc.writeTo(os);
                            os.close();
                            ContentValues done = new ContentValues();
                            done.put(MediaStore.Downloads.IS_PENDING, 0);
                            getContentResolver().update(uri, done, null, null);
                            ok = true;
                        }
                        doc.close();
                    }
                } catch (Throwable ignored) {
                }
                final boolean success = ok;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(ViewerActivity.this,
                                success ? "PDF 已保存到 Download/小瞳心照片" : "生成 PDF 失败",
                                Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }

    private void watermark() {
        final EditText et = new EditText(this);
        SimpleDateFormat f = new SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.CHINA);
        et.setText("小瞳心照片 · " + f.format(new Date()));
        et.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
        new AlertDialog.Builder(this)
                .setTitle("隐私保护水印")
                .setView(et)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存为新照片", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                        if (imm != null) imm.hideSoftInputFromWindow(et.getWindowToken(), 0);
                        addWatermark(et.getText().toString());
                    }
                }).show();
    }

    private void addWatermark(final String text) {
        final LivePhotoItem it = current();
        if (it == null) return;
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean ok = false;
                try {
                    Bitmap b = ThumbLoader.decode(ViewerActivity.this, it.imageUri, it.imageFile, 2560);
                    if (b != null) {
                        Bitmap out = b.copy(Bitmap.Config.ARGB_8888, true);
                        Canvas c = new Canvas(out);
                        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                        p.setColor(0xCCFFFFFF);
                        p.setTextSize(out.getWidth() * 0.030f);
                        p.setShadowLayer(out.getWidth() * 0.004f, 0, 0, 0x99000000);
                        float tw = p.measureText(text);
                        c.drawText(text, out.getWidth() - tw - out.getWidth() * 0.04f,
                                out.getHeight() - out.getHeight() * 0.05f, p);
                        ok = saveNewPhoto(out);
                    }
                } catch (Throwable ignored) {
                }
                final boolean success = ok;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(ViewerActivity.this,
                                success ? "已保存带水印的新照片" : "保存失败", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).start();
    }

    private boolean saveNewPhoto(Bitmap b) {
        try {
            String name = "小瞳心_" + System.currentTimeMillis() + ".jpg";
            ContentValues v = new ContentValues();
            v.put(MediaStore.Images.Media.DISPLAY_NAME, name);
            v.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            v.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/小瞳心照片");
            v.put(MediaStore.Images.Media.IS_PENDING, 1);
            Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            if (uri == null) return false;
            OutputStream os = getContentResolver().openOutputStream(uri);
            b.compress(Bitmap.CompressFormat.JPEG, 95, os);
            os.close();
            ContentValues done = new ContentValues();
            done.put(MediaStore.Images.Media.IS_PENDING, 0);
            getContentResolver().update(uri, done, null, null);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private void copyToClipboard() {
        LivePhotoItem it = current();
        if (it == null) return;
        try {
            Uri uri = ShareUtil.prepare(this, it, false);
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null || uri == null) return;
            cm.setPrimaryClip(ClipData.newUri(getContentResolver(), "照片", uri));
            Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            Toast.makeText(this, "复制失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void rename() {
        final LivePhotoItem it = current();
        if (it == null) return;
        final EditText et = new EditText(this);
        et.setText(it.title == null ? "" : it.title);
        et.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
        new AlertDialog.Builder(this)
                .setTitle("重命名")
                .setView(et)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        String newName = et.getText().toString().trim();
                        if (newName.isEmpty()) return;
                        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                        if (imm != null) imm.hideSoftInputFromWindow(et.getWindowToken(), 0);
                        doRename(it, newName);
                    }
                }).show();
    }

    private void doRename(LivePhotoItem it, String newName) {
        try {
            if (it.id > 0) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.Images.Media.DISPLAY_NAME, newName);
                getContentResolver().update(it.imageUri, v, null, null);
            } else if (it.imageFile != null) {
                File dst = new File(it.imageFile.getParentFile(), newName);
                if (!it.imageFile.renameTo(dst)) {
                    Toast.makeText(this, "重命名失败", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            Toast.makeText(this, "已重命名", Toast.LENGTH_SHORT).show();
            finish();
        } catch (Throwable t) {
            Toast.makeText(this, "重命名失败", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (playing || preparing || videoPlaying) stopPlay();
    }

    @Override
    public void onBackPressed() {
        if (moreOverlay.getVisibility() == View.VISIBLE) {
            toggleMore(false);
            return;
        }
        if (playing) {
            stopPlay();
            return;
        }
        super.onBackPressed();
    }
}
