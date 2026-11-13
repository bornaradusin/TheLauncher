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
        mContext = context;
        mFlags = flags;
        mBridge = FeedBridge.useBridge(context)
                ? new LauncherClientBridge(this, flags)
                : this;
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
            mContext.unbindService(mBridge);
            mConnected = false;
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
    }
}
