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

import android.app.Dialog;
import android.content.Context;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.*;

import androidx.core.content.ContextCompat;

import org.dkf.jed2k.PeerInfo;
import org.dkf.jed2k.TransferStatus;
import org.dkf.jed2k.Utils;
import org.dkf.jmule.ConfigurationManager;
import org.dkf.jmule.Constants;
import org.dkf.jmule.NetworkManager;
import org.dkf.jmule.Engine;
import org.dkf.jmule.R;
import org.dkf.jmule.adapters.menu.*;
import org.dkf.jmule.transfers.Transfer;
import org.dkf.jmule.util.UIUtils;
import org.dkf.jmule.views.ClickAdapter;
import org.dkf.jmule.views.MenuAction;
import org.dkf.jmule.views.MenuAdapter;
import org.dkf.jmule.views.MenuBuilder;
import org.slf4j.Logger;

import java.lang.ref.WeakReference;
import java.util.*;

/**
 * @author gubatron
 * @author aldenml
 */
public class TransferListAdapter extends BaseExpandableListAdapter {
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(TransferListAdapter.class);
    private final WeakReference<Context> context;
    private final OnClickListener viewOnClickListener;
    private final ViewOnLongClickListener viewOnLongClickListener;
    private final OpenOnClickListener playOnClickListener;

    /**
     * Keep track of all dialogs ever opened so we dismiss when we leave to avoid memory leaks
     */
    private final List<Dialog> dialogs;
    private List<Transfer> list;

    public TransferListAdapter(Context context, List<Transfer> list) {
        this.context = new WeakReference<>(context);
        this.viewOnClickListener = new ViewOnClickListener();
        this.viewOnLongClickListener = new ViewOnLongClickListener();
        this.playOnClickListener = new OpenOnClickListener(context);
        this.dialogs = new ArrayList<>();
        this.list = (list == null) ? new ArrayList<Transfer>() : list;
    }

    @Override
    public Object getChild(int groupPosition, int childPosition) {
        return list.get(groupPosition).getItems().get(childPosition);
    }

    private PeerInfo getChildItem(int groupPosition, int childPosition) {
        return list.get(groupPosition).getItems().get(childPosition);
    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
        return childPosition;
    }

    @Override
    public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {

        PeerInfo item = getChildItem(groupPosition, childPosition);

        if (convertView == null) {
            convertView = View.inflate(context.get(), R.layout.view_transfer_item_list_item, null);
            convertView.setOnClickListener(viewOnClickListener);
            convertView.setOnLongClickListener(viewOnLongClickListener);
        }

        try {
            initTouchFeedback(convertView, item);
            populateChildView(convertView, item);
        } catch (Exception e) {
            log.error("Fatal error getting view: {}", e);
        }

        return convertView;
    }

    @Override
    public int getChildrenCount(int groupPosition) {
        try {
            final Transfer transfer = list.get(groupPosition);
            return transfer.getItems().size();
        } catch (IndexOutOfBoundsException e) {
            log.error("out of bound in children count {}", e);
            return 0;
        }
    }

    @Override
    public Object getGroup(int groupPosition) {
        return list.get(groupPosition);
    }

    private Transfer getGroupItem(int groupPosition) {
        return list.get(groupPosition);
    }

    @Override
    public int getGroupCount() {
        return list.size();
    }

    @Override
    public long getGroupId(int groupPosition) {
        // the list is re-sorted on every 2s refresh, so a positional id made the
        // ExpandableListView carry the expanded state over to a different transfer
        if (groupPosition < 0 || groupPosition >= list.size()) {
            return groupPosition;
        }
        Transfer t = list.get(groupPosition);
        String hash = (t != null) ? t.getHash() : null;
        return (hash != null && !hash.isEmpty()) ? hash.hashCode() : groupPosition;
    }

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
        Transfer item = getGroupItem(groupPosition);
        ExpandableListView expandableListView = (ExpandableListView) parent;
        if (convertView == null) { //if we don't have it yet, we inflate it ourselves.
            convertView = View.inflate(context.get(), R.layout.view_transfer_list_item, null);
        }
        if (!(convertView instanceof LinearLayout)) {
            // guards against the holder staying null when the root of
            // view_transfer_list_item is not a LinearLayout
            return convertView;
        }
        LinearLayout listItemLinearLayoutHolder = (LinearLayout) convertView;

        listItemLinearLayoutHolder.setOnClickListener(viewOnClickListener);
        listItemLinearLayoutHolder.setOnLongClickListener(viewOnLongClickListener);
        listItemLinearLayoutHolder.setClickable(true);
        listItemLinearLayoutHolder.setLongClickable(true);
        listItemLinearLayoutHolder.setTag(item);

