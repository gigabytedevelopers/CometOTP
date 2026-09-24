@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Database

import android.net.Uri
import com.gigabytedevelopersinc.app.cometOTP.Utilities.EntryThumbnail
import com.gigabytedevelopersinc.app.cometOTP.Utilities.TokenCalculator
import org.apache.commons.codec.binary.Base32
import org.apache.commons.codec.binary.Hex
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.net.URL
import java.nio.charset.Charset
import java.util.Arrays
import java.util.Locale
import java.util.Objects

class Entry {
    enum class OTPType {
        TOTP, HOTP, MOTP, STEAM
    }

    var type = OTPType.TOTP
    var period = TokenCalculator.TOTP_DEFAULT_PERIOD
    var digits = TokenCalculator.TOTP_DEFAULT_DIGITS
    var algorithm: TokenCalculator.HashAlgorithm = TokenCalculator.DEFAULT_ALGORITHM
        private set
    var secret: ByteArray? = null
    var counter: Long = 0
    // Empty rather than null, because that is what loading an entry back produces: toJSON writes
    // the issuer through JSONObject.put, which drops the key when the value is null, and the
    // reader turns the missing key into "". Starting at null made saving and loading an entry
    // change it, and left getIssuer().toLowerCase() free to throw while searching or sorting.
    var issuer = ""
        private set
    var label: String? = null
    var currentOTP: String? = null
        private set
    var prevOTP: String? = null
        private set
    var isVisible = false
    var hideTask: Runnable? = null
    private var last_update: Long = 0
    var lastUsed: Long = 0
    var usedFrequency: Long = 0
    var tags: MutableList<String> = ArrayList()
    var thumbnail = EntryThumbnail.EntryThumbnails.Default
    var color = COLOR_DEFAULT
    var pin = ""
    var listId: Long = 0

    constructor()

    constructor(type: OTPType, secret: String, period: Int, digits: Int, issuer: String?, label: String?, algorithm: TokenCalculator.HashAlgorithm, tags: MutableList<String>) {
        this.type = type
        this.secret = Base32().decode(secret.uppercase(Locale.ROOT))
        this.period = period
        this.digits = digits
        this.issuer = issuer ?: ""
        this.label = label
        this.algorithm = algorithm
        this.tags = tags
        setThumbnailFromIssuer(issuer)
    }

    constructor(type: OTPType, secret: String, counter: Long, digits: Int, issuer: String?, label: String?, algorithm: TokenCalculator.HashAlgorithm, tags: MutableList<String>) {
        this.type = type
        this.secret = Base32().decode(secret.uppercase(Locale.ROOT))
        this.counter = counter
        this.digits = digits
        this.issuer = issuer ?: ""
        this.label = label
        this.algorithm = algorithm
        this.tags = tags
        setThumbnailFromIssuer(issuer)
    }

    constructor(type: OTPType, secret: String, issuer: String?, label: String?, tags: MutableList<String>) {
        this.type = type
        this.secret = secret.toByteArray(Charset.defaultCharset())
        this.issuer = issuer ?: ""
        this.label = label
        this.tags = tags
        this.period = TokenCalculator.TOTP_DEFAULT_PERIOD
        setThumbnailFromIssuer(issuer)
    }

