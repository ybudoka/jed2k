/*
 * Copyright (c) 2016-2026 jed2k contributors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.dkf.jmule.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.dkf.jmule.R;

/**
 * The row of quick filters under the search box.
 * <p>
 * Every filter here is applied to the results already in hand, which is what separates
 * this from the search parameters panel: those are sent to the server and can only be
 * changed by searching again, these cost a redraw. The user can therefore try one,
 * dislike it, and get everything back without another round trip - so the chips are
 * safe to poke at, which is the point.
 * <p>
 * The view owns the selection state and reports it as four plain values; deciding what
 * they mean is the adapter's job.
 */
public class SearchFiltersView extends LinearLayout {

    /** Most sources first - the default, and the one that usually means "fastest". */
    public static final int SORT_BY_SOURCES = 0;
    /** Largest first. */
    public static final int SORT_BY_SIZE = 1;
    /** A to Z, which is how you spot the episodes of a series among 200 hits. */
    public static final int SORT_BY_NAME = 2;

    /**
     * Thresholds behind the two source chips. A hit with ten sources downloads; one
     * with fifty downloads quickly. Anything finer than that is what the parameters
     * panel is for.
     */
    private static final int SOURCES_LOW = 10;
    private static final int SOURCES_HIGH = 50;

    /** Share of complete sources the "50%+" chip asks for. */
    private static final int HALF_COMPLETE_PERCENT = 50;

    public interface OnFiltersChangedListener {
        void onFiltersChanged(SearchFiltersView view);
    }

    private TextView chipComplete;
    private TextView chipHalf;
    private TextView chipSourcesLow;
    private TextView chipSourcesHigh;
    private TextView chipSort;
    private TextView chipMore;
    private TextView summary;
    private TextView clear;

    private boolean completeOnly;
    private boolean halfComplete;
    private int minSources;
    private int sortMode = SORT_BY_SOURCES;

    private OnFiltersChangedListener listener;

    public SearchFiltersView(Context context, AttributeSet set) {
        super(context, set);
        setOrientation(VERTICAL);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        View.inflate(getContext(), R.layout.view_search_filters, this);

        chipComplete = (TextView) findViewById(R.id.view_search_filters_chip_complete);
        chipHalf = (TextView) findViewById(R.id.view_search_filters_chip_half);
        chipSourcesLow = (TextView) findViewById(R.id.view_search_filters_chip_sources_low);
        chipSourcesHigh = (TextView) findViewById(R.id.view_search_filters_chip_sources_high);
        chipSort = (TextView) findViewById(R.id.view_search_filters_chip_sort);
        chipMore = (TextView) findViewById(R.id.view_search_filters_chip_more);
        summary = (TextView) findViewById(R.id.view_search_filters_summary);
        clear = (TextView) findViewById(R.id.view_search_filters_clear);

        chipSourcesLow.setText(getContext().getString(R.string.search_filter_sources, SOURCES_LOW));
        chipSourcesHigh.setText(getContext().getString(R.string.search_filter_sources, SOURCES_HIGH));

        chipComplete.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // the two completeness chips are two settings of one dial, not two
                // independent switches: asking for "at least one complete source" and
                // "half the sources complete" at once is one filter too many to
                // reason about, so turning one on turns the other off
                completeOnly = !completeOnly;
                if (completeOnly) {
                    halfComplete = false;
                }
                onFilterToggled();
            }
        });

        chipHalf.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                halfComplete = !halfComplete;
                if (halfComplete) {
                    completeOnly = false;
                }
                onFilterToggled();
            }
        });

        chipSourcesLow.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                minSources = (minSources == SOURCES_LOW) ? 0 : SOURCES_LOW;
                onFilterToggled();
            }
        });

        chipSourcesHigh.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                minSources = (minSources == SOURCES_HIGH) ? 0 : SOURCES_HIGH;
                onFilterToggled();
            }
        });

        // Sorting is not a filter, so it is not a toggle: the chip always shows the
        // order currently in force and each tap moves to the next one.
        chipSort.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                sortMode = (sortMode + 1) % 3;
                onFilterToggled();
            }
        });

        clear.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                reset();
            }
        });

        updateChips();
    }

    public void setOnFiltersChangedListener(OnFiltersChangedListener l) {
        this.listener = l;
    }

    /** The "more results" chip is an action, so its click goes straight to the caller. */
    public void setOnMoreResultsClickListener(OnClickListener l) {
        chipMore.setOnClickListener(l);
    }

    public void setMoreResultsAvailable(boolean available) {
        chipMore.setVisibility(available ? VISIBLE : GONE);
    }

    public boolean isCompleteOnly() {
        return completeOnly;
    }

    /**
     * @return the minimum share of complete sources a hit must have, 0 when the chip
     * is off
     */
    public int getMinCompletePercent() {
        return halfComplete ? HALF_COMPLETE_PERCENT : 0;
    }

    public int getMinSources() {
        return minSources;
    }

    public int getSortMode() {
        return sortMode;
    }

    public boolean hasActiveFilters() {
        return completeOnly || halfComplete || minSources > 0;
    }

    /**
     * Drops every filter. The sort order is deliberately left alone: it hides nothing,
     * so there is nothing to undo, and silently reordering the list under someone who
     * only wanted their results back is its own small surprise.
     */
    public void reset() {
        if (!hasActiveFilters()) {
            return;
        }
        completeOnly = false;
        halfComplete = false;
        minSources = 0;
        onFilterToggled();
    }

    /**
     * Puts the chips back the way they were, quietly.
     * <p>
     * The bar is part of the view hierarchy and the hierarchy is thrown away on a
     * rotation, while the results and the filters applied to them are not - the
     * fragment is retained. Without this the chips would come back up blank over a list
     * that is still filtered. No listener is called: nothing has changed, the bar is
     * only catching up with what is already in force.
     */
    public void setState(boolean completeOnly, int minCompletePercent, int minSources, int sortMode) {
        this.completeOnly = completeOnly;
        this.halfComplete = minCompletePercent >= HALF_COMPLETE_PERCENT;
        this.minSources = minSources;
        this.sortMode = sortMode;
        updateChips();
    }

    /**
     * @param shown    hits left after filtering
     * @param total    hits held for this search, before filtering
     */
    public void updateSummary(int shown, int total) {
        if (summary == null) {
            return;
        }

        if (shown == total) {
            summary.setText(getContext().getString(R.string.search_filter_summary_all, total));
        } else {
            summary.setText(getContext().getString(R.string.search_filter_summary, shown, total));
        }

        clear.setVisibility(hasActiveFilters() ? VISIBLE : GONE);
    }

    private void onFilterToggled() {
        updateChips();
        if (listener != null) {
            listener.onFiltersChanged(this);
        }
    }

    private void updateChips() {
        chipComplete.setSelected(completeOnly);
        chipHalf.setSelected(halfComplete);
        chipSourcesLow.setSelected(minSources == SOURCES_LOW);
        chipSourcesHigh.setSelected(minSources == SOURCES_HIGH);

        // the sort chip is always "on": there is always an order in force
        chipSort.setSelected(true);
        chipSort.setText(sortLabel());
    }

    private int sortLabel() {
        switch (sortMode) {
            case SORT_BY_SIZE:
                return R.string.search_filter_sort_size;
            case SORT_BY_NAME:
                return R.string.search_filter_sort_name;
            default:
                return R.string.search_filter_sort_sources;
        }
    }
}
