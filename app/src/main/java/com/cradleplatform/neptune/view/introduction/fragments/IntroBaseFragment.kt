package com.cradleplatform.neptune.view.introduction.fragments

import android.content.Context
import android.util.Log
import androidx.fragment.app.Fragment
import com.cradleplatform.neptune.ext.hideKeyboard
import com.cradleplatform.neptune.view.introduction.custom.MyIntroFragmentInteractionListener

/**
 * Base class for other ReadingFragments
 * - Observer
 * - Shared access to Reading object
 *
 *
 * Activities that contain fragments derived from this class  must implement the
 * [MyIntroFragmentInteractionListener] interface to handle interaction events.
 * Use the newInstance() factory method to create an instance of derived fragment.
 */
abstract class IntroBaseFragment : Fragment() {
    @JvmField
    @Suppress("VariableNaming")
    protected var TAG = IntroBaseFragment::class.java.name
    @JvmField
    protected var activityCallbackListener: MyIntroFragmentInteractionListener? = null

    /*
        Keyboard support
     */
    fun hideKeyboard() {
        this.requireActivity().currentFocus?.hideKeyboard()
    }

    /*
        Observer
     */
    override fun onAttach(context: Context) {
        Log.d(TAG, "TRACE -- onAttach(): hash" + toString())
        super.onAttach(context)

        // ensure callback functions are implemented.
        if (context !is MyIntroFragmentInteractionListener) {
            throw RuntimeException(
                context.toString() +
                    " must implement MyFragmentIntroInteractionListener"
            )
        }
        activityCallbackListener = context
    }

    override fun onDetach() {
        Log.d(TAG, "TRACE -- onDetatch(): hash" + toString())
        super.onDetach()
        activityCallbackListener = null
    }

    /**
     * Called by ReadingActivity when fragment is just being displayed (navigated to)
     */
    abstract fun onMyBeingDisplayed()

    /**
     * Called by ReadingActivity after fragment has been navigated away from (hidden)
     */
    abstract fun onMyBeingHidden(): Boolean
}