    @Throws(Exception::class)
    constructor(contents: String) {
        // Literal replacement; the Java code used the regex String.replaceFirst, which is the same
        // for this pattern and replacement.
        val httpContents = contents.replaceFirst("otpauth", "http")
        val uri = Uri.parse(httpContents)
        val url = URL(httpContents)

        if (url.protocol != "http") {
            throw Exception("Invalid Protocol")
        }

        type = when (url.host) {
            "totp" -> OTPType.TOTP
            "hotp" -> OTPType.HOTP
            "motp" -> OTPType.MOTP
            "steam" -> OTPType.STEAM
            else -> throw Exception("unknown otp type")
        }

        val secret = uri.getQueryParameter("secret")

        val counter = uri.getQueryParameter("counter")
        val issuer = uri.getQueryParameter("issuer")
        val label = getStrippedLabel(issuer, uri.path!!.substring(1))
        val period = uri.getQueryParameter("period")
        val digits = uri.getQueryParameter("digits")
        val algorithm = uri.getQueryParameter("algorithm")
        val tags: MutableList<String>? = uri.getQueryParameters("tags")

        if (type == OTPType.HOTP) {
            if (counter != null) {
                this.counter = counter.toLong()
            } else {
                throw Exception("missing counter for HOTP")
            }
        } else if (type == OTPType.TOTP || type == OTPType.STEAM) {
            if (period != null) {
                this.period = period.toInt()
            } else {
                this.period = TokenCalculator.TOTP_DEFAULT_PERIOD
            }
        }

        this.issuer = issuer ?: ""
        this.label = label

        if (secret == null)
            throw Exception("Empty secret")

        if (type == OTPType.MOTP) {
            this.secret = secret.toByteArray(Charset.defaultCharset())
        } else {
            this.secret = Base32().decode(secret.uppercase(Locale.ROOT))
        }

        if (digits != null) {
            this.digits = digits.toInt()
        } else {
            this.digits = if (this.type == OTPType.STEAM) TokenCalculator.STEAM_DEFAULT_DIGITS else TokenCalculator.TOTP_DEFAULT_DIGITS
        }

        if (algorithm != null) {
            this.algorithm = TokenCalculator.HashAlgorithm.valueOf(algorithm.uppercase(Locale.ROOT))
        } else {
            this.algorithm = TokenCalculator.DEFAULT_ALGORITHM
        }

        if (tags != null) {
            this.tags = tags
        } else {
            this.tags = ArrayList()
        }

        if (issuer != null) {
            setThumbnailFromIssuer(issuer)
        }
    }

    @Throws(Exception::class)
    constructor(jsonObj: JSONObject) {
        this.secret = Base32().decode(jsonObj.getString(JSON_SECRET).uppercase(Locale.ROOT))
        this.label = jsonObj.getString(JSON_LABEL)

        try {
            this.issuer = jsonObj.getString(JSON_ISSUER)
        } catch (e: JSONException) {
            // Older backup version did not save issuer and label separately
            this.issuer = ""
        }

        try {
            this.type = OTPType.valueOf(jsonObj.getString(JSON_TYPE))
        } catch (e: Exception) {
            this.type = DEFAULT_TYPE
        }

        try {
            this.period = jsonObj.getInt(JSON_PERIOD)
        } catch (e: Exception) {
            if (type == OTPType.TOTP)
                this.period = DEFAULT_PERIOD
        }

        try {
            this.counter = jsonObj.getLong(JSON_COUNTER)
        } catch (e: Exception) {
            if (type == OTPType.HOTP)
                throw Exception("missing counter for HOTP")
        }

        try {
            this.digits = jsonObj.getInt(JSON_DIGITS)
        } catch (e: Exception) {
            this.digits = if (type == OTPType.STEAM) TokenCalculator.STEAM_DEFAULT_DIGITS else TokenCalculator.TOTP_DEFAULT_DIGITS
        }

        try {
            this.algorithm = TokenCalculator.HashAlgorithm.valueOf(jsonObj.getString(JSON_ALGORITHM))
        } catch (e: Exception) {
            this.algorithm = TokenCalculator.DEFAULT_ALGORITHM
        }

        this.tags = ArrayList()
        try {
            val tagsArray = jsonObj.getJSONArray(JSON_TAGS)
            for (i in 0 until tagsArray.length()) {
                this.tags.add(tagsArray.getString(i))
            }
        } catch (e: Exception) {
            // Nothing wrong here
        }

        try {
            this.thumbnail = EntryThumbnail.EntryThumbnails.valueOfIgnoreCase(jsonObj.getString(JSON_THUMBNAIL))
        } catch (e: Exception) {
            this.thumbnail = EntryThumbnail.EntryThumbnails.Default
        }

        try {
            this.lastUsed = jsonObj.getLong(JSON_LAST_USED)
        } catch (e: Exception) {
            this.lastUsed = 0
        }

        try {
            this.usedFrequency = jsonObj.getLong(JSON_USED_FREQUENCY)
        } catch (e: Exception) {
            this.usedFrequency = 0
        }
    }

