/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nu.staldal.djdplayer.tv;

import android.annotation.TargetApi;
import android.content.res.Resources;
import android.os.Build;
import android.support.v17.leanback.widget.Presenter;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.TextView;
import nu.staldal.djdplayer.R;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class ActionPresenter extends Presenter {

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        TextView view = new TextView(parent.getContext());

        Resources resources = parent.getResources();
        view.setLayoutParams(new ViewGroup.LayoutParams(
                resources.getDimensionPixelSize(R.dimen.lb_basic_card_main_width),
                resources.getDimensionPixelSize(R.dimen.settings_card_height)));
        view.setTextColor(resources.getColor(R.color.lb_action_text_color));
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimensionPixelSize(R.dimen.lb_action_text_size));
        view.setGravity(Gravity.CENTER);

        view.setBackgroundColor(resources.getColor(R.color.standard_background));
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);

        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, Object item) {
        ((TextView) viewHolder.view).setText(item.toString());
    }

    @Override
    public void onUnbindViewHolder(ViewHolder viewHolder) {
        // nothing to do
    }
}
