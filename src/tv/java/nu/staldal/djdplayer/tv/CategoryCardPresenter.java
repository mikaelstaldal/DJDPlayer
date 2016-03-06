/*
 * Copyright (C) 2015 Mikael Ståldal
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

import android.support.v17.leanback.widget.Presenter;
import android.view.ViewGroup;
import nu.staldal.djdplayer.R;
import nu.staldal.leanback.TextCardView;

public class CategoryCardPresenter extends Presenter {

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        TextCardView cardView = new TextCardView(parent.getContext());

        cardView.setBackgroundColor(parent.getResources().getColor(R.color.standard_background));
        cardView.setFocusable(true);
        cardView.setFocusableInTouchMode(true);

        return new ViewHolder(cardView);
    }

    @Override
    public void onBindViewHolder(ViewHolder viewHolder, Object o) {
        CategoryItem item = (CategoryItem) o;
        TextCardView cardView = (TextCardView) viewHolder.view;

        cardView.setTitleText(item.name);
        cardView.setContentText(
                viewHolder.view.getContext().getResources().getQuantityString(R.plurals.Nsongs, item.count, item.count));
    }

    @Override
    public void onUnbindViewHolder(ViewHolder viewHolder) {
        // nothing to do
    }
}
