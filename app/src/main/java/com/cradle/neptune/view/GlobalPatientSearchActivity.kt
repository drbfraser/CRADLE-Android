package com.cradle.neptune.view

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.SearchView.OnQueryTextListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.volley.DefaultRetryPolicy
import com.android.volley.Request
import com.android.volley.VolleyError
import com.android.volley.toolbox.JsonArrayRequest
import com.android.volley.toolbox.Volley
import com.cradle.neptune.R
import com.cradle.neptune.dagger.MyApp
import com.cradle.neptune.manager.UrlManager
import com.cradle.neptune.model.GlobalPatient
import com.cradle.neptune.viewmodel.GlobalPatientAdapter
import org.json.JSONArray
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

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.global_patient_search)
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
                val searchUrl = urlManager.globalPatientSearch+ "/$query"
                searchServerForThePatient(searchUrl)
                return true
            }

        })
    }

    private fun searchServerForThePatient(searchUrl: String) {
     val jsonArrayRequest = object :JsonArrayRequest(Request.Method.GET,searchUrl,null,
         { response: JSONArray? ->
             setupPatientsRecycler(response as (JSONArray))
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
        jsonArrayRequest.retryPolicy = DefaultRetryPolicy(8000,DefaultRetryPolicy.DEFAULT_MAX_RETRIES,DefaultRetryPolicy.DEFAULT_BACKOFF_MULT)
        val queue = Volley.newRequestQueue(MyApp.getInstance())
        queue.add<JSONArray>(jsonArrayRequest)
    }

    private fun setupPatientsRecycler(response: JSONArray?) {
        if (response==null ||response.length()==0){
            return
        }
        val emptyView = findViewById<ImageView>(R.id.emptyView)
        emptyView.visibility = View.GONE
        val globalPatientList = ArrayList<GlobalPatient>()

        for (i in 0 until response.length()){
            val jsonObject: JSONObject = response[i] as JSONObject
            globalPatientList.add(GlobalPatient(jsonObject.getString("patientId"),
            jsonObject.getString("patientName"), jsonObject
                    .getString("villageNumber")))
        }
        val globalPatientAdapter = GlobalPatientAdapter(globalPatientList)
        val layout = LinearLayoutManager(this)
        val recyclerView = findViewById<RecyclerView>(R.id.globalPatientrecyclerview)
        recyclerView.layoutManager = layout
        recyclerView.adapter = globalPatientAdapter
        globalPatientAdapter.notifyDataSetChanged()

    }
}
