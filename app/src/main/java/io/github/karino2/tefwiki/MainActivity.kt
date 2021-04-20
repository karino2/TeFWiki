package io.github.karino2.tefwiki

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.GravityCompat
import androidx.core.widget.NestedScrollView
import androidx.documentfile.provider.DocumentFile
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.coroutineScope
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.whenStarted
import androidx.preference.PreferenceManager
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.*
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.flavours.gfm.StrikeThroughParser
import org.intellij.markdown.html.*
import org.intellij.markdown.parser.LinkMap
import org.intellij.markdown.parser.MarkdownParser
import org.intellij.markdown.parser.sequentialparsers.*
import org.intellij.markdown.parser.sequentialparsers.impl.*
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.prefs.PreferenceChangeEvent
import kotlin.collections.ArrayDeque
import kotlin.coroutines.CoroutineContext

class MainActivity : AppCompatActivity() {
    companion object {
        const val REQUEST_OPEN_TREE_ID = 1
        const val REQUEST_EDIT_MD = 2
        const val REQUEST_SETTING = 3

        @JvmField
        val WIKI_LINK: IElementType = MarkdownElementType("WIKI_LINK")


        const val  LAST_URI_KEY = "last_uri_path"
        fun lastUriStr(ctx: Context) = sharedPreferences(ctx).getString(LAST_URI_KEY, null)
        fun writeLastUriStr(ctx: Context, path : String) = sharedPreferences(ctx).edit()
                .putString(LAST_URI_KEY, path)
                .commit()

        fun resetLastUriStr(ctx: Context) = sharedPreferences(ctx).edit()
            .putString(LAST_URI_KEY, null)
            .commit()

        private fun sharedPreferences(ctx: Context) = ctx.getSharedPreferences("TEFWIKI", Context.MODE_PRIVATE)

        fun showMessage(ctx: Context, msg : String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
    }

    fun showMessage(msg: String) = showMessage(this, msg)

    val settingPrefs : SharedPreferences by lazy {  PreferenceManager.getDefaultSharedPreferences(this) }

    val webView : WebView by lazy {
        val view = findViewById<WebView>(R.id.webView)
        view.settings.javaScriptEnabled = true
        view.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (request.url.scheme == "tefwiki")
                {
                    openWikiLink(request.url.host!!)
                }
                else
                {
                    openUriExternal(request.url!!)
                }
                return true
            }
        }
        view
    }

    private fun openUriExternal(url: Uri) {
        startActivity(Intent(Intent.ACTION_VIEW).apply { data = url })
    }

    val drawerLayout : DrawerLayout by lazy { findViewById(R.id.drawer_layout) }

    val drawerToggle: ActionBarDrawerToggle by lazy {
        ActionBarDrawerToggle(this, drawerLayout, R.string.drawer_open, R.string.drawer_close)
    }


    val history = ArrayDeque<String>()

    fun pushHistory(doc: DocumentFile) {
        if (!history.isEmpty() && history.last() == doc.name)
            return
        history.addLast(doc.name!!)
    }

    val recentFiles : ArrayList<DocumentFile> = ArrayList()

    val adapter : ArrayAdapter<DocumentFile> by lazy {
        object: ArrayAdapter<DocumentFile>(this, R.layout.recent_item, recentFiles) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = convertView ?: layoutInflater.inflate(R.layout.recent_item, null)
                val doc = getItem(position)!!
                view.findViewById<TextView>(R.id.recentFileName).text = doc.name!!.removeSuffix(".md")
                view.findViewById<TextView>(R.id.recentDate).text = formatMTime(doc.lastModified())
                view.tag = doc
                return view
            }
        }
    }

    fun updateRecents() {
        lifecycle.coroutineScope.launch(Dispatchers.IO) {
            whenStarted {
                val files =
                        wikiRoot.listFiles()
                                .filter{ it.name!!.endsWith(".md") }
                                .sortedByDescending { it.lastModified() }
                                .take(20)

                recentFiles.clear()
                recentFiles.addAll(files)
                withContext(Dispatchers.Main) {
                    adapter.notifyDataSetChanged()
                }

            }

        }
    }

    val recentsList : ListView by lazy {
        val list = findViewById<ListView>(R.id.navigation_recents_list)
        list.setOnItemClickListener { parent, view, position, id ->
            val doc = view.tag as DocumentFile
            openMd(doc)
            drawerLayout.closeDrawers()
        }
        list
    }

    val navigationView : NavigationView by lazy {
        findViewById(R.id.navigation_view)
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        setSupportActionBar(findViewById(R.id.toolbar_main))
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        supportActionBar!!.setHomeButtonEnabled(true)

        drawerLayout.addDrawerListener(drawerToggle)
        drawerToggle.isDrawerIndicatorEnabled = true

        navigationView.setNavigationItemSelectedListener { item->
            showMessage(item.title.toString())
            drawerLayout.closeDrawers()
            true
        }

        drawerToggle.syncState()
        recentsList.adapter = adapter

        onBackPressedDispatcher.addCallback(this, object: OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if(drawerLayout.isDrawerOpen(GravityCompat.START))
                {
                    drawerLayout.closeDrawer(GravityCompat.START)
                    return
                }


                if(history.isEmpty()) {
                    finish()
                    return
                }
                if (history.last() == currentFileName) {
                    // first history back.
                    history.removeLast()
                    if(history.isEmpty()) {
                        finish()
                        return
                    }
                }

                val last = history.last()
                openWikiLinkWithoutHistory(last)
            }
        })

        lastUriStr(this)?.let {
            updateFolder()
            return
        }

        requestRootPickup()
    }

    private fun MainActivity.requestRootPickup() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        startActivityForResult(intent, REQUEST_OPEN_TREE_ID)
        showMessage("Choose wiki folder")
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        drawerToggle.syncState()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        drawerToggle.syncState()
    }

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
        }
    }

    class MdRootGenerator : OpenCloseGeneratingProvider() {
        override fun openTag(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
            visitor.consumeTagOpen(node, "div", " id=\"content-root\" class=\"content\"")
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
                        MarkdownElementTypes.MARKDOWN_FILE to MdRootGenerator(),
                        GFMElementTypes.STRIKETHROUGH to SimpleInlineTagProvider("s", 2, -2),
                        WIKI_LINK to WikiLinkProvider(),
                )
            }
        }
        val parser = MarkdownParser(flavour)

        val tree = parser.buildMarkdownTreeFromString(md)
        return HtmlGenerator(md, tree, flavour).generateHtml(
                HtmlGenerator.DefaultTagRenderer(
                    {node, tagName, attributes->
                        if (node.type == GFMElementTypes.TABLE)
                            attributes + "class=\"table is-striped\""
                        else
                            attributes
                    }, false))
    }


    var currentFileName = "Home.md"

    fun openWikiLink(fileName: String) {
        val doc = wikiRoot.findFile(fileName)
        if (doc == null) {
            startEditActivityForNew(fileName)
            return
        }
        openMd(doc)
    }

    fun openWikiLinkWithoutHistory(fileName: String) {
        val doc = wikiRoot.findFile(fileName)
        if (doc == null) {
            showMessage("File in history is deleted. Finish activity.")
            finish()
            return
        }
        openMdWithoutHistory(doc)
    }


    fun formatMTime( lastModified: Long ) : String {
        val sdf = SimpleDateFormat("YYYY-MM-dd HH:mm")
        return sdf.format(lastModified)
    }

    fun buildHeader(title: String, lastModified: Long) : String {
        val mtime = formatMTime(lastModified)
        return """
    <!DOCTYPE html>
    <html>
    <head>
        <meta charset="UTF-8">
        <title>TeFWiki</title>
        <meta http-equiv="Content-Security-Policy" content="script-src 'self' 'unsafe-inline';" />
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <link rel="stylesheet" href="./bulma.css">
        <link rel="stylesheet" href="./prism.css">
        <script src="./prism.js"></script>
        <style>
            hr {
                background-color: #888888
            }        
        </style>
    </head>
    <body id="body" style="background: #FBF8ED">
            <div class="container">
                <section class="hero is-dark">
                    <div class="hero-body">
                        <div class="container">
                            <h1 class="title" id="title">$title</h1>
                            <h3 class="subtitle" id="date">${mtime}</h3>
                        </div>
                    </div>
                </section>
                <section class="section">
                """.trimIndent()

    }

    val footer = """
                </section>
            </div>
    </body>
    </html>
    """.trimIndent()

    var mdSrc = ""

    fun openMdWithoutHistory( file: DocumentFile ) {
        val istream = contentResolver.openInputStream(file.uri)
        istream.use {
            val reader = BufferedReader(InputStreamReader(it))
            val src = reader.readText()
            openMdContent(file, src)
        }
    }

    fun openMd( file: DocumentFile ) {
        openMdWithoutHistory(file)
        pushHistory(file)
    }

    val nestedScrollView : NestedScrollView by lazy { findViewById(R.id.nestedScrollView) }

    private fun openMdContent(file: DocumentFile, content: String) {
        currentFileName = file.name!!
        mdSrc = content
        val html = parseMd(content)

        val title = currentFileName.removeSuffix(".md")
        val header = buildHeader(title, file.lastModified())

        webView.loadDataWithBaseURL(
            "file:///android_asset/",
            header + html + footer,
            "text/html",
            null,
            null
        )
        nestedScrollView.scrollTo(0, 0)
    }

    val defaultHome = """
        # Heading

        Initial wiki page.
        Please Edit this file.
        
        - list1
        - ~~list2~~
        - list3

        [[HelloLink]]
    """.trimIndent()

    fun ensureHome(dir: DocumentFile) : DocumentFile {
        dir.findFile("Home.md")?.let { return it }

        val doc = dir.createFile("text/markdown", "Home.md") ?: throw Error("Can't create  Home.md")
        writeContent(doc, defaultHome)
        return doc
    }

    fun createOrWriteContent(fileName: String, content: String) : DocumentFile? {
        wikiRoot.findFile(fileName)?.let {
            writeContent(it, content)
            return it
        }

        wikiRoot.createFile("text/markdown", fileName)?.let {
            writeContent(it, content)
            return it
        }
        showMessage("Can't create file $fileName")
        return null
    }

    fun writeContent(file: DocumentFile, content:String) {
        contentResolver.openOutputStream(file.uri, "wt").use {
            val writer = BufferedWriter(OutputStreamWriter(it))
            writer.use {
                writer.write(content)
            }
        }
    }

    val wikiRoot : DocumentFile
    get() {
        return DocumentFile.fromTreeUri(this, Uri.parse(lastUriStr(this))) ?: throw Exception("can't open dir")
    }

    fun updateFolder() {
        try{
            val df = wikiRoot
            val home = ensureHome(df)
            history.clear()
            openMd(home)
            updateRecents()
        }catch (e: RuntimeException) {
            showMessage(this, e.message!!)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        when(requestCode) {
            REQUEST_OPEN_TREE_ID-> {
                if(resultCode == Activity.RESULT_OK) {
                    resultData?.data?.also {uri ->
                        contentResolver.takePersistableUriPermission(uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                        writeLastUriStr(this, uri.toString())
                        updateFolder()
                    }
                    return
                }
            }
            REQUEST_EDIT_MD-> {
                if(resultCode == Activity.RESULT_OK) {
                    val fileName = resultData!!.getStringExtra("MD_FILE_NAME")
                    val content = resultData!!.getStringExtra("MD_CONTENT")
                    createOrWriteContent(fileName, content)?.let {
                        openMdContent(it, content)
                        updateRecents()
                    }
                }
                return
            }
            REQUEST_SETTING-> {
                if (null == lastUriStr(this))
                    requestRootPickup()
                return
            }
        }
        super.onActivityResult(requestCode, resultCode, resultData)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId) {
            R.id.menu_item_edit -> {
                startEditActivity(currentFileName, mdSrc)
                return true
            }
            R.id.menu_item_reload -> {
                showMessage(getString(R.string.reload_msg))
                lifecycleScope.launch {
                    delay(1)
                    openWikiLinkWithoutHistory(currentFileName)
                    updateRecents()
                }
                return true
            }
            R.id.menu_item_setting -> {
                startActivityForResult(Intent(this, SettingsActivity::class.java), REQUEST_SETTING)
            }
        }

        if(drawerToggle.onOptionsItemSelected(item))
            return true

        return super.onOptionsItemSelected(item)
    }


    private fun startInternalEditActivity(fileName: String, content: String) {
        Intent(this, EditActivity::class.java).apply {
            putExtra("MD_FILE_NAME", fileName)
            putExtra("MD_CONTENT", content)
        }.also {
            startActivityForResult(it, REQUEST_EDIT_MD)
        }
    }

    val useExternalEditor : Boolean
    get() = settingPrefs.getBoolean("use_external_editor", false)

    private fun startEditActivity(fileName: String, content: String) {
        if(useExternalEditor)
        {
            wikiRoot.findFile(fileName)?.let {doc ->
                Intent().apply {
                    action = Intent.ACTION_VIEW
                    setDataAndType(doc.uri, "text/markdown")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }.also { startActivity(Intent.createChooser(it, getString(R.string.label_editor_chooser_title))) }
            }
        }
        else
        {
            startInternalEditActivity(fileName, content)
        }
    }

    private fun startEditActivityForNew(fileName: String) {
        startInternalEditActivity(fileName, "")
    }

}