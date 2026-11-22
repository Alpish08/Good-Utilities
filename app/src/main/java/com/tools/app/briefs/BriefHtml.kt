package com.tools.app.briefs

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** A brief made readable: the article HTML, its text for reading aloud, and its section headings. */
class Article(val html: String, val paragraphs: List<String>, val headings: List<Pair<String, String>>, val images: Int)

/**
 * Turns a newsletter's email HTML into a plain article: no layout tables, inline styles, tracking pixels,
 * hidden preview text or "unsubscribe / view in browser" boilerplate. The page then gets One UI's own type
 * and colours, so dark mode works even for emails that hard-code white backgrounds.
 */
object BriefHtml {
    private val boilerplate = Regex(
        "(?i)unsubscribe|view (it |this )?(email )?(in|on) (your |a )?(web )?browser|view online|view as (a )?web ?page|" +
            "manage (your )?(email )?(preferences|subscriptions?|settings)|update (your )?(email )?preferences|email preferences|" +
            "forward(ed)? (this|to a friend)|having trouble (viewing|reading)|add us to your address book|" +
            "read (this|it|the) (issue |email |newsletter |post )?(in|on) (a|your|the) (web )?browser|(someone|somebody|a friend) forward(ed)? (you )?this|" +
            "subscribe to (this|our|the) newsletter|sign up (here|for free)|was this (email )?forwarded to you",
    )
    private val hidden = Regex("(?i)display\\s*:\\s*none|visibility\\s*:\\s*hidden|max-height\\s*:\\s*0(px)?\\s*(;|$)|font-size\\s*:\\s*0(px)?\\s*(;|$)|opacity\\s*:\\s*0(\\.0+)?\\s*(;|$)|mso-hide\\s*:\\s*all")
    private val keepTags = setOf("p", "div", "br", "hr", "a", "img", "b", "strong", "i", "em", "u", "h1", "h2", "h3", "h4", "h5", "h6",
        "ul", "ol", "li", "blockquote", "pre", "code", "sup", "sub", "figure", "figcaption", "span", "body", "html")

    fun plainText(raw: String): String = clean(raw).body().text()

    /** True for emoji and their modifiers (pictographs, dingbats, flags, skin tones, joiners), not ©, ® or ™. */
    private fun isEmoji(cp: Int) = cp in 0x1F000..0x1FAFF || cp in 0x2600..0x27BF || cp in 0x2B00..0x2BFF ||
        cp in 0x2300..0x23FF || cp in 0x1F1E6..0x1F1FF || cp in 0xE0020..0xE007F ||
        cp == 0xFE0F || cp == 0xFE0E || cp == 0x200D || cp == 0x20E3 || cp == 0x3030 || cp == 0x303D || cp == 0x3297 || cp == 0x3299

