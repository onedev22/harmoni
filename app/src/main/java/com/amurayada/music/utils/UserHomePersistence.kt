package com.amurayada.music.utils

import android.net.Uri
import com.amurayada.music.data.model.Album
import com.amurayada.music.data.model.Artist
import com.amurayada.music.data.model.Song
import com.amurayada.music.data.repository.HomeSection
import com.amurayada.music.data.youtube.YouTubeMusicClient
import org.json.JSONArray
import org.json.JSONObject

object UserHomePersistence {
    private const val PREF_KEY_SECTIONS = "user_home_sections"

    fun saveSections(sections: List<HomeSection>) {
        try {
            val jsonArray = JSONArray()
            sections.forEach { section ->
                val jsonSection = JSONObject()
                jsonSection.put("title", section.title)
                
                // Songs
                val songsArray = JSONArray()
                section.songs.forEach { song ->
                    songsArray.put(serializeSong(song))
                }
                jsonSection.put("songs", songsArray)
                
                // Albums
                val albumsArray = JSONArray()
                section.albums.forEach { album ->
                    albumsArray.put(serializeAlbum(album))
                }
                jsonSection.put("albums", albumsArray)
                
                // Artists (if any)
                val artistsArray = JSONArray()
                section.artists.forEach { artist ->
                    artistsArray.put(serializeArtist(artist))
                }
                jsonSection.put("artists", artistsArray)
                
                jsonArray.put(jsonSection)
            }
            YouTubeMusicClient.savePreference(PREF_KEY_SECTIONS, jsonArray.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadSections(): List<HomeSection> {
        val sections = mutableListOf<HomeSection>()
        try {
            val jsonString = YouTubeMusicClient.getPreference(PREF_KEY_SECTIONS)
            if (jsonString.isEmpty()) return emptyList()

            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i) ?: continue
                val title = obj.optString("title")
                
                val songs = mutableListOf<Song>()
                val sArray = obj.optJSONArray("songs")
                if (sArray != null) {
                    for (j in 0 until sArray.length()) {
                        deserializeSong(sArray.optJSONObject(j))?.let { songs.add(it) }
                    }
                }
                
                val albums = mutableListOf<Album>()
                val aArray = obj.optJSONArray("albums")
                if (aArray != null) {
                    for (j in 0 until aArray.length()) {
                        deserializeAlbum(aArray.optJSONObject(j))?.let { albums.add(it) }
                    }
                }
                
                val artists = mutableListOf<Artist>()
                val arArray = obj.optJSONArray("artists")
                if (arArray != null) {
                    for (j in 0 until arArray.length()) {
                        deserializeArtist(arArray.optJSONObject(j))?.let { artists.add(it) }
                    }
                }
                
                sections.add(HomeSection(title, songs, albums, artists))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return sections
    }

    private fun serializeSong(song: Song): JSONObject {
        val obj = JSONObject()
        obj.put("id", song.id)
        obj.put("title", song.title)
        obj.put("artist", song.artist)
        obj.put("album", song.album)
        obj.put("duration", song.duration)
        obj.put("albumArtUri", song.albumArtUri?.toString() ?: "")
        obj.put("path", song.path)
        obj.put("dateAdded", song.dateAdded)
        obj.put("albumId", song.albumId)
        return obj
    }

    private fun deserializeSong(obj: JSONObject?): Song? {
        if (obj == null) return null
        return try {
            Song(
                id = obj.optLong("id"),
                title = obj.optString("title"),
                artist = obj.optString("artist"),
                album = obj.optString("album"),
                duration = obj.optLong("duration"),
                albumArtUri = if (obj.optString("albumArtUri").isNotEmpty()) Uri.parse(obj.optString("albumArtUri")) else null,
                path = obj.optString("path"),
                dateAdded = obj.optLong("dateAdded"),
                albumId = obj.optLong("albumId")
            )
        } catch (e: Exception) { null }
    }

    private fun serializeAlbum(album: Album): JSONObject {
        val obj = JSONObject()
        obj.put("id", album.id)
        obj.put("name", album.name)
        obj.put("artist", album.artist)
        obj.put("artworkUri", album.artworkUri?.toString() ?: "")
        obj.put("year", album.year)
        obj.put("songCount", album.songCount)
        obj.put("path", album.path)
        return obj
    }

    private fun deserializeAlbum(obj: JSONObject?): Album? {
        if (obj == null) return null
        return try {
            Album(
                id = obj.optLong("id"),
                name = obj.optString("name"),
                artist = obj.optString("artist"),
                artworkUri = if (obj.optString("artworkUri").isNotEmpty()) Uri.parse(obj.optString("artworkUri")) else null,
                year = obj.optInt("year"),
                songCount = obj.optInt("songCount"),
                path = obj.optString("path")
            )
        } catch (e: Exception) { null }
    }
    
    private fun serializeArtist(artist: Artist): JSONObject {
        val obj = JSONObject()
        obj.put("id", artist.id)
        obj.put("name", artist.name)
        obj.put("path", artist.path)
        obj.put("imageUrl", artist.imageUrl ?: "")
        return obj
    }
    
    private fun deserializeArtist(obj: JSONObject?): Artist? {
        if (obj == null) return null
        return try {
            Artist(
                id = obj.optLong("id"),
                name = obj.optString("name"),
                songCount = 0,
                albumCount = 0,
                path = obj.optString("path"),
                imageUrl = obj.optString("imageUrl").ifEmpty { null }
            )
        } catch (e: Exception) { null }
    }
}
