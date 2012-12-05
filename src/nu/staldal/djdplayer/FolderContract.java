/*
 * Copyright (C) 2012 Mikael Ståldal
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
package nu.staldal.djdplayer;

import android.net.Uri;
import android.provider.BaseColumns;

public final class FolderContract {
    public static final String AUTHORITY = "nu.staldal.djdplayer";
    public static final Uri AUTHORITY_URI = Uri.parse("content://" + AUTHORITY);

    protected interface FolderColumns {
        public static final String PATH = "path";
        public static final String NAME = "name";
    }

    public static class Folder implements BaseColumns, FolderColumns {
        private Folder() {}

        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/djd.folder";
        // public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/djd.folder";

        public static final String FOLDER_PATH = "djd.folder";

        public static final Uri CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, FOLDER_PATH);

        /*
        public static Uri getFolderUri(long folderId) {
            return ContentUris.withAppendedId(CONTENT_URI, folderId);
        }
        */
    }
}
