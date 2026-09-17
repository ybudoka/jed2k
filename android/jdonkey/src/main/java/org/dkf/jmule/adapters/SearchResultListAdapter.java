/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2016, FrostWire(R). All rights reserved.
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

package org.dkf.jmule.adapters;

import android.content.Context;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import org.apache.commons.io.FilenameUtils;
import org.dkf.jmule.Constants;
import org.dkf.jmule.MediaType;
import org.dkf.jed2k.protocol.Hash;
import org.dkf.jed2k.protocol.SearchEntry;
import org.dkf.jed2k.util.Ref;
import org.dkf.jmule.Engine;
import org.dkf.jmule.R;
import org.dkf.jmule.activities.MainActivity;
import org.dkf.jmule.adapters.menu.BlockSearchAction;
import org.dkf.jmule.adapters.menu.CopyToClipboardMenuAction;
import org.dkf.jmule.adapters.menu.SearchMoreAction;
import org.dkf.jmule.util.UIUtils;
import org.dkf.jmule.views.AbstractListAdapter;
import org.dkf.jmule.views.ClickAdapter;
import org.dkf.jmule.views.MenuAction;
import org.dkf.jmule.views.MenuAdapter;
import org.dkf.jmule.views.SearchFiltersView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author gubatron
 * @author aldenml
 */
public abstract class SearchResultListAdapter extends AbstractListAdapter<SearchEntry> {

    private static final Logger log = LoggerFactory.getLogger(SearchResultListAdapter.class);
    private static final int NO_FILE_TYPE = -1;

    private final OnLinkClickListener linkListener;
    private final PreviewClickListener previewClickListener;
    private boolean moreResults = false;
    private final Set<Hash> seenHashes = new HashSet<>();

    /**
     * What the file name says about a hit, worked out once.
     * <p>
     * The extension and its media type used to be recomputed from the file name on
     * every filter pass <em>and</em> on every single row bind - and the media type
     * lookup is a linear walk over nine tables of extensions. With a full result set
     * that is thousands of lookups per scroll fling and per chip tap, all of them
     * answering the same question about the same unchanging string. The answer is
     * cached the moment a result is accepted instead.
     * <p>
     * Keyed by identity: two distinct hits can carry the same name, and one hit is
     * exactly one row.
     */
    private final Map<SearchEntry, EntryMeta> metaCache = new IdentityHashMap<>();

    private int fileType;

    /** Quick filters - see {@link SearchFiltersView}. All of them are local. */
    private boolean completeOnly = false;
    private int minCompletePercent = 0;
    private int minSources = 0;
    private int sortMode = SearchFiltersView.SORT_BY_SOURCES;

    protected SearchResultListAdapter(Context context) {
        super(context, R.layout.view_bittorrent_search_result_list_item);
        this.linkListener = new OnLinkClickListener();
        this.previewClickListener = new PreviewClickListener(context, this);
        this.fileType = NO_FILE_TYPE;
    }

    public int getFileType() {
        return fileType;
    }

    public void setFileType(int fileType) {
        this.fileType = fileType;
        filter();
    }

    /**
     * Applies the quick filter bar in one go.
     * <p>
     * One call rather than a setter per chip: each one re-filters the whole result set,
     * and doing that four times to answer a single tap is three redraws of a list the
     * user is looking at.
     *
     * @param completeOnly        keep only hits with at least one complete source
     * @param minCompletePercent  minimum share of complete sources, 0 to ignore
     * @param minSources          minimum number of sources, 0 to ignore
     * @param sortMode            one of the SORT_BY_* constants
     */
    public void applyFilters(boolean completeOnly, int minCompletePercent, int minSources, int sortMode) {
        boolean orderChanged = this.sortMode != sortMode;

        this.completeOnly = completeOnly;
        this.minCompletePercent = minCompletePercent;
        this.minSources = minSources;
        this.sortMode = sortMode;

        if (orderChanged) {
            sort();
        }

        filter();
    }

