@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Utilities

/**
 * How wrong passwords and PINs slow down guessing. The first [FREE_ATTEMPTS] wrong ones cost
 * nothing; from then on every wrong one locks the unlock screen, for longer each time up to
 * [LOCKOUTS]' last entry. A right one starts the count over.
 *
 * A wrong attempt used to close the app. That cost a typo the whole session but slowed nobody
 * down, as reopening the app took a second.
 */
internal object UnlockThrottle {
    const val FREE_ATTEMPTS = 5

    private val LOCKOUTS = longArrayOf(30_000, 60_000, 5 * 60_000, 15 * 60_000, 30 * 60_000)

    /** How long the screen locks after the [failures]th wrong attempt in a row; 0 for none. */
    fun lockoutAfter(failures: Int): Long {
        if (failures < FREE_ATTEMPTS)
            return 0
        return LOCKOUTS[minOf(failures - FREE_ATTEMPTS, LOCKOUTS.size - 1)]
    }

    /** The tries left after [failures] wrong ones before a wrong one locks the screen (it may still be made). */
    fun attemptsLeft(failures: Int): Int {
        return maxOf(0, FREE_ATTEMPTS - failures)
    }
}

/**
 * A lockout as stored: it ends at [endsAt] on the elapsed-realtime clock of boot [boot] (which the
 * user cannot set, unlike the time of day) and lasts [duration].
 */
internal data class Lockout(val boot: Int, val endsAt: Long, val duration: Long)

/**
 * [lockout] as it stands at [now] (elapsed realtime) on boot [boot]. The elapsed-realtime clock
 * starts over at every boot, so a lockout from another boot, or one that ends further away than
 * it lasts (the boot could not be told apart), starts over from [now]; store the result if it is
 * not [lockout] itself.
 */
internal fun lockoutAt(lockout: Lockout, boot: Int, now: Long): Lockout {
    if (lockout.boot != boot || lockout.endsAt - now > lockout.duration)
        return Lockout(boot, now + lockout.duration, lockout.duration)
    return lockout
}

/** What is left of [lockout] at [now]; see [lockoutAt] for a lockout stored on another boot. */
internal fun remainingLockout(lockout: Lockout?, now: Long): Long {
    if (lockout == null || lockout.duration <= 0)
        return 0
    return (lockout.endsAt - now).coerceIn(0, lockout.duration)
}

/** What the unlock screen tells the user about earlier wrong attempts, see [unlockStatus]. */
internal sealed class UnlockStatus {
    /** No wrong attempts since the last unlock. */
    object Clear : UnlockStatus()
    /** [count] more tries; the screen locks if they are all wrong. */
    data class AttemptsLeft(val count: Int) : UnlockStatus()
    /** One more wrong attempt locks the screen for [lockout]. */
    data class NextLocks(val lockout: Long) : UnlockStatus()
    /** Locked for [remaining] more. */
    data class Locked(val remaining: Long) : UnlockStatus()
}

internal fun unlockStatus(failures: Int, remaining: Long): UnlockStatus {
    return when {
        remaining > 0 -> UnlockStatus.Locked(remaining)
        failures <= 0 -> UnlockStatus.Clear
        UnlockThrottle.attemptsLeft(failures) > 0 -> UnlockStatus.AttemptsLeft(UnlockThrottle.attemptsLeft(failures))
        else -> UnlockStatus.NextLocks(UnlockThrottle.lockoutAfter(failures + 1))
    }
}
