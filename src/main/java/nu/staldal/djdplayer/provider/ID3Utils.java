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
package nu.staldal.djdplayer.provider;

public class ID3Utils {
    private static final String[] GENRE_NAMES = new String[256];

    static {
        GENRE_NAMES[0] = "Blues";
        GENRE_NAMES[1] = "Classic Rock";
        GENRE_NAMES[2] = "Country";
        GENRE_NAMES[3] = "Dance";
        GENRE_NAMES[4] = "Disco";
        GENRE_NAMES[5] = "Funk";
        GENRE_NAMES[6] = "Grunge";
        GENRE_NAMES[7] = "Hip-Hop";
        GENRE_NAMES[8] = "Jazz";
        GENRE_NAMES[9] = "Metal";
        GENRE_NAMES[10] = "New Age";
        GENRE_NAMES[11] = "Oldies";
        GENRE_NAMES[12] = "Other";
        GENRE_NAMES[13] = "Pop";
        GENRE_NAMES[14] = "R&B";
        GENRE_NAMES[15] = "Rap";
        GENRE_NAMES[16] = "Reggae";
        GENRE_NAMES[17] = "Rock";
        GENRE_NAMES[18] = "Techno";
        GENRE_NAMES[19] = "Industrial";
        GENRE_NAMES[20] = "Alternative";
        GENRE_NAMES[21] = "Ska";
        GENRE_NAMES[22] = "Death Metal";
        GENRE_NAMES[23] = "Pranks";
        GENRE_NAMES[24] = "Soundtrack";
        GENRE_NAMES[25] = "Euro-Techno";
        GENRE_NAMES[26] = "Ambient";
        GENRE_NAMES[27] = "Trip-Hop";
        GENRE_NAMES[28] = "Vocal";
        GENRE_NAMES[29] = "Jazz+Funk";
        GENRE_NAMES[30] = "Fusion";
        GENRE_NAMES[31] = "Trance";
        GENRE_NAMES[32] = "Classical";
        GENRE_NAMES[33] = "Instrumental";
        GENRE_NAMES[34] = "Acid";
        GENRE_NAMES[35] = "House";
        GENRE_NAMES[36] = "Game";
        GENRE_NAMES[37] = "Sound Clip";
        GENRE_NAMES[38] = "Gospel";
        GENRE_NAMES[39] = "Noise";
        GENRE_NAMES[40] = "Alternative Rock";
        GENRE_NAMES[41] = "Bass";
        GENRE_NAMES[42] = "Soul";
        GENRE_NAMES[43] = "Punk";
        GENRE_NAMES[44] = "Space";
        GENRE_NAMES[45] = "Meditative";
        GENRE_NAMES[46] = "Instrumental Pop";
        GENRE_NAMES[47] = "Instrumental Rock";
        GENRE_NAMES[48] = "Ethnic";
        GENRE_NAMES[49] = "Gothic";
        GENRE_NAMES[50] = "Darkwave";
        GENRE_NAMES[51] = "Techno-Industrial";
        GENRE_NAMES[52] = "Electronic";
        GENRE_NAMES[53] = "Pop-Folk";
        GENRE_NAMES[54] = "Eurodance";
        GENRE_NAMES[55] = "Dream";
        GENRE_NAMES[56] = "Southern Rock";
        GENRE_NAMES[57] = "Comedy";
        GENRE_NAMES[58] = "Cult";
        GENRE_NAMES[59] = "Gangsta";
        GENRE_NAMES[60] = "Top 40";
        GENRE_NAMES[61] = "Christian Rap";
        GENRE_NAMES[62] = "Pop/Funk";
        GENRE_NAMES[63] = "Jungle";
        GENRE_NAMES[64] = "Native US";
        GENRE_NAMES[65] = "Cabaret";
        GENRE_NAMES[66] = "New Wave";
        GENRE_NAMES[67] = "Psychadelic";
        GENRE_NAMES[68] = "Rave";
        GENRE_NAMES[69] = "Showtunes";
        GENRE_NAMES[70] = "Trailer";
        GENRE_NAMES[71] = "Lo-Fi";
        GENRE_NAMES[72] = "Tribal";
        GENRE_NAMES[73] = "Acid Punk";
        GENRE_NAMES[74] = "Acid Jazz";
        GENRE_NAMES[75] = "Polka";
        GENRE_NAMES[76] = "Retro";
        GENRE_NAMES[77] = "Musical";
        GENRE_NAMES[78] = "Rock & Roll";
        GENRE_NAMES[79] = "Hard Rock";
        GENRE_NAMES[80] = "Folk";
        GENRE_NAMES[81] = "Folk-Rock";
        GENRE_NAMES[82] = "National Folk";
        GENRE_NAMES[83] = "Swing";
        GENRE_NAMES[84] = "Fast Fusion";
        GENRE_NAMES[85] = "Bebob";
        GENRE_NAMES[86] = "Latin";
        GENRE_NAMES[87] = "Revival";
        GENRE_NAMES[88] = "Celtic";
        GENRE_NAMES[89] = "Bluegrass";
        GENRE_NAMES[90] = "Avantgarde";
        GENRE_NAMES[91] = "Gothic Rock";
        GENRE_NAMES[92] = "Progressive Rock";
        GENRE_NAMES[93] = "Psychedelic Rock";
        GENRE_NAMES[94] = "Symphonic Rock";
        GENRE_NAMES[95] = "Slow Rock";
        GENRE_NAMES[96] = "Big Band";
        GENRE_NAMES[97] = "Chorus";
        GENRE_NAMES[98] = "Easy Listening";
        GENRE_NAMES[99] = "Acoustic";
        GENRE_NAMES[100] = "Humour";
        GENRE_NAMES[101] = "Speech";
        GENRE_NAMES[102] = "Chanson";
        GENRE_NAMES[103] = "Opera";
        GENRE_NAMES[104] = "Chamber Music";
        GENRE_NAMES[105] = "Sonata";
        GENRE_NAMES[106] = "Symphony";
        GENRE_NAMES[107] = "Booty Bass";
        GENRE_NAMES[108] = "Primus";
        GENRE_NAMES[109] = "Porn Groove";
        GENRE_NAMES[110] = "Satire";
        GENRE_NAMES[111] = "Slow Jam";
        GENRE_NAMES[112] = "Club";
        GENRE_NAMES[113] = "Tango";
        GENRE_NAMES[114] = "Samba";
        GENRE_NAMES[115] = "Folklore";
        GENRE_NAMES[116] = "Ballad";
        GENRE_NAMES[117] = "Power Ballad";
        GENRE_NAMES[118] = "Rhythmic Soul";
        GENRE_NAMES[119] = "Freestyle";
        GENRE_NAMES[120] = "Duet";
        GENRE_NAMES[121] = "Punk Rock";
        GENRE_NAMES[122] = "Drum Solo";
        GENRE_NAMES[123] = "Acapella";
        GENRE_NAMES[124] = "Euro-House";
        GENRE_NAMES[125] = "Dance Hall";
        GENRE_NAMES[126] = "Goa";
        GENRE_NAMES[127] = "Drum & Bass";
        GENRE_NAMES[128] = "Club - House";
        GENRE_NAMES[129] = "Hardcore";
        GENRE_NAMES[130] = "Terror";
        GENRE_NAMES[131] = "Indie";
        GENRE_NAMES[132] = "BritPop";
        GENRE_NAMES[133] = "Negerpunk";
        GENRE_NAMES[134] = "Polsk Punk";
        GENRE_NAMES[135] = "Beat";
        GENRE_NAMES[136] = "Christian Gangsta Rap";
        GENRE_NAMES[137] = "Heavy Metal";
        GENRE_NAMES[138] = "Black Metal";
        GENRE_NAMES[139] = "Crossover";
        GENRE_NAMES[140] = "Contemporary Christian";
        GENRE_NAMES[141] = "Christian Rock";
        GENRE_NAMES[142] = "Merengue";
        GENRE_NAMES[143] = "Salsa";
        GENRE_NAMES[144] = "Thrash Metal";
        GENRE_NAMES[145] = "Anime";
        GENRE_NAMES[146] = "JPop";
        GENRE_NAMES[147] = "Synthpop";
        GENRE_NAMES[148] = "Rock/Pop";
    }

    public static String decodeGenre(String genre) {
        if (genre != null && genre.length() > 2 && genre.charAt(0) == '(' && genre.charAt(genre.length()-1) == ')') {
            try {
                return GENRE_NAMES[Integer.parseInt(genre.substring(1, genre.length()-1))];
            } catch (NumberFormatException e) {
                return genre;
            } catch (ArrayIndexOutOfBoundsException e) {
                return genre;
            }
        } else {
            try {
                return GENRE_NAMES[Integer.parseInt(genre)];
            } catch (NumberFormatException e) {
                return genre;
            } catch (ArrayIndexOutOfBoundsException e) {
                return genre;
            }
        }
    }
}
