package com.cradleplatform.neptune.binding

import android.widget.AutoCompleteTextView
import androidx.databinding.BindingAdapter
import com.cradleplatform.neptune.R

/**
 * FIXME: comment DO NOT COMMIT THIS
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
