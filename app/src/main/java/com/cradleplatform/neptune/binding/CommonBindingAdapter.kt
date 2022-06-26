package com.cradleplatform.neptune.binding

import android.widget.AutoCompleteTextView
import androidx.databinding.BindingAdapter
import com.cradleplatform.neptune.R

/**
 * A Utility file containing static Binding Adapters that is common across different view bindings
*/

/**
 * Populates a AutoCompleteView for the dropdown menu in Material's TextInputLayout from an Array.
 *
 * Commonly used for binding with ViewModel's list/data access functions
 */
@BindingAdapter("setMaterialSpinnerItemsWithArray")
fun setMaterialSpinnerItemsWithArray(
    view: AutoCompleteTextView,
    oldArray: Array<String>?,
    newArray: Array<String>?
) {
    if (oldArray === newArray) return
    if (newArray == null) return
    if (oldArray?.contentEquals(newArray) == true) return

    val adapter = MaterialSpinnerArrayAdapter(
        view.context,
        R.layout.list_dropdown_menu_item,
        newArray
    )
    view.setAdapter(adapter)
}
