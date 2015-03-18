package com.theonelab.navi.gypsum;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.Region;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Pair;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class DisplayView extends View {
  private static final String TAG = "DisplayView";

  private Bitmap framebuffer;
  private Canvas fbCanvas;
  private Paint paint;

  public DisplayView(Context context) {
    super(context);
    Log.v(TAG, "DisplayView(Context)");
    paint = new Paint();
  }

  public DisplayView(Context context, AttributeSet attrs) {
    super(context, attrs);
    Log.v(TAG, "DisplayView(Context, AttributeSet)");
    paint = new Paint();
  }

  public DisplayView(Context context, AttributeSet attrs, int defStyleAttr) {
    super(context, attrs, defStyleAttr);
    Log.v(TAG, "DisplayView(Context, AttributeSet, int)");
    paint = new Paint();
  }

  @Override
  public void onDetachedFromWindow() {
    super.onDetachedFromWindow();
    Log.v(TAG, "onDetachedFromWindow");
    framebuffer = null;
    fbCanvas = null;
  }

  @Override
  public void onDraw(Canvas canvas) {
    super.onDraw(canvas);

    if (framebuffer == null) {
      framebuffer = Bitmap.createBitmap(getWidth(), getHeight(),
          Bitmap.Config.ARGB_8888);

      if (framebuffer == null) {
        Log.wtf(TAG, "Couldn't allocate framebuffer?!?!?!?!?!");
      }

      fbCanvas = new Canvas(framebuffer);
    }

    canvas.drawRGB(0, 0, 0);
    canvas.drawBitmap(framebuffer, 0, 0, null);
  }

  /**
   * Registers the various {@link Command} callbacks for this
   * {@link DisplayView} in the given {@link CommandParser}.
   */
  public void registerWithParser(CommandParser parser) {
    // (line :width num :color "#argb" :start coord :end coord)
    parser.registerCommand("line", new Command() {
        @Override
        public void execute(Map<String, Value> params) {
          if (fbCanvas != null) {
            Pair<Float, Float> start = getCoordParam("start", params, ZERO_COORD);
            Pair<Float, Float> end = getCoordParam("end", params, ZERO_COORD);
            int color = getColorParam("color", params, Color.WHITE);
            float width = getNumberParam("width", params, 1.0f);

            paint.setColor(color);
            paint.setAntiAlias(true);

            fbCanvas.drawLine(
                start.first, start.second,
                end.first, end.second,
                paint);
          }
        }
      });

    // (box :filled (truep) :start (coord) :end (coord))
    parser.registerCommand("box", new Command() {
        @Override
        public void execute(Map<String, Value> params) {
          if (fbCanvas != null) {
            Pair<Float, Float> start = getCoordParam("start", params);
            Pair<Float, Float> end = getCoordParam("end", params);
            boolean isFilled = getBooleanParam("filled", params, false);
            int color = getColorParam("color", params, Color.WHITE);

            paint.setColor(color);
            paint.setAntiAlias(true);

            if (isFilled) {
              paint.setStyle(Paint.Style.FILL_AND_STROKE);
            } else {
              paint.setStyle(Paint.Style.STROKE);
            }

            fbCanvas.drawRect(
                start.first, start.second,
                end.first, end.second,
                paint);
          }
        }
        });

    // (text :font "fontname"
    //       :weight 'bold|'normal
    //       :size num
    //       :color "#rgb"
    //       :text "text")
    parser.registerCommand("text", new Command() {
        @Override
        public void execute(Map<String, Value> params) {
          String font = getStringParam("font", params, "sans");
          boolean bold = getBooleanParam("bold", params, false);
          boolean italic = getBooleanParam("italic", params, false);
          float size = getNumberParam("size", params, 10.0f);
          int color = getColorParam("color", params, Color.WHITE);
          boolean isFilled = getBooleanParam("filled", params, true);
          String text = getStringParam("text", params, null);
          Pair<Float, Float> start = getCoordParam("start", params);

          if (text == null) {
            Log.e(TAG + "/text", "No :text parameter specified.");
            return;
          }

          int style = 0;
          if (bold) style |= Typeface.BOLD;
          if (italic) style |= Typeface.ITALIC;

          if (isFilled) {
            paint.setStyle(Paint.Style.FILL_AND_STROKE);
          } else {
            paint.setStyle(Paint.Style.STROKE);
          }

          boolean loadFromAssets = true;

          if (font.equals("sans") || font.equals("serif") || font.equals("monospace")) {
            loadFromAssets = false;
          }

          Typeface face = null;

          if (loadFromAssets) {
            try {
              face = Typeface.createFromAsset(getContext().getAssets(), font + ".ttf");
            } catch (RuntimeException e) {
              Log.w(TAG + "/text", "Couldn't create font from " + font + ".ttf: " + e.getMessage());
              return;
            }
          } else {
            face = Typeface.create(face, style);
          }

          if (font == null) {
            Log.e(TAG + "/text", "Couldn't open font " + font + ".");
            return;
          }
          
          paint.setTypeface(face);
          paint.setTextSize(size);
          paint.setColor(color);
          paint.setAntiAlias(true);

          fbCanvas.drawText(
              text,
              start.first, start.second,
              paint);
        }
      });

    // (scroll :start coord
    //         :end coord
    //         :dx number
    //         :dy number)
    parser.registerCommand("scroll", new Command() {
        @Override
        public void execute(Map<String, Value> params) {
          Pair<Float, Float> start = getCoordParam("start", params);
          Pair<Float, Float> end = getCoordParam("end", params);
          float dx = getNumberParam("dx", params, 0.0f);
          float dy = getNumberParam("dy", params, 0.0f);
          int bgcolor = getColorParam("bgcolor", params, Color.BLACK);

          Bitmap subBitmap = Bitmap.createBitmap(
              framebuffer,
              (int) (float) start.first, (int) (float) start.second,
              (int) (end.first - start.first), (int) (end.second - start.second));

          paint.setColor(bgcolor);

          fbCanvas.save();
          fbCanvas.clipRect(
              start.first, start.second,
              end.first, end.second,
              Region.Op.REPLACE);
          fbCanvas.drawPaint(paint);
          fbCanvas.drawBitmap(
              subBitmap,
              (int) (float) start.first - dx, (int) (float) start.second - dy,
              null /* paint */);
          fbCanvas.restore();
        }
      });

    // (move :start coord
    //       :end coord
    //       :pos coord
    //       :bgcolor color)
    parser.registerCommand("move", new Command() {
        @Override
        public void execute(Map<String, Value> params) {
          Pair<Float, Float> start = getCoordParam("start", params);
          Pair<Float, Float> end = getCoordParam("end", params);
          Pair<Float, Float> pos = getCoordParam("pos", params);
          int bgcolor = getColorParam("bgcolor", params, Color.BLACK);

          Bitmap subBitmap = Bitmap.createBitmap(
              framebuffer,
              (int) (float) start.first, (int) (float) start.second,
              (int) (end.first - start.first), (int) (end.second - start.second));

          paint.setColor(bgcolor);

          fbCanvas.save();
          fbCanvas.clipRect(
              start.first, start.second,
              end.first, end.second,
              Region.Op.REPLACE);
          fbCanvas.drawPaint(paint);
          fbCanvas.restore();

          fbCanvas.drawBitmap(
              subBitmap,
              (int) (float) pos.first, (int) (float) pos.second,
              null /* paint */);
        }
      });

    // (clip :start coord
    //       :end coord)
    parser.registerCommand("clip", new Command() {
        @Override
        public void execute(Map<String, Value> params) {
          Pair<Float, Float> start = getCoordParam("start", params);
          Pair<Float, Float> end = getCoordParam("end", params);

          fbCanvas.clipRect(
              start.first, start.second,
              end.first, end.second,
              Region.Op.REPLACE);
        }
      });

    // (reset-clip)
    parser.registerCommand("reset-clip", new Command() {
        @Override
        public void execute(Map<String, Value> params) {
          fbCanvas.clipRect(
              0, 0,
              framebuffer.getWidth(), framebuffer.getHeight(),
              Region.Op.REPLACE);
        }
      });

    // (commit)
    parser.registerCommand("commit", new Command() {
        @Override
        public void execute(Map<String, Value> params) {
          postInvalidate();
        }
      });
  }

  public Bitmap getFrameBuffer() {
    if (framebuffer != null) {
      return Bitmap.createBitmap(framebuffer);
    }

    return null;
  }

  public void clear() {
    if (fbCanvas != null) {
      fbCanvas = new Canvas(framebuffer);
      fbCanvas.drawARGB(255, 0, 0, 0);
    }
  }
}
