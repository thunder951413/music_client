# Tempus Usage Guide

[<- back home](README.md)

## Table of Contents
- [Tempus hub](#tempus-hub)
- [Prerequisites](#prerequisites)
    - [Verified backends](#verified-backends)
- [Getting Started](#getting-started)
    - [Installation](#installation)
    - [First Launch](#first-launch)
- [Server Configuration](#server-configuration)
    - [Initial Setup](#initial-setup)
    - [Home and away addresses](#home-and-away-addresses)
    - [Advanced Settings](#advanced-settings)
- [Main Features](#main-features)
    - [Library View](#library-view)
    - [Folder or index playback](#folder-or-index-playback)
    - [Now Playing Screen](#now-playing-screen)
    - [Podcasts](#podcasts)
    - [Radio Stations](#radio-stations)
    - [Downloads](#downloads)
    - [Continue listening](#continue-listening)
- [Navigation](#navigation)
    - [Bottom Navigation Bar](#bottom-navigation-bar)
- [Playing on another device](#playing-on-another-device)
- [Controlling another phone](#controlling-another-phone)
- [Favorites](#favorites)
    - [Favorites (aka heart aka star) to albums and artists](#favorites-aka-heart-aka-star-to-albums-and-artists)
- [Playlist Management](#playlist-management)
    - [Editing a playlist](#editing-a-playlist)
    - [Sorting playlists](#sorting-playlists)
- [Settings](#settings)
    - [Finding a setting](#finding-a-setting)
    - [Language](#language)
    - [Appearance](#appearance)
    - [Sound](#sound)
    - [Buffering and cache](#buffering-and-cache)
    - [Scrobbling](#scrobbling)
- [Android Auto](#android-auto)
    - [Enabling on your head unit](#enabling-on-your-head-unit)
    - [Tabs](#tabs)
    - [Bundles](#bundles)
    - [Thumbnails or lists](#thumbnails-or-lists)
    - [Artists and Instant Mix](#artists-and-instant-mix)
    - [A-Z and search](#a-z-and-search)
    - [Shortcuts](#shortcuts)
- [Known Issues](#known-issues)
    - [Airsonic Distorted Playback](#airsonic-distorted-playback)
    - [Support](#support)

## Prerequisites

**Music library requirements**: Tempus is a Subsonic-compatible client. Your personal music library requires the following; bundled public radio and podcast sources are documented below:

- An active Subsonic API server (or compatible service) already set up
- Valid login credentials for your Subsonic server
- Music content uploaded and organized on your server

Tempus offers advanced features such as Instant Mix, Continuous Play, Artists Page, and many others, which require a properly configured server capable to respond to "similar songs" requests and correctly tagged music files.

### Verified backends
This app works with any service that implements the Subsonic API, including:
- [LMS - Lightweight Music Server](https://github.com/epoupon/lms) -  *personal fave and my backend*
- [Navidrome](https://www.navidrome.org/)
- [Gonic](https://github.com/sentriz/gonic)
- [Ampache](https://github.com/ampache/ampache)
- [NextCloud Music](https://apps.nextcloud.com/apps/music)
- [Airsonic Advanced](https://github.com/kagemomiji/airsonic-advanced)

[Back to top](#table-of-contents)

## Getting Started

### Installation
1. Download the APK from the [Releases](https://github.com/eddyizm/tempus/releases) section
2. Enable "Install from unknown sources" in your Android settings
3. Install the application

### First Launch
1. Open the application
2. You will be prompted to configure your server connection
3. Grant necessary permissions for media playback and background operation

[Back to top](#table-of-contents)

## Server Configuration

### Initial Setup

The first screen lists your servers. Tap **Open server editor** to add one:
<p align="left">
    <img src="mockup/usage/server_add.png" width=250>
</p>


1. **Server name** is any name you like.
2. **User name** and **Password** are your login for that server. Or in some cases, API key (eg LMS: https://github.com/epoupon/lms/discussions/562).
3. **Public URL** is your server's address, starting with `http://` or `https://`, for example `https://your-subsonic-server.com`.
4. **Plaintext password** sends your password with every request instead of a token. Turn it on only if your server does not accept token login.
5. **Set a local URL** is optional and adds a **Local URL** field, see [Home and away addresses](#home-and-away-addresses).
6. **Client certificate (mTLS)** is optional, for servers that ask for one.
7. **Custom HTTP headers** is optional, see [Advanced Settings](#advanced-settings).

**Server name**, **User name**, **Password** and **Public URL** are required, and so is **Local URL** once its switch is on. Tap **Create**, then **Go to login** to return to the server list, and tap the server there to log in. If the login fails, Tempus shows the error and you stay on the list.

To edit a server, open the editor, pick the server in the menu at the top, change what you need and tap **Update**. **Test** checks a saved server's connection. **Delete** removes the picked server at once, with no confirmation. A change to the server you are logged in to takes effect after you log out and tap the server again.

Long press the Tempus icon and pick **Introduction** to open the welcome, permissions, themes and server pages on their own. The heart icon at the top of the server list opens the same pages.

To switch servers, go to **Settings → Account and library → Log out**, which takes you back to this list.

If you intend to play to a TV or a network speaker, the address you enter here also has to be reachable from that device, because it fetches the music from your server itself. See [Playing on another device](#playing-on-another-device).

### Home and away addresses

With a Local URL set, Tempus uses it when it answers and the Public URL otherwise. Each time the app comes to the front while on the Public URL, it tests the Local URL and moves to it only if it answers. If the Local URL stops answering, the app moves back to the Public URL. This suits a server you reach by its home network address at home and by a public or VPN address everywhere else.

When neither address answers, Tempus shows **Server unreachable**. **Continue anyway** hides it for 24 hours, and **Go to login** logs you out and returns to the server list.

### Advanced Settings

#### Custom HTTP headers

For servers behind an authenticating proxy (e.g. Cloudflare Access service tokens), enable
**Custom HTTP headers** on the server in the server editor and enter one header per line:

    CF-Access-Client-Id: <id>
    CF-Access-Client-Secret: <secret>

Blank lines and lines starting with `#` are ignored. A line that is not `Name: Value`, or that names `Host`, `Content-Length`, `Transfer-Encoding` or `Connection`, is refused when you save, with the line number.

Headers are sent only to that server's address and local address (same scheme, host and port), on API calls, streaming, downloads and cover art, including the artwork shown in the notification, lock screen, Bluetooth and Android Auto. They are not sent to radio stations or other hosts, and they are not carried along when the server redirects to another host. Debug builds hide their values in the HTTP log.

The local address gets the headers too, like it already gets your Subsonic credentials. If your local address is plain `http`, any device answering on that IP on the network you are connected to would receive them, so use an `https` local address or leave it empty when the headers are sensitive.

After editing the headers of the server you are signed in to, sign out and back in for them to take effect.

**Test** in the server editor sends the headers only for the server you are signed in to. For a new server behind a proxy that requires them, Test fails; sign in to the server instead.

Devices that fetch the music themselves cannot send them:

- Chromecast and UPnP/DLNA renderers (see [Playing on another device](#playing-on-another-device)) get a plain stream URL, so they will be refused by a proxy that requires the headers.
- Local-network playback to another phone running Tempus works only if that phone has the same headers set on its own server entry, because it streams from your server with its own settings.

The values are stored on the device the same way as your password.

[Back to top](#table-of-contents)

## Main Features

### Library View

**Multi-library**

Tempus handles multi-library setups gracefully. They are displayed as Library folders. 

If your server reports more than one music folder, Tempus can limit browsing and search to a single one of them.

Tap the app name in the toolbar on the Music tab of Home, or on Library. A second line under it names the library in use, and tapping opens a checked list of the server's libraries. The same setting lives in Settings under General as "Music library". Either one takes effect on the screen you are looking at, with no restart and no navigating away.

<p align="left">
    <img src="mockup/usage/library_switcher.jpg" width=317>
</p>

"All libraries" is the default and keeps the old merged view. The filter reaches browsing and search only, so it never touches your starred items, your playlists or your downloads. On the Podcast and Radio tabs that second line names your server instead of a library, and it is not tappable there. On Downloads there is no second line at all.

<p align="left">
    <img src="mockup/usage/toolbar_server_name.jpg" width=317>
</p>

The setting is hidden when there is only one library to choose from.

### Folder or index playback

If your Subsonic-compatible server exposes the folder tree **or** provides an artist index (for example Gonic, Navidrome, or any backend with folder browsing enabled), Tempus lets you play an entire folder from anywhere in the library hierarchy:

<p align="left">
    <img src="mockup/usage/music_folders_root.png" width=317 style="margin-right:16px;">
    <img src="mockup/usage/music_folders_playback.png" width=317>
</p>

- The **Library ▸ Music folders** screen shows each top-level folder with a play icon only after you drill into it. The root entry remains a simple navigator.
- When viewing **inner folders** **or artist index entries**, tap the new play button to immediately enqueue every audio track inside that folder/index and all nested subfolders.
- Video files are excluded automatically, so only playable audio ends up in the queue.

No extra config is needed—Tempus adjusts based on the connected backend.

### Now Playing Screen

Swipe the artwork left or right to play the next or previous track in the queue. Radio stations have one cover and do not swipe.

Tap the artwork to show four buttons over it, and tap again to hide them.
<p align="left">
    <img src="mockup/usage/player_icons.png" width=159>
</p>

*marked the icons with numbers for clarity* 

1. Downloads the track.
2. Adds the track to a playlist.
3. Adds this track again, followed by songs similar to it, right after the current track (Instant Mix). It uses [getSimilarSongs](https://opensubsonic.netlify.app/docs/endpoints/getsimilarsongs/) of the OpenSubsonic API, so which songs you get depends on your server. For example, Navidrome gets 15 similar artists from Last.fm, then 20 top songs from each.
4. With **Sync play queue for this user** turned on under Settings → Devices and sync, saves the play queue. With it off and lyrics available, this button opens the lyrics instead.

On phones in portrait, the player shows only the cover, the title and artist, the progress bar and three large controls: previous, play/pause and next. "Now playing" and the album name sit at the top. The player is always dark: behind it, colours taken from the cover flow slowly across the screen, and they move more slowly while playback is paused. This is the only continuous motion; the other effects happen only when something changes. The mini player takes a light tint of the same colours. The down arrow closes the player back to a compact player above the bottom navigation. The cover-colour background continues behind the status bar and system navigation area. Controls keep a safe distance from system bars and display cutouts.

The cover is a record: round in the mini player and a vinyl disc in the full player, both turning while music plays and stopping when paused. Dragging the mini player up carries its cover onto the disc, and dragging down carries it back. Swipe the mini player left for the next track or right for the previous one. The progress bar is a thin line that thickens while you drag it. The heart next to the title adds the track to favourites. Three buttons along the bottom open **Lyrics** (shown only when the track has lyrics), the audio output and the **Queue**. The output button picks a Tempus receiver when **Remote player** is on, and otherwise opens Android's output switcher. The button at the bottom left shows and switches the play order: repeat queue, shuffle, repeat one, or in order without repeating. **Shuffle** and **Repeat** are also at the top of the queue. Back from any tab returns to Home, and Back on Home leaves the app. Playback speed, audio details, the sleep timer and track info are in the **⋯** menu. While a sleep timer runs, its countdown appears above the bottom buttons. With Android's **Remove animations** setting on, the backdrop and the progress bar stay still.

Synced lyrics keep the current line in the upper part of the view, with the lines below following in a short ripple. Lines further from the current one are dimmer and slightly blurred. Three dots fill during instrumental breaks that last at least three seconds, including before the first line. Swipe to read ahead; the lyrics return to the current line about two and a half seconds after you let go. Tap a line to play from it. The sync lock button stops the lyrics from following playback. Unsynced lyrics and episode descriptions scroll as plain text.

Below the playback controls, **Sleep timer** stops playback after 5 to 60 minutes, a number of minutes you type in, or at the end of the current track. The centre label identifies local or remote playback, and **Queue** opens the queue. The mini player's controls are disabled when there is no track; clearing the queue hides the collapsed player.

The menu at the top right contains **Track info**, **Audio details**, **Playback Speed**, **Sleep Timer**, and the existing playlist, device and equalizer actions. **Audio details** reveals the format/bitrate row; these controls remain available without occupying the default cover view. **Lyrics** appears in this menu only after the server or local cache returns lyrics. No external lyrics-search service is required. When lyrics are missing, the player stays on the cover. Landscape and tablet layouts retain their dedicated arrangements.

Tap the title to open its album, or the artist to open the artist. Long press either one to copy it.

To show the track number in front of the title, turn on **Show track number** under **Settings → What to show**.

The player menu also has **Play on…** and **Add to playlist**, plus **Built-in equalizer** when Built-in is picked under Settings → Playback and sound. Tablets keep their dedicated controls.

**Format and bitrate**

The player's format and bitrate label gives what was actually decoded, not the transcode Tempus asked for. If your server ignored the request, or had no rule matching the track, the label shows what it really sent. A track the server did transcode reads as the new format followed by "(Transcoding)".

Downloaded tracks show the format on disk. When Tempus transcodes a download, the row and the track info dialog carry the transcoded format and bitrate instead of the source values.

### Podcasts
- The app includes 36 public Chinese-language podcast subscriptions, including 日谈公园, 故事FM, 忽左忽右, 硅谷101 and overseas Chinese shows. They appear automatically in **My subscriptions** on first use; **See all** opens the full list with category filters and search by name, category or region.
- Bundled and manually added RSS feeds are read directly by the client. They work even when the signed-in music server does not implement podcast APIs. Existing server subscriptions remain available.
- **Add podcast channel** accepts a public HTTP/HTTPS audio RSS or Atom URL and validates it before saving. **Refresh** updates feeds; each feed caches up to 60 recent episodes and normally refreshes after six hours. Tap a channel’s status to retry a failed refresh.
- Tap an episode to play; use the player’s skip controls or add it to the queue from its menu. RSS audio streams from the original publisher. Cached episode lists do not mean the audio is downloaded.
- Long-press a channel and choose **Unsubscribe** to remove it. Default subscriptions you remove stay removed on restart and upgrade. Server-only download/delete actions are not offered for client RSS episodes.
- See [Chinese audio sources](CHINESE_AUDIO_SOURCES.md) for the complete list, attribution and verification tool.

### Radio Stations
- The app includes 42 Chinese stations, covering news, music, stories and arts, business and finance, and traffic and daily life. They are automatically added to the local radio list on first use, alongside existing custom and server stations.
- Category chips filter bundled stations; **All** also includes your custom and server stations. Bundled rows show a category and region instead of a technical stream URL.
- Radio streaming and local subscriptions do not depend on the music server’s radio support. Tap a station to play. Long-press to edit or delete it; deleted defaults are remembered.
- Add more stations through Radio Browser search or paste a direct MP3, AAC or HLS URL. Choose device storage to keep a station local; server storage still requires Subsonic internet-radio support.
- Public stream availability can change. See [Chinese audio sources](CHINESE_AUDIO_SOURCES.md) for all bundled stations and their sources.

### Downloads

The Download tab lists every track stored on the phone. Tap the filter button at the top right to list them by Track, Album, Artist, Genre, Playlist or Year. Tempus remembers the choice.

The Track and Album lists are sorted by album artist, then album, then disc and track number, so a compilation with a different artist on each track stays together in track order and is listed under its album artist. An album whose server reports no album artist sorts under its own title.

**Playlist** lists the tracks you downloaded from a playlist, under the playlist's name. Each download remembers one playlist only, so a track that is in two playlists, or was downloaded from an album first, may not show under the playlist you expect.

**Keeping a playlist downloaded**

In a playlist's ⋮ menu, turn on **Keep synced**. Tempus downloads every track of the playlist that is not on the phone yet, then checks again each time you open the playlist and once each time the app starts, and downloads any track added since. Tracks removed from the playlist stay downloaded. There is no check while Tempus is closed.

**Download only on Wi-Fi**, under Settings → Downloads and storage, pauses downloads on mobile data and resumes them on Wi-Fi. It does not apply when downloads are saved to a folder you picked.

### Continue listening

When you open Tempus again, your queue comes back with the track you were on, paused where you stopped.

Podcast episodes, audiobooks and music tracks longer than 10 minutes also keep their spot after you move on to something else. Tempus saves it when you pause, when you switch tracks, and every 15 seconds while playing. These tracks are listed under **Continue listening** on the Music tab of Home, newest first, each with its cover, title, album and the time it resumes at. Tap one to play it from that spot, with the rest of its album after it. The row is hidden while it is empty.

The spot is also saved as a bookmark on your server, if the server supports bookmarks, so the row also shows tracks you stopped on another device with the same account, and bookmarks made by other apps. A track leaves the row when it plays through to the next one.

[Back to top](#table-of-contents)

## Navigation

### Bottom Navigation Bar
- **Home**: Shortcuts, a daily mix, personal radio, and the existing sections
- **Discover**: Library charts plus NetEase and QQ Music charts, browsing by language, decade and genre, and an A–Z artist index (language is guessed from the script of artist names). A public chart matches itself against your library and shows only playable songs; **Export missing** copies or shares the rest as "title - artist" lines for QQ Music's text import; **Export all missing** above the public charts matches every chart and exports what all of them lack, each song once
- **Search**: Genre tiles until you search
- **Library**: Your collection, with **Downloads** at the top

[Back to top](#table-of-contents)

## Playing on another device

Tempus can hand playback to a UPnP or DLNA renderer on your network, such as a TV, an AV receiver or a network speaker. Tap the cast button in the toolbar, pick the device, and the queue, the position and the play state move to it while the app keeps the controls. On the GitHub build the same button lists Chromecast devices and network renderers together. On the degoogled build the button shows a speaker icon, not the Cast icon.

<p align="left">
    <img src="mockup/usage/toolbar_github.png" width=400 style="margin-right:16px;">
    <img src="mockup/usage/toolbar_degoogled.png" width=400>
</p>

The renderer fetches the music from your server itself. Nothing is streamed through the phone, which is how the protocol works. Downloaded tracks are played from your server too, because the renderer cannot reach what is stored on the phone.

**Your server address has to be one the renderer can reach.** If Tempus is configured with an address only the phone can use, the device still appears in the list and still accepts being selected, and then plays nothing. Addresses that fail this way:

- a VPN or mesh network address, a Tailscale or WireGuard address for example, when the renderer is not on that network
- `localhost` or `127.0.0.1`
- an address on a different subnet from the renderer, which is easy to end up with when there is a second router

Use the address your server has on the same network as the renderer.

Volume is left to the renderer's own remote, because some renderers report a volume that does not match what they are doing. The sleep timer's fade out has no effect on a renderer, so playback stops at full volume when the timer runs out. Discovery uses SSDP, which is UDP and lossy, so if your device does not appear the first time, close the picker and open it again.

[Back to top](#table-of-contents)

## Controlling another phone

This feature is experimental. Two phones running Tempus can pair over your local network, so one phone, the controller, picks the music and runs playback on the other, the receiver. Both phones need a Tempus version with this feature and have to use the same server address, because the receiver plays every track from your server with its own account. Songs downloaded on the controller still need the server on the receiver.

**Pairing**

Open **Settings → Remote player**. Set the phone's name under **This device**. One phone can be both a receiver and a controller.

1. On the phone that will play the music, turn on **Allow control from the local network** under **Receiver**. Tempus then shows a notification while the receiver runs.
2. On the receiver, turn on **Make available for pairing**. This opens pairing for two minutes.
3. On the controller, tap **Find devices** and select the receiver.
4. Both phones show a code. If the codes match, confirm on the receiver first, then on the controller.

A paired controller does not need pairing opened again. To remove one, tap it under **Paired controllers** on the receiver and confirm.

**Playing on the receiver**

Tap the device icon on the mini player, or **Play on…** in the expanded player or its overflow menu, and pick the receiver. The normal player then shows **Playing on** and the receiver's name, and its playback controls, shuffle, repeat, speed, seeking from the lyrics and the sleep timer all act on the receiver. The volume keys change the receiver's volume. With the screen off or Tempus in the background, that works only while Android keeps Tempus running and sends the keys to it. Audio effects are set on the receiver itself.

While a receiver is selected, **Play**, **Add to queue** and **Play next** in the library send music to it, albums and playlists included. Adding to an empty receiver queue loads it without starting playback. **Queue** shows the receiver's queue, where you can tap to play or pause, swipe to remove, drag to reorder, and use the menu to shuffle or clear the upcoming tracks, or save the whole queue as a playlist. The controller's own queue is left as it was.

Tap the device button again to go back. The receiver keeps playing, and the controller does not start playing on its own.

**Limits**

- Music only, up to 500 tracks, repeats included. Radio and podcasts are not supported.
- The selection survives rotating the phone but not Tempus being closed. If the receiver restarts or changes address, release it and select it again.
- A failed command never falls back to playing on the controller.
- Guest networks may block discovery. Found receivers drop off the list after 45 seconds and the search starts again. Tap **Find devices** to search at any time.
- When a found phone cannot connect, the message on screen says at which step it failed. Include that message in a report.

Connections use mutual TLS, and each phone is approved by hand. There is no unencrypted fallback.

[Back to top](#table-of-contents)

## Favorites

### Favorites (aka heart aka star) to albums and artists
- Long pressing on an album gives you access to heart/unheart an album   

<p align="center">
    <img src="mockup/usage/fave_album.png" width=376>
</p>

- Long pressing on an artist cover gets you the same access to to heart/unheart an album   

<p align="center">
    <img src="mockup/usage/fave_artist.png" width=376>
</p>

[Back to top](#table-of-contents)

## Playlist Management

### Editing a playlist

Open a playlist and pick Edit playlist from its menu to rename it, drag tracks into a new order, or remove tracks. Saving writes the list exactly as it stands on screen, so a removal or a reorder takes effect on the server instead of being added on top of the tracks already there.

Two things to know:
- A playlist has to keep at least one track. A save that would leave it empty is refused and the editor says so, use Delete if you want the playlist gone.
- Saving needs the server. The editor will not save from the offline cache, because writing that older copy back would drop anything added to the playlist since it was cached.

### Sorting playlists

Open the full playlist list with **See all** next to Playlists on Home or Library, and tap the sort button to change the order. The same choice is under **Settings → What to show → Playlist sorting**. It also orders the Playlists row on Home, which shows up to 20 playlists, and the list you pick from when you add songs to a playlist. Tempus remembers it.

- **Name** sorts A to Z. This is the default.
- **Random** shuffles the list.
- **Date Created** puts the newest playlist first.
- **Song Count** puts the playlist with the most songs first.
- **Faves** puts your faves first.
- **Last played** puts the playlist you opened most recently first.
- **Last updated** puts the playlist changed on the server most recently first.
- **Recently active** uses whichever is newer, when you last opened it or when it last changed.

**Faves**

To make a playlist a fave, tap **Add to faves** in its ⋮ menu, or long press it in the playlist list. Tap **Remove from faves** to undo it. Faves are kept on the phone, not on your server, and a playlist deleted from the server stops being a fave.

[Back to top](#table-of-contents)

## Settings

### Finding a setting

Tap the search icon at the top of Settings and type part of a setting's name or description. Only the matching settings stay on screen, each under its page. Capital letters and accents are ignored, and a match on a page name, such as **Sound**, shows that whole page. The options on the Theme screen are not searched, so search for **Theme** to find that screen.

### Language

Select **Settings → Appearance → Language** to choose an app language. Swedish is available as **Swedish** (or **Svenska**, depending on the current language). Select **System language** to follow your device language.

### Appearance

Open **Settings → Appearance → Theme** to change how Tempus looks. Each change applies right away.

- **Theme** is Light, Dark or System default, which follows the phone.
- **True black background for dark mode** makes the backgrounds pure black while Tempus is dark. It does nothing in Light.
- **Dynamic color accent from wallpaper** takes the app's colors from your wallpaper. It is on by default.
- **Choose Accent Color** builds the app's colors from one color instead. Turn off the wallpaper switch first, or your choice is saved but the wallpaper colors stay. Tap a color to use it. The first one opens a picker where you choose any color.

Accent colors, from the wallpaper or picked, need Android 12 or newer, and some phones on Android 12 do not support them. Without them Tempus keeps its default colors.

**Tiles size**, under **Settings → Appearance**, sets how big album and artist covers are in grids and rows, and the Discover cards on Home. **Default** is the largest and **Tiny** the smallest.

**Rounded corners** rounds the corners of covers and **Corners size** sets how much. Restart Tempus after changing either.

### Sound

**Settings → Playback and sound** holds the equalizer, ReplayGain and volume settings.

**Select an equalizer to use** picks one of three:

- **Default** adds no equalizer.
- **Built-in** uses the equalizer in Android itself. Open it from **Built-in equalizer** below the choice, or from the ⋮ menu on the Now Playing screen, turn on **Enable** and set each band. How many bands you get depends on the phone. **Reset** puts every band back to 0 dB.
- **External** lets an equalizer app on the phone work on Tempus. **System equalizer** below the choice opens that app, and shows only when the phone has one.

**Set replay gain mode** evens out the volume between tracks. It is off by default.

- **Track** plays every track at about the same loudness.
- **Album** keeps the differences between tracks of an album and evens out albums. A track with no album value uses its track value.
- **Auto** uses the album value when the track before it is from the same album, and the track value otherwise.

Tempus takes the values from your server and, if the server sends none, from the file's tags. **Prevent clipping**, on by default, lowers the gain when the file's peak value shows the track would clip. It works only while ReplayGain is on.

**Pre-amplification** makes everything Tempus plays louder or quieter, by up to 15 dB, with ReplayGain on or off. A change is heard from the next track.

### Buffering and cache

These are under **Settings → Downloads and storage**. The streaming cache (songs and podcast episodes you have played, up to 16 GiB) and the artwork cache each show their limit and current use, and can be cleared; a new limit applies after restarting the app. Downloads have no limit and are kept when caches are cleared. **Settings → Updates and about → Update now** checks GitHub and installs a newer release straight away.

**Song preload buffer** sets how much music ahead of where you are Tempus downloads while streaming, 1 minute by default. Restart Tempus after changing it.

**Size of streaming cache** keeps the music you stream on the phone, so playing it again does not download it again. It is 256 MiB by default, and when it is full the music you played longest ago is removed first. The setting shows how much of it is in use. A new size takes effect after Tempus restarts, and **Disabled** turns the cache off.

**Pre-cache upcoming tracks** downloads the next 1, 2, 3 or 5 tracks of the queue into the streaming cache, so skipping to them starts at once and short signal drops do not stop playback. It follows shuffle and repeat, leaves out radio and downloaded tracks, and does nothing while the streaming cache is **Disabled**. **Pre-cache on Wi-Fi only**, on by default, pauses it on mobile data.

### Scrobbling

**Enable music scrobbling**, under **Settings → Devices and sync**, is on by default. It tells your server what you are playing and which tracks you played. Tempus sends this only to your server, which may pass it on to a service such as Last.fm if it is set up to.

A track counts as played when it plays to the end. When you skip to another track in the queue, the one you left counts if it played past its halfway point or 4 minutes, whichever comes first, unless it is 30 seconds or shorter. Only music counts, not podcasts or radio. A play that does not reach the server is not sent again later.

[Back to top](#table-of-contents)

## Android Auto

Android Auto needs the `app-tempus` build from the GitHub releases. The degoogled build, `app-degoogled` and the version on F-Droid and IzzyOnDroid, does not offer your library to Android Auto, and its Settings has no Android Auto page.

### Enabling on your head unit

To allow the Tempus app on your car's head unit, "Unknown sources" needs to be enabled in the Android Auto "Developer settings". This is because Tempus isn't installed through Play Store. Note that the Android Auto developer settings are different from the global Android "Developer options".
1. Switch to developer mode in the Android Auto settings by tapping ten times on the "Version" item at the bottom, followed by giving your permission.
<p align="left">
   <img width="270" height="600" alt="1a" src="https://github.com/user-attachments/assets/f09f6999-9761-4b05-8ec7-bf221a15dda3" />
   <img width="270" height="600" alt="1b" src="https://github.com/user-attachments/assets/0795e508-ba01-41c5-96a7-7c03b0156591" />
   <img width="270" height="600" alt="1c" src="https://github.com/user-attachments/assets/51c15f67-fddb-452e-b5d3-5092edeab390" />
</p>

2. Go to the "Developer settings" by the menu at the top right.
<p align="left">
   <img width="270" height="600" alt="2" src="https://github.com/user-attachments/assets/1ecd1f3e-026d-4d25-87f2-be7f12efbac6" />
</p>

3. Scroll down to the bottom and check "Unknown sources".
<p align="left">
   <img width="270" height="600" alt="3" src="https://github.com/user-attachments/assets/37db88e9-1b76-417f-9c47-da9f3a750fff" />
</p>

### Tabs

The Android Auto interface can be configured by the user to best suit their preferences. The settings are under **Settings → Android Auto**.

<p align="left">
    <img src="mockup/usage/aa_preferences.png" width=317 style="margin-right:16px;">
    <img src="mockup/usage/aa_functions.png" width=317>
</p>

4 tabs can be configured with the following functions:

| Function | What it shows |
|---|---|
| Do not display | This tab is not used |
| Home | Displays all functions not used in other tabs |
| Recent | The 15 recently listened-to albums |
| Albums | Albums sorted by name |
| Artists | Albums sorted by artist or Artists, selected by preference |
| Playlists | |
| Podcast | The 100 podcasts recently added |
| Radio | Your server's stations together with any you added locally in the app. The local ones show even when the server reports no stations of its own |
| Folder | Navigation through music directories |
| Albums most played | The 15 most played albums |
| Tracks played | The 100 last tracks that were completely played |
| Albums added | The 15 recently added albums |
| For You bundle | |
| Starred bundle | |
| Tracks bundle | |
| Genres | 500 songs of the chosen genre OR 100 random songs if "shuffle genre songs" is selected |
| Downloads | the 500 first downloaded tracks OR 100 random downloaded tracks if "shuffle downloaded tracks" is selected. Shown at the top of the Home tab and playable without connectivity |

If all tabs are set to "Do not display", then "Home" tab will be created with all functions inside.

If "Home" is selected after another tab, it becomes "More".

### Bundles

For You bundle includes:

| Item | What it shows |
|---|---|
| Quick mix | features 12 tracks chosen randomly from the 15 last played albums |
| My mix | features 15 tracks chosen randomly from the 15 last played albums, and starred artists or starred albums, following preference |
| Discovery mix | features 18 tracks, as My mix, with similar songs |
| Starred artists | |
| Starred albums | |
| Starred tracks | the 500 first starred tracks OR 100 random starred tracks if "shuffle starred tracks" is selected |

Starred bundle includes:

| Item | What it shows |
|---|---|
| Starred artists | |
| Starred albums | |
| Starred tracks | the 500 first starred tracks OR 100 random starred tracks if "shuffle starred tracks" is selected |

Tracks bundle includes:

| Item | What it shows |
|---|---|
| Random | 100 random songs |
| Genres | 500 songs of the chosen genre OR 100 random songs if "shuffle genre songs" is selected |
| Tracks played | The 100 recently listened-to tracks |
| Starred tracks | the 500 first starred tracks OR 100 random starred tracks if "shuffle starred tracks" is selected |

<p align="left">
    <img src="mockup/usage/aa_tracks.jpg" width=317 style="margin-right:16px;">
    <img src="mockup/usage/aa_for_you.jpg" width=317>
</p>

### Thumbnails or lists

<p align="left">
    <img src="mockup/usage/aa_thumbnails.jpg" width=317 style="margin-right:16px;">
    <img src="mockup/usage/aa_list.jpg" width=317>
</p>

In addition, you can choose to display the following functions as thumbnails or lists:
- Home, For You bundle, Starred bundle and Tracks bundle
- Albums (Last played, Most played, Recently added, Artists, Starred albums, Starred artists)
- Playlists
- Radio
- Podcast

As they displayed tracks, Tracks played, Starred tracks, Random and Genres are always displayed as a list.

### Artists and Instant Mix

Artists view and View by albums:
<p align="left">
    <img src="mockup/usage/aa_artists_view1.jpg" width=317>
    <img src="mockup/usage/aa_artists_view2.jpg" width=317 style="margin-right:16px;">
</p>

Starred Artists view:
<p align="left">
    <img src="mockup/usage/aa_starred_artists_view.jpg" width=317>
</p>

On an artist's page, if they have at least 2 albums with a minimum of 20 tracks, an "Instant Mix by Tempus" album is added at the beginning.
This album features 12, 15 or 18 tracks chosen randomly from their discography and is one click play.
The number of tracks on the album depends on the size of the artist's discography (>20, >30 or >40)

<p align="left">
    <img src="mockup/usage/aa_instantMix.jpg" width=317>
</p>

When an album has tracks on more than one disc, its track list is grouped under a heading for each disc, such as **Disc 1**, with the disc's title after the number when your server sends one.

### A-Z and search

The A-Z button allows you to jump to items starting with the chosen letter.

Search button returns albums or artists, even if they are not displayed by the selected function.

Results of the A-Z jump will always be displayed as a list.

<p align="left">
    <img src="mockup/usage/aa_AZ.jpg" width=317 style="margin-right:16px;">
    <img src="mockup/usage/aa_search.jpg" width=317>
</p>

Display of albums and artists is limited to 500. For large libraries, it's preferable to use star albums or star artists.

### Shortcuts

Shortcuts are displayed only if the function is selected from root level:
- On albums page: jump to starred albums
- On starred albums page: jump to albums
- On artists page: jump to starred artists
- On starred artists page: jump to artists

[Back to top](#table-of-contents)

## Music discovery and collections

### Import NetEase and QQ playlists

Open **Discover → 导入外部歌单** and paste a NetEase or QQ Music playlist sharing link. Public charts, curated playlists and shareable personal playlists are supported. Read the complete playlist, review library matches and explicitly choose between ambiguous versions, then save it to the music server. Songs already in the library become playable immediately; missing songs remain a download request, not a claim that audio has been imported. Imports preserve source order and duplicates, with a limit of 5,000 entries. A truncated or unavailable platform response cannot replace the server playlist.

On the Mac, open [启动歌单导入助手.command](tools/playlist_import/启动歌单导入助手.command). Refresh phone requests, download the source playlist in the already signed-in official QQ/NetEase desktop client, then choose **校验并入库**. Downloading remains an explicit operation in the official client; the helper does not log in, purchase music or operate its download UI. QQ downloads reuse the existing local processing tool; NCM files are processed locally. Only complete audio matching the pending request is eligible for upload. Original downloads and existing server audio are preserved.

After the server scans new audio, use **手动更新／入库后重新匹配** on the phone and save the refreshed result. Updates reuse the same imported server playlist, update its source order and membership, and do not delete audio files. Imports are private initially; updates preserve existing visibility. The source binding and pending list are stored with the server playlist so another installation on the same server/account can resume. No scheduled synchronization is enabled.

The Mac helper requires the existing `unlock_music` checkout, FFmpeg, the `mini_server` SSH connection and the Navidrome database path. Default NetEase download folders are `~/Music/网易云音乐` and `~/Music/CloudMusic`; a custom path can be supplied with `--netease-dir`. It listens only at `127.0.0.1:8769`. Details and local tests are in [tools/playlist_import/README.md](tools/playlist_import/README.md).

Playlists that require an additional platform login or are only partially exposed by the sharing API are reported as unavailable; they are not silently imported as empty or shortened playlists. Missing or invalid downloads remain pending.

### Daily mix

The **Daily mix** card on Home builds 30 songs from recent plays, most-played songs, favourites and favourites not heard lately, plus a few never-played songs. Related songs come from the server's similar-song data when Navidrome has Last.fm configured; otherwise from the same artist, genre and decade. Each device builds its own mix, saved as a private server playlist (**每日推荐 · MM-dd · device model**); the last 7 days are kept.

Up to 5 chart songs by artists you listen to (or have in the library) that the library lacks are listed as "new to download". The Mac import helper shows them as **每日推荐新歌**; download them in the official client and choose **校验并入库**. Unhandled picks expire with their day's playlist.

### Browse charts and manage collections

**Home → Music** and **Library** both provide **Discover music** and **Collections & playlists** shortcuts.

- **Unheard exploration** selects up to 40 songs without a known play count or a local listening-history entry. Use the refresh icon to get another batch. Tap the explanatory text for the record limits: servers with incomplete counts cannot identify everything heard on other devices.
- **Random roaming** offers a fresh set of 40 songs. **Recently added** and **Most played** show server album lists. **Genres** uses server tags and loads another 100 songs at a time.
- **External charts** offers NetEase Hot, New and Rising charts plus Apple Music most-played charts for mainland China, Japan and the US, up to 100 entries each. Choose **Match my library**, then optionally **Library matches only**. Playback always uses your own music server. An exact title/artist match with a compatible duration is required for automatic selection; ambiguous versions require a choice. Other search candidates can be reviewed manually. A missing match does not fetch or import external audio.
- Charts normally reuse a six-hour cache. Refresh requests current data; a failed refresh can use the last validated cache and labels it as cached. Chart access does not receive music-server credentials. Library requests respect the active server and music folder.
- Open **Collections & playlists → +** to create a named collection and choose an icon. Use a song’s **Add to collection / playlist** action to add it to any collection; repeat this for multiple collections. Existing heart favorites are independent. The existing duplicate-playlist preference still applies.
- Collections are standard server playlists. The icon is a Unicode prefix in the server playlist name, so another client can display it too; Tempus renders it as a cover tile in the collection list. Existing playlists can be renamed and assigned an icon through **Edit details**. Save and add actions wait for the server result and report failures.
- Tapping a large **Home → Discover** cover prepares a queue with that song first and any available similar songs after it. If the server has no recommendations or its recommendation request fails, the selected song still plays. Recommendation lookup has a four-second request timeout; rapid clicks use the latest selection.
- Tapping a song keeps the current list visible and updates the mini player. Tap the mini player to open the full player; asynchronous queue initialization preserves an explicit expansion. Swipe the mini player down to dismiss it and clear the queue. Its first appearance and dismissal stay at the bottom of the screen.

See [Discovery and collection implementation notes](MUSIC_DISCOVERY.md) for research references, source details and validation.

## Tempus hub

部署可选的 [tempus-hub](services/tempus-hub/README.md) 后，手机会自动发现同一音乐地址的服务，登记当前账号和设备并同步独立播放事件。各设备独立生成推荐，不混用其他设备的收听偏好；多个 Navidrome 账号的数据相互隔离。首次打开可能显示“正在准备今日推荐”，随后可直接播放服务器保存的私有歌单。

新导入歌单保存时会将完整顺序和选择的歌曲版本登记到 hub。Mac 助手从账号专用配置读取待下载任务；官方客户端下载、校验并上传后，hub 等待 Navidrome 扫描确认并自动补回歌单。存在多个准确版本时需在手机重新匹配确认；其他客户端改动过歌单时停止自动覆盖。旧版导入歌单需手动更新并保存一次。hub 离线时显示最近缓存，待同步记录保留到恢复后上传。

## Known Issues

### Airsonic Distorted Playback

First reported in issue [#226](https://github.com/eddyizm/tempus/issues/226)  
The work around is to disable the cache in the settings, (set to 0), and if needed, cleaning the (Android) cache fixes the problem.

### Support
For additional help:
- Question? Start a [Discussion](https://github.com/eddyizm/tempus/discussions)
- Open an [issue](https://github.com/eddyizm/tempus/issues) if you don't find a discussion solving your issue. 
- Consult your Subsonic server's documentation

---

*Note: This app requires a pre-existing Subsonic-compatible server with music content.*

[Back to top](#table-of-contents)
