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

public class FolderItem extends CategoryItem {
    public final String path;

    public FolderItem(long id, String name, int count, String path) {
        super(id, name, count);
        this.path = path;
    }

    @Override
    public String toString() {
        return "FolderItem{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", count=" + count +
                ", path=" + path +
                '}';
    }
}
