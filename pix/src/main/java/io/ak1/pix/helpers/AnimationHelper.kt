package io.ak1.pix.helpers

import android.view.animation.AccelerateDecelerateInterpolator
import io.ak1.pix.databinding.FragmentCameraBinding

/**
 * Created By Akshay Sharma on 17,June,2021
 * https://ak1.io
 */
fun FragmentCameraBinding.videoRecordingStartAnim() {
    val adInterpolator = AccelerateDecelerateInterpolator()
    gridLayout.controlsLayout.primaryClickButton.animate().apply {
        scaleX(1.2f)
        scaleY(1.2f)
        duration = 300
        interpolator = adInterpolator
    }.start()

    listOf(
        cameraTopBarLayout.flashButton,
        cameraTopBarLayout.closeImage,
        gridLayout.controlsLayout.changeCameraButton,
        gridLayout.controlsLayout.galleryImage,
        gridLayout.textAdditionalDescription
    ).forEach {
        it.animate().apply {
            alpha(0f)
            duration = 300
            interpolator = adInterpolator
        }.start()
    }
}

fun FragmentCameraBinding.videoRecordingEndAnim() {
    val adInterpolator = AccelerateDecelerateInterpolator()
    gridLayout.controlsLayout.primaryClickButton.animate().apply {
        scaleX(1f)
        scaleY(1f)
        duration = 300
        interpolator = adInterpolator
    }.start()

    listOf(
        cameraTopBarLayout.flashButton,
        cameraTopBarLayout.closeImage,
        gridLayout.controlsLayout.changeCameraButton,
        gridLayout.controlsLayout.galleryImage,
        gridLayout.textAdditionalDescription
    ).forEach {
        it.animate().apply {
            alpha(1f)
            duration = 300
            interpolator = adInterpolator
        }.start()
    }
}
