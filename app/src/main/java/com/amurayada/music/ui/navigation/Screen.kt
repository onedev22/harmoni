package com.amurayada.music.ui.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Library : Screen("library")
    data object Favorites : Screen("favorites")
    data object History : Screen("history")
    data object Search : Screen("search")
    data object NowPlaying : Screen("now_playing")
    data object Settings : Screen("settings")
    data object AlbumDetail : Screen("album/{albumId}") {
        fun createRoute(albumId: Long) = "album/$albumId"
    }
    data object ArtistDetail : Screen("artist_detail/{artistId}") {
        fun createRoute(artistId: Long) = "artist_detail/$artistId"
    }
    data object GenreDetail : Screen("genre_detail/{genreId}") {
        fun createRoute(genreId: Long) = "genre_detail/$genreId"
    }
}
