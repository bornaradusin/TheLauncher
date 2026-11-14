package com.google.android.libraries.launcherclient;

import android.content.ComponentName;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;

import amirz.aidlbridge.IBridge;
import amirz.aidlbridge.IBridgeCallback;

public class LauncherClientBridge extends IBridgeCallback.Stub implements ServiceConnection {
    private static final String INTERFACE_DESCRIPTOR = "amirz.aidlbridge.IBridge";

    private final BaseClientService mClientService;
    private final int mFlags;
    private ComponentName mConnectionName;

    public LauncherClientBridge(BaseClientService launcherClientService, int flags) {
        mClientService = launcherClientService;
        mFlags = flags;
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        try {
            if (INTERFACE_DESCRIPTOR.equals(service.getInterfaceDescriptor())) {
                IBridge bridge = IBridge.Stub.asInterface(service);
                try {
                    bridge.bindService(this, mFlags);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            } else {
                mClientService.onServiceConnected(name, service);
                mConnectionName = name;
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        if (mConnectionName != null) {
            mClientService.onServiceDisconnected(mConnectionName);
            mConnectionName = null;
        }
    }

    /** The companion or the Google app was updated: this link is dead for good, so make a new one. */
    @Override
    public void onBindingDied(ComponentName name) {
        onServiceDisconnected(name);
        new android.os.Handler(android.os.Looper.getMainLooper()).post(mClientService::rebind);
    }

    @Override
    public void onNullBinding(ComponentName name) {
        onServiceDisconnected(name);
    }
}
