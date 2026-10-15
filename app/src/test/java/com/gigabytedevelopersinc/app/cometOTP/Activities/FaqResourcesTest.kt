@file:Suppress("PackageName")
package com.gigabytedevelopersinc.app.cometOTP.Activities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * The FAQ's arrays (strings_support.xml), read straight from the file: the unit tests run without
 * Android's resources. [FaqDetailActivity] shows answer i, and its links, from item i of each, so
 * a missing or extra item would put an answer under the wrong question or links under the wrong
 * answer.
 */
class FaqResourcesTest {

    private val arrays: Map<String, List<String>> by lazy {
        // Gradle runs unit tests in the module's directory.
        val file = File("src/main/res/values/strings_support.xml")
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val result = HashMap<String, List<String>>()
        for (tag in listOf("array", "string-array")) {
            val nodes = document.getElementsByTagName(tag)
            for (i in 0 until nodes.length) {
                val array = nodes.item(i) as Element
                val items = array.getElementsByTagName("item")
                result[array.getAttribute("name")] = (0 until items.length).map { items.item(it).textContent.trim() }
            }
        }
        result
    }

    @Test
    fun everyQuestionHasAnAnswerAndALinkEntry() {
        val titles = arrays.getValue("faq_titles").size
        assertEquals(titles, arrays.getValue("faq_bodies").size)
        assertEquals(titles, arrays.getValue("faq_link_names").size)
        assertEquals(titles, arrays.getValue("faq_link_urls").size)
    }

    @Test
    fun linksHaveAPageForEveryNameAndOpenOverHttps() {
        val names = arrays.getValue("faq_link_names")
        val urls = arrays.getValue("faq_link_urls")
        for (i in names.indices) {
            // An answer either has both lists or neither.
            assertEquals("faq $i", names[i] == "@null", urls[i] == "@null")
            if (names[i] == "@null")
                continue
            val pageNames = arrays.getValue(names[i].removePrefix("@array/"))
            val pages = arrays.getValue(urls[i].removePrefix("@array/"))
            assertEquals("faq $i", pageNames.size, pages.size)
            for (page in pages)
                assertTrue(page, page.startsWith("https://"))
        }
    }

    @Test
    fun theAnswerFaqReplacesWithBrowserSyncIsTheFirst() {
        // Faq.kt gives the first answer faq_what_is_body_with_browsers where browser sync is available.
        assertEquals("What is CometOTP", arrays.getValue("faq_titles").first())
        val strings = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/res/values/strings_support.xml")).getElementsByTagName("string")
        val names = (0 until strings.length).map { (strings.item(it) as Element).getAttribute("name") }
        for (name in listOf("faq_browser_title", "faq_browser_body", "faq_what_is_body_with_browsers"))
            assertTrue(name, name in names)
    }

    @Test
    fun turningOn2faLinksToTheServicesPages() {
        val index = arrays.getValue("faq_titles").indexOf("How do I turn on 2FA")
        assertTrue(index >= 0)
        assertEquals("@array/faq_2fa_service_names", arrays.getValue("faq_link_names")[index])
        assertEquals("@array/faq_2fa_service_urls", arrays.getValue("faq_link_urls")[index])
    }
}