    @Throws(JSONException::class)
    fun toJSON(): JSONObject {
        val jsonObj = JSONObject()
        jsonObj.put(JSON_SECRET, String(Base32().encode(secret!!), Charset.defaultCharset()))
        jsonObj.put(JSON_ISSUER, issuer)
        jsonObj.put(JSON_LABEL, label)
        jsonObj.put(JSON_DIGITS, digits)
        jsonObj.put(JSON_TYPE, type.toString())
        jsonObj.put(JSON_ALGORITHM, algorithm.toString())
        jsonObj.put(JSON_THUMBNAIL, thumbnail.name)
        jsonObj.put(JSON_LAST_USED, lastUsed)
        jsonObj.put(JSON_USED_FREQUENCY, usedFrequency)

        if (type == OTPType.TOTP || type == OTPType.STEAM)
            jsonObj.put(JSON_PERIOD, period)
        else if (type == OTPType.HOTP)
            jsonObj.put(JSON_COUNTER, counter)

        val tagsArray = JSONArray()
        for (tag in tags) {
            tagsArray.put(tag)
        }
        jsonObj.put(JSON_TAGS, tagsArray)

        return jsonObj
    }

    fun toUri(): Uri {
        val type = when (this.type) {
            OTPType.TOTP -> "totp"
            OTPType.HOTP -> "hotp"
            OTPType.STEAM -> "steam"
            OTPType.MOTP -> "motp"
        }
        val builder = Uri.Builder()
            .scheme("otpauth")
            .authority(type)
            .appendPath(this.label)

        if (this.type == OTPType.MOTP)
            builder.appendQueryParameter("secret", String(this.secret!!, Charset.defaultCharset()))
        else
            builder.appendQueryParameter("secret", Base32().encodeAsString(this.secret))

        // Empty as well as null: an entry with no issuer now holds "", and an exported
        // otpauth:// URL should leave the parameter out rather than carry "issuer=".
        if (this.issuer.isNotEmpty()) {
            builder.appendQueryParameter("issuer", this.issuer)
        }

        when (this.type) {
            OTPType.HOTP -> builder.appendQueryParameter("counter", this.counter.toString())
            // Steam has a period too, and the otpauth:// constructor reads it back for Steam.
            OTPType.TOTP, OTPType.STEAM -> {
                if (this.period != TokenCalculator.TOTP_DEFAULT_PERIOD)
                    builder.appendQueryParameter("period", this.period.toString())
            }
            else -> {}
        }

        if (this.digits != TokenCalculator.TOTP_DEFAULT_DIGITS) {
            builder.appendQueryParameter("digits", this.digits.toString())
        }

        if (this.algorithm != TokenCalculator.DEFAULT_ALGORITHM) {
            builder.appendQueryParameter("algorithm", this.algorithm.name)
        }

        for (tag in this.tags) {
            builder.appendQueryParameter("tags", tag)
        }

        return builder.build()
    }

    val isTimeBased: Boolean
        get() = type == OTPType.TOTP || type == OTPType.STEAM || type == OTPType.MOTP

    val isCounterBased: Boolean
        get() = type == OTPType.HOTP

    val secretEncoded: String
        get() = if (type == OTPType.MOTP)
            String(secret!!, Charset.defaultCharset())
        else
            String(Base32().encode(secret), Charset.defaultCharset())

    fun setIssuer(issuer: String?, updateThumbnail: Boolean) {
        this.issuer = issuer ?: ""

        if (updateThumbnail)
            setThumbnailFromIssuer(this.issuer)
    }

    fun hasNonDefaultPeriod(): Boolean {
        return this.period != TokenCalculator.TOTP_DEFAULT_PERIOD
    }

    fun updateOTP(updateNow: Boolean): Boolean {
        if (type == OTPType.TOTP || type == OTPType.STEAM || type == OTPType.MOTP) {
            val time = System.currentTimeMillis() / 1000
            val counter = time / this.period

            if (updateNow || counter > last_update) {
                // Store the previous token so we don't have to recalculate it every time
                if (!currentOTP.isNullOrEmpty())
                    prevOTP = currentOTP
                else
                    prevOTP = ""

                when (type) {
                    OTPType.TOTP -> {
                        currentOTP = TokenCalculator.TOTP_RFC6238(secret!!, period, digits, algorithm, 0)

                        if (prevOTP.isNullOrEmpty())
                            prevOTP = TokenCalculator.TOTP_RFC6238(secret!!, period, digits, algorithm, -1)
                    }
                    OTPType.STEAM -> {
                        currentOTP = TokenCalculator.TOTP_Steam(secret!!, period, digits, algorithm, 0)

                        if (prevOTP.isNullOrEmpty())
                            prevOTP = TokenCalculator.TOTP_Steam(secret!!, period, digits, algorithm, -1)
                    }
                    OTPType.MOTP -> {
                        val currentPin = this.pin

                        if (currentPin.isEmpty()) {
                            currentOTP = MOTP_NO_PIN_CODE
                        } else {
                            currentOTP = TokenCalculator.MOTP(currentPin, String(this.secret!!, Charset.defaultCharset()), time, 0)

                            if (prevOTP.isNullOrEmpty())
                                prevOTP = TokenCalculator.MOTP(currentPin, String(this.secret!!, Charset.defaultCharset()), time, -1)
                        }
                    }
                    else -> {}
                }

                last_update = counter
                color = COLOR_DEFAULT
                return true
            } else {
                return false
            }
        } else if (type == OTPType.HOTP) {
            currentOTP = TokenCalculator.HOTP(secret!!, counter, digits, algorithm)
            return true
        } else {
            return false
        }
    }

