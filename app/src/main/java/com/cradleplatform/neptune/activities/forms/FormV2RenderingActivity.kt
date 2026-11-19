package com.cradleplatform.neptune.activities.forms

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.view.descendants
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.cradleplatform.neptune.R
import com.cradleplatform.neptune.activities.patients.PatientProfileActivity
import com.cradleplatform.neptune.model.AnswerV2
import com.cradleplatform.neptune.model.FormTemplateQuestionV2
import com.cradleplatform.neptune.model.QuestionTypeEnum
import com.cradleplatform.neptune.model.getTranslation
import com.cradleplatform.neptune.utilities.Protocol
import com.cradleplatform.neptune.utilities.connectivity.api24.ConnectivityOptions
import com.cradleplatform.neptune.utilities.connectivity.api24.NetworkStateManager
import com.cradleplatform.neptune.viewmodel.forms.FormTemplateDetailV2ViewModel
import com.cradleplatform.neptune.viewmodel.forms.FormV2DraftSaveState
import com.cradleplatform.neptune.viewmodel.forms.FormV2DraftDeleteState
import com.cradleplatform.neptune.viewmodel.forms.FormV2RenderingState
import com.cradleplatform.neptune.viewmodel.forms.FormV2RenderingViewModel
import com.cradleplatform.neptune.viewmodel.forms.FormV2SubmissionState
import com.cradleplatform.neptune.viewmodel.forms.FormV2SubmissionErrorType
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

/**
 * Renders V2 answers, saves local drafts, and submits validated forms over HTTP or SMS.
 */
@AndroidEntryPoint
class FormV2RenderingActivity : AppCompatActivity() {

    @Inject
    lateinit var networkStateManager: NetworkStateManager

