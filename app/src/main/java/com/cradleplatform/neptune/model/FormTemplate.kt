package com.cradleplatform.neptune.model


data class FormTemplate (

	val id : String?,
	val name : String?,
	val category : String?,
	val version : String?,
	val questions : List<Questions>?
)

data class Questions (

	val questionId : String?,
	val categoryIndex : String?,
	val questionIndex : Int?,
	val questionType : String?,
	val required : Boolean?,
	val numMin : Double?,
	val visibleCondition : List<VisibleCondition>?,
	val questionLangVersions : List<QuestionLangVersions>?
)

data class VisibleCondition (

	val qidx : Int?,
	val relation : String?,
	val answers : Answers?
)

data class Answers (

	val number : Int?
)

data class QuestionLangVersions (

	val lang : String?,
	val questionText : String?,
	val mcOptions : List<McOptions>?
)

data class McOptions (

	val mcid : Int?,
	val opt : String?
)