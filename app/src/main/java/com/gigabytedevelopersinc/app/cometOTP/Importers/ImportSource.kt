@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Importers

import androidx.annotation.ArrayRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.gigabytedevelopersinc.app.cometOTP.R

/**
 * The apps CometOTP can bring accounts across from, in the order the Import tokens sheet lists
 * them. Adding an app is adding a constant here, with its strings and its [ImportFormat].
 *
 * @param label the app's name, as the sheet lists it
 * @param title the title of the app's own sheet
 * @param steps a string array: what to do in the app to get the export, one item per step
 * @param note an extra line under the steps (a password hint, a limitation), or 0 for none
 * @param mimeTypes what the file picker is narrowed to; the export's own type and the generic
 *        ones apps and file managers fall back to
 * @param format reads the chosen file; null for sources that are not read from a file
 */
enum class ImportSource(
    val kind: Kind,
    @StringRes val label: Int,
    @StringRes val title: Int,
    @DrawableRes val icon: Int,
    @ArrayRes val steps: Int,
    @StringRes val note: Int,
    val mimeTypes: Array<String>,
    val format: ImportFormat?
) {
    GOOGLE_AUTHENTICATOR(Kind.QR_CODES, R.string.import_source_google_auth, R.string.import_google_auth_title,
            R.drawable.ic_qr_code, 0, 0, emptyArray(), null),

    ONE_PASSWORD(Kind.FILE, R.string.import_source_1password, R.string.import_1password_title,
            R.drawable.ic_file_document, R.array.import_1password_steps, R.string.import_1password_note, ONE_PASSWORD_MIME_TYPES, OnePasswordImport),

    TWO_FAS(Kind.FILE, R.string.import_source_2fas, R.string.import_2fas_title,
            R.drawable.ic_file_document, R.array.import_2fas_steps, 0, JSON_MIME_TYPES, TwoFasImport),

    AEGIS(Kind.FILE, R.string.import_source_aegis, R.string.import_aegis_title,
            R.drawable.ic_file_document, R.array.import_aegis_steps, R.string.import_aegis_note, JSON_MIME_TYPES, AegisImport),

    ANDOTP(Kind.FILE, R.string.import_source_andotp, R.string.import_andotp_title,
            R.drawable.ic_file_document, R.array.import_andotp_steps, R.string.import_andotp_note, ANDOTP_MIME_TYPES, AndOtpImport),

    AUTHY(Kind.FILE, R.string.import_source_authy, R.string.import_authy_title,
            R.drawable.ic_link, R.array.import_authy_steps, R.string.import_authy_note, TEXT_MIME_TYPES, OtpauthLinksImport),

    BITWARDEN(Kind.FILE, R.string.import_source_bitwarden, R.string.import_bitwarden_title,
            R.drawable.ic_file_document, R.array.import_bitwarden_steps, R.string.import_bitwarden_note, BITWARDEN_MIME_TYPES, BitwardenImport),

    ENTE(Kind.FILE, R.string.import_source_ente, R.string.import_ente_title,
            R.drawable.ic_file_document, R.array.import_ente_steps, 0, TEXT_MIME_TYPES, EnteImport),

    FREEOTP_PLUS(Kind.FILE, R.string.import_source_freeotp_plus, R.string.import_freeotp_plus_title,
            R.drawable.ic_file_document, R.array.import_freeotp_plus_steps, R.string.import_freeotp_plus_note, JSON_MIME_TYPES, FreeOtpPlusImport),

    KEEPASS(Kind.FILE, R.string.import_source_keepass, R.string.import_keepass_title,
            R.drawable.ic_file_document, R.array.import_keepass_steps, R.string.import_keepass_note, KEEPASS_MIME_TYPES, KeePassImport),

    LASTPASS(Kind.FILE, R.string.import_source_lastpass, R.string.import_lastpass_title,
            R.drawable.ic_file_document, R.array.import_lastpass_steps, 0, JSON_MIME_TYPES, LastPassImport),

    MICROSOFT(Kind.INFORMATION, R.string.import_source_microsoft, R.string.import_microsoft_title,
            R.drawable.ic_info_outline, R.array.import_microsoft_steps, R.string.import_microsoft_note, emptyArray(), null),

    PROTON(Kind.FILE, R.string.import_source_proton, R.string.import_proton_title,
            R.drawable.ic_file_document, R.array.import_proton_steps, 0, JSON_MIME_TYPES, ProtonImport),

    RAIVO(Kind.FILE, R.string.import_source_raivo, R.string.import_raivo_title,
            R.drawable.ic_file_document, R.array.import_raivo_steps, 0, RAIVO_MIME_TYPES, RaivoImport),

    STRATUM(Kind.FILE, R.string.import_source_stratum, R.string.import_stratum_title,
            R.drawable.ic_file_document, R.array.import_stratum_steps, R.string.import_stratum_note, JSON_MIME_TYPES, StratumImport),

    /** Last: the fallback for any app that can write its accounts out as links. */
    OTPAUTH_LINKS(Kind.FILE, R.string.import_source_links, R.string.import_links_title,
            R.drawable.ic_link, R.array.import_links_steps, R.string.import_links_note, TEXT_MIME_TYPES, OtpauthLinksImport);

    enum class Kind {
        /** Read from the QR codes the app shows; the source has a flow of its own. */
        QR_CODES,
        /** Read from a file the app exports. */
        FILE,
        /** Nothing to read: the sheet only explains what to do. */
        INFORMATION
    }

}

/** Types the picker is narrowed to for a JSON export; file managers vary in what they call one. */
internal val JSON_MIME_TYPES = arrayOf("application/json", "text/plain", "text/json", "application/octet-stream")

internal val TEXT_MIME_TYPES = arrayOf("text/plain", "application/octet-stream")

/** KeePass's XML export. */
internal val KEEPASS_MIME_TYPES = arrayOf("text/xml", "application/xml", "text/plain", "application/octet-stream")

/** 1Password's 1PUX archive or CSV. */
internal val ONE_PASSWORD_MIME_TYPES = arrayOf("application/zip", "application/x-zip-compressed", "text/csv", "text/comma-separated-values", "text/plain", "application/octet-stream")

/** Raivo's encrypted archive, or the JSON taken out of it. */
internal val RAIVO_MIME_TYPES = arrayOf("application/zip", "application/x-zip-compressed", "application/json", "application/octet-stream")

/** Bitwarden exports as JSON or CSV. */
internal val BITWARDEN_MIME_TYPES = arrayOf("application/json", "text/csv", "text/comma-separated-values", "text/plain", "application/octet-stream")

/** andOTP's plain and password-protected backups, the latter written as "binary/aes". */
internal val ANDOTP_MIME_TYPES = arrayOf("application/json", "binary/aes", "text/plain", "application/octet-stream")
