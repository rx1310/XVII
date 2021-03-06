package com.twoeightnine.root.xvii.background.music.services

import android.annotation.TargetApi
import android.app.PendingIntent
import android.app.Service
import android.content.*
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.*
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.LayoutRes
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.twoeightnine.root.xvii.R
import com.twoeightnine.root.xvii.background.music.models.Track
import com.twoeightnine.root.xvii.lg.L
import com.twoeightnine.root.xvii.model.attachments.Audio
import com.twoeightnine.root.xvii.utils.NotificationChannels
import io.reactivex.subjects.PublishSubject
import kotlin.random.Random


class MusicService : Service(), MediaPlayer.OnPreparedListener,
        MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener, AudioManager.OnAudioFocusChangeListener {

    private val binder by lazy { MusicBinder() }
    private val player by lazy { MediaPlayer() }
    private val tracks = arrayListOf<Track>()

    private val noisyReceiver = NoisyReceiver()
    private var receiverRegistered = false

    private val audioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    private val handler = Handler()
    private val focusLock = Any()
    private var isPlaybackDelayed = false
    private var focusRequest: AudioFocusRequest? = null

    private var playedPosition: Int = 0

    private var playbackSpeed = 1f

    override fun onCreate() {
        super.onCreate()
        player.apply {
            setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
            setAudioStreamType(AudioManager.STREAM_MUSIC)
            setOnPreparedListener(this@MusicService)
            setOnCompletionListener(this@MusicService)
            setOnErrorListener(this@MusicService)
        }
    }

    private fun updateAudios(tracks: ArrayList<Track>, position: Int = 0) {
        val nowPlayed = getPlayedTrack()
        this.tracks.clear()
        this.tracks.addAll(tracks)
        playedPosition = position
        if (nowPlayed == getPlayedTrack()) {
            playOrPause()
        } else {
            requestFocus()
        }
    }

    override fun onDestroy() {
        try {
            pausingAudioSubject.onNext(Unit)
            player.release()
            unregisterNoisyReceiver()
        } catch (e: Exception) {
            lw("destroying", e)
        }
        super.onDestroy()
    }

    private fun startPlaying() {
        val playedTrack = getPlayedTrack()
        val path = when {
            playedTrack == null -> null
            playedTrack.isCached() -> playedTrack.cachePath
            else -> playedTrack.audio.url
        } ?: return

        l("playing ${playedTrack?.audio?.fullId} when cached is ${playedTrack?.isCached() == true}")
        try {
            player.reset()
            player.setDataSource(path)
            updateSpeed(showNotification = false)
            player.prepareAsync()
            registerNoisyReceiver()
        } catch (e: Exception) {
            lw("preparing error", e)
        }
    }

    private fun requestFocus() {
        if (Build.VERSION.SDK_INT >= 26) {
            val focusRequest = createFocusRequest()
            this.focusRequest = focusRequest

            val result = audioManager.requestAudioFocus(focusRequest)
            synchronized(focusLock) {
                when (result) {
                    AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
                        startPlaying()
                    }
                    AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
                        isPlaybackDelayed = true
                    }
                }
            }
        } else {
            val result: Int = audioManager.requestAudioFocus(
                    this,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
            )
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                startPlaying()
            }
        }
    }

    private fun toggleSpeed() {
        playbackSpeed = when (playbackSpeed) {
            1f -> 1.25f
            1.25f -> 1.5f
            1.5f -> 2f
            2f -> 0.5f
            else -> 1f
        }
        updateSpeed()
    }

    private fun updateSpeed(showNotification: Boolean = true) {
        if (Build.VERSION.SDK_INT >= 23) {
            try {
                player.playbackParams = player.playbackParams.setSpeed(playbackSpeed)
            } catch (e: Exception) {
                lw("unable to update speed", e)
            }
        }
        if (showNotification) {
            showNotification()
        }
    }

    private fun playNext() {
        onCompletion(player)
    }

    private fun playPrevious() {
        playedPosition -= 2
        onCompletion(player)
    }

    private fun playOrPause() {
        if (player.isPlayingSafe()) {
            pause()
        } else {
            try {
                player.start()
                playingAudioSubject.onNext(getPlayedTrack() ?: return)
            } catch (e: Exception) {
                lw("playing error", e)
                startPlaying()
            }
        }
        showNotification()
    }

    private fun pause() {
        if (!player.isPlayingSafe()) return
        try {
            player.pause()
            pausingAudioSubject.onNext(Unit)
        } catch (e: Exception) {
            lw("error pausing", e)
            stop()
        }
    }

    private fun stop() {
        try {
            player.stop()
            tracks.clear()
            pausingAudioSubject.onNext(Unit)
            if (Build.VERSION.SDK_INT >= 26) {
                focusRequest?.let(audioManager::abandonAudioFocusRequest)
            } else {
                audioManager.abandonAudioFocus(this)
            }
            unregisterNoisyReceiver()
        } catch (e: Exception) {
            lw("error stopping", e)
            stopForeground(true)
        }
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                l("audiofocus gain")
                if (isPlaybackDelayed) {
                    synchronized(focusLock) {
                        isPlaybackDelayed = false
                    }
                    startPlaying()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                l("audiofocus loss")
                synchronized(focusLock) {
                    isPlaybackDelayed = false
                }
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                l("audiofocus transient gain")
                synchronized(focusLock) {
                    isPlaybackDelayed = false
                }
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                l("audiofocus transient loss can duck")
                pause()
            }
        }
    }

    override fun onPrepared(mp: MediaPlayer?) {
        player.start()
        showNotification()
        playingAudioSubject.onNext(getPlayedTrack() ?: return)
    }

    override fun onCompletion(mp: MediaPlayer?) {
        playedPosition++
        if (playedPosition !in tracks.indices) {
            playedPosition = 0
            stop()
            stopForeground(true)
        } else {
            startPlaying()
        }
    }

    override fun onError(mp: MediaPlayer?, what: Int, extra: Int): Boolean {
        lw("onError what=$what extra=$extra")
        return false // call OnCompletion
    }

    private fun getPlayedTrack() = tracks.getOrNull(playedPosition)

    private fun showNotification() {
        val audio = getPlayedTrack()?.audio ?: return

        val notification = NotificationCompat.Builder(applicationContext, NotificationChannels.musicPlayer.id)
                .setCustomContentView(bindRemoteViews(R.layout.view_music_notification, audio))
                .setCustomBigContentView(bindRemoteViews(R.layout.view_music_notification_extended, audio, true))
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setSmallIcon(R.drawable.ic_play_music)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setColor(ContextCompat.getColor(applicationContext, R.color.background))
                .build()

        startForeground(FOREGROUND_ID, notification)
    }

    private fun bindRemoteViews(@LayoutRes viewId: Int, audio: Audio, isExtended: Boolean = false): RemoteViews {
        val remoteViews = RemoteViews(packageName, viewId)
        with(remoteViews) {
//            setInt(R.id.rlTrack, "setBackgroundColor", ContextCompat.getColor(applicationContext, R.color.background))
            setTextViewText(R.id.tvTitle, audio.title)
            setTextViewText(R.id.tvArtist, audio.artist)
            val playPauseRes = if (player.isPlayingSafe()) R.drawable.ic_pause_music else R.drawable.ic_play_music
            setImageViewResource(R.id.ivPlayPause, playPauseRes)
            setOnClickPendingIntent(R.id.ivNext, getActionPendingIntent(MusicBroadcastReceiver.ACTION_NEXT))
            setOnClickPendingIntent(R.id.ivPrevious, getActionPendingIntent(MusicBroadcastReceiver.ACTION_PREVIOUS))
            setOnClickPendingIntent(R.id.ivPlayPause, getActionPendingIntent(MusicBroadcastReceiver.ACTION_PLAY_PAUSE))

            if (isExtended) {
//                setInt(R.id.rlTrackExtended, "setBackgroundColor", ContextCompat.getColor(applicationContext, R.color.background))
                setOnClickPendingIntent(R.id.tvClose, getActionPendingIntent(MusicBroadcastReceiver.ACTION_CLOSE))
                setOnClickPendingIntent(R.id.tvPlaybackSpeed, getActionPendingIntent(MusicBroadcastReceiver.ACTION_SPEED))

                setTextViewText(R.id.tvPlaybackSpeed, getString(R.string.playback_speed, playbackSpeed.toString()))
                setViewVisibility(R.id.tvPlaybackSpeed, if (Build.VERSION.SDK_INT >= 23) {
                    View.VISIBLE
                } else {
                    View.GONE
                })
            }
        }
        return remoteViews
    }

    private fun getActionPendingIntent(action: String) = PendingIntent.getBroadcast(
            applicationContext, Random.nextInt(),
            Intent(applicationContext, MusicBroadcastReceiver::class.java).apply {
                this.action = action
            },
            PendingIntent.FLAG_UPDATE_CURRENT
    )

    @TargetApi(26)
    private fun createFocusRequest() =
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .run {
                        setAudioAttributes(AudioAttributes.Builder().run {
                            setUsage(AudioAttributes.USAGE_MEDIA)
                            setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            build()
                        })
                        setAcceptsDelayedFocusGain(true)
                        setWillPauseWhenDucked(true)
                        setOnAudioFocusChangeListener(this@MusicService, handler)
                        build()
                    }

    override fun onBind(intent: Intent?) = binder

    override fun onUnbind(intent: Intent?): Boolean {
        player.release()
        return false
    }

    private fun registerNoisyReceiver() = synchronized(this) {
        if (!receiverRegistered) {
            registerReceiver(noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
            receiverRegistered = true
        }
    }

    private fun unregisterNoisyReceiver() = synchronized(this) {
        if (receiverRegistered) {
            unregisterReceiver(noisyReceiver)
            receiverRegistered = false
        }
    }

    private fun MediaPlayer.isPlayingSafe() = try {
        isPlaying
    } catch (e: Exception) {
        lw("isPlaying", e)
        false
    }

    private fun l(s: String) {
        L.tag(TAG).log(s)
    }

    private fun lw(s: String, throwable: Throwable? = null) {
        L.tag(TAG).throwable(throwable).log(s)
    }

    /**
     * also provides public API
     */
    companion object {

        private const val TAG = "music"
        private const val FOREGROUND_ID = 3676

        private var service: MusicService? = null

        private val playingAudioSubject = PublishSubject.create<Track>()
        private val pausingAudioSubject = PublishSubject.create<Unit>()

        var isBound = false
            private set

        fun launch(
                applicationContext: Context?,
                tracks: ArrayList<Track>,
                position: Int
        ) {
            applicationContext ?: return

            val serviceConnection = object : ServiceConnection {

                override fun onServiceConnected(name: ComponentName?, iBinder: IBinder?) {
                    val binder = iBinder as MusicBinder
                    binder.getService().also {
                        service = it
                        it.updateAudios(tracks, position)
                    }
                    isBound = true
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    isBound = false
                }
            }
            val intent = Intent(applicationContext, MusicService::class.java)
            applicationContext.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            applicationContext.startService(intent)
        }

        fun exit() {
            service?.stop()
            service?.stopForeground(true)
        }

        fun next() {
            service?.playNext()
        }

        fun previous() {
            service?.playPrevious()
        }

        fun playPause() {
            service?.playOrPause()
        }

        fun toggleSpeed() {
            service?.toggleSpeed()
        }

        fun subscribeOnAudioPlaying(consumer: (Track) -> Unit) = playingAudioSubject.subscribe(consumer)

        fun subscribeOnAudioPausing(consumer: (Unit) -> Unit) = pausingAudioSubject.subscribe(consumer)

        fun getPlayedTrack() = service?.getPlayedTrack()

        fun isPlaying() = try {
            service?.player?.isPlaying ?: false
        } catch (e: Exception) {
            false
        }
    }

    inner class MusicBinder : Binder() {

        fun getService() = this@MusicService
    }

    private inner class NoisyReceiver : BroadcastReceiver() {

        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                l("becoming noisy")
                pause()
            }
        }
    }
}