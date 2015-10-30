/*
 * Copyright (C) 2014 The Android Open Source Project
 * Copyright (C) 2015 Mikael Ståldal
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package nu.staldal.djdplayer.tv;

import android.content.Context;
import android.support.v17.leanback.widget.BaseCardView;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import nu.staldal.djdplayer.R;

/**
 * A card view with an {@link TextView} as its main region.
 */
public class TextCardView extends BaseCardView {

    private final TextView mTitleView;
    private final TextView mContentView;

    public TextCardView(Context context) {
        this(context, null);
    }

    public TextCardView(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.imageCardViewStyle);
    }

    public TextCardView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        LayoutInflater inflater = LayoutInflater.from(context);
        View v = inflater.inflate(R.layout.text_card_view, this);

        mTitleView = (TextView) v.findViewById(R.id.title_text);
        mContentView = (TextView) v.findViewById(R.id.content_text);
    }

    public void setTitleText(CharSequence text) {
        if (mTitleView == null) {
            return;
        }

        mTitleView.setText(text);
        setTextMaxLines();
    }

    public CharSequence getTitleText() {
        if (mTitleView == null) {
            return null;
        }

        return mTitleView.getText();
    }

    public void setContentText(CharSequence text) {
        if (mContentView == null) {
            return;
        }

        mContentView.setText(text);
        setTextMaxLines();
    }

    public CharSequence getContentText() {
        if (mContentView == null) {
            return null;
        }

        return mContentView.getText();
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    private void setTextMaxLines() {
        if (TextUtils.isEmpty(getTitleText())) {
            mContentView.setMaxLines(2);
        } else {
            mContentView.setMaxLines(1);
        }
        if (TextUtils.isEmpty(getContentText())) {
            mTitleView.setMaxLines(2);
        } else {
            mTitleView.setMaxLines(1);
        }
    }

}