    /** [s] without emoji. With [tidy], doubled spaces left behind are closed up and the ends trimmed. */
    fun noEmoji(s: String, tidy: Boolean = true): String {
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            if (!isEmoji(cp)) sb.appendCodePoint(cp)
            i += Character.charCount(cp)
        }
        val out = sb.toString()
        return if (tidy) out.replace(Regex("[ \\t]{2,}"), " ").trim().ifEmpty { s.trim() } else out
    }

    fun article(raw: String, showImages: Boolean, subject: String = ""): Article {
        val doc = clean(raw)
        val body = doc.body()
        // The subject is already the page title: drop the newsletter's own copy of it near the top.
        val key = subject.lowercase().filter { it.isLetterOrDigit() }
        if (key.length >= 4) body.select("h1, h2, h3, p, div").take(12)
            .firstOrNull { el -> el.children().none { it.tagName() in setOf("p", "div") } && el.text().lowercase().filter { it.isLetterOrDigit() } == key }
            ?.remove()
        val images = body.select("img").size
        if (!showImages) body.select("img").remove()
        pruneEmpty(body)
        // Section headings (id → text), for the contents list.
        val headings = body.select("h1, h2, h3").mapIndexedNotNull { i, h ->
            h.attr("id", "gu-h$i")
            h.text().trim().takeIf { it.isNotEmpty() }?.let { "gu-h$i" to it }
        }
        val paragraphs = body.select("h1, h2, h3, h4, p, li, blockquote, div")
            .filter { el -> el.children().none { it.tagName() in setOf("p", "div", "li", "h1", "h2", "h3", "h4", "ul", "ol", "blockquote") } }
            .map { it.text().trim() }.filter { it.isNotEmpty() }
        return Article(body.html(), paragraphs, headings, images)
    }

    private fun clean(raw: String): Document {
        val doc = Jsoup.parse(raw)
        doc.select("script, style, head, meta, link, noscript, title, iframe, form, input, button, select, textarea, svg, map, object, embed").remove()
        // Preview text and other hidden bits.
        doc.select("[style]").filter { hidden.containsMatchIn(it.attr("style")) }.forEach { it.remove() }
        doc.select("[hidden], [aria-hidden=true]").remove()
        // Tracking pixels and spacers.
        doc.select("img").forEach { img ->
            val w = img.attr("width").filter(Char::isDigit).toIntOrNull()
            val h = img.attr("height").filter(Char::isDigit).toIntOrNull()
            val style = img.attr("style")
            if (img.attr("src").isBlank() || (w != null && w <= 3) || (h != null && h <= 3) ||
                Regex("(?i)(width|height)\\s*:\\s*[0-3]px").containsMatchIn(style)) img.remove()
        }
        // Footer and header boilerplate: the smallest block around such a link, if it's short.
        doc.select("a").filter { boilerplate.containsMatchIn(it.text()) }.forEach { a ->
            var block: Element = a
            while (block.parent() != null && block.parent()!!.tagName() !in setOf("body", "html") &&
                block.parent()!!.text().length < 300) block = block.parent()!!
            block.remove()
        }
        doc.select("p, div, td, span").filter { it.ownText().length < 300 && boilerplate.containsMatchIn(it.ownText()) && it.select("a").isEmpty() }
            .forEach { it.remove() }
        // Tiny tables of a few short values (a market price stacked in its own table) become one line.
        doc.select("table").filter { it.select("table").size == 1 }.forEach { t ->
            val cells = t.select("td, th").filter { it.text().isNotBlank() }
            if (cells.size in 2..6 && t.select("p, ul, ol").isEmpty() && t.select("img").all(::isIcon) && cells.all { it.text().length <= 40 }) {
                t.replaceWith(Element("p").text(cells.joinToString(" · ") { it.text().trim() }))
            }
        }
        // Small data rows (market prices, scores…) stay on one line: "Nasdaq · 11,361.85 · +0.35%".
        doc.select("tr").forEach { tr ->
            val cells = tr.children().filter { it.tagName() == "td" || it.tagName() == "th" }
            // Judged by text, not markup: each cell may wrap its value in a <p> or a little badge table.
            if (tr.parent() != null && cells.size >= 2 && tr.text().length <= 120 &&
                cells.all { c -> c.select("ul, ol, h1, h2, h3").isEmpty() && c.select("img").all(::isIcon) && c.text().length <= 40 } &&
                cells.count { it.text().isNotBlank() } >= 2
            ) {
                val line = cells.map { it.text().trim() }.filter { it.isNotEmpty() }.joinToString(" · ")
                tr.replaceWith(Element("p").text(line))
            }
        }
        // Layout tables become plain blocks.
        doc.select("td, th").forEach { it.tagName("div") }
        doc.select("table, tbody, thead, tfoot, tr, center, font, colgroup, col").forEach { it.unwrap() }
        // Everything else keeps only what reading needs.
        doc.getAllElements().forEach { el ->
            if (el.tagName() !in keepTags) { el.tagName("div") }
            val keep = when (el.tagName()) {
                "a" -> setOf("href")
                "img" -> setOf("src", "alt")
                else -> emptySet()
            }
            el.attributes().asList().map { it.key }.filter { it !in keep }.forEach { el.removeAttr(it) }
        }
        doc.select("a[href^=javascript]").forEach { it.unwrap() }
        // No emoji anywhere in the article.
        doc.body()?.let { body ->
            org.jsoup.select.NodeTraversor.traverse(org.jsoup.select.NodeVisitor { node, _ ->
                if (node is org.jsoup.nodes.TextNode) {
                    val t = node.wholeText
                    val clean = noEmoji(t, tidy = false)
                    if (clean != t) node.text(clean.replace(Regex("^ +| +$"), " "))
                }
            }, body)
        }
        return doc
    }

    /** A small inline icon (an up/down arrow beside a price), at most 40 px by its own size attributes. */
    private fun isIcon(img: Element): Boolean {
        val w = img.attr("width").filter(Char::isDigit).toIntOrNull()
        val h = img.attr("height").filter(Char::isDigit).toIntOrNull()
        val style = Regex("(?i)(?:^|;)\\s*(?:width|height)\\s*:\\s*(\\d+)px").findAll(img.attr("style")).map { it.groupValues[1].toInt() }.toList()
        val sizes = listOfNotNull(w, h) + style
        return sizes.isNotEmpty() && sizes.all { it <= 40 }
    }

    /** Drops elements left with nothing to show (repeatedly, as emptying one can empty its parent). */
    private fun pruneEmpty(body: Element) {
        repeat(6) {
            val empty = body.select("div, p, span, a, b, strong, i, em, li, ul, ol, blockquote, h1, h2, h3, h4, h5, h6, figure")
                .filter { it.text().isBlank() && it.select("img, hr, br").isEmpty() }
            if (empty.isEmpty()) return
            empty.forEach { it.remove() }
        }
        // Runs of <br> and dividers collapse to one.
        body.select("br + br").remove()
        body.select("hr + hr").remove()
    }
}
