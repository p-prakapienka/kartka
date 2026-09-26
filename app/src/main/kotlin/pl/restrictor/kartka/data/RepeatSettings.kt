package pl.restrictor.kartka.data

import android.content.Context
import pl.restrictor.kartka.domain.RepeatDelays

class RepeatSettings(context: Context) {
    private val prefs = context.getSharedPreferences("kartka-repeat", Context.MODE_PRIVATE)

    fun get(): RepeatDelays = RepeatDelays(
        badMs = prefs.getLong(BAD, RepeatDelays.Default.badMs),
        mediumMs = prefs.getLong(MEDIUM, RepeatDelays.Default.mediumMs),
        goodMs = prefs.getLong(GOOD, RepeatDelays.Default.goodMs),
    ).sanitized()

    fun set(delays: RepeatDelays) {
        val safe = delays.sanitized()
        prefs.edit()
            .putLong(BAD, safe.badMs)
            .putLong(MEDIUM, safe.mediumMs)
            .putLong(GOOD, safe.goodMs)
            .apply()
    }

    private companion object {
        const val BAD = "bad_ms"
        const val MEDIUM = "medium_ms"
        const val GOOD = "good_ms"
    }
}
