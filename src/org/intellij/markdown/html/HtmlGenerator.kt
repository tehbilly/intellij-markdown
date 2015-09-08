package org.intellij.markdown.html

import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.ast.visitors.RecursiveVisitor
import org.intellij.markdown.flavours.MarkdownFlavourDescriptor
import org.intellij.markdown.html.entities.EntityConverter
import org.intellij.markdown.parser.LinkMap


public class HtmlGenerator(private val markdownText: String,
                           private val root: ASTNode,
                           flavour: MarkdownFlavourDescriptor,
                           linkMap: LinkMap = LinkMap.buildLinkMap(root, markdownText)) {
    private val providers: Map<IElementType, GeneratingProvider> = flavour.createHtmlGeneratingProviders(linkMap)
    private val htmlString: StringBuilder = StringBuilder()

    public fun generateHtml(): String {
        HtmlGeneratingVisitor().visitNode(root)
        return htmlString.toString()
    }

    inner class HtmlGeneratingVisitor : RecursiveVisitor() {
        override fun visitNode(node: ASTNode) {
            @suppress("USELESS_ELVIS")
            (providers.get(node.type)?.processNode(this, markdownText, node)
                    ?: node.acceptChildren(this))
        }

        public fun visitLeaf(node: ASTNode) {
            @suppress("USELESS_ELVIS")
            (providers.get(node.type)?.processNode(this, markdownText, node)
                    ?: consumeHtml(leafText(markdownText, node)))
        }

        public final fun consumeHtml(html: CharSequence) {
            htmlString.append(html)
        }
    }

    companion object {
        public fun leafText(text: String, node: ASTNode, replaceEscapesAndEntities: Boolean = true): CharSequence {
            if (node.type == MarkdownTokenTypes.BLOCK_QUOTE) {
                return ""
            }
            return EntityConverter.replaceEntities(node.getTextInNode(text), replaceEscapesAndEntities, replaceEscapesAndEntities)
        }

        public fun trimIndents(text: CharSequence, indent: Int): CharSequence {
            if (indent == 0) {
                return text
            }

            val buffer = StringBuilder()

            var lastFlushed = 0
            var offset = 0
            while (offset < text.length()) {
                if (offset == 0 || text[offset - 1] == '\n') {
                    buffer.append(text.subSequence(lastFlushed, offset))
                    var indentEaten = 0

                    eatIndentLoop@
                    while (indentEaten < indent && offset < text.length()) {
                        when (text[offset]) {
                            ' ' -> indentEaten++
                            '\t' -> indentEaten += 4 - indentEaten % 4
                            else -> break@eatIndentLoop
                        }
                        offset++
                    }

                    if (indentEaten > indent) {
                        buffer.append(" ".repeat(indentEaten - indent))
                    }
                    lastFlushed = offset
                }

                offset++
            }

            buffer.append(text.subSequence(lastFlushed, text.length()))
            return buffer
        }

    }
}