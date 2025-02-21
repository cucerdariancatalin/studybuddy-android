package com.edricchan.studybuddy.ui.widget.bottomsheet

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.edricchan.studybuddy.ui.widget.bottomsheet.dsl.ModalBottomSheetDSL

/** Instantiates a [modal bottom sheet][ModalBottomSheetFragment] given the specified options. */
inline fun modalBottomSheet(init: ModalBottomSheetFragment.() -> Unit) =
    ModalBottomSheetFragment().apply(init)

/**
 * Instantiates a [modal bottom sheet][ModalBottomSheetFragment] given the specified options.
 * @param headerTitle The header title to use
 * @param items Items to be shown in the bottom sheet.
 */
fun modalBottomSheet(
    headerTitle: String,
    items: ModalBottomSheetDSL.() -> Unit
) = modalBottomSheet {
    this.headerTitle = headerTitle
    this.setItems(items)
}

/**
 * Shows a [modal bottom sheet][ModalBottomSheetFragment] given the specified
 * options.
 */
inline fun Fragment.showModalBottomSheet(
    init: ModalBottomSheetFragment.() -> Unit
) {
    val frag = modalBottomSheet(init)
    frag.show(parentFragmentManager, frag.tag)
}

/**
 * Shows a [modal bottom sheet][ModalBottomSheetFragment] given the specified options.
 * @param headerTitle The header title to use.
 * @param items Items to be shown in the bottom sheet.
 */
fun Fragment.showModalBottomSheet(
    headerTitle: String,
    items: ModalBottomSheetDSL.() -> Unit
) {
    showModalBottomSheet {
        this.headerTitle = headerTitle
        this.setItems(items)
    }
}

/**
 * Shows a [modal bottom sheet][ModalBottomSheetFragment] given the specified
 * options.
 */
inline fun FragmentActivity.showModalBottomSheet(
    init: ModalBottomSheetFragment.() -> Unit
) {
    val frag = modalBottomSheet(init)
    frag.show(supportFragmentManager, frag.tag)
}

/**
 * Shows a [modal bottom sheet][ModalBottomSheetFragment] given the specified options.
 * @param headerTitle The header title to use.
 * @param items Items to be shown in the bottom sheet.
 */
fun FragmentActivity.showModalBottomSheet(
    headerTitle: String,
    items: ModalBottomSheetDSL.() -> Unit
) {
    showModalBottomSheet {
        this.headerTitle = headerTitle
        this.setItems(items)
    }
}
