package com.cradleplatform.neptune.model


import androidx.room.Entity

/**
 * Holds the latest version of [FormTemplate] for a [FormClassification]
 *
 * only hold one version(the newest) of a FormTemplate, same name but different languages counts
 * as a different [FormTemplate]. They are stored as a Json String in this table with
 * [formClass] ("FormClassification") + [language] as composite key
 *
 * @property formClass The name for the Classification for the set of the same forms in different versions
 * @property language The language for this formTemplate
 * @property formTemplate The Json String for a formTemplate, stores the latest version got from server
 */
@Entity(
    indices = [],
    primaryKeys = ["formClass","language"]
)
class FormClassification(
    var formClass:String,

    var language:String,

    var formTemplate: FormTemplate
) {


}