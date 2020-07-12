package com.cradle.neptune.view

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.SearchView.OnQueryTextListener
import com.android.volley.Request
import com.android.volley.VolleyError
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import com.cradle.neptune.R
import com.cradle.neptune.dagger.MyApp
import com.cradle.neptune.manager.UrlManager
import org.json.JSONObject
import java.util.HashMap
import javax.inject.Inject

class GlobalPatientSearchActivity : AppCompatActivity() {

    @Inject
    lateinit var urlManager: UrlManager
    @Inject
    lateinit var sharedPreferences:SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_global_patient_search)
        // inject:
        (application as MyApp).appComponent.inject(this)
        if (supportActionBar != null) {
            supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        }
        setupGlobalPatientSearch()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private fun setupGlobalPatientSearch(){
        val searchView:SearchView = findViewById(R.id.globalPatientSearchView)

        searchView.setOnQueryTextListener(object :OnQueryTextListener {

            override fun onQueryTextChange(newText: String): Boolean {
                return false
            }

            override fun onQueryTextSubmit(query: String): Boolean {
                if (query=="")
                    return false
                val searchUrl = urlManager.globalPatientSearch+ query
                searchServerForThePatient(searchUrl)
                return true
            }

        })
    }

    private fun searchServerForThePatient(searchUrl: String) {
     var jsonObjectRequest = object :JsonObjectRequest(Request.Method.GET,searchUrl,null,
         { response: JSONObject? ->
            Log.d("bugg","success: "+response?.toString(4))
         },{ error: VolleyError? ->
             Log.d("bugg","error: "+error?.toString())
         }) {
         /**
          * Passing some request headers
          */
         override fun getHeaders(): Map<String, String>? {
             val headers =
                 HashMap<String, String>()
             val token =
                 sharedPreferences.getString(LoginActivity.TOKEN, LoginActivity.DEFAULT_TOKEN)
             headers[LoginActivity.AUTH] = "Bearer $token"
             return headers
         }
     }
        val queue = Volley.newRequestQueue(MyApp.getInstance())
        queue.add<JSONObject>(jsonObjectRequest)
    }
}
