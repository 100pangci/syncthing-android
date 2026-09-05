package com.nutomic.syncthingandroid.service

import android.content.ContentResolver
import android.content.SyncStatusObserver
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowContentResolver

/**
 * Robolectric's content service binding is null, which makes the real
 * [ContentResolver.addStatusChangeListener] throw a NullPointerException.
 * This shadow stubs out the sync status observer registration instead.
 */
@Implements(ContentResolver::class)
class ShadowContentResolverWithSyncObserver : ShadowContentResolver() {

    companion object {
        @JvmStatic
        @Implementation
        fun addStatusChangeListener(which: Int, observer: SyncStatusObserver): Any {
            return Any()
        }

        @JvmStatic
        @Implementation
        fun removeStatusChangeListener(handle: Any?) {
            // No-op.
        }
    }
}
