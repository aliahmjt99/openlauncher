package com.openlauncher.app.data

data class PhoneFavorite(val name: String, val number: String)

internal fun normalizeDialNumber(value: String): String? {
    val number = value.filterNot { it.isWhitespace() || it in "-()." }
    return number.takeIf {
        it.length in 1..40 && it.all { ch -> ch in "0123456789+*#" } &&
            it.indexOf('+', 1) < 0 && it.any { ch -> ch in '0'..'9' }
    }
}
