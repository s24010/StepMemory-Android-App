package com.kic.stepmemory.ui.review

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.Marker
import com.kic.stepmemory.R
import com.kic.stepmemory.data.Landmark

class CustomInfoWindowAdapter(private val context: Context) : GoogleMap.InfoWindowAdapter {

    override fun getInfoWindow(marker: Marker): View? {
        // This function is responsible for the window frame.
        // Returning null uses the default frame.
        return null
    }

    override fun getInfoContents(marker: Marker): View? {
        // This function is responsible for the content inside the window.

        // Return null for markers that are not landmarks (e.g., Start/Goal)
        val landmark = marker.tag as? Landmark ?: return null

        val view = LayoutInflater.from(context).inflate(R.layout.custom_info_window, null)

        val iconView = view.findViewById<TextView>(R.id.info_window_icon)
        val titleView = view.findViewById<TextView>(R.id.info_window_title)
        val snippetView = view.findViewById<TextView>(R.id.info_window_snippet)

        iconView.text = getEmojiForIconType(landmark.iconType)
        titleView.text = landmark.title
        snippetView.text = landmark.episode

        return view
    }

    private fun getEmojiForIconType(iconType: String): String {
        return when (iconType) {
            "PIN" -> "📍"
            "FOOD" -> "🍽️"
            "SCENERY" -> "🏞️"
            "ONSEN" -> "♨️"
            "SHOPPING" -> "🛍️"
            "SIGHTSEEING" -> "🏰"
            "BRONZE_PIN" -> "🥉"
            "SILVER_PIN" -> "🥈"
            "GOLD_PIN" -> "🥇"
            "MOON_ICON" -> "🌙"
            else -> "📍" // Default emoji
        }
    }
}