        try {
            populateGroupView(listItemLinearLayoutHolder, item);
        } catch (Exception e) {
            log.error("Not able to populate group view in expandable list: {}", e);
        }

        try {
            setupGroupIndicator(listItemLinearLayoutHolder, expandableListView, isExpanded, item, groupPosition);
        } catch (Exception e) {
            log.error("Not able to setup touch handlers for group indicator ImageView: {}", e);
        }

        return listItemLinearLayoutHolder;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return false;
    }

    public void updateList(List<Transfer> g) {
        list = g;
        notifyDataSetChanged();
    }

    public void dismissDialogs() {
        for (Dialog dialog : dialogs) {
            try {
                dialog.dismiss();
            } catch (Exception e) {
                log.warn("Error dismissing dialog {}", e);
            }
        }
        // the list was never emptied, so every dialog ever opened was retained
        // (and re-dismissed) for the lifetime of the adapter
        dialogs.clear();
    }

    @SuppressWarnings("unchecked")
    private static <TView extends View> TView findView(View view, int id) {
        return (TView) view.findViewById(id);
    }

    private void populateGroupView(View view, Transfer transfer) {
        populateTransferDownload(view, transfer);
    }

    private void populateChildView(View view, PeerInfo item) {
        populatePeerItem(view, item);
    }

    private MenuAdapter getMenuAdapter(View view) {
        Object tag = view.getTag();
        // peer (child) rows are tagged with a PeerInfo and have no menu of their own;
        // the unchecked cast used to throw a ClassCastException on every long press
        if (!(tag instanceof Transfer) || context.get() == null) {
            return null;
        }
        List<MenuAction> items = new ArrayList<>();
        String title = populateTransferDownloadMenuAction((Transfer) tag, items);
        return items.size() > 0 ? new MenuAdapter(context.get(), title, items) : null;
    }

    private String populateTransferDownloadMenuAction(Transfer download, List<MenuAction> items) {
        String title = download.getDisplayName();

        if (download.isComplete()) {
            items.add(new OpenMenuAction(context.get(), download.getFile()));
        }

//        LOG.info("download.isComplete(): " + download.isComplete());
//        LOG.info("download.isDownloading(): " + download.isDownloading());
//        LOG.info("download.isFinished(): " + download.isFinished());
//        LOG.info("download.isPaused(): " + download.isPaused());
//        LOG.info("download.isSeeding(): " + download.isSeeding());

        if (!download.isComplete()) {
            if (!download.isPaused()) {
                items.add(new PauseDownloadMenuAction(context.get(), download));
            } else {
                boolean wifiIsUp = NetworkManager.instance().isDataWIFIUp();
                boolean bittorrentOnMobileData = ConfigurationManager.instance().getBoolean(Constants.PREF_KEY_NETWORK_USE_MOBILE_DATA);
                boolean bittorrentOff = Engine.instance().isStopped();

                if (wifiIsUp || bittorrentOnMobileData) {
                    if (!download.isComplete() && !bittorrentOff) {
                        items.add(new ResumeDownloadMenuAction(context.get(), download, R.string.resume_torrent_menu_action));
                    }
                }
            }
        }

        items.add(new CopyToClipboardMenuAction(context.get(),
                R.drawable.ic_content_copy_black_24dp,
                R.string.transfers_context_menu_copy_name,
                R.string.transfers_context_menu_copy_name_copied, download.getDisplayName()));

        items.add(new CopyToClipboardMenuAction(context.get(),
                R.drawable.ic_insert_link_black_24dp,
                R.string.transfers_context_menu_copy_link,
                R.string.transfers_context_menu_copy_link_copied, download.toLink()));

        // names the sources report for this hash - see ShowRemoteFileNamesMenuAction
        items.add(new ShowRemoteFileNamesMenuAction(context.get(), download));

        // only useful while there is still something to download
        if (!download.isComplete()) {
            items.add(new FindMoreSourcesMenuAction(context.get(), download));
        }

        items.add(new CancelMenuAction(context.get(), download, !download.isComplete()));
        items.add(new ShowThePathMenuAction(context.get(), download));
        return title;
    }

    private String extractMime(Transfer download) {
        return UIUtils.getMimeType(download.getFilePath());
    }

    private void trackDialog(Dialog dialog) {
        dialogs.add(dialog);
    }

    private void setupGroupIndicator(final LinearLayout listItemMainLayout,
                                     final ExpandableListView expandableListView,
                                     final boolean expanded,
                                     final Transfer item,
                                     final int groupPosition) {
        final ImageView groupIndicator = findView(listItemMainLayout, R.id.view_transfer_list_item_group_indicator);
        groupIndicator.setClickable(true);
        boolean hasPeers = !item.getItems().isEmpty();
        prepareGroupIndicatorDrawable(item, groupIndicator, hasPeers, expanded);

        if (hasPeers) {
            groupIndicator.setOnClickListener(new GroupIndicatorClickAdapter(expandableListView, groupPosition));
        }
    }

    private void prepareGroupIndicatorDrawable(final Transfer item,
                                               final ImageView groupIndicator,
                                               final boolean hasPeers,
                                               final boolean expanded) {
        if (hasPeers) {
            groupIndicator.setImageResource(expanded ? R.drawable.transfer_menuitem_minus : R.drawable.transfer_menuitem_plus);
        } else {
            groupIndicator.setImageResource(R.drawable.browse_peer_application_icon_selector_menu);
        }
    }

    private void initTouchFeedback(View v, PeerInfo item) {
        v.setOnClickListener(viewOnClickListener);
        v.setOnLongClickListener(viewOnLongClickListener);
        v.setTag(item);

        if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;

            int count = vg.getChildCount();
            for (int i = 0; i < count; i++) {
                View child = vg.getChildAt(i);
                initTouchFeedback(child, item);
            }
        }
    }

    private void populateTransferDownload(View view, Transfer download) {
        final Context ctx = context.get();
        if (ctx == null || download == null) {
            return;
        }

        TextView title = findView(view, R.id.view_transfer_list_item_title);
        ProgressBar progress = findView(view, R.id.view_transfer_list_item_progress);
        TextView status = findView(view, R.id.view_transfer_list_item_status);
        TextView speed = findView(view, R.id.view_transfer_list_item_speed);
        TextView size = findView(view, R.id.view_transfer_list_item_size);

        TextView seeds = findView(view, R.id.view_transfer_list_item_seeds);
        //TextView hash = findView(view, R.id.view_transfer_list_item_hash);
        //hash.setText(download.getHash());

        //ImageButton buttonPlay = findView(view, R.id.view_transfer_list_item_button_play);

        seeds.setText(ctx.getString(R.string.seeds_n, formatPeers(download)));
        seeds.setVisibility(View.VISIBLE);
        //peers.setVisibility(View.VISIBLE);


        title.setText(download.getDisplayName());
        setProgress(progress, download.getProgress());
        title.setCompoundDrawables(null, null, null, null);

        if (!NetworkManager.instance().isInternetDataConnectionUp()) {
            status.setText(R.string.check_internet_connection);
            status.setTextColor(ContextCompat.getColor(ctx, R.color.warning_red));
            seeds.setText("");
        } else {
            // colour-code the state so it is readable at a glance instead of being
            // four identical grey labels
            int statusColor = R.color.app_text_secondary;
            switch (download.getState()) {
                case PAUSED:
                    status.setText(R.string.transfer_state_paused);
                    statusColor = R.color.transfer_state_paused;
                    break;
                case COMPLETED:
                    status.setText(R.string.transfer_state_completed);
                    statusColor = R.color.transfer_state_completed;
                    break;
                case DOWNLOADING:
                    status.setText(R.string.transfer_state_downloading);
                    statusColor = R.color.transfer_state_downloading;
                    break;
                case STALLED:
                    status.setText(R.string.transfer_state_stalled);
                    statusColor = R.color.transfer_state_stalled;
                    break;
                default:
                    status.setText("");
                    break;
            }
            status.setTextColor(ContextCompat.getColor(ctx, statusColor));
        }

        speed.setText(UIUtils.getBytesInHuman(download.getDownloadSpeed()) + "/s");
        size.setText(UIUtils.getBytesInHuman(download.getSize()));
/*
        if (download instanceof UIBittorrentDownload) {
            UIBittorrentDownload uidl = (UIBittorrentDownload) download;
            if (uidl.hasPaymentOptions()) {
                setPaymentOptionDrawable(uidl, title);
            }
        }

        List<TransferItem> items = download.getItems();
        if (items != null && items.size() == 1) {
            TransferItem item = items.get(0);
            buttonPlay.setTag(item);
            updatePlayButtonVisibility(item, buttonPlay);
            buttonPlay.setOnClickListener(playOnClickListener);
        } else {
            buttonPlay.setVisibility(View.GONE);
        }
        */
    }

    private static String formatPeers(Transfer dl) {
        int connectedPeers = dl.getConnectedPeers();
        int peers = dl.getTotalPeers();

        // when we know of fewer peers than we are connected to, the total is stale
        // and showing "12 / 3" would be nonsense
        if (connectedPeers > peers) {
            return String.valueOf(connectedPeers);
        }

        return connectedPeers + " / " + peers;
    }

    private void populatePeerItem(View view, PeerInfo item) {
        ImageView icon = findView(view, R.id.view_transfer_item_list_item_icon);
        TextView title = findView(view, R.id.view_transfer_item_list_item_title);
        TextView summary = findView(view, R.id.view_transfer_item_list_item_summary);
        TextView totalBytes = findView(view, R.id.view_transfer_item_list_item_total_bytes);
        TextView speed = findView(view, R.id.view_transfer_item_list_item_speed);

        // for DHT set swap icon and computer icon for all other cases
        if (Utils.isBit(item.getSourceFlag(), PeerInfo.DHT)) {
            icon.setImageResource(R.drawable.ic_swap_calls_black_24dp);
        } else {
            icon.setImageResource(R.drawable.ic_computer_black_24dp);
        }

        //ImageButton buttonPlay = findView(view, R.id.view_transfer_item_list_item_button_play);

        //icon.setImageResource(MediaType.getFileTypeIconId(FilenameUtils.getExtension(item.getFilePath().getAbsolutePath())));
        title.setText(item.getEndpoint() != null ? item.getEndpoint().toString() : "");
        totalBytes.setText(view.getResources().getString(R.string.peer_total_bytes_download,
                UIUtils.getBytesInHuman(item.getDownloadPayload() + item.getDownloadProtocol())));
        speed.setText(view.getResources().getString(R.string.peer_speed,
                UIUtils.rate2speed(item.getDownloadSpeed() / 1024)));

        // a null mod name used to be rendered literally as the text "null"
        String modVersion = (item.getStrModVersion() != null) ? item.getStrModVersion() : "";
        String modName = (item.getModName() != null) ? item.getModName() : "";
        String strSummary = modVersion.isEmpty() && modName.isEmpty()
                ? ""
                : String.format("[%s] %s", modVersion, modName).trim();
        summary.setText(strSummary);
        //buttonPlay.setTag(item);
        //updatePlayButtonVisibility(item, buttonPlay);
        //buttonPlay.setOnClickListener(playOnClickListener);
    }

    private void updatePlayButtonVisibility(PeerInfo item, ImageButton buttonPlay) {
        //if (item.isComplete()) {
            buttonPlay.setVisibility(View.GONE);
        /*} else {
            if (item instanceof BTDownloadItem) {
                buttonPlay.setVisibility(previewFile((BTDownloadItem) item) != null ? View.VISIBLE : View.GONE);
            } else {
                buttonPlay.setVisibility(View.GONE);
            }
        }
        */
    }

    private boolean showTransferItemMenu(View v) {
        try {
            MenuAdapter adapter = getMenuAdapter(v);
            if (adapter != null) {
                trackDialog(new MenuBuilder(adapter).show());
                return true;
            }
        } catch (Exception e) {
            log.error("Failed to create the menu {}", e);
        }
        return false;
    }

    // at least one phone does not provide this trivial optimization
    // TODO: move this for a more framework like place, like a Views (utils) class
    private static void setProgress(ProgressBar v, int progress) {
        int old = v.getProgress();
        if (old != progress) {
            v.setProgress(progress);
        }
    }

    private final class ViewOnClickListener implements OnClickListener {
        public void onClick(View v) {
            showTransferItemMenu(v);
        }
    }

    private final class ViewOnLongClickListener implements OnLongClickListener {
        public boolean onLongClick(View v) {
            return showTransferItemMenu(v);
        }
    }

    private static final class OpenOnClickListener extends ClickAdapter<Context> {

        public OpenOnClickListener(Context ctx) {
            super(ctx);
        }

        public void onClick(Context ctx, View v) {
            Object tag = v.getTag();
            /*
            if (tag instanceof TransferItem) {
                TransferItem item = (TransferItem) tag;

                File path = item.isComplete() ? item.getFile() : null;

                if (path == null && item instanceof BTDownloadItem) {
                    path = previewFile((BTDownloadItem) item);
                }

                if (path != null) {
                    if (path.exists()) {
                        UIUtils.openFile(ctx, path);
                    } else {
                        UIUtils.showShortMessage(ctx, R.string.cant_open_file_does_not_exist, path.getName());
                    }
                }
            } else if (tag instanceof File) {
                File path = (File) tag;
                System.out.println(path);

                if (path.exists()) {
                    UIUtils.openFile(ctx, path);
                } else {
                    UIUtils.showShortMessage(ctx, R.string.cant_open_file_does_not_exist, path.getName());
                }
            }
            */
        }
    }

    private static final class GroupIndicatorClickAdapter extends ClickAdapter<ExpandableListView> {
        private final int groupPosition;

        public GroupIndicatorClickAdapter(ExpandableListView owner, int groupPosition) {
            super(owner);
            this.groupPosition = groupPosition;
        }

        @Override
        public void onClick(ExpandableListView owner, View v) {
            if (owner.isGroupExpanded(groupPosition)) {
                owner.collapseGroup(groupPosition);
            } else {
                owner.expandGroup(groupPosition);
            }
        }
    }
}
