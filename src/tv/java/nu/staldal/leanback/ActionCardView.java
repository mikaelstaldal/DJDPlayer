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
package nu.staldal.leanback;

import android.content.Context;
import android.support.v17.leanback.widget.BaseCardView;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import nu.staldal.djdplayer.R;

/**
 * A static card view with an {@link TextView}
 */
public class ActionCardView extends BaseCardView {

    private final TextView mTextView;

    public ActionCardView(Context context) {
        this(context, null);
    }

    public ActionCardView(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.imageCardViewStyle);
    }

    public ActionCardView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        LayoutInflater inflater = LayoutInflater.from(context);
        View v = inflater.inflate(R.layout.action_card_view, this);

        mTextView = (TextView) v.findViewById(R.id.text);
    }

    public void setText(CharSequence text) {
        if (mTextView == null) {
            return;
        }

        mTextView.setText(text);
    }

    public CharSequence getText() {
        if (mTextView == null) {
            return null;
        }

        return mTextView.getText();
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

}
