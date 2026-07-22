package com.dewijones92.uniapp.data.xml

import org.w3c.dom.Element
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Shared DOM traversal used by the feed and OPML parsers, so neither
 * re-implements child/descendant walking or the XXE hardening.
 */
internal fun Element.childElements(name: String): List<Element> {
    val result = mutableListOf<Element>()
    var child = firstChild
    while (child != null) {
        if (child is Element && child.tagName == name) result += child
        child = child.nextSibling
    }
    return result
}

internal fun Element.firstChildElement(name: String): Element? = childElements(name).firstOrNull()

internal fun Element.firstChildText(name: String): String? =
    firstChildElement(name)?.textContent?.trim()?.ifBlank { null }

/** Every descendant element named [name], at any depth (document order). */
internal fun Element.descendantsNamed(name: String): List<Element> {
    val nodes = getElementsByTagName(name)
    return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
}

/**
 * External entities and doctypes are attack surface, not feed data.
 * Note: only [DocumentBuilderFactory.setFeature]-based hardening is used. The
 * bean-property toggles (isExpandEntityReferences / isXIncludeAware) make
 * Android's Expat-backed parser throw "does not support specification Unknown
 * version 0.0" — and disallowing DOCTYPE outright covers XXE regardless.
 */
internal fun hardenedDocumentBuilderFactory(): DocumentBuilderFactory =
    DocumentBuilderFactory.newInstance().apply {
        HARDENING_FEATURES.forEach { (feature, value) ->
            runCatching { setFeature(feature, value) }
        }
    }

private val HARDENING_FEATURES = mapOf(
    "http://apache.org/xml/features/disallow-doctype-decl" to true,
    "http://xml.org/sax/features/external-general-entities" to false,
    "http://xml.org/sax/features/external-parameter-entities" to false,
)
