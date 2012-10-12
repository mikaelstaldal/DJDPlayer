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
package nu.staldal.djdplayer.tests;

import android.test.InstrumentationTestCase;
import nu.staldal.djdplayer.ID3Utils;

public class ID3UtilsTest extends InstrumentationTestCase {

    public void testDecodeGenre() {
        assertNull(ID3Utils.decodeGenre(null));
        assertEquals("", ID3Utils.decodeGenre(""));
        assertEquals("f", ID3Utils.decodeGenre("f"));
        assertEquals("fo", ID3Utils.decodeGenre("fo"));
        assertEquals("foo", ID3Utils.decodeGenre("foo"));
        assertEquals("(foo)", ID3Utils.decodeGenre("(foo)"));
        assertEquals("Salsa", ID3Utils.decodeGenre("(143)"));
    }

}