    public boolean isCompleteOnly() {
        return completeOnly;
    }

    public int getMinCompletePercent() {
        return minCompletePercent;
    }

    public int getMinSources() {
        return minSources;
    }

    public int getSortMode() {
        return sortMode;
    }

    /**
     * Appends a page of results.
     * <p>
     * Servers happily repeat hits between the first page and the "more results" pages,
     * so entries whose hash is already on the list are dropped rather than shown twice.
     *
     * @return the entries actually added, so the caller can count only those
     */
    public List<SearchEntry> addResults(final List<SearchEntry> entries, boolean hasMoreResults) {
        log.info("results {} more {}", entries.size(), hasMoreResults ? "yes" : "no");
        moreResults = hasMoreResults;

        final List<SearchEntry> added = new ArrayList<>(entries.size());
        for (SearchEntry e : entries) {
            if (e != null && seenHashes.add(e.getHash())) {
                metaCache.put(e, new EntryMeta(e));
                added.add(e);
            }
        }

        if (added.size() != entries.size()) {
            log.info("dropped {} duplicate results", entries.size() - added.size());
        }

        list.addAll(added);
        sort();
        // visualList used to be fed with addAll(list). When no media filter is active
        // visualList *is* list, so every batch of results doubled the backing list and
        // the user saw each hit twice, then four times, then eight...
        filter();
        return added;
    }

    /**
     * @return true when the server said it truncated the result set. Asking it for more
     * when it did not is answered with silence, which is indistinguishable from a
     * broken button.
     */
    public boolean hasMoreResults() {
        return moreResults;
    }

    @Override
    public void clear() {
        moreResults = false;
        seenHashes.clear();
        metaCache.clear();
        super.clear();
    }


    @Override
    protected void populateView(View view, final SearchEntry entry) {
        populateFilePart(view, entry);
    }

    public void removeEntry(SearchEntry searchEntry) {
        boolean removed = list.remove(searchEntry);
        if (visualList != list) {
            visualList.remove(searchEntry);
        }
        metaCache.remove(searchEntry);
        log.info("item blocked {}", removed);
        notifyDataSetChanged();
    }

