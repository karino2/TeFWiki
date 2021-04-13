package io.github.karino2.tefwiki

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.text.Html
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.LeafASTNode
import org.intellij.markdown.ast.accept
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.flavours.gfm.StrikeThroughParser
import org.intellij.markdown.html.*
import org.intellij.markdown.parser.*
import org.intellij.markdown.parser.markerblocks.MarkerBlock
import org.intellij.markdown.parser.markerblocks.MarkerBlockProvider
import org.intellij.markdown.parser.sequentialparsers.*
import org.intellij.markdown.parser.sequentialparsers.impl.*
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class MainActivity : AppCompatActivity() {
    companion object {
        const val REQUEST_OPEN_TREE_ID = 1

        @JvmField
        val WIKI_LINK: IElementType = MarkdownElementType("WIKI_LINK")


        const val  LAST_URI_KEY = "last_uri_path"
        fun lastUriStr(ctx: Context) = sharedPreferences(ctx).getString(LAST_URI_KEY, null)
        fun writeLastUriStr(ctx: Context, path : String) = sharedPreferences(ctx).edit()
                .putString(LAST_URI_KEY, path)
                .commit()


        private fun sharedPreferences(ctx: Context) = ctx.getSharedPreferences("TEFWIKI", Context.MODE_PRIVATE)

        fun showMessage(ctx: Context, msg : String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
    }

    val button: Button by lazy {
        findViewById<Button>(R.id.button)
    }

    val webView : WebView by lazy {
        val view = findViewById<WebView>(R.id.webView)
        view.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (request.url.scheme == "tefwiki")
                {
                    openWikiLink(request.url.host!!)
                    /*
                    val path = request.url.host
                    val tmp = request.url.toString()
                    showMessage(this@MainActivity, "tefwiki link $tmp")
                     */
                }
                else
                {
                    openUriExternal(request.url!!)
                }
                return true
                // return super.shouldOverrideUrlLoading(view, request)
            }
        }
        view
    }

    private fun openUriExternal(url: Uri) {
        startActivity(Intent(Intent.ACTION_VIEW).apply { data = url })
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        button.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            startActivityForResult(intent, Companion.REQUEST_OPEN_TREE_ID)
        }

        val urlstr = lastUriStr(this)

        if (urlstr == null) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            startActivityForResult(intent, Companion.REQUEST_OPEN_TREE_ID)
            return
        }

        updateFolder()
    }

    /*
    class WikiLinkMarkerProvider : MarkerBlockProvider<MarkerProcessor.StateInfo> {
        override fun createMarkerBlocks(pos: LookaheadText.Position,
                                        productionHolder: ProductionHolder,
                                        stateInfo: MarkerProcessor.StateInfo): List<MarkerBlock> {

            if (!MarkerBlockProvider.isStartOfLineWithConstraints(pos, stateInfo.currentConstraints)) {
                return emptyList()
            }
            return emptyList()
        }

    }
     */

    class WikiLinkParser : SequentialParser {
        fun parseWikiLink(iterator: TokensCache.Iterator): LocalParsingResult? {
            val startIndex = iterator.index
            var it = iterator
            val delegate = RangesListBuilder()

            assert( it.type == MarkdownTokenTypes.LBRACKET)

            it = it.advance()
            if (it.type != MarkdownTokenTypes.LBRACKET)
                return null
            it = it.advance()
            while(it.type != null) {
                if (it.type == MarkdownTokenTypes.RBRACKET) {
                    it = it.advance()
                    if (it.type == MarkdownTokenTypes.RBRACKET) {
                        // success
                        return LocalParsingResult(it,
                                listOf(SequentialParser.Node(startIndex..it.index + 1, WIKI_LINK)),
                                delegate.get())
                    }
                    return null
                }
                delegate.put(it.index)
                it = it.advance()
            }
            return null
        }

        override fun parse(tokens: TokensCache, rangesToGlue: List<IntRange>): SequentialParser.ParsingResult {
            var result = SequentialParser.ParsingResultBuilder()
            val delegateIndices = RangesListBuilder()
            var iterator: TokensCache.Iterator = tokens.RangesListIterator(rangesToGlue)

            while (iterator.type != null) {
                if (iterator.type == MarkdownTokenTypes.LBRACKET) {
                    val wikiLink = parseWikiLink(iterator)
                    if (wikiLink != null) {
                        iterator = wikiLink.iteratorPosition.advance()
                        result = result.withOtherParsingResult(wikiLink)
                        continue
                    }
                }

                delegateIndices.put(iterator.index)
                iterator = iterator.advance()
            }

            return result.withFurtherProcessing(delegateIndices.get())

        }

    }

    /*
    class WikiLinkParagraphProvider : TrimmingInlineHolderProvider() {
        // true if [SHORT_REFERENCE_LINK] and SHORT_REFERENCE_LINK is parent of LINK_LABEL and LINK_LABEL is [TEXT]
        fun isWikiLink(children : List<ASTNode>, index: Int) : Boolean {
            if (children.size <= index +2)
                return false
            if (!(children[index].type == MarkdownTokenTypes.LBRACKET &&
                    children[index+1].type == MarkdownElementTypes.SHORT_REFERENCE_LINK &&
                    children[index+2].type == MarkdownTokenTypes.RBRACKET))
                return false
            val shortRef = children[index+1]
            if (shortRef.children.size != 1 || shortRef.children[0].type != MarkdownElementTypes.LINK_LABEL)
                return false
            val linkLabel = shortRef.children[0]
            if (linkLabel.children.size != 3)
                return false
            return linkLabel.children[0].type == MarkdownTokenTypes.LBRACKET &&
                    linkLabel.children[1].type == MarkdownTokenTypes.TEXT &&
                    linkLabel.children[2].type == MarkdownTokenTypes.RBRACKET
        }

        override fun processNode(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
            visitor.consumeTagOpen(node, "p")

            val children = childrenToRender(node)
            var i = 0

            while (i < children.size) {
                if (isWikiLink(children, i)) {
                    val shortRefLink = children[i+1]
                    val linkLabel = shortRefLink.children[0].children[1].getTextInNode(text).toString()
                    visitor.consumeTagOpen(shortRefLink, "a", "class=\"wikilink\" href=\"/$linkLabel.md\"")
                    visitor.consumeHtml(linkLabel)
                    visitor.consumeTagClose("a")
                    // Log.d("TeFWiki", linkLabel)
                    i += 3
                } else {
                    val child = children[i]
                    if (child is LeafASTNode) {
                        visitor.visitLeaf(child)
                    } else {
                        child.accept(visitor)
                    }
                    i++
                }
            }

            visitor.consumeTagClose("p")
        }
    }

     */

    val header = """
    <!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>TeFWiki</title>
    <meta http-equiv="Content-Security-Policy" content="script-src 'self' 'unsafe-inline';" />
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <link rel="stylesheet" href="./bulma.css">
</head>
<body id="body" style="background: #FBF8ED">
    <div class="section">
        <div class="container">
            <div class="columns">
                <div class="column is-four-fifths">
                    <nav class="level">
                        <div class="level-left">
                            <div class="level-item">
                                <div class="container">
                                    <h1 class="title" id="title">Hello</h1>
                                    <div class="content is-small">
                                        Last modified: <span id="date">21:34</span>
                                    </div>    
                                </div>
                            </div>
                    
                        </div>
                    </nav>
                    <section class="section">
    """.trimIndent()

    val footer = """
                            </section>
                        </div>
                    </div>
                </div>    
            </div>
        </body>
        </html>

    """.trimIndent()

    open class WikiLinkProvider : GeneratingProvider {
        override fun processNode(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
            val children = node.children
            // [[TEXT]]
            assert(children.size == 5)

            val label = children[2]
            val linkText = label.getTextInNode(text).toString()

            visitor.consumeTagOpen(label, "a", "class=\"wikilink\" href=\"tefwiki://$linkText.md\"")
            visitor.consumeHtml(linkText)
            visitor.consumeTagClose("a")
            /*
            val index = children.indexOf(node)
            val next = children[index+1]
            val next1 = children[index+2]
            val next2 = children[index+3]

             */
            Log.d("TeFWiki", linkText)
        }
    }

    class MdRootGenerator : OpenCloseGeneratingProvider() {
        override fun openTag(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
            visitor.consumeTagOpen(node, "div", "id=\"content-root\" class=\"content\"")
        }
        override fun closeTag(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
            visitor.consumeTagClose("div")
        }


    }

    fun parseMd(md:String) : String {
        val flavour = object : GFMFlavourDescriptor() {
            override val sequentialParserManager = object : SequentialParserManager() {
                override fun getParserSequence(): List<SequentialParser> {
                    return listOf(AutolinkParser(listOf(MarkdownTokenTypes.AUTOLINK, GFMTokenTypes.GFM_AUTOLINK)),
                            BacktickParser(),
                            WikiLinkParser(),
                            ImageParser(),
                            InlineLinkParser(),
                            ReferenceLinkParser(),
                            StrikeThroughParser(),
                            EmphStrongParser())

                }
            }
            override fun createHtmlGeneratingProviders(
                    linkMap: LinkMap,
                    baseURI: URI?
            ): Map<IElementType, GeneratingProvider> {
                return super.createHtmlGeneratingProviders(linkMap, baseURI) + hashMapOf(
                        /*
                        MarkdownElementTypes.SHORT_REFERENCE_LINK to MyInlineHolderProvider(),
                        MarkdownElementTypes.LINK_LABEL to MyInlineHolderProvider(),
                        MarkdownElementTypes.LINK_DEFINITION to MyInlineHolderProvider(),
                        MarkdownTokenTypes.LBRACKET to MyInlineHolderProvider(),
                         */
                        MarkdownElementTypes.MARKDOWN_FILE to MdRootGenerator(),
                        WIKI_LINK to WikiLinkProvider(),
                        // MarkdownElementTypes.PARAGRAPH to WikiLinkParagraphProvider(),
                        // MarkdownElementTypes.LINK_TEXT to MyInlineHolderProvider(),
                        // MarkdownElementTypes.LINK_TITLE to MyInlineHolderProvider(),
                )
            }
        }
        val parser = MarkdownParser(flavour)

        val tree = parser.buildMarkdownTreeFromString(md)
        return HtmlGenerator(md, tree, flavour).generateHtml()
    }


    var current = "Home.md"

    fun openWikiLink(name: String) {
        val doc = wikiRoot.findFile(name)
        if (doc == null) {
            showMessage(this, "NYI: $name is newly created")
            return
        }
        openMd(doc)
    }

    fun openMd( file: DocumentFile ) {
        current = file.name!!
        val istream = contentResolver.openInputStream(file.uri)
        istream.use {
            val reader = BufferedReader(InputStreamReader(it))
            val src = reader.readText()
            val html = parseMd(src)

            webView.loadDataWithBaseURL("file:///assets/", header+html+footer, "text/html", null, null)
            /*
            val spanned = Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT)
            textViewMd.text = spanned

             */
        }
    }

    val defaultHome = """
        # Home

        Initial wiki page.
        Please Edit this file.

        [[HelloLink]]
    """.trimIndent()

    fun ensureHome(dir: DocumentFile) : DocumentFile {
        val home = dir.findFile("Home.md")
        if ( home != null)
            return home

        val doc = dir.createFile("text/markdown", "Home.md") ?: throw Exception("Can't create  Home.md")
        writeContent(doc, defaultHome)
        return doc
    }

    fun writeContent(file: DocumentFile, content:String) {
        val ostream = contentResolver.openOutputStream(file.uri)
        ostream.use {
            val writer = BufferedWriter(OutputStreamWriter(it))
            writer.write(content)
        }
    }

    val wikiRoot : DocumentFile
    get() {
        return DocumentFile.fromTreeUri(this, Uri.parse(lastUriStr(this))) ?: throw Error("can't open dir")
    }

    fun updateFolder() {
        try{
            val df = wikiRoot
            val home = ensureHome(df)
            openMd(home)
            /*
            val files = df.listFiles()
            val text = files.map {
                val name = it.name
                val lm = it.lastModified()
                val uri = it.uri
                "$name, $lm, $uri"
            }.joinToString("\n")
            editText.setText(text)
             */
        }catch (e: RuntimeException) {
            showMessage(this, e.message!!)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        if (requestCode == Companion.REQUEST_OPEN_TREE_ID &&
                resultCode == Activity.RESULT_OK)
        {
            resultData?.data?.also {uri ->
                contentResolver.takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                writeLastUriStr(this, uri.toString())
                updateFolder()
            }
            return
        }
        super.onActivityResult(requestCode, resultCode, resultData)
    }

}