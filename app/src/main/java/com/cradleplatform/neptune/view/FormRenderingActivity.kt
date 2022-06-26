package com.cradleplatform.neptune.view

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.model.FormTemplate
import com.cradleplatform.neptune.model.RecyclerAdapter

class FormRenderingActivity : AppCompatActivity() {

    private var layoutManager: RecyclerView.LayoutManager? = null
    private var adapter: RecyclerView.Adapter<RecyclerAdapter.ViewHolder>? = null
    private var form: FormTemplate? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_form_rendering)

        form = intent.getSerializableExtra(EXTRA_FORM_TEMPLATE) as FormTemplate
        layoutManager = LinearLayoutManager(this)

        var recyclerView = findViewById<RecyclerView>(R.id.myRecyclerView)
        recyclerView.layoutManager = layoutManager

        adapter = RecyclerAdapter(form!!)
        recyclerView.adapter = adapter
    }

    companion object {
        private const val EXTRA_FORM_TEMPLATE = "JSON string for form template"

        @JvmStatic
        fun makeIntentWithFormTemplate(context: Context, formTemplate: FormTemplate): Intent {
            val bundle = Bundle()
            bundle.putSerializable(EXTRA_FORM_TEMPLATE, formTemplate)
            return Intent(context, FormRenderingActivity::class.java).apply {
                this.putExtras(bundle)
            }
        }
    }
}