    private val viewModel: FormV2RenderingViewModel by viewModels()
    private val disabledInputs = mutableMapOf<View, Boolean>()
    private val questionErrors = mutableMapOf<String, TextView>()
    private val questionInputs = mutableMapOf<String, View>()
    private val inputTints = mutableMapOf<String, ColorStateList?>()
    private var highlightedQuestionId: String? = null
    private var isLeavingForm = false
    private var submissionErrorDialog: AlertDialog? = null
    private var navigationDialog: AlertDialog? = null
    private var submissionModeDialog: android.app.AlertDialog? = null
    private var draftDeleteDialog: android.app.AlertDialog? = null
    private val formBackCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (isLeavingForm || viewModel.draftDeleteState.value == FormV2DraftDeleteState.Deleting) return
            if (viewModel.submissionState.value == FormV2SubmissionState.Submitting) {
                Toast.makeText(this@FormV2RenderingActivity, R.string.form_submitting, Toast.LENGTH_SHORT).show()
                return
            }
            if (viewModel.hasUnsavedChanges) {
                showUnsavedChangesDialog()
            } else {
                isLeavingForm = true
                finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_form_v2_rendering)
        supportActionBar?.title = getString(R.string.create_new_form)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        onBackPressedDispatcher.addCallback(this, formBackCallback)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.state.collectLatest { state ->
                        render(state)
                        updateSubmissionControls()
                    }
                }
                // Draft writes update the footer without rebuilding the form fields.
                launch {
                    viewModel.draftSaveState.collectLatest { state -> renderDraftSaveState(state) }
                }
                launch {
                    viewModel.submissionState.collectLatest { state -> renderSubmissionState(state) }
                }
                launch {
                    viewModel.submissionErrors.collectLatest { showSubmissionErrorDialog(it) }
                }
                launch {
                    viewModel.draftDeleteState.collectLatest { state -> renderDraftDeleteState(state) }
                }
            }
        }
    }

    override fun onStop() {
        // Rotation keeps the ViewModel; backgrounding still backs up the draft.
        if (!isLeavingForm && !isChangingConfigurations) viewModel.saveDraft()
        super.onStop()
    }

    override fun onDestroy() {
        submissionErrorDialog?.dismiss()
        navigationDialog?.dismiss()
        submissionModeDialog?.dismiss()
        draftDeleteDialog?.dismiss()
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_forms_v2, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val patientId = intent.getStringExtra(FormTemplateListV2Activity.EXTRA_PATIENT_ID)
        menu.findItem(R.id.formSubmit)?.apply {
            isVisible = !patientId.isNullOrBlank()
            isEnabled = viewModel.canSubmit && !isLeavingForm &&
                viewModel.draftSaveState.value != FormV2DraftSaveState.Saving
        }
        menu.findItem(R.id.formDelete)?.apply {
            isVisible = viewModel.hasSavedDraft
            isEnabled = viewModel.canDeleteDraft && !isLeavingForm
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        android.R.id.home -> {
            onBackPressedDispatcher.onBackPressed()
            true
        }
        R.id.formSubmit -> {
            if (viewModel.canSubmit && !isLeavingForm) showFormSubmissionModeDialog()
            true
        }
        R.id.formDelete -> {
            if (viewModel.canDeleteDraft && !isLeavingForm) showDeleteFormDialog()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun showFormSubmissionModeDialog() {
        if (submissionModeDialog?.isShowing == true) return
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle(R.string.how_to_submit)
        builder.setMessage(R.string.choose_an_option)
        val internetString = getInternetTypeString()
        if (internetString.isEmpty()) {
            builder.setPositiveButton(R.string.form_dialog_not_connected_internet) { _, _ ->
                startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
            }
        } else {
            builder.setPositiveButton(internetString) { _, _ ->
                submitForm(Protocol.HTTP)
            }
        }

        builder.setNeutralButton(R.string.form_v2_save_and_send_later) { _, _ ->
            saveDraftAndLeave()
        }
        builder.setNegativeButton(R.string.SMS) { _, _ ->
            submitForm(Protocol.SMS)
        }
        submissionModeDialog = builder.show()
    }

    private fun getInternetTypeString(): String {
        return when (networkStateManager.getConnectivity()) {
            ConnectivityOptions.WIFI -> "Wifi"
            ConnectivityOptions.CELLULAR_DATA -> "Cellular"
            ConnectivityOptions.SMS -> ""
            ConnectivityOptions.NONE -> ""
        }
    }

    private fun showDeleteFormDialog() {
        if (draftDeleteDialog?.isShowing == true) return
        draftDeleteDialog = android.app.AlertDialog.Builder(this)
            .setTitle(R.string.form_v2_delete_title)
            .setMessage(R.string.form_v2_delete_message)
            .setPositiveButton(R.string.yes) { _, _ ->
                viewModel.deleteDraft()
                updateSubmissionControls()
            }
            .setNegativeButton(R.string.no, null)
            .show()
    }

    private fun renderDraftDeleteState(state: FormV2DraftDeleteState) {
        updateSubmissionControls()
        when (state) {
            FormV2DraftDeleteState.Deleted -> {
                if (!isLeavingForm) {
                    isLeavingForm = true
                    finish()
                }
            }
            FormV2DraftDeleteState.Error ->
                Toast.makeText(this, R.string.form_v2_delete_error, Toast.LENGTH_LONG).show()
            else -> Unit
        }
    }

    private fun showUnsavedChangesDialog() {
        if (navigationDialog?.isShowing == true) return
        navigationDialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.unsaved_changes_dialog_title)
            .setMessage(R.string.unsaved_changes_dialog_message)
            .setPositiveButton(R.string.action_leave) { _, _ ->
                isLeavingForm = true
                finish()
            }
            .setNegativeButton(R.string.action_stay, null)
            .show()
    }

    private fun showSubmissionErrorDialog(errorType: FormV2SubmissionErrorType) {
        if (isLeavingForm || isFinishing) return
        val messageRes = when (errorType) {
            FormV2SubmissionErrorType.SERVER_ERROR -> R.string.form_submission_server_error_message
            FormV2SubmissionErrorType.NETWORK_ERROR -> R.string.form_submission_network_error_message
            FormV2SubmissionErrorType.AUTH_ERROR -> R.string.form_submission_auth_error_message
            FormV2SubmissionErrorType.PERMISSION_ERROR -> R.string.form_v2_submission_permission_error_message
        }
        val builder = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.form_submission_error_title)
            .setIcon(R.drawable.ic_baseline_warning_24)
            .setMessage(messageRes)
        if (errorType == FormV2SubmissionErrorType.SERVER_ERROR ||
            errorType == FormV2SubmissionErrorType.NETWORK_ERROR
        ) {
            builder.setPositiveButton(R.string.form_submission_retry) { _, _ -> submitForm() }
        }
        if (errorType != FormV2SubmissionErrorType.AUTH_ERROR) {
            builder.setNeutralButton(R.string.form_submission_save_draft) { _, _ ->
                saveDraftAndLeave()
            }
        }
        builder.setNegativeButton(R.string.cancel, null)
        submissionErrorDialog?.dismiss()
        submissionErrorDialog = builder.show()
    }

    private fun saveDraftAndLeave() {
        if (isLeavingForm) return
        isLeavingForm = true
        updateSubmissionControls()
        lifecycleScope.launch {
            val saveJob = viewModel.saveDraft()
            saveJob?.join()
            if (saveJob != null && viewModel.draftSaveState.value == FormV2DraftSaveState.Error) {
                isLeavingForm = false
                updateSubmissionControls()
                return@launch
            }
            if (saveJob != null && viewModel.draftSaveState.value == FormV2DraftSaveState.Saved) {
                Toast.makeText(
                    this@FormV2RenderingActivity,
                    R.string.form_submission_saved_draft_success,
                    Toast.LENGTH_LONG
                ).show()
            }
            returnToPatientProfile()
        }
    }

    private fun renderDraftSaveState(state: FormV2DraftSaveState) {
        val saveStatus = findViewById<TextView>(R.id.form_v2_draft_save_status)
        saveStatus.text = when (state) {
            FormV2DraftSaveState.Idle -> ""
            FormV2DraftSaveState.Saving -> getString(R.string.form_v2_draft_saving)
            FormV2DraftSaveState.Saved -> getString(R.string.form_submission_saved_draft_success)
            FormV2DraftSaveState.Error -> getString(R.string.form_v2_draft_save_error)
        }
        saveStatus.visibility = if (saveStatus.text.isEmpty()) View.GONE else View.VISIBLE
        invalidateOptionsMenu()
    }

    private fun submitForm(protocol: Protocol = viewModel.lastSubmissionProtocol) {
        viewModel.submitForm(protocol)
        // Validation is synchronous; reveal the result even when StateFlow has not changed.
        highlightedQuestionId = null
        renderSubmissionState(viewModel.submissionState.value)
    }

    private fun renderSubmissionState(state: FormV2SubmissionState) {
        renderQuestionError(state)
        val status = findViewById<TextView>(R.id.form_v2_submission_status)
        status.text = when (state) {
            FormV2SubmissionState.Idle -> ""
            FormV2SubmissionState.Submitting -> getString(R.string.form_submitting)
            FormV2SubmissionState.Submitted -> getString(R.string.form_submission_success)
            is FormV2SubmissionState.ValidationError ->
                if (state.questionId in questionErrors) "" else validationMessage(state)
            is FormV2SubmissionState.Error -> state.message
        }
        status.visibility = if (status.text.isEmpty()) View.GONE else View.VISIBLE
        updateSubmissionControls()
        if (state == FormV2SubmissionState.Submitted && !isLeavingForm) {
            isLeavingForm = true
            Toast.makeText(this, R.string.form_submission_success, Toast.LENGTH_LONG).show()
            returnToPatientProfile()
        }
    }

    private fun validationMessage(error: FormV2SubmissionState.ValidationError): String {
        val template = (viewModel.state.value as? FormV2RenderingState.Success)?.template
        val question = template?.questions?.firstOrNull { it.id == error.questionId }
        val label = question?.questionText?.getTranslation(viewModel.language)
        return if (label.isNullOrBlank()) error.message else
            getString(R.string.form_v2_validation_error, label, error.message)
    }

    private fun renderQuestionError(state: FormV2SubmissionState) {
        val error = state as? FormV2SubmissionState.ValidationError
        questionErrors.forEach { (id, message) ->
            val isInvalid = id == error?.questionId
            message.text = if (isInvalid) error?.message else ""
            message.visibility = if (isInvalid) View.VISIBLE else View.GONE
            (questionInputs[id] as? EditText)?.backgroundTintList = if (isInvalid) {
                ColorStateList.valueOf(getColor(R.color.error))
            } else {
                inputTints[id]
            }
        }
        if (error?.questionId != highlightedQuestionId) {
            questionErrors[error?.questionId]?.let { message ->
                message.post {
                    if (message.visibility == View.VISIBLE) {
                        message.requestRectangleOnScreen(Rect(0, 0, message.width, message.height), false)
                    }
                }
            }
        }
        highlightedQuestionId = error?.questionId
    }

    private fun updateSubmissionControls() {
        invalidateOptionsMenu()
        // Remember disabled controls (including unsupported TIME) when unlocking after an error
        val content = findViewById<LinearLayout>(R.id.form_v2_content)
        if (!viewModel.canEditAnswers || isLeavingForm) {
            content.descendants.forEach { input ->
                disabledInputs.putIfAbsent(input, input.isEnabled)
                input.isEnabled = false
            }
        } else {
            disabledInputs.forEach { (input, enabled) -> input.isEnabled = enabled }
            disabledInputs.clear()
        }
    }

    private fun returnToPatientProfile() {
        if (isFinishing) return
        intent.getStringExtra(FormTemplateListV2Activity.EXTRA_PATIENT_ID)?.let { patientId ->
            val intent = PatientProfileActivity.makeIntentForPatientId(this, patientId)
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
        }
        finish()
    }

    private fun render(state: FormV2RenderingState) {
        val content = findViewById<LinearLayout>(R.id.form_v2_content)
        val status = findViewById<TextView>(R.id.form_v2_status)
        content.removeAllViews()
        disabledInputs.clear()
        questionErrors.clear()
        questionInputs.clear()
        inputTints.clear()
        highlightedQuestionId = null

        when (state) {
            FormV2RenderingState.Loading -> status.text = getString(R.string.form_v2_rendering_loading)
            is FormV2RenderingState.Error -> status.text = state.message
            is FormV2RenderingState.Success -> {
                supportActionBar?.title = state.template.classification.name.getTranslation(viewModel.language)
                    ?: getString(R.string.create_new_form)
                status.text = ""
                var questionNumber = 0
                state.template.questions.orEmpty().sortedBy { it.order }.forEach { question ->
                    val label = TextView(this).apply {
                        text = question.questionText.getTranslation(viewModel.language).orEmpty()
                        setPadding(0, 16, 0, 4)
                    }
                    content.addView(label)

                    if (question.questionType == QuestionTypeEnum.CATEGORY) {
                        label.textSize = 20f
                        label.setTextColor(getColor(R.color.colorPrimaryDark))
                        return@forEach
                    }

                    questionNumber++
                    label.text = "$questionNumber. ${label.text}" + if (question.required) " *" else ""
                    label.setTypeface(label.typeface, Typeface.BOLD)
                    label.textSize = 18f

                    if (question.questionType == QuestionTypeEnum.MULTIPLE_CHOICE ||
                        question.questionType == QuestionTypeEnum.MULTIPLE_SELECT
                    ) {
                        content.addView(makeChoiceInput(question))
                    } else if (question.questionType == QuestionTypeEnum.TIME) {
                        content.addView(makeTimeInput(question))
                    } else if (question.questionType == QuestionTypeEnum.DATE ||
                        question.questionType == QuestionTypeEnum.DATETIME
                    ) {
                        content.addView(makeDateInput(question))
                    } else if (question.questionType == QuestionTypeEnum.STRING ||
                        question.questionType == QuestionTypeEnum.INTEGER ||
                        question.questionType == QuestionTypeEnum.DECIMAL
                    ) {
                        content.addView(EditText(this).apply {
                            hint = getString(R.string.form_v2_rendering_string_hint)
                            isSingleLine = question.questionType != QuestionTypeEnum.STRING ||
                                (question.stringMaxLines != null && question.stringMaxLines <= 1)
                            inputType = when (question.questionType) {
                                QuestionTypeEnum.INTEGER -> InputType.TYPE_CLASS_NUMBER or
                                    InputType.TYPE_NUMBER_FLAG_SIGNED
                                QuestionTypeEnum.DECIMAL -> InputType.TYPE_CLASS_NUMBER or
                                    InputType.TYPE_NUMBER_FLAG_DECIMAL or
                                    InputType.TYPE_NUMBER_FLAG_SIGNED
                                else -> InputType.TYPE_CLASS_TEXT
                            }
                            viewModel.answerState.getAnswer(question.id)?.let { answer ->
                                setText(
                                    if (question.questionType == QuestionTypeEnum.STRING) {
                                        answer.textAnswer.orEmpty()
                                    } else if (question.questionType == QuestionTypeEnum.INTEGER) {
                                        answer.numericAnswer?.toLong()?.toString().orEmpty()
                                    } else {
                                        answer.numericAnswer?.toString().orEmpty()
                                    }
                                )
                            }
                            addTextChangedListener { text ->
                                val value = text?.toString().orEmpty()
                                if (value.isEmpty()) {
                                    viewModel.answerState.removeAnswer(question.id)
                                } else if (question.questionType == QuestionTypeEnum.STRING) {
                                    viewModel.answerState.setAnswer(
                                        question.id,
                                        AnswerV2.createTextAnswer(value)
                                    )
                                } else if (question.questionType == QuestionTypeEnum.INTEGER) {
                                    val number = value.toLongOrNull()
                                    if (number == null) {
                                        viewModel.answerState.removeAnswer(question.id)
                                    } else {
                                        viewModel.answerState.setAnswer(
                                            question.id,
                                            AnswerV2.createNumericAnswer(number)
                                        )
                                    }
                                } else {
                                    val number = value.toDoubleOrNull()
                                    if (number == null) {
                                        viewModel.answerState.removeAnswer(question.id)
                                    } else {
                                        viewModel.answerState.setAnswer(
                                            question.id,
                                            AnswerV2.createNumericAnswer(number)
                                        )
                                    }
                                }
                                viewModel.revalidateQuestion(question.id)
                            }
                        })
                    } else {
                        content.addView(TextView(this).apply {
                            text = getString(R.string.form_v2_rendering_unsupported, question.questionType.name)
                            alpha = 0.65f
                        })
                    }
                    question.id?.let { id ->
                        val input = content.getChildAt(content.childCount - 1)
                        questionInputs[id] = input
                        inputTints[id] = input.backgroundTintList
                        val error = TextView(this).apply {
                            setTextColor(getColor(R.color.error))
                            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
                            visibility = View.GONE
                        }
                        questionErrors[id] = error
                        content.addView(error)
                    }
                }
            }
        }
        status.visibility = if (status.text.isEmpty()) View.GONE else View.VISIBLE
        renderSubmissionState(viewModel.submissionState.value)
    }

    private fun makeChoiceInput(question: FormTemplateQuestionV2): View {
        val options = question.mcOptions.orEmpty()
        if (question.questionType == QuestionTypeEnum.MULTIPLE_CHOICE) {
            return RadioGroup(this).apply {
                options.forEach { option ->
                    addView(RadioButton(context).apply {
                        id = View.generateViewId()
                        text = option.translations.getTranslation(viewModel.language).orEmpty()
                    })
                }
                viewModel.answerState.getAnswer(question.id)?.mcIdArrayAnswer
                    ?.firstOrNull()
                    ?.takeIf { it in 0 until childCount }
                    ?.let { check(getChildAt(it).id) }
                setOnCheckedChangeListener { group, checkedId ->
                    val selectedIndex = (0 until group.childCount)
                        .firstOrNull { group.getChildAt(it).id == checkedId }
                    if (selectedIndex == null) {
                        viewModel.answerState.removeAnswer(question.id)
                    } else {
                        viewModel.answerState.setAnswer(
                            question.id,
                            AnswerV2.createMcAnswer(listOf(selectedIndex))
                        )
                    }
                    viewModel.revalidateQuestion(question.id)
                }
            }
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val selectedIndices = viewModel.answerState.getAnswer(question.id)
                ?.mcIdArrayAnswer
                ?.toMutableSet()
                ?: mutableSetOf()
            options.forEachIndexed { index, option ->
                addView(CheckBox(context).apply {
                    text = option.translations.getTranslation(viewModel.language).orEmpty()
                    isChecked = index in selectedIndices
                    setOnCheckedChangeListener { _, isChecked ->
                        if (isChecked) selectedIndices.add(index) else selectedIndices.remove(index)
                        if (selectedIndices.isEmpty()) {
                            viewModel.answerState.removeAnswer(question.id)
                        } else {
                            viewModel.answerState.setAnswer(
                                question.id,
                                AnswerV2.createMcAnswer(selectedIndices.sorted())
                            )
                        }
                        viewModel.revalidateQuestion(question.id)
                    }
                })
            }
        }
    }

    private fun makeDateInput(question: FormTemplateQuestionV2): Button {
        val button = Button(this).apply {
            text = viewModel.answerState.getAnswer(question.id)?.dateAnswer
                ?: getString(R.string.form_v2_rendering_date_hint)
        }
        button.setOnClickListener {
            val now = Calendar.getInstance()
            val datePicker = DatePickerDialog(
                this,
                { _, year, month, day ->
                    val date = String.format(
                        Locale.ROOT,
                        "%04d-%02d-%02d",
                        year,
                        month + 1,
                        day,
                    )
                    if (question.questionType == QuestionTypeEnum.DATE) {
                        button.text = date
                        viewModel.answerState.setAnswer(question.id, AnswerV2.createDateAnswer(date))
                        viewModel.revalidateQuestion(question.id)
                    } else {
                        TimePickerDialog(
                            this,
                            { _, hour, minute ->
                                if (isDateTimeAllowed(question, year, month, day, hour, minute)) {
                                    val value = String.format(
                                        Locale.ROOT,
                                        "%s %02d:%02d",
                                        date,
                                        hour,
                                        minute,
                                    )
                                    button.text = value
                                    button.error = null
                                    viewModel.answerState.setAnswer(
                                        question.id,
                                        AnswerV2.createDateAnswer(value)
                                    )
                                } else {
                                    button.error = getString(R.string.form_v2_rendering_datetime_not_allowed)
                                    viewModel.answerState.removeAnswer(question.id)
                                }
                                viewModel.revalidateQuestion(question.id)
                            },
                            now.get(Calendar.HOUR_OF_DAY),
                            now.get(Calendar.MINUTE),
                            true,
                        ).show()
                    }
                },
                now.get(Calendar.YEAR),
                now.get(Calendar.MONTH),
                now.get(Calendar.DAY_OF_MONTH),
            )
            if (question.allowPastDates == false) {
                datePicker.datePicker.minDate = System.currentTimeMillis()
            }
            if (question.allowFutureDates == false) {
                datePicker.datePicker.maxDate = System.currentTimeMillis()
            }
            datePicker.show()
        }
        return button
    }

    private fun isDateTimeAllowed(
        question: FormTemplateQuestionV2,
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ): Boolean {
        val selected = Calendar.getInstance().apply {
            set(year, month, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val now = Calendar.getInstance()

        return when {
            question.allowPastDates == false && selected.before(now) -> false
            question.allowFutureDates == false && selected.after(now) -> false
            else -> true
        }
    }

    private fun makeTimeInput(question: FormTemplateQuestionV2): Button = Button(this).apply {
        // TODO: Re-enable TIME editing when the V2 submission API supports TIME answers
        // https://github.sfu.ca/cradle-project/Cradle-Platform/issues/1161
        isEnabled = false
        text = viewModel.answerState.getAnswer(question.id)?.dateAnswer
            ?: getString(R.string.form_v2_rendering_time_hint)
        setOnClickListener {
            val now = Calendar.getInstance()
            TimePickerDialog(
                this@FormV2RenderingActivity,
                { _, hour, minute ->
                    val value = String.format(Locale.ROOT, "%02d:%02d", hour, minute)
                    text = value
                    viewModel.answerState.setAnswer(question.id, AnswerV2.createDateAnswer(value))
                },
                now.get(Calendar.HOUR_OF_DAY),
                now.get(Calendar.MINUTE),
                true,
            ).show()
        }
    }

    @Suppress("DEPRECATION")
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    companion object {
        const val EXTRA_LANGUAGE = "form_v2_language"

        fun makeIntent(
            context: Context,
            templateId: String,
            patientId: String? = null,
            language: String? = null
        ): Intent =
            Intent(context, FormV2RenderingActivity::class.java).apply {
                putExtra(FormTemplateDetailV2ViewModel.EXTRA_TEMPLATE_ID, templateId)
                putExtra(FormTemplateListV2Activity.EXTRA_PATIENT_ID, patientId)
                putExtra(EXTRA_LANGUAGE, language)
            }
    }
}
