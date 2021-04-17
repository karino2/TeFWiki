package io.github.karino2.tefwiki

import android.app.Dialog
import android.content.DialogInterface
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.MenuItem
import android.webkit.WebView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.add
import androidx.fragment.app.commit
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

class SettingsActivity : AppCompatActivity(R.layout.activity_settings) {
    class OSSLicenseDialog : DialogFragment() {
        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val webView = WebView(activity)
            webView.loadUrl("file:///android_asset/open_source_licenses.html")

            return AlertDialog.Builder(requireContext())
                .setTitle(R.string.label_oss_license)
                .setView(webView)
                .setPositiveButton(R.string.label_ok, object: DialogInterface.OnClickListener{
                    override fun onClick(dialog: DialogInterface?, which: Int) {
                        dialog!!.dismiss()
                    }
                })
                .create()
        }
    }

    class SettingsFragment : PreferenceFragmentCompat(){
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.prefs, rootKey)
            findPreference<Preference>("reset_root_url")!!.setOnPreferenceClickListener {
                MainActivity.resetLastUriStr(requireContext())
                MainActivity.showMessage(requireContext(), "Root url reset.")
                true
            }
            findPreference<Preference>("oss_license")!!.setOnPreferenceClickListener {
                val fm = requireActivity().supportFragmentManager
                val ft = fm.beginTransaction()
                fm.findFragmentByTag("dialog_license")?.let {
                    ft.remove(it)
                }
                ft.addToBackStack(null)

                val dialog = OSSLicenseDialog()
                dialog.show(ft, "dialog_license")
                true
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)

        if(savedInstanceState == null) {
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                add<SettingsFragment>(R.id.fragmentContainer)
            }

        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }
}