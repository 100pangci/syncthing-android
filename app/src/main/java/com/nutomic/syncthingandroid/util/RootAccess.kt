package com.nutomic.syncthingandroid.util

import com.topjohnwu.superuser.Shell

/**
 * Root (su) availability gate for the optional "run Syncthing as root" feature.
 *
 * The first call spawns the su shell, which may surface the Magisk grant dialog; always
 * call from a background thread. The result is intentionally NOT cached: users can grant
 * or revoke the authorization between calls, and the probing cost only matters on
 * explicit user actions (toggling the setting, browsing folders, starting the core).
 */
object RootAccess {

    /**
     * Returns true if a root shell could be obtained and granted. On non-rooted devices
     * libsu falls back to an unprivileged shell, which yields false here.
     */
    fun isSuAvailable(): Boolean {
        Shell.getShell()
        return Shell.isAppGrantedRoot() == true
    }
}
