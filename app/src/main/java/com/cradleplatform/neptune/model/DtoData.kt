package com.cradleplatform.neptune.model

class DtoData {

    companion object {
        //Current user answer
        var form = mutableListOf<Pair<Int, String>>()

        //Form template with answers
        var resultForm: FormTemplate? = null

        //Raw form template
        var template: FormTemplate? = null
    }
}
