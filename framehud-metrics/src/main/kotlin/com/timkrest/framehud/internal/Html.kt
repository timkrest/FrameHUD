package com.timkrest.framehud.internal

internal fun buildHtmlBody(build: HtmlScope.() -> Unit): String {
    val out = StringBuilder()
    HtmlScope(out).build()
    return out.toString()
}

internal class HtmlScope(private val out: StringBuilder) {

    fun header(title: String, meta: String) {
        out.append("<header>\n<h1>").append(escapeHtml(title)).append("</h1>\n")
        out.append("<p class=\"meta\">").append(escapeHtml(meta)).append("</p>\n</header>\n")
    }

    fun section(heading: String, build: HtmlScope.() -> Unit) {
        out.append("<section>\n<h2>").append(escapeHtml(heading)).append("</h2>\n")
        build()
        out.append("</section>\n")
    }

    fun tiles(build: TileScope.() -> Unit) {
        out.append("<section class=\"tiles\">\n")
        TileScope(out).build()
        out.append("</section>\n")
    }

    fun table(build: TableScope.() -> Unit) {
        out.append("<table>\n")
        TableScope(out).build()
        out.append("</table>\n")
    }

    fun list(build: ListScope.() -> Unit) {
        out.append("<ul>\n")
        ListScope(out).build()
        out.append("</ul>\n")
    }

    fun meta(text: String) = paragraph("meta", text)

    fun caution(text: String) = paragraph("caution", text)

    fun footer(text: String) {
        out.append("<footer>").append(escapeHtml(text)).append("</footer>\n")
    }

    fun markup(value: String) {
        out.append(value)
    }

    private fun paragraph(cssClass: String, text: String) {
        out.append("<p class=\"").append(cssClass).append("\">").append(escapeHtml(text)).append("</p>\n")
    }
}

internal class TileScope(private val out: StringBuilder) {

    fun tile(value: String, label: String) {
        out.append("<div class=\"tile\"><strong>").append(escapeHtml(value))
        out.append("</strong><span>").append(escapeHtml(label)).append("</span></div>\n")
    }
}

internal class TableScope(private val out: StringBuilder) {

    fun headings(vararg cells: String) {
        out.append("<tr>")
        for (cell in cells) out.append("<th>").append(escapeHtml(cell)).append("</th>")
        out.append("</tr>\n")
    }

    fun row(label: String, vararg values: String) {
        out.append("<tr><th>").append(escapeHtml(label)).append("</th>")
        appendCells(values)
    }

    fun cells(vararg values: String) {
        out.append("<tr>")
        appendCells(values)
    }

    fun chipRow(label: String, chips: List<String>) {
        out.append("<tr><th>").append(escapeHtml(label)).append("</th><td>")
        chips.forEachIndexed { index, chip ->
            if (index > 0) out.append(' ')
            out.append("<code>").append(escapeHtml(chip)).append("</code>")
        }
        out.append("</td></tr>\n")
    }

    private fun appendCells(values: Array<out String>) {
        for (value in values) out.append("<td>").append(escapeHtml(value)).append("</td>")
        out.append("</tr>\n")
    }
}

internal class ListScope(private val out: StringBuilder) {

    fun item(text: String) {
        out.append("<li>").append(escapeHtml(text)).append("</li>\n")
    }
}

internal fun escapeHtml(value: String): String = buildString(value.length) {
    for (char in value) {
        when (char) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(char)
        }
    }
}
