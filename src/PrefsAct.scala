package org.aprsdroid.app

import _root_.android.content.Intent
import _root_.android.net.Uri
import _root_.android.os.Bundle
import _root_.android.os.Environment
import _root_.android.preference.Preference
import _root_.android.preference.Preference.OnPreferenceClickListener
import _root_.android.preference.PreferenceActivity
import _root_.android.preference.PreferenceManager
import _root_.android.view.{Menu, MenuItem}
import _root_.android.widget.Toast

import java.text.SimpleDateFormat
import java.io.{PrintWriter, File}
import java.util.Date
import org.json.JSONObject

class PrefsAct extends PreferenceActivity {
	lazy val db = StorageDatabase.open(this)

	def exportPrefs() {
		val filename = "profile-%s.aprs".format(new SimpleDateFormat("yyyyMMdd-HHmm").format(new Date()))
		val file = new File(Environment.getExternalStorageDirectory(), filename)
		try {
			val prefs = PreferenceManager.getDefaultSharedPreferences(this)
			val json = new JSONObject(prefs.getAll)
			val fo = new PrintWriter(file)
			fo.println(json.toString(2))
			fo.close()

			startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND)
				.setType("text/plain")
				.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file))
				.putExtra(Intent.EXTRA_SUBJECT, filename),
				file.toString()))
		} catch {
			case e : Exception => Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show()
		}
	}

	def fileChooserPreference(pref_name : String, reqCode : Int, titleId : Int) {
		findPreference(pref_name).setOnPreferenceClickListener(new OnPreferenceClickListener() {
			def onPreferenceClick(preference : Preference) = {
				val get_file = new Intent(Intent.ACTION_GET_CONTENT).setType("*/*")
				startActivityForResult(Intent.createChooser(get_file,
					getString(titleId)), reqCode)
				true
			}
		});
	}
	override def onCreate(savedInstanceState: Bundle) {
		super.onCreate(savedInstanceState)
		addPreferencesFromResource(R.xml.preferences)
	}

	def resolveContentUri(uri : Uri) = {
		val Array(storage, path) = uri.getPath().replace("/document/", "").split(":", 2)
		android.util.Log.d("PrefsAct", "resolveContentUri s=" + storage + " p=" + path)
		if (storage == "primary")
			Environment.getExternalStorageDirectory() + "/" + path
		else
			"/storage/" + storage + "/" + path
	}

	def parseFilePickerResult(data : Intent, pref_name : String, error_id : Int) {
		val file = data.getData().getScheme() match {
		case "file" =>
			data.getData().getPath()
		case "content" =>
			// fix up Uri for KitKat+; http://stackoverflow.com/a/20559175/539443
			// http://stackoverflow.com/a/27271131/539443
			if ("com.android.externalstorage.documents".equals(data.getData().getAuthority())) {
				resolveContentUri(data.getData())
			} else {
				val fixup_uri = Uri.parse(data.getDataString().replace(
					"content://com.android.providers.downloads.documents/document",
					"content://downloads/public_downloads"))
				val cursor = getContentResolver().query(fixup_uri, null, null, null, null)
				cursor.moveToFirst()
				val idx = cursor.getColumnIndex("_data")
				val result = if (idx != -1) cursor.getString(idx) else null
				cursor.close()
				result
			}
		case _ =>
			null
		}
		if (file != null) {
			PreferenceManager.getDefaultSharedPreferences(this)
				.edit().putString(pref_name, file).commit()
			Toast.makeText(this, file, Toast.LENGTH_SHORT).show()
			// reload prefs
			finish()
			startActivity(getIntent())
		} else {
			val errmsg = getString(error_id, data.getDataString())
			Toast.makeText(this, errmsg, Toast.LENGTH_SHORT).show()
			db.addPost(System.currentTimeMillis(), StorageDatabase.Post.TYPE_ERROR,
				getString(R.string.post_error), errmsg)
		}
	}

	override def onActivityResult(reqCode : Int, resultCode : Int, data : Intent) {
		android.util.Log.d("PrefsAct", "onActResult: request=" + reqCode + " result=" + resultCode + " " + data)
		if (resultCode == android.app.Activity.RESULT_OK && reqCode == 123456) {
		} else
		if (resultCode == android.app.Activity.RESULT_OK && reqCode == 123457) {
		} else
		if (resultCode == android.app.Activity.RESULT_OK && reqCode == 123458) {
			data.setClass(this, classOf[ProfileImportActivity])
			startActivity(data)
		} else
			super.onActivityResult(reqCode, resultCode, data)
	}

	override def onCreateOptionsMenu(menu : Menu) : Boolean = {
		getMenuInflater().inflate(R.menu.options_prefs, menu)
		true
	}
	override def onOptionsItemSelected(mi : MenuItem) : Boolean = {
		mi.getItemId match {
		case R.id.profile_load =>
			val get_file = new Intent(Intent.ACTION_GET_CONTENT).setType("*/*")
				startActivityForResult(Intent.createChooser(get_file,
				getString(R.string.profile_import_activity)), 123458)
			true
		case R.id.profile_export =>
			exportPrefs()
			true
		case _ => super.onOptionsItemSelected(mi)
		}
	}
}
