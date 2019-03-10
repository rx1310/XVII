package com.twoeightnine.root.xvii.chats.fragments

import android.content.Intent
import android.provider.MediaStore
import android.provider.MediaStore.MediaColumns
import com.google.android.material.floatingactionbutton.FloatingActionButton
import android.view.View
import android.widget.GridView
import android.widget.ImageView
import butterknife.BindView
import butterknife.ButterKnife
import com.twoeightnine.root.xvii.R
import com.twoeightnine.root.xvii.adapters.SimpleAdapter
import com.twoeightnine.root.xvii.chats.Titleable
import com.twoeightnine.root.xvii.chats.adapters.GalleryAdapter
import com.twoeightnine.root.xvii.fragments.BaseFragment
import com.twoeightnine.root.xvii.managers.Lg
import com.twoeightnine.root.xvii.managers.Style
import com.twoeightnine.root.xvii.utils.ImageUtils
import com.twoeightnine.root.xvii.utils.PermissionHelper
import com.twoeightnine.root.xvii.utils.showError
import java.io.File

class GalleryFragment: BaseFragment(), Titleable, SimpleAdapter.OnMultiSelected {

    companion object {
        fun newInstance(listener: ((MutableList<String>) -> Unit)?, fromSettings: Boolean = false): GalleryFragment {
            val frag = GalleryFragment()
            frag.listener = listener
            frag.fromSettings = fromSettings
            return frag
        }
    }

    override fun getTitle() = getString(R.string.gallery)

    @BindView(R.id.gvGallery)
    lateinit var gvGallery: GridView
    @BindView(R.id.fabDone)
    lateinit var fabDone: FloatingActionButton
    @BindView(R.id.ivRefresh)
    lateinit var ivRefresh: ImageView

    private lateinit var adapter: GalleryAdapter
    private lateinit var imut: ImageUtils
    private lateinit var permissionHelper: PermissionHelper
    private var viewsBind = false

    var fromSettings = false
    var listener: ((MutableList<String>) -> Unit)? = null

    override fun bindViews(view: View) {
        super.bindViews(view)
        ButterKnife.bind(this, view)
        imut = ImageUtils(safeActivity)
        permissionHelper = PermissionHelper(this)
        if (fromSettings) {
            initAdapter()
        }
        Style.forFAB(fabDone)
        Style.forImageView(ivRefresh, Style.MAIN_TAG)
        viewsBind = true
        ivRefresh.setOnClickListener { checkPermissions() }
        if (!permissionHelper.hasStoragePermissions()) {
            ivRefresh.visibility = View.VISIBLE
        }
    }

    fun initAdapter() {
        adapter = GalleryAdapter({}, {})
        gvGallery.adapter = adapter
        if (!fromSettings) {
            adapter.add(GalleryAdapter.CAMERA_MARKER)
        }
        try {
            adapter.add(getAllShownImagesPath())
        } catch (e: Exception) {
            Lg.wtf("error in gallery: ${e.message}")
        }
        fabDone.setOnClickListener {
            listener?.invoke(adapter.multiSelectRaw)
            adapter.clearMultiSelect()
        }
        fabDone.hide()
        adapter.multiListener = this
        gvGallery.setOnItemClickListener {
            _, _, pos, _ ->
            val path = adapter.items[pos]
            if (path != GalleryAdapter.CAMERA_MARKER) {
                adapter.multiSelect(path)
                adapter.notifyDataSetChanged()
            } else {
                imut.dispatchTakePictureIntent(this)
            }
        }
    }

    private fun checkPermissions() {
        permissionHelper.doOrRequest(
                arrayOf(PermissionHelper.READ_STORAGE, PermissionHelper.WRITE_STORAGE),
                R.string.no_access_to_storage,
                R.string.need_access_to_storage) {
            ivRefresh.visibility = View.GONE
            initAdapter()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val path = imut.getPath(requestCode, data)
        if (path != null) {
            listener?.invoke(mutableListOf(path))
            adapter.clearMultiSelect()
        } else {
            Lg.wtf("camera: path is null but request code is $requestCode and data = $data")
        }
    }

    override fun setUserVisibleHint(isVisibleToUser: Boolean) {
        super.setUserVisibleHint(isVisibleToUser)
        if (isVisibleToUser && viewsBind) {
            initAdapter()
        }
    }

    override fun onNonEmpty() {
        fabDone.show()
    }

    override fun onEmpty() {
        fabDone.hide()
    }

    private fun getAllShownImagesPath(): MutableList<String> {
        val listOfAllImages: MutableList<String> = mutableListOf()
        val uri = android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaColumns.DATA, MediaStore.Images.Media.BUCKET_DISPLAY_NAME, MediaColumns.DATE_MODIFIED)
        val cursor = safeActivity.contentResolver.query(uri, projection, null, null, "${MediaColumns.DATE_MODIFIED} DESC")
        val columnIndexData = cursor.getColumnIndexOrThrow(MediaColumns.DATA)

        var corrupted = 0
        while (cursor.moveToNext()) {
            val absolutePathOfImage = cursor.getString(columnIndexData)
            if (absolutePathOfImage != null) {
                listOfAllImages.add(absolutePathOfImage)
            } else {
                corrupted++
            }
        }
        if (corrupted > 1) {
            Lg.wtf("gallery open: string is null or file does not exist for $corrupted corrupted images")
        }
        cursor.close()
        return listOfAllImages
    }

    override fun getLayout() = R.layout.fragment_gallery
}