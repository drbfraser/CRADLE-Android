package com.cradleplatform.neptune.binding

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.Filter

/**
 * We make an ArrayAdapter that has a no-op filtering. If we don't do this, then Spinners as
 * prescribed by Material Design will remove all of its options when doing Data Binding, since there
 * is no good way to have that use setText with filter parameter set to false.
 *
 * Source:
 * https://gist.github.com/rmirabelle/7670cb01627ac530ce3e5d47d9785258#file-nofilterarrayadapter-kt
 */
class MaterialSpinnerArrayAdapter(
    context: Context,
    layout: Int,
    var values: Array<String>
) : ArrayAdapter<String>(context, layout, values) {
    private val noOpFilter = object : Filter() {
        override fun performFiltering(constraint: CharSequence?) =
            FilterResults().also {
                it.values = values
                it.count = values.size
            }

        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            notifyDataSetChanged()
        }
    }

    override fun getFilter() = noOpFilter
}
