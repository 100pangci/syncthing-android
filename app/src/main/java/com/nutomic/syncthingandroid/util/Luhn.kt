package com.nutomic.syncthingandroid.util

/**
 * Check digit generator for Syncthing device IDs.
 */
object Luhn {

    /**
     * An alphabet is a string of N characters, representing the digits of a given
     * base N.
     */
    private const val LUHN_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    /**
     * generate returns a check digit for the string s, which should be composed
     * of characters from the Alphabet a.
     * Doesn't follow the actual Luhn algorithm
     * see https://forum.syncthing.net/t/v0-9-0-new-node-id-format/478/6 for more.
     */
    fun generate(s: ByteArray): String? {
        var factor = 1
        var sum = 0
        val n = LUHN_ALPHABET.length

        for (b in s) {
            // Char lookup of the sign-extended byte; out-of-alphabet bytes (incl. negative, as
            // in the Java indexOf(int) original) yield -1 and make generate return null.
            val codepoint = LUHN_ALPHABET.indexOf(b.toInt().toChar())
            if (codepoint == -1) {
                // Error "Digit %q not valid in alphabet %q", s[i], a
                return null
            }
            var addend = factor * codepoint
            factor = if (factor == 2) 1 else 2
            addend = (addend / n) + (addend % n)
            sum += addend
        }
        val remainder = sum % n
        val checkCodepoint = (n - remainder) % n
        return LUHN_ALPHABET.substring(checkCodepoint, checkCodepoint + 1)
    }
}
