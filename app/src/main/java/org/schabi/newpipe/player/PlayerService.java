/*
 * Copyright 2017 Mauricio Colli <mauriciocolli@outlook.com>
 * Part of NewPipe
 *
 * License: GPL-3.0+
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
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.schabi.newpipe.player;

import static org.schabi.newpipe.util.Localization.assureCorrectAppLanguage;

import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.media.MediaBrowserCompat.MediaItem;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media.MediaBrowserServiceCompat;

import org.schabi.newpipe.player.mediasession.MediaSessionPlayerUi;
import org.schabi.newpipe.util.ThemeHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * One service for all players.
 */
public final class PlayerService extends MediaBrowserServiceCompat {
    private static final String TAG = PlayerService.class.getSimpleName();
    private static final boolean DEBUG = Player.DEBUG;

    private Player player;

    private final IBinder mBinder = new PlayerService.LocalBinder();


    /*//////////////////////////////////////////////////////////////////////////
    // Service's LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreate() {
        super.onCreate();

        if (DEBUG) {
            Log.d(TAG, "onCreate() called");
        }
        assureCorrectAppLanguage(this);
        ThemeHelper.setTheme(this);

        player = new Player(this);
    }

    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {
        if (DEBUG) {
            Log.d(TAG, "onStartCommand() called with: intent = [" + intent
                    + "], flags = [" + flags + "], startId = [" + startId + "]");
        }

        if (Intent.ACTION_MEDIA_BUTTON.equals(intent.getAction())
                && player.getPlayQueue() == null) {
            // No need to process media button's actions if the player is not working, otherwise the
            // player service would strangely start with nothing to play
            return START_NOT_STICKY;
        }

        player.handleIntent(intent);
        player.UIs().get(MediaSessionPlayerUi.class)
                .ifPresent(ui -> ui.handleMediaButtonIntent(intent));

        return START_NOT_STICKY;
    }

    public void stopForImmediateReusing() {
        if (DEBUG) {
            Log.d(TAG, "stopForImmediateReusing() called");
        }

        if (!player.exoPlayerIsNull()) {
            player.saveWasPlaying();

            // Releases wifi & cpu, disables keepScreenOn, etc.
            // We can't just pause the player here because it will make transition
            // from one stream to a new stream not smooth
            player.smoothStopForImmediateReusing();
        }
    }

    @Override
    public void onTaskRemoved(final Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        if (!player.videoPlayerSelected()) {
            return;
        }
        onDestroy();
        // Unload from memory completely
        Runtime.getRuntime().halt(0);
    }

    @Override
    public void onDestroy() {
        if (DEBUG) {
            Log.d(TAG, "destroy() called");
        }
        cleanup();
    }

    private void cleanup() {
        if (player != null) {
            player.destroy();
            player = null;
        }
    }

    public void stopService() {
        cleanup();
        stopSelf();
    }

    @Override
    protected void attachBaseContext(final Context base) {
        super.attachBaseContext(AudioServiceLeakFix.preventLeakOf(base));
    }

    @Override
    public IBinder onBind(final Intent intent) {
        if (SERVICE_INTERFACE.equals(intent.getAction())) {
            return super.onBind(intent);
        }
        return mBinder;
    }

    public class LocalBinder extends Binder {

        public PlayerService getService() {
            return PlayerService.this;
        }

        public Player getPlayer() {
            return PlayerService.this.player;
        }
    }

    // MediaBrowserServiceCompat methods

    @NonNull
    private static final String MY_MEDIA_ROOT_ID = "media_root_id";

    @Nullable
    @Override
    public BrowserRoot onGetRoot(@NonNull final String clientPackageName, final int clientUid,
                                 @Nullable final Bundle rootHints) {
        Log.d(TAG, String.format("MediaBrowserService.onGetRoot(%s, %s, %s)",
                                 clientPackageName, clientUid, rootHints));

        return new MediaBrowserServiceCompat.BrowserRoot(MY_MEDIA_ROOT_ID, null);
    }

    @Override
    public void onLoadChildren(@NonNull final String parentId,
                               @NonNull final Result<List<MediaItem>> result) {
        Log.d(TAG, String.format("MediaBrowserService.onLoadChildren(%s)", parentId));

        final List<MediaItem> mediaItems = new ArrayList<>();

        result.sendResult(mediaItems);
    }
}
