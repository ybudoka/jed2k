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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
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
    private Comparator<SearchEntry> sourcesCountComparator = new SourcesCountComparator();

    private int fileType;

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
                added.add(e);
            }
        }

        if (added.size() != entries.size()) {
            log.info("dropped {} duplicate results", entries.size() - added.size());
        }

        list.addAll(added);
        Collections.sort(list, Collections.reverseOrder(sourcesCountComparator));
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
        log.info("item blocked {}", removed);
        notifyDataSetChanged();
    }

    private void populateFilePart(View view, final SearchEntry entry) {
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
        extra.setText(FilenameUtils.getExtension(entry.getFileName()));

        TextView seeds = findView(view, R.id.view_bittorrent_search_result_list_item_text_seeds);
        seeds.setText(view.getContext().getString(R.string.search_item_sources, entry.getSources()));
        TextView completeSources = findView(view, R.id.view_bittorrent_search_result_list_item_text_comp_percent);
        int completePercent = (entry.getSources() != 0)
                ? (int) (entry.getCompleteSources() * 100L / entry.getSources())
                : 0;
        completeSources.setText(view.getContext().getString(R.string.complete_sources, completePercent) + "%");

        TextView sourceLink = findView(view, R.id.view_bittorrent_search_result_list_item_text_source);

        if (entry.getSource() == SearchEntry.SOURCE_SERVER) {
            sourceLink.setText(R.string.search_item_source_server);
        } else {
            sourceLink.setText(R.string.search_source_type_dht);
        }
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
            MediaType mt;
            String extension = FilenameUtils.getExtension(se.getFileName());
            mt = MediaType.getMediaTypeForExtension(extension);

            if (accept(se, mt)) {
                l.add(se);
            }
        }

        return l;
    }

    private boolean accept(SearchEntry se, MediaType mt) {
        // no media filter chosen yet -> show everything instead of an empty list
        if (fileType == NO_FILE_TYPE) {
            return true;
        }
        return (mt != null && mt.getId() == fileType) ||
                (mt == null && fileType == Constants.FILE_TYPE_OTHERS);
    }

    private int getFileTypeIconId() {
        switch (fileType) {
            case Constants.FILE_TYPE_APPLICATIONS:
                return R.drawable.list_item_application_icon;
            case Constants.FILE_TYPE_AUDIO:
                return R.drawable.list_item_audio_icon;
            case Constants.FILE_TYPE_DOCUMENTS:
                return R.drawable.list_item_document_icon;
            case Constants.FILE_TYPE_PICTURES:
                return R.drawable.list_item_picture_icon;
            case Constants.FILE_TYPE_VIDEOS:
                return R.drawable.list_item_video_icon;
            case Constants.FILE_TYPE_TORRENTS:
                return R.drawable.list_item_torrent_icon;
            default:
                return R.drawable.list_item_question_mark;
        }
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

    private static final class SourcesCountComparator implements Comparator<SearchEntry> {
        public int compare(final SearchEntry lhs, SearchEntry rhs) {
            try {
                return Integer.signum(lhs.getSources() - rhs.getSources());
            } catch (Exception e) {
                // ignore, not really super important
            }
            return 0;
        }
    }
}
