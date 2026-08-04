/*
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

package org.dkf.jmule.adapters.menu;

import android.content.Context;

import org.dkf.jmule.R;
import org.dkf.jmule.transfers.Transfer;
import org.dkf.jmule.util.UIUtils;
import org.dkf.jmule.views.MenuAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Asks the server and KAD for more sources for a transfer that is already running.
 * <p>
 * A transfer looks for sources on a slow schedule once it has any peer at all - twenty
 * minutes between server requests - because ed2k servers ban clients that re-ask for the
 * same file too often. That is the right default for a download left in the background
 * and the wrong one for a user watching a transfer sit at one dead peer, so this asks
 * immediately. It is still floored at one request a minute per transfer.
 */
public class FindMoreSourcesMenuAction extends MenuAction {

    private static final Logger log = LoggerFactory.getLogger(FindMoreSourcesMenuAction.class);

    private final Transfer transfer;

    public FindMoreSourcesMenuAction(Context context, Transfer transfer) {
        super(context, R.drawable.ic_search_black_24dp, R.string.transfers_context_menu_find_sources);
        this.transfer = transfer;
    }

    @Override
    protected void onClick(Context context) {
        if (context == null || transfer == null) {
            return;
        }

        final boolean requested = transfer.requestMoreSources();
        log.info("find more sources requested {}", requested ? "yes" : "no");

        UIUtils.showLongMessage(context, requested
                ? R.string.transfers_context_menu_find_sources_started
                : R.string.transfers_context_menu_find_sources_skipped);
    }
}
