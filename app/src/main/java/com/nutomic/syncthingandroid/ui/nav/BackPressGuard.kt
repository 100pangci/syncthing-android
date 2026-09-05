package com.nutomic.syncthingandroid.ui.nav

import android.os.SystemClock

/**
 * Guards navigateBack() against the ghost second tap of a fast double-tap on a
 * back button: Nav3 keeps the outgoing screen's composition (its toolbar back
 * button included) interactive while the pop transition plays, so the second
 * tap re-fires navigateBack with the stack already one level shorter. For a
 * single-entry stack that overshoots out of the app entirely (moveTaskToBack
 * to the desktop / finish of the host activity).
 *
 * Only calls that leave the stack are guarded - in-stack pops stay allowed so
 * rapid back-walking through several screens keeps working.
 */
internal class BackPressGuard {

    private var lastPopAtMillis = Long.MIN_VALUE / 2

    /** Records an in-stack pop that just happened. */
    fun recordPop() {
        lastPopAtMillis = SystemClock.uptimeMillis()
    }

    /** True when a call may leave the stack (moveTaskToBack / finish). */
    fun mayLeaveStack(): Boolean =
        SystemClock.uptimeMillis() - lastPopAtMillis >= BACK_POP_GUARD_MILLIS
}
