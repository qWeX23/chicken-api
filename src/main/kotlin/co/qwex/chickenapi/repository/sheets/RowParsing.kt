package co.qwex.chickenapi.repository.sheets

import java.time.Instant

fun List<Any?>.stringAt(index: Int, trim: Boolean = true): String? {
    val rawValue = getOrNull(index)?.toString() ?: return null
    val value = if (trim) rawValue.trim() else rawValue
    return value.takeIf { it.isNotEmpty() }
}

fun List<Any?>.intAt(index: Int): Int? {
    val value = getOrNull(index) ?: return null
    return when (value) {
        is Number -> value.toInt()
        is String -> value.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
        else -> value.toString().trim().toIntOrNull()
    }
}

fun List<Any?>.instantAt(index: Int): Instant? =
    stringAt(index)?.let { raw ->
        runCatching { Instant.parse(raw) }.getOrNull()
    }

fun List<Any?>.stringListAt(index: Int, delimiter: String): List<String>? =
    stringAt(index, trim = false)
        ?.split(delimiter)
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.takeIf { it.isNotEmpty() }
