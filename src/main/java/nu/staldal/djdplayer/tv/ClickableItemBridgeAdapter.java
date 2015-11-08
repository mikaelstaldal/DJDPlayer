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

import android.support.v17.leanback.widget.ItemBridgeAdapter;
import android.support.v17.leanback.widget.ObjectAdapter;
import android.support.v17.leanback.widget.OnItemViewClickedListener;
import android.view.View;

public class ClickableItemBridgeAdapter extends ItemBridgeAdapter {
    private final OnItemViewClickedListener clickedListener;

    public ClickableItemBridgeAdapter(ObjectAdapter adapter, OnItemViewClickedListener clickedListener) {
        super(adapter);
        this.clickedListener = clickedListener;
    }

    public final OnItemViewClickedListener getOnItemViewClickedListener() {
        return clickedListener;
    }

    @Override
    public void onBind(final ItemBridgeAdapter.ViewHolder itemViewHolder) {
        itemViewHolder.getViewHolder().view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (clickedListener != null) {
                    clickedListener.onItemClicked(itemViewHolder.getViewHolder(), itemViewHolder.getItem(), null, null);
                }
            }
        });
    }

    @Override
    public void onUnbind(ItemBridgeAdapter.ViewHolder viewHolder) {
        if (clickedListener != null) {
            viewHolder.getViewHolder().view.setOnClickListener(null);
        }
    }
}
