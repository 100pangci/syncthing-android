package com.nutomic.syncthingandroid.service;

import android.content.ContentResolver;
import android.content.SyncStatusObserver;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowContentResolver;

/**
 * Robolectric's content service binding is null, which makes the real
 * {@link ContentResolver#addStatusChangeListener} throw a NullPointerException.
 * This shadow stubs out the sync status observer registration instead.
 */
@Implements(ContentResolver.class)
public class ShadowContentResolverWithSyncObserver extends ShadowContentResolver {

    @Implementation
    protected static Object addStatusChangeListener(int which, SyncStatusObserver observer) {
        return new Object();
    }

    @Implementation
    protected static void removeStatusChangeListener(Object handle) {
        // No-op.
    }
}
