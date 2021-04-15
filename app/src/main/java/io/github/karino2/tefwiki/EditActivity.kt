package io.github.karino2.tefwiki

import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.PersistableBundle
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.InputMethodManager
import android.widget.EditText

class EditActivity : AppCompatActivity() {

    val editText : EditText by lazy { findViewById(R.id.editText) }
    lateinit var fileName : String

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("MD_FILE_NAME", fileName)
        super.onSaveInstanceState(outState)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        fileName = savedInstanceState.getString("MD_FILE_NAME")!!
    }

    fun showMessage(msg: String) = MainActivity.showMessage(this, msg)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit)

        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        intent?.let {
            fileName = it.getStringExtra("MD_FILE_NAME")
            editText.setText(it.getStringExtra("MD_CONTENT"))

            supportActionBar!!.title = fileName
        }
    }


    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_edit, menu)
        return super.onCreateOptionsMenu(menu)
    }

    fun closeSoftKey() {
        currentFocus?.let {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(it.windowToken, 0)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId) {
            R.id.menu_item_save -> {
                Intent().apply {
                    putExtra("MD_FILE_NAME", fileName)
                    putExtra("MD_CONTENT", editText.text.toString())
                }.also { setResult(RESULT_OK, it) }
                closeSoftKey()
                finish()
                return true
            }
            R.id.menu_item_brabra -> {
                val curPos = editText.selectionEnd
                val text = editText.text
                editText.setText(text.subSequence(0, curPos).toString() + "[[]]" + text.subSequence(curPos, text.length))
                editText.setSelection(curPos+2)
            }
            android.R.id.home -> {
                finish()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

}