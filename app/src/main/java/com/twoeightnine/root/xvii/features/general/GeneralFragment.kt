package com.twoeightnine.root.xvii.features.general

import android.os.Bundle
import android.view.View
import android.widget.CompoundButton
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.twoeightnine.root.xvii.R
import com.twoeightnine.root.xvii.base.BaseFragment
import com.twoeightnine.root.xvii.managers.Prefs
import com.twoeightnine.root.xvii.utils.*
import kotlinx.android.synthetic.main.fragment_general.*

/**
 * Created by root on 2/2/17.
 */

class GeneralFragment : BaseFragment() {

    private lateinit var viewModel: GeneralViewModel

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initSwitches()
        btnClearCache.setOnClickListener {
            viewModel.clearCache()
        }
        btnRefreshStickers.setOnClickListener {
            viewModel.refreshStickers()
        }
        llContainer.stylizeAll()
        svContent.setBottomInsetPadding()
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        updateTitle(getString(R.string.general))
        viewModel = ViewModelProviders.of(this)[GeneralViewModel::class.java]
        viewModel.calculateCacheSize()

        viewModel.cacheSize.observe(viewLifecycleOwner, Observer { size ->
            context?.resources?.also {
                tvCacheSize.text = getString(R.string.cache_size, getSize(it, size.toInt()))
            }
        })
        viewModel.stickersRefreshing.observe(viewLifecycleOwner, Observer { loading ->
            btnRefreshStickers.setVisible(!loading)
            if (!loading) {
                showToast(context, R.string.stickers_refreshed)
            }
        })
    }

    private fun initSwitches() {
        switchOffline.isChecked = Prefs.beOffline
        switchOnline.isChecked = Prefs.beOnline
        switchHideStatus.isChecked = Prefs.hideStatus
        switchRead.isChecked = Prefs.markAsRead
        switchTyping.isChecked = Prefs.showTyping
        switchSendByEnter.isChecked = Prefs.sendByEnter
        switchStickerSuggestions.isChecked = Prefs.stickerSuggestions
        switchSwipeToBack.isChecked = Prefs.enableSwipeToBack
        switchStoreKeys.isChecked = Prefs.storeCustomKeys
        switchLiftKeyboard.isChecked = Prefs.liftKeyboard

        switchOffline.onCheckedListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
            if (isChecked) switchOnline.isChecked = false
        }
        switchOnline.onCheckedListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
            if (isChecked) switchOffline.isChecked = false
        }
        switchHideStatus.onCheckedListener = CompoundButton.OnCheckedChangeListener { _, isChecked ->
            viewModel.setHideMyStatus(isChecked)
        }
    }

    private fun saveSwitches() {
        Prefs.beOffline = switchOffline.isChecked
        Prefs.beOnline = switchOnline.isChecked
        Prefs.hideStatus = switchHideStatus.isChecked
        Prefs.markAsRead = switchRead.isChecked
        Prefs.showTyping = switchTyping.isChecked
        Prefs.sendByEnter = switchSendByEnter.isChecked
        Prefs.stickerSuggestions = switchStickerSuggestions.isChecked
        Prefs.enableSwipeToBack = switchSwipeToBack.isChecked
        Prefs.storeCustomKeys = switchStoreKeys.isChecked
        Prefs.liftKeyboard = switchLiftKeyboard.isChecked
    }

    override fun onStop() {
        super.onStop()
        saveSwitches()
    }

    override fun getLayoutId() = R.layout.fragment_general

    companion object {

        fun newInstance() = GeneralFragment()
    }
}
