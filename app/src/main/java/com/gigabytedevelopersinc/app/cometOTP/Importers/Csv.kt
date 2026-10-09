@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Importers

/**
 * Reads CSV as password managers write it (RFC 4180): fields in double quotes may hold commas,
 * line breaks and doubled quotes. Rows that are completely empty are dropped.
 */
internal object Csv {

    fun parse(text: String): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val field = StringBuilder()
        var quoted = false
        var i = 0

        fun endField() {
            row.add(field.toString())
            field.setLength(0)
        }

        fun endRow() {
            endField()
            if (row.size > 1 || row[0].isNotEmpty())
                rows.add(row)
            row = ArrayList()
        }

        while (i < text.length) {
            val c = text[i]
            when {
                quoted -> {
                    if (c == '"') {
                        if (i + 1 < text.length && text[i + 1] == '"') {
                            field.append('"')
                            i++
                        } else {
                            quoted = false
                        }
                    } else {
                        field.append(c)
                    }
                }
                c == '"' -> quoted = true
                c == ',' -> endField()
                c == '\r' -> {
                    if (i + 1 < text.length && text[i + 1] == '\n')
                        i++
                    endRow()
                }
                c == '\n' -> endRow()
                else -> field.append(c)
            }
            i++
        }

        if (field.isNotEmpty() || row.isNotEmpty())
            endRow()

        return rows
    }
}
