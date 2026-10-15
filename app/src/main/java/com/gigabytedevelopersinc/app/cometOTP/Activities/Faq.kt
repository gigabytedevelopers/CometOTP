@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Activities

import android.content.Context
import com.gigabytedevelopersinc.app.cometOTP.BrowserSync.BrowserSync
import com.gigabytedevelopersinc.app.cometOTP.R

/**
 * The FAQ as the support screens show it: the questions in strings_support.xml and, where browser
 * sync is available, one more on using codes on a computer, with the first answer ("What is
 * CometOTP") saying that a connected browser gets a copy. Release builds leave browser sync out
 * until it ships (browser_sync.xml), so their FAQ stays as it is. The questions keep their places,
 * so [FaqDetailActivity] still finds each one's links by its index; the extra one, last, has none.
 */
object Faq {

    class Texts(val titles: List<String>, val bodies: List<String>)

    fun texts(
        titles: List<String>,
        bodies: List<String>,
        browserSync: Boolean,
        browserTitle: String,
        browserBody: String,
        firstBodyWithBrowsers: String,
    ): Texts {
        if (!browserSync)
            return Texts(titles, bodies)
        return Texts(titles + browserTitle, listOf(firstBodyWithBrowsers) + bodies.drop(1) + browserBody)
    }

    fun texts(context: Context): Texts = texts(
        context.resources.getStringArray(R.array.faq_titles).toList(),
        context.resources.getStringArray(R.array.faq_bodies).toList(),
        BrowserSync.isAvailable(context),
        context.getString(R.string.faq_browser_title),
        context.getString(R.string.faq_browser_body),
        context.getString(R.string.faq_what_is_body_with_browsers),
    )
}
