package com.google.android.libraries.launcherclient;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;
import app.thelauncher.FeedBridge;

public class BaseClientService implements ServiceConnection {
    private boolean mConnected;
    private final Context mContext;
    private final int mFlags;
    private final ServiceConnection mBridge;

    BaseClientService(Context context, int flags) {
        this(context, flags, true);
    }

    /**
     * @param bridged false for a connection that only keeps the feed's process running: with
     *                TheLauncher Feed it must not open a second link to the Google app, which the
     *                companion would keep after this one ends.
     */
    BaseClientService(Context context, int flags, boolean bridged) {
        mContext = context;
        mFlags = flags;
        mBridge = FeedBridge.useBridge(context) && bridged
                ? new LauncherClientBridge(this, flags)
                : this;
    }

    /** The other end was replaced (updated or reinstalled): this binding will never come back. */
    final void rebind() {
        boolean wasConnected = mConnected;
        disconnect();
        if (wasConnected) {
            connect();
        }
    }

    public final boolean connect() {
        // TheLauncher: without the TheLauncher Feed companion a release build can't reach the
        // Google feed, and trying makes the Google app's feed service crash and back off.
        if (!mConnected && FeedBridge.Companion.getInstance(mContext).needsCompanionApp()) {
            return false;
        }
        if (!mConnected) {
            try {
                mConnected = mContext.bindService(LauncherClient.getIntent(mContext,
                        FeedBridge.useBridge(mContext)), mBridge, mFlags);
            } catch (Throwable e) {
                Log.e("LauncherClient", "Unable to connect to overlay service", e);
            }
        }
        return mConnected;
    }

    public final void disconnect() {
        if (mConnected) {
            try {
                mContext.unbindService(mBridge);
            } catch (IllegalArgumentException ignored) {
            }
            mConnected = false;
        }
    }

    @Override
    public void onBindingDied(ComponentName name) {
        onServiceDisconnected(name);
        rebind();
    }

    @Override
    public void onNullBinding(ComponentName name) {
        onServiceDisconnected(name);
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
    }
}