    /**
     * Checks if the OTP is expiring. The color for the entry will be changed to red if the expiry time is less than or equal to 8 seconds
     * COLOR_DEFAULT indicates that the OTP has not expired. In this case check if the OTP is about to expire. Update color to COLOR_RED if it's about to expire
     * COLOR_RED indicates that the OTP is already about to expire. Don't check again.
     * The color will be reset to COLOR_DEFAULT in [updateOTP] method
     *
     * @return Return true only if the color has changed to red to save from unnecessary notifying dataset
     * */
    fun hasColorChanged(): Boolean {
        if (color == COLOR_DEFAULT) {
            val time = System.currentTimeMillis() / 1000
            if ((time % period) > (period - EXPIRY_TIME)) {
                color = COLOR_RED
                return true
            }
        }
        return false
    }

    private fun setThumbnailFromIssuer(issuer: String?) {
        try {
            this.thumbnail = EntryThumbnail.EntryThumbnails.valueOfIgnoreCase(issuer)
        } catch (e: Exception) {
            try {
                this.thumbnail = EntryThumbnail.EntryThumbnails.valueOfFuzzy(issuer)
            } catch (e2: Exception) {
                this.thumbnail = EntryThumbnail.EntryThumbnails.Default
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other)
            return true

        if (other == null || javaClass != other.javaClass)
            return false
        val entry = other as Entry

        return type == entry.type &&
                period == entry.period &&
                counter == entry.counter &&
                digits == entry.digits &&
                algorithm == entry.algorithm &&
                Arrays.equals(secret, entry.secret) &&
                Objects.equals(label, entry.label) &&
                Objects.equals(issuer, entry.issuer)
    }

    // The secret is hashed by content, matching Arrays.equals in equals().
    override fun hashCode(): Int {
        return Objects.hash(type, period, counter, digits, algorithm, secret.contentHashCode(), label, issuer)
    }

    /**
     * Returns the label with issuer prefix removed (if present)
     * @param issuer - Name of the issuer to remove from the label
     * @param label - Full label from which the issuer should be removed
     * @return - label with the issuer removed
     */
    private fun getStrippedLabel(issuer: String?, label: String): String {
        // trim { it <= ' ' } is java.lang.String.trim(); Kotlin's trim() strips Unicode whitespace.
        return if (issuer == null || issuer.isEmpty() || !label.startsWith("$issuer:")) {
            label.trim { it <= ' ' }
        } else {
            label.substring(issuer.length + 1).trim { it <= ' ' }
        }
    }

    companion object {
        private val DEFAULT_TYPE = OTPType.TOTP
        private const val DEFAULT_PERIOD = 30
        private const val MOTP_NO_PIN_CODE = "PINREQ"

        private const val JSON_SECRET = "secret"
        private const val JSON_ISSUER = "issuer"
        private const val JSON_LABEL = "label"
        private const val JSON_PERIOD = "period"
        private const val JSON_COUNTER = "counter"
        private const val JSON_DIGITS = "digits"
        private const val JSON_TYPE = "type"
        private const val JSON_ALGORITHM = "algorithm"
        private const val JSON_TAGS = "tags"
        private const val JSON_THUMBNAIL = "thumbnail"
        private const val JSON_LAST_USED = "last_used"
        private const val JSON_USED_FREQUENCY = "used_frequency"

        private const val COLOR_DEFAULT = 0
        const val COLOR_RED = 1
        private const val EXPIRY_TIME = 8

        @JvmStatic
        fun validateSecret(secret: String?, type: OTPType?): Boolean {
            try {
                if (type == OTPType.MOTP)
                    Hex.decodeHex(secret)
                else
                    Base32().decode(secret!!.uppercase(Locale.ROOT))
            } catch (e: Exception) {
                return false
            }

            return true
        }
    }
}
