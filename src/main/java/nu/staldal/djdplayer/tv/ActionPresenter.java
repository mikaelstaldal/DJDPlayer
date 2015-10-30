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
import android.os.Build;
import android.support.v17.leanback.widget.Presenter;
import android.view.ViewGroup;
import nu.staldal.djdplayer.R;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class ActionPresenter extends Presenter {

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        ActionCardView cardView = new ActionCardView(parent.getContext());

        cardView.setBackgroundColor(parent.getResources().getColor(R.color.standard_background));
        cardView.setFocusable(true);
        cardView.setFocusableInTouchMode(true);

        return new ViewHolder(cardView);
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, Object item) {
        ActionCardView cardView = (ActionCardView)viewHolder.view;
        cardView.setText(item.toString());
    }

    @Override
    public void onUnbindViewHolder(ViewHolder viewHolder) {
        // nothing to do
    }
}
