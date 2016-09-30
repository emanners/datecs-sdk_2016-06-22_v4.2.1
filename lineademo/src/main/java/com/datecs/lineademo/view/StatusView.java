package com.datecs.lineademo.view;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.datecs.lineademo.R;


/**
 * Implements status view
 */
public class StatusView extends RelativeLayout {

    private ImageView mImageView;

    public StatusView(Context context) {
        super(context);
        initViews(context, null);
    }

    public StatusView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initViews(context, attrs);
    }

    public StatusView(Context context, AttributeSet attrs, int defStyle) {
        this(context, attrs);
        initViews(context, attrs);
    }

    private void initViews(Context context, AttributeSet attrs) {
        LayoutInflater.from(context).inflate(R.layout.status, this);
        mImageView = (ImageView) this.findViewById(R.id.status_image);
    }

    public void hide() {
        setVisibility(GONE);
    }

    public void show(int resId) {
        mImageView.setImageResource(resId);
        setVisibility(VISIBLE);
    }

}