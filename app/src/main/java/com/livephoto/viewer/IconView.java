package com.livephoto.viewer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** 用 Canvas 绘制的小图标，避免依赖图片资源。 */
public class IconView extends View {
    public static final int BACK = 0;
    public static final int SHARE = 1;
    public static final int HEART = 2;
    public static final int HEART_FILLED = 3;
    public static final int INFO = 4;
    public static final int PLAY = 5;
    public static final int REFRESH = 6;
    public static final int CLOUD = 7;
    public static final int CLOSE = 8;
    public static final int MORE = 9;
    public static final int FOLDER = 10;
    public static final int SETTINGS = 11;
    public static final int SEARCH = 12;
    public static final int GRID = 13;
    public static final int LOCK = 14;
    public static final int EDIT = 15;
    public static final int TRASH = 16;
    public static final int PLUS = 17;
    public static final int WALLPAPER = 18;
    public static final int EXTRACT = 19;
    public static final int PDF = 20;
    public static final int SHIELD = 21;
    public static final int CAST = 22;
    public static final int COPY = 23;
    public static final int RENAME = 24;
    public static final int TARGET = 25;
    public static final int SLIDERS = 26;

    private int type;
    private int color = Color.WHITE;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();

    public IconView(Context c, int type) {
        super(c);
        this.type = type;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Ui.dp(c, 1.9f));
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        setClickable(true);
    }

    public void setIconType(int t) {
        type = t;
        invalidate();
    }

    public int getIconType() {
        return type;
    }

    public void setIconColor(int c) {
        color = c;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float cx = w / 2f;
        float cy = h / 2f;
        float s = Math.min(w, h);
        p.setColor(color);

        switch (type) {
            case BACK: {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Ui.dp(getContext(), 2.2f));
                path.reset();
                path.moveTo(cx + s * 0.14f, cy - s * 0.24f);
                path.lineTo(cx - s * 0.14f, cy);
                path.lineTo(cx + s * 0.14f, cy + s * 0.24f);
                canvas.drawPath(path, p);
                break;
            }
            case CLOSE: {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Ui.dp(getContext(), 2.0f));
                float d = s * 0.16f;
                canvas.drawLine(cx - d, cy - d, cx + d, cy + d, p);
                canvas.drawLine(cx - d, cy + d, cx + d, cy - d, p);
                break;
            }
            case SHARE: {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Ui.dp(getContext(), 1.9f));
                // 方框（上边留口）
                float bw = s * 0.44f;
                float bh = s * 0.40f;
                float left = cx - bw / 2f;
                float right = cx + bw / 2f;
                float top = cy - s * 0.02f;
                float bottom = cy + bh;
                path.reset();
                path.moveTo(left, top);
                path.lineTo(left, bottom - s * 0.08f);
                path.quadTo(left, bottom, left + s * 0.08f, bottom);
                path.lineTo(right - s * 0.08f, bottom);
                path.quadTo(right, bottom, right, bottom - s * 0.08f);
                path.lineTo(right, top);
                canvas.drawPath(path, p);
                // 上升箭头
                float ay = cy - s * 0.20f;
                canvas.drawLine(cx, cy + s * 0.04f, cx, ay, p);
                canvas.drawLine(cx - s * 0.10f, ay + s * 0.10f, cx, ay, p);
                canvas.drawLine(cx + s * 0.10f, ay + s * 0.10f, cx, ay, p);
                break;
            }
            case HEART:
            case HEART_FILLED: {
                p.setStyle(type == HEART_FILLED ? Paint.Style.FILL : Paint.Style.STROKE);
                p.setStrokeWidth(Ui.dp(getContext(), 1.9f));
                float k = s * 0.30f;
                path.reset();
                path.moveTo(cx, cy + k * 0.92f);
                path.cubicTo(cx - k * 1.5f, cy + k * 0.05f, cx - k * 0.95f, cy - k * 1.15f, cx, cy - k * 0.42f);
                path.cubicTo(cx + k * 0.95f, cy - k * 1.15f, cx + k * 1.5f, cy + k * 0.05f, cx, cy + k * 0.92f);
                path.close();
                canvas.drawPath(path, p);
                break;
            }
            case INFO: {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Ui.dp(getContext(), 1.7f));
                float r = s * 0.30f;
                canvas.drawCircle(cx, cy, r, p);
                p.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx, cy - r * 0.52f, s * 0.035f, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Ui.dp(getContext(), 1.7f));
                canvas.drawLine(cx, cy - r * 0.10f, cx, cy + r * 0.52f, p);
                break;
            }
            case PLAY: {
                p.setStyle(Paint.Style.FILL);
                float r = s * 0.30f;
                path.reset();
                path.moveTo(cx - r * 0.55f, cy - r);
                path.lineTo(cx + r * 0.85f, cy);
                path.lineTo(cx - r * 0.55f, cy + r);
                path.close();
                canvas.drawPath(path, p);
                break;
            }
            case REFRESH: {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Ui.dp(getContext(), 1.9f));
                float r = s * 0.27f;
                rect.set(cx - r, cy - r, cx + r, cy + r);
                canvas.drawArc(rect, -60f, 300f, false, p);
                float ax = cx + r * 0.72f;
                float ay = cy - r * 0.72f;
                canvas.drawLine(ax - s * 0.11f, ay - s * 0.03f, ax, ay, p);
                canvas.drawLine(ax + s * 0.02f, ay + s * 0.11f, ax, ay, p);
                break;
            }
            case CLOUD: {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Ui.dp(getContext(), 1.8f));
                float r = s * 0.22f;
                path.reset();
                path.addRoundRect(new RectF(cx - r * 1.9f, cy - r * 0.75f, cx + r * 1.25f, cy + r * 0.55f), r, r, Path.Direction.CW);
                path.addRoundRect(new RectF(cx - r * 0.2f, cy - r * 1.25f, cx + r * 1.95f, cy + r * 0.05f), r, r, Path.Direction.CW);
                path.addRoundRect(new RectF(cx - r * 1.25f, cy - r * 0.15f, cx + r * 1.0f, cy + r * 1.05f), r, r, Path.Direction.CW);
                canvas.drawPath(path, p);
                p.setStyle(Paint.Style.STROKE);
                canvas.drawLine(cx, cy + r * 1.5f, cx, cy + r * 2.2f, p);
                canvas.drawLine(cx - r * 0.5f, cy + r * 1.75f, cx, cy + r * 2.2f, p);
                canvas.drawLine(cx + r * 0.5f, cy + r * 1.75f, cx, cy + r * 2.2f, p);
                break;
            }
            case MORE: {
                p.setStyle(Paint.Style.FILL);
                float r = s * 0.045f;
                canvas.drawCircle(cx - s * 0.18f, cy, r, p);
                canvas.drawCircle(cx, cy, r, p);
                canvas.drawCircle(cx + s * 0.18f, cy, r, p);
                break;
            }
            case GRID: {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Ui.dp(getContext(), 1.7f));
                float r = s * 0.13f;
                float off = s * 0.09f;
                canvas.drawRoundRect(new RectF(cx - off - r, cy - off - r, cx - off + r, cy - off + r), r * 0.4f, r * 0.4f, p);
                canvas.drawRoundRect(new RectF(cx + off - r, cy - off - r, cx + off + r, cy - off + r), r * 0.4f, r * 0.4f, p);
                canvas.drawRoundRect(new RectF(cx - off - r, cy + off - r, cx - off + r, cy + off + r), r * 0.4f, r * 0.4f, p);
                canvas.drawRoundRect(new RectF(cx + off - r, cy + off - r, cx + off + r, cy + off + r), r * 0.4f, r * 0.4f, p);
                break;
            }
            case SEARCH: {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Ui.dp(getContext(), 1.9f));
                float r = s * 0.20f;
                canvas.drawCircle(cx - s * 0.04f, cy - s * 0.04f, r, p);
                canvas.drawLine(cx + r * 0.62f - s * 0.04f, cy + r * 0.62f - s * 0.04f,
                        cx + s * 0.26f, cy + s * 0.26f, p);
                break;
            }
            case SETTINGS: {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Ui.dp(getContext(), 1.8f));
                float r = s * 0.30f;
                canvas.drawCircle(cx, cy, r * 0.52f, p);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI * i / 4.0;
                    float x1 = (float) (cx + Math.cos(a) * r * 0.78f);
                    float y1 = (float) (cy + Math.sin(a) * r * 0.78f);
                    float x2 = (float) (cx + Math.cos(a) * r * 1.05f);
                    float y2 = (float) (cy + Math.sin(a) * r * 1.05f);
                    canvas.drawLine(x1, y1, x2, y2, p);
                }
                break;
            }
            case SLIDERS: {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Ui.dp(getContext(), 1.8f));
                float l = cx - s * 0.28f, r = cx + s * 0.28f;
                canvas.drawLine(l, cy - s * 0.18f, r, cy - s * 0.18f, p);
                canvas.drawLine(l, cy, r, cy, p);
                canvas.drawLine(l, cy + s * 0.18f, r, cy + s * 0.18f, p);
                p.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx - s * 0.06f, cy - s * 0.18f, s * 0.05f, p);
                canvas.drawCircle(cx + s * 0.1f, cy, s * 0.05f, p);
                canvas.drawCircle(cx - s * 0.14f, cy + s * 0.18f, s * 0.05f, p);
                break;
            }
            case LOCK: {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Ui.dp(getContext(), 1.8f));
                float bw = s * 0.34f;
                float bh = s * 0.26f;
                rect.set(cx - bw / 2f, cy - bh * 0.1f, cx + bw / 2f, cy + bh * 0.95f);
                canvas.drawRoundRect(rect, s * 0.05f, s * 0.05f, p);
                android.graphics.RectF arc = new android.graphics.RectF(
                        cx - bw * 0.34f, cy - bh * 1.1f, cx + bw * 0.34f, cy + bh * 0.2f);
                canvas.drawArc(arc, 180f, 180f, false, p);
                break;
            }
            case EDIT: {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Ui.dp(getContext(), 1.8f));
                path.reset();
                path.moveTo(cx - s * 0.24f, cy + s * 0.24f);
                path.lineTo(cx - s * 0.16f, cy + s * 0.02f);
                path.lineTo(cx + s * 0.16f, cy - s * 0.3f);
                path.lineTo(cx + s * 0.28f, cy - s * 0.18f);
                path.lineTo(cx - s * 0.04f, cy + s * 0.14f);
                path.close();
                canvas.drawPath(path, p);
                canvas.drawLine(cx - s * 0.22f, cy + s * 0.28f, cx - s * 0.04f, cy + s * 0.28f, p);
                break;
            }
            case TRASH: {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Ui.dp(getContext(), 1.7f));
                canvas.drawLine(cx - s * 0.26f, cy - s * 0.22f, cx + s * 0.26f, cy - s * 0.22f, p);
                canvas.drawLine(cx - s * 0.16f, cy - s * 0.28f, cx + s * 0.16f, cy - s * 0.28f, p);
                path.reset();
                path.moveTo(cx - s * 0.2f, cy - s * 0.18f);
                path.lineTo(cx - s * 0.14f, cy + s * 0.26f);
                path.lineTo(cx + s * 0.14f, cy + s * 0.26f);
                path.lineTo(cx + s * 0.2f, cy - s * 0.18f);
                canvas.drawPath(path, p);
                canvas.drawLine(cx - s * 0.05f, cy - s * 0.06f, cx - s * 0.03f, cy + s * 0.16f, p);
                canvas.drawLine(cx + s * 0.05f, cy - s * 0.06f, cx + s * 0.03f, cy + s * 0.16f, p);
                break;
            }
            case PLUS: {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Ui.dp(getContext(), 1.7f));
                canvas.drawCircle(cx, cy, s * 0.3f, p);
                canvas.drawLine(cx - s * 0.13f, cy, cx + s * 0.13f, cy, p);
                canvas.drawLine(cx, cy - s * 0.13f, cx, cy + s * 0.13f, p);
                break;
            }
            case WALLPAPER: {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Ui.dp(getContext(), 1.7f));
                rect.set(cx - s * 0.3f, cy - s * 0.24f, cx + s * 0.3f, cy + s * 0.24f);
                canvas.drawRoundRect(rect, s * 0.04f, s * 0.04f, p);
                path.reset();
                path.moveTo(cx - s * 0.24f, cy + s * 0.18f);
                path.lineTo(cx - s * 0.06f, cy - s * 0.04f);
                path.lineTo(cx + s * 0.06f, cy + s * 0.1f);
                path.lineTo(cx + s * 0.14f, cy + s * 0.02f);
                path.lineTo(cx + s * 0.24f, cy + s * 0.18f);
                canvas.drawPath(path, p);
                break;
            }
            case EXTRACT: {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Ui.dp(getContext(), 1.7f));
                float b = s * 0.32f;
                float len = s * 0.12f;
                canvas.drawLine(cx - b, cy - b, cx - b + len, cy - b, p);
                canvas.drawLine(cx - b, cy - b, cx - b, cy - b + len, p);
                canvas.drawLine(cx + b, cy - b, cx + b - len, cy - b, p);
                canvas.drawLine(cx + b, cy - b, cx + b, cy - b + len, p);
                canvas.drawLine(cx - b, cy + b, cx - b + len, cy + b, p);
                canvas.drawLine(cx - b, cy + b, cx - b, cy + b - len, p);
                canvas.drawLine(cx + b, cy + b, cx + b - len, cy + b, p);
                canvas.drawLine(cx + b, cy + b, cx + b, cy + b - len, p);
                canvas.drawLine(cx - s * 0.12f, cy, cx + s * 0.12f, cy, p);
                break;
            }
            case PDF: {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Ui.dp(getContext(), 1.6f));
                path.reset();
                path.moveTo(cx - s * 0.22f, cy - s * 0.3f);
                path.lineTo(cx + s * 0.08f, cy - s * 0.3f);
                path.lineTo(cx + s * 0.22f, cy - s * 0.16f);
                path.lineTo(cx + s * 0.22f, cy + s * 0.3f);
                path.lineTo(cx - s * 0.22f, cy + s * 0.3f);
                path.close();
                canvas.drawPath(path, p);
                canvas.drawLine(cx - s * 0.1f, cy, cx + s * 0.1f, cy, p);
                canvas.drawLine(cx - s * 0.1f, cy + s * 0.1f, cx + s * 0.1f, cy + s * 0.1f, p);
                break;
            }
            case SHIELD: {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Ui.dp(getContext(), 1.7f));
                path.reset();
                path.moveTo(cx, cy - s * 0.3f);
                path.lineTo(cx + s * 0.26f, cy - s * 0.2f);
                path.lineTo(cx + s * 0.22f, cy + s * 0.1f);
                path.quadTo(cx, cy + s * 0.34f, cx - s * 0.22f, cy + s * 0.1f);
                path.lineTo(cx - s * 0.26f, cy - s * 0.2f);
                path.close();
                canvas.drawPath(path, p);
                break;
            }
            case CAST: {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Ui.dp(getContext(), 1.7f));
                rect.set(cx - s * 0.3f, cy - s * 0.26f, cx + s * 0.3f, cy + s * 0.1f);
                canvas.drawRoundRect(rect, s * 0.04f, s * 0.04f, p);
                for (int i = 0; i < 3; i++) {
                    android.graphics.RectF a = new android.graphics.RectF(
                            cx - s * 0.16f + i * s * 0.02f, cy + s * 0.08f + i * s * 0.02f,
                            cx + s * 0.16f - i * s * 0.02f, cy + s * 0.4f - i * s * 0.02f);
                    canvas.drawArc(a, 180f, 90f, false, p);
                }
                break;
            }
            case COPY: {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Ui.dp(getContext(), 1.7f));
                rect.set(cx - s * 0.26f, cy - s * 0.2f, cx + s * 0.14f, cy + s * 0.3f);
                canvas.drawRoundRect(rect, s * 0.04f, s * 0.04f, p);
                rect.set(cx - s * 0.1f, cy - s * 0.3f, cx + s * 0.28f, cy + s * 0.18f);
                canvas.drawRoundRect(rect, s * 0.04f, s * 0.04f, p);
                break;
            }
            case RENAME: {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Ui.dp(getContext(), 1.6f));
                rect.set(cx - s * 0.28f, cy - s * 0.24f, cx + s * 0.28f, cy + s * 0.24f);
                canvas.drawRoundRect(rect, s * 0.04f, s * 0.04f, p);
                p.setStyle(Paint.Style.FILL);
                canvas.drawRect(cx - s * 0.12f, cy - s * 0.11f, cx + s * 0.12f, cy - s * 0.06f, p);
                p.setStyle(Paint.Style.STROKE);
                canvas.drawLine(cx, cy - s * 0.06f, cx, cy + s * 0.12f, p);
                break;
            }
            case TARGET: {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Ui.dp(getContext(), 1.5f));
                canvas.drawCircle(cx, cy, s * 0.3f, p);
                canvas.drawCircle(cx, cy, s * 0.16f, p);
                p.setStyle(Paint.Style.FILL);
                canvas.drawCircle(cx, cy, s * 0.05f, p);
                break;
            }
            case FOLDER: {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Ui.dp(getContext(), 1.7f));
                float ww = s * 0.34f;
                float hh = s * 0.24f;
                path.reset();
                path.moveTo(cx - ww, cy - hh * 0.55f);
                path.lineTo(cx - ww * 0.35f, cy - hh * 0.55f);
                path.lineTo(cx - ww * 0.15f, cy - hh * 1.1f);
                path.lineTo(cx + ww * 0.85f, cy - hh * 1.1f);
                path.lineTo(cx + ww * 0.85f, cy + hh * 1.15f);
                path.lineTo(cx - ww, cy + hh * 1.15f);
                path.close();
                canvas.drawPath(path, p);
                break;
            }
            default:
                break;
        }
    }
}
