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

import android.annotation.TargetApi;
import android.os.Build;
import android.support.v17.leanback.widget.Presenter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import nu.staldal.djdplayer.MusicUtils;
import nu.staldal.djdplayer.R;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class SongRowPresenter extends Presenter {

    class ViewHolder extends Presenter.ViewHolder {
        final TextView title;
        final TextView artist;
        final TextView duration;

        public ViewHolder(View view) {
            super(view);
            title = (TextView)view.findViewById(R.id.title);
            artist = (TextView)view.findViewById(R.id.artist);
            duration = (TextView)view.findViewById(R.id.duration);
        }
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View view = inflater.inflate(R.layout.song_list_row, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(Presenter.ViewHolder vh, Object o) {
        ViewHolder viewHolder = (ViewHolder)vh;
        SongItem item = (SongItem)o;

        viewHolder.title.setText(item.title);
        viewHolder.artist.setText(item.artist);
        viewHolder.duration.setText(MusicUtils.formatDuration(viewHolder.view.getContext(), item.duration));
    }

    @Override
    public void onUnbindViewHolder(Presenter.ViewHolder viewHolder) {
        // nothing to do
    }
}
