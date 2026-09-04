package com.nutomic.syncthingandroid.util

import android.content.Context

import com.nutomic.syncthingandroid.R

/**
 * Device compression attribute helper. This unifies operations between string values as expected by
 * Syncthing with string values as displayed to the user.
 */
enum class Compression(val index: Int) {
    NONE(0),
    METADATA(1),
    ALWAYS(2);

    fun getValue(context: Context): String {
        return context.resources.getStringArray(R.array.compress_values)[index]
    }

    companion object {

        fun fromIndex(index: Int): Compression {
            return when (index) {
                0 -> NONE
                2 -> ALWAYS
                else -> METADATA
            }
        }

        fun fromValue(context: Context, value: String): Compression {
            var index = 0
            val values = context.resources.getStringArray(R.array.compress_values)
            for (i in values.indices) {
                if (values[i] == value) {
                    index = i
                }
            }

            return fromIndex(index)
        }
    }
}
