package com.eddyizm.tempus.ui.player

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.media3.common.Player
import com.eddyizm.tempus.R

/** The play-order choices of the player's mode button, cycled in this order like NetEase's. */
enum class PlayMode(
    val shuffle: Boolean,
    val repeat: Int,
    @DrawableRes val icon: Int,
    @StringRes val label: Int,
) {
    REPEAT_ALL(false, Player.REPEAT_MODE_ALL, R.drawable.ic_repeat, R.string.player_mode_repeat_all),
    SHUFFLE(true, Player.REPEAT_MODE_ALL, R.drawable.ic_shuffle, R.string.player_mode_shuffle),
    REPEAT_ONE(false, Player.REPEAT_MODE_ONE, R.drawable.ic_repeat_one, R.string.player_mode_repeat_one),
    SEQUENTIAL(false, Player.REPEAT_MODE_OFF, R.drawable.ic_play_order, R.string.player_mode_sequential);

    fun next(): PlayMode = entries[(ordinal + 1) % entries.size]

    companion object {
        /** Maps the player's two settings onto one mode; repeat one wins over shuffle. */
        @JvmStatic
        fun of(shuffle: Boolean, repeat: Int): PlayMode = when {
            repeat == Player.REPEAT_MODE_ONE -> REPEAT_ONE
            shuffle -> SHUFFLE
            repeat == Player.REPEAT_MODE_ALL -> REPEAT_ALL
            else -> SEQUENTIAL
        }
    }
}
