package com.amurayada.music.ui.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Library : Screen("library")
    data object Favorites : Screen("favorites")
    data object History : Screen("history")
    data object Search : Screen("search")
    data object NowPlaying : Screen("now_playing")
    data object Settings : Screen("settings")
    data object AlbumDetail : Screen("album/{albumId}?from={from}") {
        fun createRoute(albumId: Long, from: String? = null) = "album/$albumId?from=${from ?: ""}"
    }
    data object ArtistDetail : Screen("artist_detail/{artistId}?from={from}") {
        fun createRoute(artistId: Long, from: String? = null) = "artist_detail/$artistId?from=${from ?: ""}"
    }
    data object GenreDetail : Screen("genre_detail/{genreId}") {
        fun createRoute(genreId: Long) = "genre_detail/$genreId"
    }
    data object Playlists : Screen("playlists")
    data object PlaylistDetail : Screen("playlist/{playlistId}?from={from}") {
        fun createRoute(playlistId: Long, from: String? = null) = "playlist/$playlistId?from=${from ?: ""}"
    }
    data object Recap : Screen("recap")
    data object TimeCapsule : Screen("time_capsule")
    data object ArtistDetailOnline : Screen("artist_detail_online?url={url}&from={from}") {
        fun createRoute(url: String, from: String? = null) = "artist_detail_online?url=${android.net.Uri.encode(url)}&from=${from ?: ""}"
    }
    data object AlbumDetailOnline : Screen("album_detail_online?url={url}&from={from}") {
        fun createRoute(url: String, from: String? = null) = "album_detail_online?url=${android.net.Uri.encode(url)}&from=${from ?: ""}"
    }
    data object YouTubeLogin : Screen("youtube_login")
    data object Personalization : Screen("personalization")
    data object Equalizer : Screen("equalizer")

}
