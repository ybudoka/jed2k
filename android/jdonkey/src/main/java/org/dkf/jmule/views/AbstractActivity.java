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

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.appcompat.widget.Toolbar;

import org.dkf.jmule.R;
import org.dkf.jmule.util.NightMode;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * @author gubatron
 * @author aldenml
 */
public abstract class AbstractActivity extends Activity {

    private final int layoutResId;
    private final ArrayList<String> fragmentTags;

    private boolean paused;

    /**
     * Theme setting this instance was built with, so onResume() can tell whether the
     * user changed it in the settings screen while we sat on the back stack.
     */
    private String appliedNightModeSetting;

    public AbstractActivity(int layoutResId) {
        this.layoutResId = layoutResId;
        this.fragmentTags = new ArrayList<>();

        this.paused = false;
    }

    @Override
    protected void attachBaseContext(Context newBase) {
        // applies the light/dark preference before any resource is resolved
        appliedNightModeSetting = NightMode.currentSetting();
        super.attachBaseContext(NightMode.wrap(newBase));
    }

    @Override
    public void onAttachFragment(Fragment fragment) {
        super.onAttachFragment(fragment);

        String tag = fragment.getTag();
        if (tag != null && !fragmentTags.contains(tag)) {
            fragmentTags.add(tag);
        }
    }

    @Override
    protected void onResume() {
        paused = false;
        super.onResume();

        // the theme is applied in attachBaseContext, so a change made in the settings
        // screen only reaches this activity by rebuilding it. Comparing against the
        // value we attached with keeps this from looping.
        if (appliedNightModeSetting != null
                && !appliedNightModeSetting.equals(NightMode.currentSetting())) {
            recreate();
            return;
        }
    }

    @Override
    protected void onPause() {
        paused = true;
        super.onPause();
    }

    public boolean isPaused() {
        return paused;
    }

    List<Fragment> getVisibleFragments() {
        List<Fragment> result = new LinkedList<Fragment>();

        FragmentManager fm = getFragmentManager();
        for (String tag : fragmentTags) {
            Fragment f = fm.findFragmentByTag(tag);
            if (f != null && f.isVisible()) {
                result.add(f);
            }
        }

        return result;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(layoutResId);
        initComponents(savedInstanceState);
    }

    protected abstract void initComponents(Bundle savedInstanceState);

    @SuppressWarnings("unchecked")
    protected final <T extends View> T findView(int id) {
        return (T) super.findViewById(id);
    }

    public final Toolbar findToolbar() {
        return findView(R.id.toolbar_main);
    }

    protected final void setToolbarView(View view, int gravity) {
        FrameLayout placeholder = findView(R.id.toolbar_main_placeholder);
        if (placeholder != null) {
            placeholder.removeAllViews();
        }
        if (view != null && placeholder != null) {
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            params.gravity = gravity;
            placeholder.addView(view, params);
            placeholder.setVisibility(View.VISIBLE);
        }
    }

    protected final void setToolbarView(View view) {
        setToolbarView(view, Gravity.START | Gravity.CENTER_VERTICAL);
    }
}
