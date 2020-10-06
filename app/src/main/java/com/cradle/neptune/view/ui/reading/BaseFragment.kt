package com.cradle.neptune.view.ui.reading

import android.content.Context
import android.util.Log
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.cradle.neptune.manager.ReadingManager
import com.cradle.neptune.viewmodel.PatientReadingViewModel

/**
 * Base class for other ReadingFragments
 * - Observer
 * - Shared access to Reading object
 *
 *
 * Activities that contain fragments derived from this class  must implement the
 * [MyFragmentInteractionListener] interface to handle interaction events.
 * Use the newInstance() factory method to create an instance of derived fragment.
 */
abstract class BaseFragment : Fragment() {
    protected lateinit var activityCallbackListener: MyFragmentInteractionListener
    protected val viewModel: PatientReadingViewModel by activityViewModels()
    protected lateinit var readingManager: ReadingManager

    /*
        Keyboard support
     */
    fun hideKeyboard() {
        // SOURCE: https://stackoverflow.com/questions/1109022/close-hide-the-android-soft-keyboard
        if (this.activity == null) {
            return
        }
        context
        val view = this.requireActivity().currentFocus
        if (view != null) {
            val imm =
                requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    /*
        Observer
     */
    override fun onAttach(context: Context) {
        Log.d(TAG, "TRACE -- onAttach(): hash" + toString())
        super.onAttach(context)

        // ensure callback functions are implemented.
        if (context !is MyFragmentInteractionListener) {
            throw RuntimeException(
                context.toString() +
                    " must implement MyFragmentInteractionListener"
            )
        }
        activityCallbackListener = context
        readingManager = viewModel.readingManager
    }

    override fun onDetach() {
        Log.d(TAG, "TRACE -- onDetatch(): hash" + toString())
        super.onDetach()
    }

    /**
     * Called by ReadingActivity when fragment is just being displayed (navigated to)
     */
    abstract fun onMyBeingDisplayed()

    /**
     * Called by ReadingActivity after fragment has been navigated away from (hidden)
     */
    abstract fun onMyBeingHidden(): Boolean

    @JvmField
    @Suppress("VariableNaming")
    protected var TAG = BaseFragment::class.java.name
}
