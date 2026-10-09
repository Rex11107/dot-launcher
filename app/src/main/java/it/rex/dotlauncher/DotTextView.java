package it.rex.dotlauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

import java.text.Normalizer;
import java.util.Locale;

/** Testo disegnato a puntini con il font DotFont. */
public class DotTextView extends View {
    private String text = "";
    private float pitch = 8f;
    private float maxPitch = 0f;
    private float usedPitch = 8f;
    private boolean fitWidth = false;
    private boolean center = false;
    private int color = Color.WHITE;
    private int accentColor = 0xFFD71921;
    private String accentChars = "";
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public DotTextView(Context c) {
        super(c);
    }

    static String normalize(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT);
    }

    public void setText(String s) {
        String n = normalize(s);
        if (n.equals(text)) return;
        text = n;
        requestLayout();
        invalidate();
    }

    public String getText() {
        return text;
    }

    public void setPitch(float px) {
        pitch = px;
        requestLayout();
        invalidate();
    }

    public void setFitWidth(boolean fit, float maxPitchPx) {
        fitWidth = fit;
        maxPitch = maxPitchPx;
        requestLayout();
    }

    public void setCenter(boolean c) {
        center = c;
        invalidate();
    }

    public void setColor(int c) {
        color = c;
        invalidate();
    }

    public void setAccent(String chars, int c) {
        accentChars = chars;
        accentColor = c;
        invalidate();
    }

    private int columns() {
        int cols = 0;
        for (int i = 0; i < text.length(); i++) {
            if (i > 0) cols += 1;
            cols += DotFont.glyph(text.charAt(i))[0].length();
        }
        return Math.max(cols, 1);
    }

    @Override
    protected void onMeasure(int ws, int hs) {
        int cols = columns();
        int padH = getPaddingLeft() + getPaddingRight();
        int padV = getPaddingTop() + getPaddingBottom();
        float p = pitch;
        if (fitWidth && MeasureSpec.getMode(ws) != MeasureSpec.UNSPECIFIED) {
            int avail = MeasureSpec.getSize(ws) - padH;
            if (avail > 0) {
                p = avail / (float) cols;
                if (maxPitch > 0) p = Math.min(p, maxPitch);
            }
        }
        usedPitch = p;
        int w = (int) Math.ceil(cols * p) + padH;
        int h = (int) Math.ceil(DotFont.ROWS * p) + padV;
        setMeasuredDimension(resolveSize(w, ws), resolveSize(h, hs));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float p = usedPitch;
        float contentW = columns() * p;
        float x = getPaddingLeft();
        if (center) x = (getWidth() - contentW) / 2f;
        float top = getPaddingTop();
        float r = p * 0.39f;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            String[] g = DotFont.glyph(ch);
            int w = g[0].length();
            paint.setColor(accentChars.indexOf(ch) >= 0 ? accentColor : color);
            for (int row = 0; row < DotFont.ROWS; row++) {
                String line = g[row];
                for (int col = 0; col < w; col++) {
                    if (line.charAt(col) == '#') {
                        canvas.drawCircle(x + col * p + p / 2f, top + row * p + p / 2f, r, paint);
                    }
                }
            }
            x += (w + 1) * p;
        }
    }
}
