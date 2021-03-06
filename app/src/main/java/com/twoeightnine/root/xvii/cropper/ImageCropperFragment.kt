package com.twoeightnine.root.xvii.cropper

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.core.view.ViewCompat
import com.twoeightnine.root.xvii.R
import com.twoeightnine.root.xvii.base.BaseFragment
import com.twoeightnine.root.xvii.utils.*
import io.reactivex.Single
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.fragment_cropper.*
import java.io.File

class ImageCropperFragment : BaseFragment() {

    private val path by lazy {
        arguments?.getString(ARG_PATH)
    }

    private var cropperDisposable: Disposable? = null

    override fun getLayoutId(): Int = R.layout.fragment_cropper

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cropImageView.setImageUriAsync(Uri.parse("file://$path"))
        ivDone.setOnClickListener {
            cropAndExit()
        }
        ivRotateCounter.setOnClickListener {
            cropImageView.rotateImage(-90)
        }
        ivRotateClock.setOnClickListener {
            cropImageView.rotateImage(90)
        }

        ivDone.stylize(ColorManager.MAIN_TAG)
        ViewCompat.setOnApplyWindowInsetsListener(llBottom) { v, insets ->
            val padding = context?.resources?.getDimensionPixelSize(R.dimen.attach_fab_done_margin) ?: 0
            v.setPadding(0, padding, 0, padding + insets.systemWindowInsetBottom)
            insets
        }
    }

    private fun cropAndExit() {
        rvLoader.show()
        llBottom.hide()
        cropperDisposable?.dispose()
        cropperDisposable = Single.fromCallable {
            val croppedImage = cropImageView.croppedImage
            val croppedFileName = File(requireContext().cacheDir, "cropped_${time()}.png")
            saveBmp(croppedFileName.absolutePath, croppedImage)
            croppedFileName.absolutePath
        }
                .compose(applySingleSchedulers())
                .onErrorReturnItem("")
                .subscribe(::onImageCropped)
    }

    private fun onImageCropped(croppedPath: String) {
        val intent = Intent().apply {
            putExtra(RESULT_ORIG_PATH, path)
            putExtra(RESULT_CROPPED_PATH, croppedPath)
        }
        activity?.setResult(Activity.RESULT_OK, intent)
        activity?.finish()
    }

    override fun onDestroy() {
        cropperDisposable?.dispose()
        super.onDestroy()
    }

    companion object {

        const val RESULT_ORIG_PATH = "orig_path"
        const val RESULT_CROPPED_PATH = "cropped_path"

        private const val ARG_PATH = "path"

        fun createArgs(path: String) = Bundle().apply {
            putString(ARG_PATH, path)
        }
    }
}