    private void populateFilePart(View view, final SearchEntry entry) {
        final EntryMeta meta = metaOf(entry);

        TextView adIndicator = findView(view, R.id.view_bittorrent_search_result_list_item_ad_indicator);
        adIndicator.setVisibility(View.GONE);

        TextView title = findView(view, R.id.view_bittorrent_search_result_list_item_title);
        title.setText(entry.getFileName());
        if (Engine.instance().hasTransfer(entry.getHash())) {
            title.setTextColor(ContextCompat.getColor(view.getContext(), R.color.warning_red));
        } else {
            title.setTextColor(ContextCompat.getColor(view.getContext(), R.color.app_text_primary));
        }

        TextView fileSize = findView(view, R.id.view_bittorrent_search_result_list_item_file_size);
        if (entry.getFileSize() > 0) {
            fileSize.setText(UIUtils.getBytesInHuman(entry.getFileSize()));
        } else {
            fileSize.setText("...");
        }

        TextView extra = findView(view, R.id.view_bittorrent_search_result_list_item_text_extra);
        extra.setText(meta.extension);

        TextView seeds = findView(view, R.id.view_bittorrent_search_result_list_item_text_seeds);
        seeds.setText(view.getContext().getString(R.string.search_item_sources, entry.getSources()));

        // Availability is the number that decides whether a download finishes, so it is
        // the one thing on the row that is allowed a colour: green once half the sources
        // hold the whole file, plain grey below that. It used to read "Complete 42%",
        // which parses as a status rather than as a share of the sources.
        TextView completeSources = findView(view, R.id.view_bittorrent_search_result_list_item_text_comp_percent);
        completeSources.setText(view.getContext().getString(R.string.search_item_complete_percent, meta.completePercent));
        completeSources.setTextColor(ContextCompat.getColor(view.getContext(),
                (meta.completePercent >= 50) ? R.color.app_text_complete_high : R.color.app_text_complete_low));

        TextView sourceLink = findView(view, R.id.view_bittorrent_search_result_list_item_text_source);

        if (entry.getSource() == SearchEntry.SOURCE_SERVER) {
            sourceLink.setText(R.string.search_item_source_server);
        } else {
            sourceLink.setText(R.string.search_source_type_dht);
        }

        // The row itself starts the download on click and shows the menu on long press.
        // This button is the one-click way to the same menu - long press with a pointer
        // you have to keep steady is not a gesture to build a UI on.
        ImageButton menuButton = findView(view, R.id.view_bittorrent_search_result_list_item_menu);
        menuButton.setTag(entry);
        menuButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                showItemMenu(v);
            }
        });
    }

    @Override
    protected void onItemClicked(View v) {
        Object tag = v.getTag();
        if (!(tag instanceof SearchEntry)) {
            return;
        }
        SearchEntry se = (SearchEntry) tag;
        if (!Engine.instance().hasTransfer(se.getHash())) {
            searchResultClicked(se);
        }
    }

    abstract protected void searchResultClicked(SearchEntry se);

    private void filter() {
        this.visualList = filter(list);
        notifyDataSetChanged();
    }

    public List<SearchEntry> filter(List<SearchEntry> results) {
        ArrayList<SearchEntry> l = new ArrayList<>();
        for (SearchEntry se : results) {
            if (accept(se, metaOf(se))) {
                l.add(se);
            }
        }

        return l;
    }

    private boolean accept(SearchEntry se, EntryMeta meta) {
        if (fileType != NO_FILE_TYPE) {
            // An extension nothing recognises maps to MediaType.TYPE_UNKNOWN, and that
            // is what the "others" tab is for. The test used to be written against a
            // null media type, which getMediaTypeForExtension never returns, so the
            // tab counted its hits in the header and then showed an empty list.
            final boolean typeMatches = meta.mediaTypeId == fileType
                    || (meta.mediaTypeId == Constants.FILE_TYPE_UNKNOWN && fileType == Constants.FILE_TYPE_OTHERS);
            if (!typeMatches) {
                return false;
            }
        }

        if (minSources > 0 && se.getSources() < minSources) {
            return false;
        }

        if (completeOnly && se.getCompleteSources() < 1) {
            return false;
        }

        if (minCompletePercent > 0 && meta.completePercent < minCompletePercent) {
            return false;
        }

        return true;
    }

    private void sort() {
        final Comparator<SearchEntry> comparator;
        switch (sortMode) {
            case SearchFiltersView.SORT_BY_SIZE:
                comparator = SIZE_DESC;
                break;
            case SearchFiltersView.SORT_BY_NAME:
                comparator = NAME_ASC;
                break;
            default:
                comparator = SOURCES_DESC;
                break;
        }

        Collections.sort(list, comparator);
    }

    private EntryMeta metaOf(SearchEntry entry) {
        EntryMeta meta = metaCache.get(entry);
        if (meta == null) {
            // Only reachable for an entry that arrived by some path other than
            // addResults; cache it too rather than recomputing it on every bind.
            meta = new EntryMeta(entry);
            metaCache.put(entry, meta);
        }
        return meta;
    }

    private static class OnLinkClickListener implements OnClickListener {

        @Override
        public void onClick(View v) {
            String url = (String) v.getTag();
            UIUtils.openURL(v.getContext(), url);
        }
    }

    private static final class PreviewClickListener extends ClickAdapter<Context> {
        final WeakReference<SearchResultListAdapter> adapterRef;

        PreviewClickListener(Context ctx, SearchResultListAdapter adapter) {
            super(ctx);
            adapterRef = Ref.weak(adapter);
        }

        @Override
        public void onClick(Context ctx, View v) {
            if (v == null) {
                return;
            }

            SearchEntry entry = (SearchEntry) v.getTag();
            log.info("request preview for {}", entry);
        }
    }

    void populateMenuActions(SearchEntry entry, List<MenuAction> actions) {
        if (entry == null) return;

        // Offered for every result, whatever the source: copying the name is how you
        // take a hit somewhere else - a web search, a note - without retyping it.
        actions.add(new CopyToClipboardMenuAction(getContext(),
                R.drawable.ic_content_copy_black_24dp,
                R.string.transfers_context_menu_copy_name,
                R.string.transfers_context_menu_copy_name_copied,
                entry.getFileName()));

        // search more is available only on server source
        if (entry.getSource() != SearchEntry.SOURCE_SERVER) return;
        // the adapter is not guaranteed to be hosted by MainActivity, a blind cast
        // turned a missing menu into a ClassCastException
        if (!(getContext() instanceof MainActivity)) return;
        MainActivity a = (MainActivity) getContext();
        if (a.getSearchFragment() != null) {
            // Only when the server actually said it had more. OP_QUERY_MORE_RESULT is
            // fire-and-forget: a server that already sent everything simply does not
            // reply, so offering the action there produced a button that visibly did
            // nothing - which is exactly how it was reported.
            if (moreResults) {
                actions.add(new SearchMoreAction(getContext(), a.getSearchFragment()));
            }
            actions.add(new BlockSearchAction(getContext(), a.getSearchFragment(), entry));
        }
    }

    protected MenuAdapter getMenuAdapter(View view) {
        Object tag = view.getTag();
        if (!(tag instanceof SearchEntry)) {
            return null;
        }
        String title = "";
        List<MenuAction> items = new ArrayList<>();
        populateMenuActions((SearchEntry) tag, items);
        return items.size() > 0 ? new MenuAdapter(view.getContext(), title, items) : null;
    }

    /** Everything about a hit that is derived from its file name or its counters. */
    private static final class EntryMeta {
        final String extension;
        final int mediaTypeId;
        final int completePercent;

        EntryMeta(SearchEntry entry) {
            final String name = entry.getFileName();
            this.extension = (name != null) ? FilenameUtils.getExtension(name) : "";

            final MediaType mt = MediaType.getMediaTypeForExtension(this.extension);
            this.mediaTypeId = (mt != null) ? mt.getId() : Constants.FILE_TYPE_UNKNOWN;

            this.completePercent = (entry.getSources() != 0)
                    ? (int) (entry.getCompleteSources() * 100L / entry.getSources())
                    : 0;
        }
    }

    private static final Comparator<SearchEntry> SOURCES_DESC = new Comparator<SearchEntry>() {
        @Override
        public int compare(final SearchEntry lhs, final SearchEntry rhs) {
            // compare rather than subtract: two counts far enough apart overflow an int
            final int bySources = Integer.compare(rhs.getSources(), lhs.getSources());
            return (bySources != 0) ? bySources : nameOf(lhs).compareToIgnoreCase(nameOf(rhs));
        }
    };

    private static final Comparator<SearchEntry> SIZE_DESC = new Comparator<SearchEntry>() {
        @Override
        public int compare(final SearchEntry lhs, final SearchEntry rhs) {
            final int bySize = Long.compare(rhs.getFileSize(), lhs.getFileSize());
            return (bySize != 0) ? bySize : nameOf(lhs).compareToIgnoreCase(nameOf(rhs));
        }
    };

    private static final Comparator<SearchEntry> NAME_ASC = new Comparator<SearchEntry>() {
        @Override
        public int compare(final SearchEntry lhs, final SearchEntry rhs) {
            return nameOf(lhs).compareToIgnoreCase(nameOf(rhs));
        }
    };

    private static String nameOf(final SearchEntry entry) {
        final String name = entry.getFileName();
        return (name != null) ? name : "";
    }
}
