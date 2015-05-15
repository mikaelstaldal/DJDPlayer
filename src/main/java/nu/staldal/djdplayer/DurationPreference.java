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
package nu.staldal.djdplayer;

import android.content.Context;
import android.content.res.TypedArray;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.NumberPicker;

/**
 * Preference for a time duration.
 */
public class DurationPreference extends DialogPreference {
    private static final int DEFAULT_VALUE = 0;

    private NumberPicker mWidget;

    private int value = DEFAULT_VALUE;

    public DurationPreference(Context context, AttributeSet attrs) {
        super(context, attrs);

        setDialogLayoutResource(R.layout.duration_dialog);
    }

    @Override
    protected void onBindDialogView(View view) {
        mWidget = (NumberPicker)view.findViewById(R.id.picker);
        mWidget.setMinValue(0);
        mWidget.setMaxValue(10);
        mWidget.setValue(value);
        super.onBindDialogView(view);
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getInteger(index, DEFAULT_VALUE);
    }

    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
        if (restorePersistedValue) {
            value = getPersistedInt(DEFAULT_VALUE);
        } else {
            value = (Integer)defaultValue;
            persistInt(value);
        }
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        if (positiveResult) {
            value = mWidget.getValue();
            persistInt(value);
        }
    }
}
