package com.twoeightnine.root.xvii.chats.messages.starred

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import com.twoeightnine.root.xvii.App
import com.twoeightnine.root.xvii.R
import com.twoeightnine.root.xvii.chats.messages.base.BaseMessagesFragment
import com.twoeightnine.root.xvii.chats.messages.base.MessagesAdapter
import com.twoeightnine.root.xvii.dialogs.fragments.DialogsForwardFragment
import com.twoeightnine.root.xvii.model.Doc
import com.twoeightnine.root.xvii.model.Message2
import com.twoeightnine.root.xvii.model.Photo
import com.twoeightnine.root.xvii.model.Video
import com.twoeightnine.root.xvii.profile.fragments.ProfileFragment
import com.twoeightnine.root.xvii.utils.copyToClip
import com.twoeightnine.root.xvii.utils.getContextPopup
import com.twoeightnine.root.xvii.utils.hide
import kotlinx.android.synthetic.main.fragment_chat_new.*

class StarredMessagesFragment : BaseMessagesFragment<StarredMessagesViewModel>() {

    override fun getViewModelClass() = StarredMessagesViewModel::class.java

    override fun inject() {
        App.appComponent?.inject(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        llInput.hide()
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        updateTitle(getString(R.string.important))
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        super.onCreateOptionsMenu(menu, inflater)
        menu?.clear()
    }


    override fun getAdapterSettings() = MessagesAdapter.Settings(
            isImportant = true
    )

    override fun getAdapterCallback() = object : MessagesAdapter.Callback {

        override fun onClicked(message: Message2) {

            getContextPopup(context ?: return, R.layout.popup_important) {
                when (it.id) {
                    R.id.llCopy -> copyToClip(message.text)
                    R.id.llUnmark -> viewModel.unmarkMessage(message)
                    R.id.llForward -> rootActivity?.loadFragment(DialogsForwardFragment.newInstance("${message.id}"))
                }
            }.show()
        }

        override fun onUserClicked(userId: Int) {
            rootActivity?.loadFragment(ProfileFragment.newInstance(userId))
        }

        override fun onDocClicked(doc: Doc) {
        }

        override fun onPhotoClicked(photo: Photo) {
        }

        override fun onVideoClicked(video: Video) {
        }
    }

    companion object {
        fun newInstance() = StarredMessagesFragment()
    }
}