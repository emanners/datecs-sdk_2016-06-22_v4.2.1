package com.datecs.printerdemo.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;

public class FingerprintView extends View {
    private TextPaint mTextPaint;
    private float mTextWidth;
    private float mTextHeight;
    private String mTextString;
    private Bitmap mImage;

    public FingerprintView(Context context) {
        super(context);
        init(null, 0);
    }

    public FingerprintView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs, 0);
    }

    public FingerprintView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(attrs, defStyle);
    }

    private void init(AttributeSet attrs, int defStyle) {
        // Set up a default TextPaint object
        mTextPaint = new TextPaint();
        mTextPaint.setFlags(Paint.ANTI_ALIAS_FLAG);
        mTextPaint.setTextAlign(Paint.Align.LEFT);
        mTextPaint.setColor(Color.BLACK);

        mTextString = "";
        
        // Update TextPaint and text measurements from attributes
        invalidateTextPaintAndMeasurements();
    }

    private void invalidateTextPaintAndMeasurements() {
        mTextPaint.setTextSize(28 * getContext().getResources().getDisplayMetrics().density);       
        mTextWidth = mTextPaint.measureText(mTextString);

        Paint.FontMetrics fontMetrics = mTextPaint.getFontMetrics();
        mTextHeight = fontMetrics.bottom;                
    }

    private Bitmap createBitmapFromData(int width, int height, byte[] data) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Config.RGB_565);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.RED);
        Paint paint = new Paint();
                
        for (int i = 0; i < data.length; i++) {
            int grey = data[i] & 0xff;
            int argb = 0xFF000000 | (grey * 0x00010101);           
            paint.setColor(argb);
            canvas.drawPoint(i % width, i / width, paint);
        }
                
        return bitmap;
    }    
    
    private static Rect fitRectWithin(Rect inner, Rect outer) {
        float innerAspectRatio = inner.width() / (float) inner.height();
        float outerAspectRatio = outer.width() / (float) outer.height();

        float resizeFactor = (innerAspectRatio >= outerAspectRatio) ? 
                (outer.width() / (float) inner.width()) : (outer.height() / (float) inner.height());

        float newWidth = inner.width() * resizeFactor;
        float newHeight = inner.height() * resizeFactor;
        float newLeft = outer.left + (outer.width() - newWidth) / 2f;
        float newTop = outer.top + (outer.height() - newHeight) / 2f;

        return new Rect((int) newLeft, (int) newTop, (int) (newWidth + newLeft), (int) (newHeight + newTop));
    }
    
    public void drawScaledImage(Canvas canvas, Bitmap image, int left, int top, int width, int height) {
        Rect src = new Rect(0, 0, image.getWidth(), image.getHeight());
        Rect dst = new Rect(left, top, left + width, top + height);                                
        // Fit image into canvas
        dst = fitRectWithin(src, dst);
        canvas.drawBitmap(image, src, dst, null);
    }
    
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int paddingLeft = getPaddingLeft();
        int paddingTop = getPaddingTop();
        int paddingRight = getPaddingRight();
        int paddingBottom = getPaddingBottom();

        int contentWidth = getWidth() - paddingLeft - paddingRight;
        int contentHeight = getHeight() - paddingTop - paddingBottom;
        
        canvas.drawColor(Color.WHITE);
        
        // Draw the text.
        if (!TextUtils.isEmpty(mTextString)) {
            canvas.drawText(mTextString, paddingLeft + (contentWidth - mTextWidth) / 2, paddingTop
                    + (contentHeight + mTextHeight) / 2, mTextPaint);
        } 

        if (mImage != null) {
            drawScaledImage(canvas, mImage, paddingLeft, paddingTop, contentWidth, contentHeight);
        }
    }
    
    public void setText(String text) {
        mTextString = text;
        mImage = null;
        invalidateTextPaintAndMeasurements();
        invalidate();
    }
    
    public void setImage(int width, int height, byte[] data) {
        mTextString = "";
        mImage = createBitmapFromData(width, height, data);
        invalidate();
    }
}
