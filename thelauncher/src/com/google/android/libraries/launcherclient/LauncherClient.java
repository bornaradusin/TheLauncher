package com.google.android.libraries.launcherclient;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Point;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;

import androidx.annotation.NonNull;

import app.thelauncher.FeedBridge;
import app.thelauncher.FeedBridge.BridgeInfo;
import java.lang.ref.WeakReference;

public class LauncherClient {
    private static int apiVersion = -1;

    private ILauncherOverlay mOverlay;
    public final IScrollCallback mScrollCallback;

    public final BaseClientService mBaseService;
    public final LauncherClientService mLauncherService;

    public final BroadcastReceiver googleInstallListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            reconnect();
        }
    };

    private int mActivityState = 0;
    int mServiceState = 0;
    public int mFlags;

    public LayoutParams mLayoutParams;
    public OverlayCallback mOverlayCallback;
    public final Activity mActivity;

    public boolean mDestroyed = false;
    private Bundle mLayoutBundle;

    public static class OverlayCallback extends ILauncherOverlayCallback.Stub implements Callback {
        public LauncherClient mClient;
        private final Handler mUIHandler = new Handler(Looper.getMainLooper(), this);
        public Window mWindow;
        private boolean mWindowHidden = false;
        public WindowManager mWindowManager;
        int mWindowShift;

        public void destroy() {
            mClient = null;
            mWindow = null;
            mWindowManager = null;
            mUIHandler.removeCallbacksAndMessages(null);
        }

        @Override
        public final void overlayScrollChanged(float f) {
            mUIHandler.removeMessages(2);
            Message.obtain(mUIHandler, 2, f).sendToTarget();
            if (f > 0f && mWindowHidden) {
                mWindowHidden = false;
            }
        }

        @Override
        public final void overlayStatusChanged(int i) {
            Message.obtain(mUIHandler, 4, i, 0).sendToTarget();
        }

        @Override
        public boolean handleMessage(@NonNull Message message) {
            if (mClient == null) {
                return true;
            }

            switch (message.what) {
                case 2:
                    if ((mClient.mServiceState & 1) != 0) {
                        float floatValue = (float) message.obj;
                        mClient.mScrollCallback.onOverlayScrollChanged(floatValue);
                    }
                    return true;
                case 3:
                    WindowManager.LayoutParams attributes = mWindow.getAttributes();
                    if ((Boolean) message.obj) {
                        attributes.x = mWindowShift;
                        attributes.flags |= 512;
                    } else {
                        attributes.x = 0;
                        attributes.flags &= -513;
                    }
                    mWindowManager.updateViewLayout(mWindow.getDecorView(), attributes);
                    return true;
                case 4:
                    mClient.setServiceState(message.arg1);
                    if (mClient.mScrollCallback instanceof ISerializableScrollCallback) {
                        ((ISerializableScrollCallback) mClient.mScrollCallback).setPersistentFlags(message.arg1);
                    }
                    return true;
                default:
                    return false;
            }
        }
    }

    public LauncherClient(Activity activity, IScrollCallback scrollCallback, StaticInteger flags) {
        mActivity = activity;
        mScrollCallback = scrollCallback;
        mBaseService = new BaseClientService(activity, Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT,
                !usesOwnBridge(activity));
        mFlags = flags.mData;

        mLauncherService = LauncherClientService.getInstance(activity);
        mLauncherService.mClient = new WeakReference<>(this);
        mOverlay = mLauncherService.mOverlay;

        IntentFilter intentFilter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        intentFilter.addDataScheme("package");
        intentFilter.addDataSchemeSpecificPart("com.google.android.googlequicksearchbox", 0);
        // TheLauncher: and when TheLauncher Feed is installed or updated, which ends the old link.
        intentFilter.addDataSchemeSpecificPart(app.thelauncher.FeedBridge.OWN_BRIDGE_PACKAGE, 0);
        mActivity.registerReceiver(googleInstallListener, intentFilter);

        if (apiVersion <= 0) {
            loadApiVersion(activity);
        }

        connect();
        if (mActivity.getWindow() != null &&
                mActivity.getWindow().peekDecorView() != null &&
                mActivity.getWindow().peekDecorView().isAttachedToWindow()) {
            onAttachedToWindow();
        }
    }

    public void setEnableFeed(boolean enable) {
        if (enable) {
            mFlags |= 1;
        } else {
            mFlags &= ~1;
        }
    }

    public final void onAttachedToWindow() {
        if (!mDestroyed) {
            setLayoutParams(mActivity.getWindow().getAttributes());
        }
    }

    public final void onDetachedFromWindow() {
        if (!mDestroyed) {
            setLayoutParams(null);
        }
    }

    public final void onResume() {
        if (!mDestroyed) {
            mActivityState |= 2;
            reconnectIfFeedStuck();
            if (mOverlay != null && mLayoutParams != null) {
                try {
                    if (apiVersion < 4) {
                        mOverlay.onResume();
                    } else {
                        mOverlay.setActivityState(mActivityState);
                    }
                } catch (RemoteException ignored) {
                }
            }
        }
    }

    public final void onPause() {
        if (!mDestroyed) {
            mActivityState &= -3;
            if (mOverlay != null && mLayoutParams != null) {
                try {
                    if (apiVersion < 4) {
                        mOverlay.onPause();
                    } else {
                        mOverlay.setActivityState(mActivityState);
                    }
                } catch (RemoteException ignored) {
                }
            }
        }
    }

    public final void onStart() {
        if (!mDestroyed) {
            mLauncherService.setStopped(false);
            connect();
            mActivityState |= 1;
            if (mOverlay != null && mLayoutParams != null) {
                try {
                    mOverlay.setActivityState(mActivityState);
                } catch (RemoteException ignored) {
                }
            }
        }
    }

    public final void onStop() {
        if (!mDestroyed) {
            mLauncherService.setStopped(true);
            mBaseService.disconnect();
            mActivityState &= -2;
            if (!(mOverlay == null || mLayoutParams == null)) {
                try {
                    mOverlay.setActivityState(mActivityState);
                } catch (RemoteException ignored) {
                }
            }
        }
    }

    public void onDestroy() {
        mDestroyed = true;
        try {
            mActivity.unregisterReceiver(googleInstallListener);
        } catch (Exception ignored) {
            // LC-Ignored
        }
        if (mOverlayCallback != null) {
            mOverlayCallback.destroy();
            mOverlayCallback = null;
        }
        if (mLauncherService.mClient != null && mLauncherService.mClient.get() == this) {
            mLauncherService.mClient = null;
        }
        mBaseService.disconnect();
    }

    private void connect() {
        if (mLastConnectAttempt == 0) mLastConnectAttempt = android.os.SystemClock.uptimeMillis();
        if (!mDestroyed && (!mLauncherService.connect() || !mBaseService.connect())) {
            mActivity.runOnUiThread(() -> setServiceState(0));
        }
    }

    public void reconnect() {
        mLastConnectAttempt = android.os.SystemClock.uptimeMillis();
        mBaseService.disconnect();
        mLauncherService.disconnect();
        // The old link is gone; don't keep talking to it, and let the next one attach afresh.
        mLauncherService.mOverlay = null;
        mOverlay = null;
        setServiceState(0);
        LauncherClient.loadApiVersion(mActivity);
        // Connect again whenever the launcher is on screen, not only when it is resumed: an update
        // of the Google app or the companion often lands while the launcher sits behind another app.
        if ((mActivityState & 1) != 0) {
            connect();
        }
    }

    private long mLastConnectAttempt = 0;
    private static final long STUCK_FEED_RETRY_MS = 30_000;

    /**
     * The feed should be there but isn't (the Google app dropped the link and never came back,
     * which used to need a force stop of the Google app): start over.
     */
    private void reconnectIfFeedStuck() {
        if (mDestroyed || (mFlags & 1) == 0 || (mServiceState & 1) != 0) return;
        long now = android.os.SystemClock.uptimeMillis();
        if (now - mLastConnectAttempt < STUCK_FEED_RETRY_MS) return;
        if (app.thelauncher.FeedBridge.Companion.getInstance(mActivity).needsCompanionApp()) return;
        android.util.Log.i("LauncherClient", "Feed not attached, reconnecting");
        reconnect();
    }

    private static boolean usesOwnBridge(Context context) {
        if (!FeedBridge.useBridge(context)) return false;
        BridgeInfo bridge = FeedBridge.Companion.getInstance(context).resolveBridge();
        return bridge != null && app.thelauncher.FeedBridge.OWN_BRIDGE_PACKAGE.equals(bridge.getPackageName());
    }

    public final void setLayoutParams(LayoutParams layoutParams) {
        if (mLayoutParams != layoutParams) {
            mLayoutParams = layoutParams;
            if (mLayoutParams != null) {
                exchangeConfig();
            } else if (mOverlay != null) {
                try {
                    mOverlay.windowDetached(mActivity.isChangingConfigurations());
                } catch (RemoteException ignored) {
                }
                mOverlay = null;
            }
        }
    }

    public final void exchangeConfig() {
        if (mOverlay != null) {
            try {
                if (mOverlayCallback == null) {
                    mOverlayCallback = new OverlayCallback();
                }
                OverlayCallback overlayCallback = mOverlayCallback;
                overlayCallback.mClient = this;
                overlayCallback.mWindowManager = mActivity.getWindowManager();
                Point point = new Point();
                overlayCallback.mWindowManager.getDefaultDisplay().getRealSize(point);
                overlayCallback.mWindowShift = -Math.max(point.x, point.y);
                overlayCallback.mWindow = mActivity.getWindow();
                if (apiVersion < 3) {
                    mOverlay.windowAttached(mLayoutParams, mOverlayCallback, mFlags);
                } else {
                    Bundle bundle = new Bundle();
                    bundle.putParcelable("layout_params", mLayoutParams);
                    bundle.putParcelable("configuration", mActivity.getResources().getConfiguration());
                    bundle.putInt("client_options", mFlags);
                    if (mLayoutBundle != null) {
                        bundle.putAll(mLayoutBundle);
                    }
                    mOverlay.windowAttached2(bundle, mOverlayCallback);
                }
                if (apiVersion >= 4) {
                    mOverlay.setActivityState(mActivityState);
                } else if ((mActivityState & 2) != 0) {
                    mOverlay.onResume();
                } else {
                    mOverlay.onPause();
                }
            } catch (RemoteException ignored) {
            }
        }
    }

    private boolean isConnected() {
        return mOverlay != null;
    }

    public final void startScroll() {
        if (isConnected()) {
            try {
                mOverlay.startScroll();
            } catch (RemoteException ignored) {
            }
        }
    }

    public final void endScroll() {
        if (isConnected()) {
            try {
                mOverlay.endScroll();
            } catch (RemoteException ignored) {
            }
        }
    }

    public final void setScroll(float f) {
        if (isConnected()) {
            try {
                mOverlay.onScroll(f);
            } catch (RemoteException ignored) {
            }
        }
    }

    private int verifyAndGetAnimationFlags(int duration) {
        if ((duration <= 0) || (duration > 2047)) {
            throw new IllegalArgumentException("Invalid duration");
        }
        return 0x1 | duration << 2;
    }

    public final void hideOverlay(boolean feedRunning) {
        if (mOverlay != null) {
            try {
                mOverlay.closeOverlay(feedRunning ? 1 : 0);
            } catch (RemoteException ignored) {
            }
        }
    }

    public final void hideOverlay(int duration) {
        if (mOverlay != null) {
            try {
                mOverlay.closeOverlay(verifyAndGetAnimationFlags(duration));
            } catch (RemoteException ignored) {
            }
        }
    }

    // Only used for accessibility
    public final void showOverlay(boolean feedRunning) {
        if (mOverlay != null) {
            try {
                mOverlay.openOverlay(feedRunning ? 1 : 0);
            } catch (RemoteException ignored) {
            }
        }
    }

    public final boolean startSearch(byte[] bArr, Bundle bundle) {
        if (apiVersion >= 6 && mOverlay != null) {
            try {
                return mOverlay.startSearch(bArr, bundle);
            } catch (Throwable e) {
                Log.e("DrawerOverlayClient", "Error starting session for search", e);
            }
        }
        return false;
    }

    public final void redraw(Bundle layoutBundle) {
        mLayoutBundle = layoutBundle;
        if (mLayoutParams != null && apiVersion >= 7) {
            exchangeConfig();
        }
    }

    public final void redraw() {
        if (mLayoutParams != null && apiVersion >= 7) {
            exchangeConfig();
        }
    }

    final void setOverlay(ILauncherOverlay overlay) {
        mOverlay = overlay;
        if (mOverlay == null) {
            setServiceState(0);
        } else if (mLayoutParams != null) {
            exchangeConfig();
        }
    }

    void setServiceState(int serviceState) {
        if (mServiceState != serviceState) {
            mServiceState = serviceState;
            mScrollCallback.onServiceStateChanged((serviceState & 1) != 0);
        }
    }

    static Intent getIntent(Context context, boolean proxy) {
        BridgeInfo bridgeInfo = proxy ? FeedBridge.Companion.getInstance(context).resolveBridge() : null;
        String pkg = context.getPackageName();
        return new Intent("com.android.launcher3.WINDOW_OVERLAY")
                .setPackage(bridgeInfo != null ? bridgeInfo.getPackageName() : "com.google.android.googlequicksearchbox")
                .setData(Uri.parse("app://" +
                                pkg +
                                ":" +
                                Process.myUid())
                        .buildUpon()
                        .appendQueryParameter("v", Integer.toString(7))
                        .appendQueryParameter("cv", Integer.toString(9))
                        .build());
    }

    private static void loadApiVersion(Context context) {
        ResolveInfo resolveService = context.getPackageManager().resolveService(getIntent(context, false), PackageManager.GET_META_DATA);
        apiVersion = resolveService == null || resolveService.serviceInfo.metaData == null ?
                1 :
                resolveService.serviceInfo.metaData.getInt("service.api.version", 1);
    }
}
