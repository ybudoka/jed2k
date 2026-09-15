/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
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

package org.dkf.jmule.views;

import android.content.Context;
import android.graphics.drawable.Drawable;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import org.dkf.jed2k.util.Ref;
import org.dkf.jmule.R;

import java.lang.ref.WeakReference;

/**
 * @author gubatron
 * @author aldenml
 *
 */
public abstract class MenuAction {

    private final WeakReference<Context> contextRef;
    private final Drawable image;
    private final String text;

    public MenuAction(Context context, Drawable image, String text) {
        this.contextRef = new WeakReference<Context>(context);
        this.image = tint(context, image);
        this.text = text;
    }

    /**
     * Menu icons are monochrome silhouettes painted black, drawn by MenuAdapter as a
     * compound drawable on a dialog row. Under the dark theme that row is dark, so the
     * icons were invisible. Tinting from the text colour makes them follow the theme.
     * <p>
     * The drawable has to be mutated first: getDrawable() hands out instances that
     * share one ConstantState, so tinting without it would recolour the same icon
     * everywhere else it is used, including on the navy chrome where it must stay
     * white.
     */
    private static Drawable tint(Context context, Drawable image) {
        if (context == null || image == null) {
            return image;
        }

        try {
            Drawable copy = DrawableCompat.wrap(image.mutate());
            DrawableCompat.setTint(copy, ContextCompat.getColor(context, R.color.icon_tint));
            return copy;
        } catch (Throwable t) {
            // an untinted icon is worse than a tinted one, not worse than no menu
            return image;
        }
    }

    public MenuAction(Context context, int imageId, String text) {
        this(context, context.getResources().getDrawable(imageId), text);
    }

    public MenuAction(Context context, int imageId, int textId) {
        this(context, context.getResources().getDrawable(imageId), context.getResources().getString(textId));
    }

    public MenuAction(Context context, int imageId, int textId, Object... formatArgs) {
        this(context, imageId, context.getResources().getString(textId, formatArgs));
    }

    public String getText() {
        return text;
    }

    public Drawable getImage() {
        return image;
    }

    public final void onClick() {
        if (contextRef.get() != null) {
            onClick(contextRef.get());
        }
    }

    public Context getContext() {
        Context result = null;
        if (Ref.alive(contextRef)) {
            result = contextRef.get();
        }
        return result;
    }

    protected abstract void onClick(Context context);
}