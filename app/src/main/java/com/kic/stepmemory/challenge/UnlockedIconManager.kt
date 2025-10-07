package com.kic.stepmemory.challenge

import android.content.Context

class UnlockedIconManager(context: Context, userId: String) {
    private val prefs = context.getSharedPreferences("UnlockedIcons_$userId", Context.MODE_PRIVATE)
    private val UNLOCKED_ICONS_KEY = "unlocked_icons_set"

    fun unlockIcon(iconId: String) {
        val unlockedIcons = getUnlockedIcons().toMutableSet()
        unlockedIcons.add(iconId)
        prefs.edit().putStringSet(UNLOCKED_ICONS_KEY, unlockedIcons).apply()
    }

    fun isIconUnlocked(iconId: String): Boolean {
        return getUnlockedIcons().contains(iconId)
    }

    fun getUnlockedIcons(): Set<String> {
        return prefs.getStringSet(UNLOCKED_ICONS_KEY, emptySet()) ?: emptySet()
    }
}