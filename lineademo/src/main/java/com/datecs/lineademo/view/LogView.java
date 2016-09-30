package com.datecs.lineademo.view;

import android.content.Context;
import android.graphics.Color;
import android.text.Editable;
import android.text.Spannable;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.datecs.lineademo.R;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Implements log view
 */
public class LogView extends LinearLayout {
    private static final int MAX_LOG_SIZE = 4 * 1024;

    private ScrollView mScrollView;
    private TextView mTextView;
    private int mMaxLogSize;
    private boolean mShowTime;

    public LogView(Context context) {
        super(context);
        initViews(context, null);
    }

    public LogView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initViews(context, attrs);
    }

    public LogView(Context context, AttributeSet attrs, int defStyle) {
        this(context, attrs);
        initViews(context, attrs);
    }

    private void initViews(Context context, AttributeSet attrs) {
        LayoutInflater.from(context).inflate(R.layout.log, this);

        mScrollView = (ScrollView) this.findViewById(R.id.log_scroll);
        mTextView = (TextView) this.findViewById(R.id.log_text);
        mMaxLogSize = MAX_LOG_SIZE;
        mShowTime = true;
    }

    private void trimLog() {
        Editable editable = mTextView.getEditableText();
        if (editable.length() > mMaxLogSize) {
            editable.delete(0, editable.length() - mMaxLogSize);
        }
    }

    public void setLogSize(int size) {
        mMaxLogSize = size;
        trimLog();
    }

    public void showTime(boolean on) {
        mShowTime = on;
    }

    public void clear() {
        mTextView.setText("");
    }

    public void add(String text, int color, boolean bold) {
        if (mShowTime) {
            SimpleDateFormat sdf = new SimpleDateFormat("ss.SSS");
            text = sdf.format(new Date()) + " " + text;
        }
        text += "\n";

        int start = mTextView.getText().length();
        mTextView.append(text);
        int end = mTextView.getText().length();
        Spannable spannableText = (Spannable) mTextView.getText();
        spannableText.setSpan(new ForegroundColorSpan(color), start, end, 0);
        if (bold) {
            spannableText.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), start, end, 0);
        }

        trimLog();

        mScrollView.post(new Runnable() {
            @Override
            public void run() {
                mScrollView.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    public void addD(String text) {
        add(text, Color.DKGRAY, false);
    }

    public void addE(String text) {
        add(text, Color.RED, false);
    }

    public void addI(String text) {
        add(text, Color.parseColor("#006400"), false);
    }

    public void addW(String text) {
        add(text, Color.parseColor("#FF6600"), false);
    }

    public void add(String text) {
        if (text.startsWith("<E>")) {
            addE(text.substring(3));
        } else if (text.startsWith("<W>")) {
            addW(text.substring(3));
        } else if (text.startsWith("<I>")) {
            addI(text.substring(3));
        } else if (text.startsWith("<D>")) {
            addD(text.substring(3));
        } else {
            addD(text);
        }
    }

}