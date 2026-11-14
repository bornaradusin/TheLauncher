package app.thelauncher.feed;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;

import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import amirz.aidlbridge.IBridge;
import amirz.aidlbridge.IBridgeCallback;

/**
 * Lets TheLauncher show the Google feed. The Google app shows its feed only to debuggable apps,
 * and TheLauncher isn't one; this small debuggable app connects to the Google app and hands the
 * connection over to TheLauncher (the same bridge protocol TheLauncher already speaks).
 * It serves only apps signed with the same key as itself.
 */
public class FeedBridgeService extends Service {
    private static final String TAG = "TheLauncherFeed";
    private static final String GOOGLE_APP = "com.google.android.googlequicksearchbox";
    private static final String OVERLAY_ACTION = "com.android.launcher3.WINDOW_OVERLAY";

    private final List<ServiceConnection> mConnections = new ArrayList<>();
    /** One link to the Google app per TheLauncher caller; a new request replaces the old link. */
    private final Map<IBinder, ServiceConnection> mByCaller = new HashMap<>();
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private static final long REBIND_DELAY_MS = 1000;
    private volatile String mVersion = "7";
    private volatile String mClientVersion = "9";

    private final IBridge.Stub mBridge = new IBridge.Stub() {
        @Override
        public void bindService(IBridgeCallback cb, int flags) {
            if (cb == null) return;
            if (!isTrusted(Binder.getCallingUid())) {
                Log.w(TAG, "Refused an app not signed like TheLauncher");
                return;
            }
            connect(cb, flags);
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        Uri data = intent.getData();
        if (data != null) {
            String v = data.getQueryParameter("v");
            String cv = data.getQueryParameter("cv");
            if (v != null) mVersion = v;
            if (cv != null) mClientVersion = cv;
        }
        return mBridge;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        List<ServiceConnection> all;
        synchronized (mConnections) {
            all = new ArrayList<>(mConnections);
            mConnections.clear();
            mByCaller.clear();
        }
        for (ServiceConnection c : all) {
            try {
                unbindService(c);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return false;
    }

    private boolean isTrusted(int uid) {
        return uid == Process.myUid()
                || getPackageManager().checkSignatures(Process.myUid(), uid)
                        == PackageManager.SIGNATURE_MATCH;
    }

    /** Binds to the Google app as this app, and passes the Google app's answer to [cb]. */
    private void connect(IBridgeCallback cb, int flags) {
        ServiceConnection previous;
        synchronized (mConnections) {
            previous = mByCaller.remove(cb.asBinder());
        }
        // The same caller asking again means its old link is finished: drop it, or the Google
        // app ends up with a pile of stale clients and stops answering until it is force stopped.
        if (previous != null) disconnect(previous);

        ServiceConnection connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                try {
                    // Every call to the Google app goes through this app, so the Google app sees
                    // this (debuggable) app calling, not TheLauncher, which it would ignore.
                    cb.onServiceConnected(name, new CallForwarder(service));
                } catch (RemoteException e) {
                    disconnect(this);
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                try {
                    cb.onServiceDisconnected(name);
                } catch (RemoteException ignored) {
                }
            }

            /** The Google app was updated or reinstalled: this link never comes back on its own. */
            @Override
            public void onBindingDied(ComponentName name) {
                onServiceDisconnected(name);
                disconnect(this);
                mHandler.postDelayed(() -> {
                    if (cb.asBinder().isBinderAlive()) connect(cb, flags);
                }, REBIND_DELAY_MS);
            }

            @Override
            public void onNullBinding(ComponentName name) {
                disconnect(this);
            }
        };
        Intent intent = new Intent(OVERLAY_ACTION)
                .setPackage(GOOGLE_APP)
                .setData(Uri.parse("app://" + getPackageName() + ":" + Process.myUid())
                        .buildUpon()
                        .appendQueryParameter("v", mVersion)
                        .appendQueryParameter("cv", mClientVersion)
                        .build());
        boolean bound;
        try {
            bound = bindService(intent, connection, flags | Context.BIND_AUTO_CREATE);
        } catch (SecurityException e) {
            Log.e(TAG, "The Google app refused the connection", e);
            bound = false;
        }
        if (!bound) {
            Log.w(TAG, "Couldn't reach the Google app's feed");
            return;
        }
        synchronized (mConnections) {
            mConnections.add(connection);
            mByCaller.put(cb.asBinder(), connection);
        }
        try {
            cb.asBinder().linkToDeath(() -> disconnect(connection), 0);
        } catch (RemoteException e) {
            disconnect(connection);
        }
    }

    /** Passes each call on to the Google app, from this app. */
    private static final class CallForwarder extends Binder {
        private final IBinder mTarget;

        CallForwarder(IBinder target) {
            mTarget = target;
        }

        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            return mTarget.transact(code, data, reply, flags);
        }
    }

    private void disconnect(ServiceConnection connection) {
        boolean known;
        synchronized (mConnections) {
            known = mConnections.remove(connection);
            mByCaller.values().remove(connection);
        }
        if (known) {
            try {
                unbindService(connection);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }
}
