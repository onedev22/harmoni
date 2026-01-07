package com.amurayada.music.data.repository

import android.net.Uri
import com.amurayada.music.data.model.Album
import com.amurayada.music.data.model.Artist
import com.amurayada.music.data.model.Song
import com.amurayada.music.data.newpipe.NewPipeDownloader
import com.amurayada.music.data.youtube.YouTubeMusicClient
import com.amurayada.music.data.youtube.SearchFilter
import com.amurayada.music.data.youtube.SearchSuggestions
import com.amurayada.music.data.youtube.parsers.SongResultParser
import com.amurayada.music.data.youtube.parsers.ArtistResultParser
import com.amurayada.music.data.youtube.parsers.AlbumResultParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.ListExtractor
import org.schabi.newpipe.extractor.channel.ChannelExtractor
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistExtractor
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem

import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeMusicSearchExtractor
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope

class SearchRepositoryImpl(private val context: android.content.Context) : SearchRepository {

    init {
        if (NewPipe.getDownloader() == null) {
            NewPipe.init(NewPipeDownloader())
        }
    }
    
    // Simple cache to reduce redundant API calls
    private data class CacheEntry<T>(
        val data: T,
        val timestamp: Long
    )
    
    private val songCache = mutableMapOf<String, CacheEntry<List<Song>>>()    // LRU Cache for search results
    private val artistCache = mutableMapOf<String, CacheEntry<List<Artist>>>()
    private val albumCache = mutableMapOf<String, CacheEntry<List<Album>>>()
    private val playlistCache = mutableMapOf<String, CacheEntry<List<com.amurayada.music.data.model.Playlist>>>()
    private val albumDetailsCache = mutableMapOf<String, CacheEntry<List<Song>>>()
    private val artistDetailsCache = mutableMapOf<String, CacheEntry<ArtistDetails>>()
    
    // UNIFIED cache - key: query, value: all results (songs, artists, albums)
    private data class UnifiedSearchResult(
        val songs: List<Song>,
        val artists: List<Artist>,
        val albums: List<Album>
    )
    private val unifiedCache = mutableMapOf<String, CacheEntry<UnifiedSearchResult>>()
    
    private val CACHE_TTL = 30 * 60 * 1000L // 30 minutes
    private val maxCacheSize = 50
    
    private fun <T> isCacheValid(entry: CacheEntry<T>?): Boolean {
        return entry != null && (System.currentTimeMillis() - entry.timestamp) < CACHE_TTL
    }
    
    // Trim caches
    private fun <T> trimCache(cache: MutableMap<String, CacheEntry<T>>) {
        if (cache.size > maxCacheSize) {
            // Remove oldest entry
            val oldestKey = cache.minByOrNull { it.value.timestamp }?.key
            oldestKey?.let { cache.remove(it) }
        }
    }
    
    // Helper to force high-resolution thumbnails
    private fun forceHighResThumbnail(url: String?): String {
        if (url == null) return ""
        var result = url
        
        // Handle standard YouTube thumbnails
        if (result.contains("default.jpg")) {
            result = result.replace("hqdefault.jpg", "maxresdefault.jpg")
            result = result.replace("mqdefault.jpg", "maxresdefault.jpg")
            result = result.replace("sddefault.jpg", "maxresdefault.jpg")
            if (!result.contains("maxresdefault.jpg")) {
                 result = result.replace("default.jpg", "maxresdefault.jpg")
            }
        }
        
        // Handle query parameters (e.g. =w120-h120)
        if (result.contains(Regex("=[whs]\\d+"))) {
            result = result.replace(Regex("=[whs]\\d+(-[wh]\\d+)*"), "=w540-h540")
        }
        
        // Handle path segments (e.g. /s120/)
        result = result.replace(Regex("/[whs]\\d+(-[wh]\\d+)*/"), "/s540/")
        
        return result
    }

    override suspend fun search(query: String): SearchResult = withContext(Dispatchers.IO) {
        coroutineScope {
            val songsDeferred = async { searchSongs(query) }
            val artistsDeferred = async { searchArtists(query) }
            val albumsDeferred = async { searchAlbums(query) }
            val playlistsDeferred = async { searchPlaylists(query) }

            SearchResult(
                songsDeferred.await(),
                artistsDeferred.await(),
                albumsDeferred.await(),
                playlistsDeferred.await()
            )
        }
    }

    // PARALLEL FILTERED SEARCHES - More accurate than unified search
    // Each uses a specific filter param to get only Songs/Artists/Albums
    // HTTP/2 connection reuse makes these fast despite being 3 requests
    
    override suspend fun searchSongs(query: String): List<Song> = withContext(Dispatchers.IO) {
        val cached = songCache[query]
        if (isCacheValid(cached)) {
            return@withContext cached!!.data
        }
        
        val startTime = System.currentTimeMillis()
        try {
            android.util.Log.d("SearchDebug", "Searching SONGS for: $query")
            // Use SimpMusic filter params for better accuracy
            val allSongs = searchYtmDirect(query, SearchFilter.FILTER_SONG.param)
                .filterIsInstance<Song>()
                .distinctBy { it.id }
            
            if (allSongs.isNotEmpty()) {
                songCache[query] = CacheEntry(allSongs, System.currentTimeMillis())
                trimCache(songCache)
            }
            android.util.Log.d("SearchDebug", "Songs found: ${allSongs.size} (unfiltered) in ${System.currentTimeMillis() - startTime}ms")
            allSongs
        } catch (e: Exception) {
            android.util.Log.e("SearchDebug", "Song search failed", e)
            emptyList()
        }
    }

    override suspend fun searchArtists(query: String): List<Artist> = withContext(Dispatchers.IO) {
        val cached = artistCache[query]
        if (isCacheValid(cached)) {
            return@withContext cached!!.data
        }
        
        val startTime = System.currentTimeMillis()
        try {
            android.util.Log.d("SearchDebug", "Searching ARTISTS for: $query")
            // Use SimpMusic filter params for better accuracy
            val allArtists = searchYtmDirect(query, SearchFilter.FILTER_ARTIST.param)
                .filterIsInstance<Artist>()
                .distinctBy { it.id }
            
            // Sort to prioritize exact matches (e.g. "Ozuna" before "Myke Towers" - if Myke had "Ozuna" in name)
            val sortedArtists = allArtists.sortedByDescending { 
                if (it.name.equals(query, ignoreCase = true)) 2 // Exact match
                else if (it.name.startsWith(query, ignoreCase = true)) 1 // Starts with
                else 0
            }
            
            if (sortedArtists.isNotEmpty()) {
                artistCache[query] = CacheEntry(sortedArtists, System.currentTimeMillis())
                trimCache(artistCache)
                
                // PREFETCH: Preload top artist details silently
                val topArtist = sortedArtists.first()
                if (topArtist.path.contains("channel/")) {
                    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                        try {
                            android.util.Log.d("SearchRepository", "Prefetching artist: ${topArtist.name}")
                            getArtistDetails(topArtist.path)
                        } catch (e: Exception) {
                            // Ignore prefetch errors
                        }
                    }
                }
            }
            android.util.Log.d("SearchDebug", "Artists found: ${sortedArtists.size} (filtered from ${allArtists.size}) in ${System.currentTimeMillis() - startTime}ms")
            sortedArtists
        } catch (e: Exception) {
            android.util.Log.e("SearchDebug", "Artist search failed", e)
            emptyList()
        }
    }

    override suspend fun searchAlbums(query: String): List<Album> = withContext(Dispatchers.IO) {
        val cached = albumCache[query]
        if (isCacheValid(cached)) {
            return@withContext cached!!.data
        }
        
        val startTime = System.currentTimeMillis()
        try {
            android.util.Log.d("SearchDebug", "Searching ALBUMS for: $query")
            // Use SimpMusic filter params for better accuracy
            val allAlbums = searchYtmDirect(query, SearchFilter.FILTER_ALBUM.param)
                .filterIsInstance<Album>()
                .distinctBy { it.id }
            
            if (allAlbums.isNotEmpty()) {
                albumCache[query] = CacheEntry(allAlbums, System.currentTimeMillis())
                trimCache(albumCache)
            }
            android.util.Log.d("SearchDebug", "Albums found: ${allAlbums.size} (unfiltered) in ${System.currentTimeMillis() - startTime}ms")
            allAlbums
        } catch (e: Exception) {
            android.util.Log.e("SearchDebug", "Album search failed", e)
            emptyList()
        }
    }

    override suspend fun searchPlaylists(query: String): List<com.amurayada.music.data.model.Playlist> = withContext(Dispatchers.IO) {
        val cached = playlistCache[query]
        if (isCacheValid(cached)) {
            return@withContext cached!!.data
        }
        
        val startTime = System.currentTimeMillis()
        try {
            android.util.Log.d("SearchDebug", "Searching PLAYLISTS for: $query")
            // Use Community Playlists filter as it covers most user searches
            val allPlaylists = searchYtmDirect(query, SearchFilter.FILTER_COMMUNITY_PLAYLIST.param)
                .filterIsInstance<com.amurayada.music.data.model.Playlist>()
                .distinctBy { it.id }
            
            if (allPlaylists.isNotEmpty()) {
                playlistCache[query] = CacheEntry(allPlaylists, System.currentTimeMillis())
                trimCache(playlistCache)
            }
            android.util.Log.d("SearchDebug", "Playlists found: ${allPlaylists.size} (unfiltered) in ${System.currentTimeMillis() - startTime}ms")
            allPlaylists
        } catch (e: Exception) {
            android.util.Log.e("SearchDebug", "Playlist search failed", e)
            emptyList()
        }
    }

    override suspend fun getArtistDetails(url: String): ArtistDetails = withContext(Dispatchers.IO) {
        // Check cache first
        val cached = artistDetailsCache[url]
        if (isCacheValid(cached)) {
            android.util.Log.d("SearchRepository", "Returning artist details from cache: $url")
            return@withContext cached!!.data
        }

        try {
            // Extract channel ID from URL
            val channelId = url.substringAfter("/channel/").substringBefore("?").substringBefore("/")
            android.util.Log.d("SearchRepository", "Fetching YTM artist for channel ID: $channelId")
            
            // Try to get artist info from YouTube Music innertube API
            val artistData = fetchYouTubeMusicArtist(channelId)
            
            if (artistData != null) {
                // Cache the result
                artistDetailsCache[url] = CacheEntry(artistData, System.currentTimeMillis())
                trimCache(artistDetailsCache)
                artistData
            } else {
                // Fallback to search if innertube fails
                android.util.Log.d("SearchRepository", "Innertube failed, falling back to search")
                val searchResult = search(channelId)
                ArtistDetails(
                    Artist(channelId.hashCode().toLong(), channelId, 0, 0, url),
                    searchResult.songs,
                    searchResult.albums,
                    emptyList()
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ArtistDetails(
                Artist(0, "Error: ${e.message}", 0, 0, ""),
                emptyList(),
                emptyList()
            )
        }
    }
    
    private fun fetchYouTubeMusicArtist(channelId: String): ArtistDetails? {
        try {
            val browseId = if (channelId.startsWith("UC")) channelId else "UC$channelId"
            
            // YouTube Music innertube API endpoint
            val apiUrl = "https://music.youtube.com/youtubei/v1/browse?prettyPrint=false"
            
            // Request body using centralized YouTubeMusicClient config
            val locale = YouTubeMusicClient.getLocale()
            val requestBody = """
                {
                    "context": {
                        "client": {
                            "clientName": "${YouTubeMusicClient.CLIENT_NAME}",
                            "clientVersion": "${YouTubeMusicClient.CLIENT_VERSION}",
                            "hl": "${locale.hl}",
                            "gl": "${locale.gl}",
                            "utcOffsetMinutes": ${YouTubeMusicClient.getUtcOffsetMinutes()},
                            "visitorData": "${YouTubeMusicClient.visitorData}"
                        }
                    },
                    "browseId": "$browseId"
                }
            """.trimIndent()
            
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = requestBody.toRequestBody(mediaType)
            
            val request = okhttp3.Request.Builder()
                .url(apiUrl)
                .post(body)
                .header("Content-Type", "application/json")
                .header("User-Agent", YouTubeMusicClient.USER_AGENT)
                .header("Referer", YouTubeMusicClient.REFERER)
                .header("Origin", "https://music.youtube.com")
                .header("X-Goog-Api-Format-Version", "1")
                .header("X-YouTube-Client-Name", YouTubeMusicClient.INNER_TUBE_NAME.toString())
                .header("X-YouTube-Client-Version", YouTubeMusicClient.CLIENT_VERSION)
                .header("X-Goog-Visitor-Id", YouTubeMusicClient.visitorData)
                .build()
            
            val client = com.amurayada.music.data.newpipe.NewPipeDownloader.getClient()
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                android.util.Log.e("SearchRepository", "YTM API error: ${response.code}")
                response.close()
                return null
            }
            
            val responseString = response.body?.string() ?: ""
            android.util.Log.d("SearchRepository", "YTM API response length: ${responseString.length}")
            
            return parseYouTubeMusicArtistResponse(responseString, channelId)
            
        } catch (e: Exception) {
            android.util.Log.e("SearchRepository", "Error fetching YTM artist", e)
            return null
        }
    }
    
    private fun parseYouTubeMusicArtistResponse(response: String, channelId: String): ArtistDetails? {
        try {
            val json = org.json.JSONObject(response)
            
            // Get artist name from header
            var artistName = "Unknown Artist"
            var artistImage: String? = null
            var subscriberCount = 0
            
            val header = json.optJSONObject("header")
            if (header != null) {
                val musicImmersiveHeaderRenderer = header.optJSONObject("musicImmersiveHeaderRenderer")
                val musicVisualHeaderRenderer = header.optJSONObject("musicVisualHeaderRenderer")
                val musicResponsiveHeaderRenderer = header.optJSONObject("musicResponsiveHeaderRenderer")
                
                val targetHeader = musicImmersiveHeaderRenderer ?: musicVisualHeaderRenderer ?: musicResponsiveHeaderRenderer

                if (targetHeader != null) {
                    val title = targetHeader.optJSONObject("title")
                    artistName = title?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: artistName
                    
                    val thumbnail = targetHeader.optJSONObject("thumbnail")
                    val thumbnails = thumbnail?.optJSONObject("musicThumbnailRenderer")?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                    if (thumbnails != null && thumbnails.length() > 0) {
                        artistImage = thumbnails.optJSONObject(thumbnails.length() - 1)?.optString("url")
                    }
                    
                    // Parse subscriber count from subscriptionButton
                    val subscriptionButton = targetHeader.optJSONObject("subscriptionButton")
                    val subscriberCountText = subscriptionButton?.optJSONObject("subscribeButtonRenderer")
                        ?.optJSONObject("subscriberCountText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                    
                    if (subscriberCountText != null) {
                        // Parse "1.5M subscribers" or "1,500,000 suscriptores"
                        val numText = subscriberCountText.replace(Regex("[^0-9.,KMkm]"), "")
                        subscriberCount = try {
                            when {
                                numText.contains("M", ignoreCase = true) -> (numText.replace(Regex("[^0-9.]"), "").toDouble() * 1_000_000).toInt()
                                numText.contains("K", ignoreCase = true) -> (numText.replace(Regex("[^0-9.]"), "").toDouble() * 1_000).toInt()
                                else -> numText.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0
                            }
                        } catch (e: Exception) { 0 }
                    }
                }
            }
            
            android.util.Log.d("SearchRepository", "Parsed artist: $artistName, subscribers: $subscriberCount")
            
            val artist = Artist(
                id = channelId.hashCode().toLong(),
                name = artistName,
                albumCount = 0,
                songCount = subscriberCount, // Using songCount as subscriber count proxy
                path = "https://music.youtube.com/channel/$channelId",
                imageUrl = artistImage
            )
            
            val allSongs = mutableListOf<Song>()
            val allAlbums = mutableListOf<Album>()
            val allSingles = mutableListOf<Album>()
            
            // Parse contents (shelves)
            val contents = json.optJSONObject("contents")
                ?.optJSONObject("singleColumnBrowseResultsRenderer")
                ?.optJSONArray("tabs")?.optJSONObject(0)
                ?.optJSONObject("tabRenderer")?.optJSONObject("content")
                ?.optJSONObject("sectionListRenderer")?.optJSONArray("contents")
            
            if (contents != null) {
                for (i in 0 until contents.length()) {
                    val shelf = contents.optJSONObject(i)
                    val musicShelfRenderer = shelf?.optJSONObject("musicShelfRenderer")
                    val musicCarouselShelfRenderer = shelf?.optJSONObject("musicCarouselShelfRenderer")
                    
                    // Get shelf title
                    val shelfTitle = (musicShelfRenderer ?: musicCarouselShelfRenderer)
                        ?.optJSONObject("header")
                        ?.optJSONObject("musicCarouselShelfBasicHeaderRenderer")
                        ?.optJSONObject("title")
                        ?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                        ?: ""
                    
                    android.util.Log.d("SearchRepository", "Processing shelf: $shelfTitle")
                    
                    val shelfContents = (musicShelfRenderer ?: musicCarouselShelfRenderer)?.optJSONArray("contents")
                    
                    if (shelfContents != null) {
                        for (j in 0 until shelfContents.length()) {
                            val item = shelfContents.optJSONObject(j)
                            
                            // Songs (musicResponsiveListItemRenderer)
                            val listItemRenderer = item?.optJSONObject("musicResponsiveListItemRenderer")
                            if (listItemRenderer != null) {
                                val song = parseMusicListItem(listItemRenderer, artistName)
                                if (song != null) allSongs.add(song)
                            }
                            
                            // Albums/Singles (musicTwoRowItemRenderer)
                            val twoRowRenderer = item?.optJSONObject("musicTwoRowItemRenderer")
                            if (twoRowRenderer != null) {
                                val album = parseMusicTwoRowItem(twoRowRenderer, artistName)
                                if (album != null) {
                                    // Filter: Only add if it's an album (MPREb_) or single, not a playlist (VL)
                                    val isPlaylist = album.path.contains("/browse/VL") || 
                                        shelfTitle.contains("playlist", ignoreCase = true) ||
                                        shelfTitle.contains("lista", ignoreCase = true)
                                    
                                    if (!isPlaylist) {
                                        val lowerTitle = shelfTitle.lowercase()
                                        if (lowerTitle.contains("single") || lowerTitle.contains("sencillo")) {
                                            allSingles.add(album)
                                        } else if (lowerTitle.contains("latest") || lowerTitle.contains("último") || lowerTitle.contains("new") || lowerTitle.contains("nuevo")) {
                                             // Check subtitle or type to decide if album or single, defaulting to album if unsure but usually 'Latest Release' is a single or album
                                             // If it has 'Single' in subtitle (year • Single), put in singles
                                             // We don't have subtitle easily here without re-parsing, but let's assume if it's not explicitly an album it might be a single?
                                             // Actually safesty is to add to Albums usually, or check if we can differentiate.
                                             // Let's add to both lists? No, duplicates.
                                             // Let's put in Albums as default for "Latest", unless it says "Single" in title (already caught).
                                             allAlbums.add(album)
                                        } else if (lowerTitle.contains("album") || lowerTitle.contains("álbum") ||
                                            lowerTitle.contains("ep") || album.path.contains("MPREb_")) {
                                            allAlbums.add(album)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            android.util.Log.d("SearchRepository", "Total YTM: ${allSongs.size} songs, ${allAlbums.size} albums, ${allSingles.size} singles")
            
            return ArtistDetails(artist, allSongs, allAlbums, allSingles)
            
        } catch (e: Exception) {
            android.util.Log.e("SearchRepository", "Error parsing YTM response", e)
            return null
        }
    }
    
    private fun parseMusicListItem(renderer: org.json.JSONObject, artistName: String): Song? {
        try {
            val flexColumns = renderer.optJSONArray("flexColumns")
            if (flexColumns == null || flexColumns.length() == 0) return null
            
            // Title from first column
            val titleRuns = flexColumns.optJSONObject(0)
                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")?.optJSONArray("runs")
            val title = titleRuns?.optJSONObject(0)?.optString("text") ?: return null
            
            // Get video ID from multiple sources (same as search parsing)
            val playlistItemData = renderer.optJSONObject("playlistItemData")
            var videoId = playlistItemData?.optString("videoId", "") ?: ""
            
            // Fallback 1: watchEndpoint in overlay
            if (videoId.isEmpty()) {
                videoId = renderer.optJSONObject("overlay")
                    ?.optJSONObject("musicItemThumbnailOverlayRenderer")
                    ?.optJSONObject("content")
                    ?.optJSONObject("musicPlayButtonRenderer")
                    ?.optJSONObject("playNavigationEndpoint")
                    ?.optJSONObject("watchEndpoint")
                    ?.optString("videoId", "") ?: ""
            }
            
            // Fallback 2: Direct navigationEndpoint
            if (videoId.isEmpty()) {
                videoId = renderer.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("watchEndpoint")
                    ?.optString("videoId", "") ?: ""
            }
            
            if (videoId.isEmpty()) {
                android.util.Log.w("ParseMusicListItem", "No videoId found for: $title")
                return null
            }
            
            // Thumbnail
            val thumbnails = renderer.optJSONObject("thumbnail")
                ?.optJSONObject("musicThumbnailRenderer")
                ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
            
            var thumbnailUrl = ""
            if (thumbnails != null && thumbnails.length() > 0) {
                // Get the last thumbnail (usually highest res)
                var url = thumbnails.optJSONObject(thumbnails.length() - 1)?.optString("url")
                // Force high resolution
                if (url != null) {
                    url = url.replace(Regex("=[whs]\\d+(-[wh]\\d+)*"), "=w540-h540")
                    url = url.replace(Regex("/[whs]\\d+(-[wh]\\d+)*/"), "/s540/")
                    thumbnailUrl = url
                }
            }
            
            var extractedArtist = artistName
            var extractedAlbum = "YouTube Music"
            
            // Try to extract Artist/Album from second column
            if (flexColumns.length() > 1) {
                val secondColumnRuns = flexColumns.optJSONObject(1)
                    ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                    ?.optJSONObject("text")?.optJSONArray("runs")
                
                if (secondColumnRuns != null) {
                    for (k in 0 until secondColumnRuns.length()) {
                        val run = secondColumnRuns.optJSONObject(k)
                        val text = run?.optString("text") ?: continue
                        
                        val browseId = run.optJSONObject("navigationEndpoint")
                            ?.optJSONObject("browseEndpoint")?.optString("browseId", "") ?: ""
                        
                        if (browseId.startsWith("UC")) {
                            // If we have a generic artist name, override it. 
                            // Or if we prioritize the specific artist for this song.
                            if (extractedArtist == "Various Artists" || extractedArtist == "Unknown" || true) {
                                extractedArtist = text
                            }
                        } else if (browseId.startsWith("MPRE") || browseId.startsWith("OLAK")) {
                            extractedAlbum = text
                        } else if (extractedArtist == "Various Artists" && k == 0 && text != " • ") {
                             // Fallback: First text element often artist if no browseId available
                             extractedArtist = text
                        }
                    }
                }
            }

            return Song(
                id = videoId.hashCode().toLong(),
                title = title,
                artist = extractedArtist,
                album = extractedAlbum,
                duration = 0L,
                albumArtUri = if (thumbnailUrl.isNotEmpty()) android.net.Uri.parse(thumbnailUrl) else null,
                path = "https://music.youtube.com/watch?v=$videoId",
                dateAdded = System.currentTimeMillis(),
                albumId = 0
            )
        } catch (e: Exception) {
            return null
        }
    }
    
    private fun parseMusicTwoRowItem(renderer: org.json.JSONObject, artistName: String): Album? {
        try {
            // Title
            val title = renderer.optJSONObject("title")
                ?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: return null
            
            // Browse ID (album ID) - with proper empty handling
            // Browse ID (album ID) or Watch ID (Playlist/Mix)
            val navEndpoint = renderer.optJSONObject("navigationEndpoint")
            var browseId = navEndpoint?.optJSONObject("browseEndpoint")?.optString("browseId", "") ?: ""
            var isPlaylistOrMix = false
            
            if (browseId.isEmpty()) {
                // Try watchEndpoint for Mixes/Radios
                val watchEndpoint = navEndpoint?.optJSONObject("watchEndpoint")
                if (watchEndpoint != null) {
                    val playlistId = watchEndpoint.optString("playlistId")
                    if (playlistId.isNotEmpty()) {
                        browseId = playlistId // Treat playlist ID as browse ID for our purposes
                        isPlaylistOrMix = true
                    }
                }
            }
            
            if (browseId.isEmpty()) {
                // android.util.Log.w("ParseTwoRowItem", "No browseId found for album: $title")
                return null
            }
            
            // Thumbnail
            val thumbnails = renderer.optJSONObject("thumbnailRenderer")
                ?.optJSONObject("musicThumbnailRenderer")
                ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
            
            var thumbnailUrl = ""
            if (thumbnails != null && thumbnails.length() > 0) {
                // Get the last thumbnail (usually highest res)
                var url = thumbnails.optJSONObject(thumbnails.length() - 1)?.optString("url")
                // Force high resolution by replacing dimensions
                if (url != null) {
                    url = url.replace(Regex("=[whs]\\d+(-[wh]\\d+)*"), "=w540-h540")
                    url = url.replace(Regex("/[whs]\\d+(-[wh]\\d+)*/"), "/s540/")
                    thumbnailUrl = url
                }
            }
            
            // Parse subtitle for year, song count, and ARTIST
            var year = 0
            var songCount = 0
            var extractedArtist = artistName
            
            val subtitleRuns = renderer.optJSONObject("subtitle")?.optJSONArray("runs")
            
            if (subtitleRuns != null) {
                for (i in 0 until subtitleRuns.length()) {
                    val run = subtitleRuns.optJSONObject(i)
                    val text = run?.optString("text") ?: continue
                    
                    // Check for Artist by Browse ID
                    val runBrowseId = run.optJSONObject("navigationEndpoint")
                        ?.optJSONObject("browseEndpoint")?.optString("browseId", "") ?: ""
                        
                    if (runBrowseId.startsWith("UC")) {
                        extractedArtist = text
                    } else if (extractedArtist == "Unknown" || extractedArtist == "YouTube Music" || extractedArtist == "Various Artists") {
                         // Heuristic: If we don't have a specific artist yet, pick the first text that isn't a separator or metadata
                         if (text != " • " && !text.matches(Regex("\\d{4}")) && !text.contains("song") && !text.contains("cancion") && !text.contains("visitas") && !text.contains("views")) {
                             // Avoid "Album", "Single", "EP" if possible, but often they are listed.
                             // Usually "Artist • Year". 
                             if (i == 0 || (i == 2 && (subtitleRuns.optJSONObject(0)?.optString("text")?.contains("Album") == true))) {
                                  extractedArtist = text
                             } else if (extractedArtist == "Unknown" && i == 0) {
                                  // Fallback: First item is often the artist (e.g. "Bad Bunny • 2023")
                                  extractedArtist = text
                             }
                         }
                    }
                    
                    // Year (4 digits)
                    if (text.matches(Regex("\\d{4}"))) {
                        year = text.toIntOrNull() ?: 0
                    }
                    
                    // Song count (e.g. "12 songs" or "12 canciones")
                    if (text.contains("song") || text.contains("canción") || text.contains("canciones")) {
                        songCount = text.replace(Regex("[^0-9]"), "").toIntOrNull() ?: 0
                    }
                }
            }
            
            return Album(
                id = browseId.hashCode().toLong(),
                name = title,
                artist = extractedArtist,
                artworkUri = if (thumbnailUrl.isNotEmpty()) android.net.Uri.parse(thumbnailUrl) else null,
                year = year,
                songCount = songCount,
                path = if (isPlaylistOrMix) "https://music.youtube.com/playlist?list=$browseId" else "https://music.youtube.com/browse/$browseId"
            )
        } catch (e: Exception) {
            return null
        }
    }

    override suspend fun getAlbumDetails(url: String): List<Song> = withContext(Dispatchers.IO) {
        // Check cache first
        val cached = albumDetailsCache[url]
        if (isCacheValid(cached)) {
            android.util.Log.d("SearchRepository", "Returning album details from cache: $url")
            return@withContext cached!!.data
        }

        try {
            // Check if this is a YouTube Music album URL OR a simple ID (LM, PL..., MPRE...)
            val isYtmUrl = url.contains("music.youtube.com/") || url.contains("MPRE") || url.contains("list=") || url == "LM" || url.startsWith("PL") || url.startsWith("VL")
            
            if (isYtmUrl) {
                // Extract browse ID from URL
                var browseId = ""
                var params: String? = null
                if (url.contains("list=")) {
                    val listId = url.substringAfter("list=").substringBefore("&")
                    // Convert Playlist ID to Browse ID (VL prefix) for browse endpoint
                    browseId = if (listId.startsWith("VL")) listId else "VL$listId"
                } else if (url.contains("/browse/")) {
                    browseId = url.substringAfter("/browse/").substringBefore("?").substringBefore("/")
                    if (url.contains("params=")) {
                        params = url.substringAfter("params=").substringBefore("&")
                    }
                } else if (url == "LM") {
                    // "Liked Music" requires authentication
                    val authHeaders = YouTubeMusicClient.getAuthHeaders()
                    if (authHeaders.isEmpty() || authHeaders["Cookie"].isNullOrEmpty()) {
                        android.util.Log.w("SearchRepository", "Liked Music requires authentication. User not logged in.")
                        return@withContext emptyList()
                    }
                    browseId = "VLLM" // Browse ID for Liked Music
                } else if (url.startsWith("PL")) {
                    browseId = "VL$url"
                } else {
                    browseId = url
                }
                
                android.util.Log.d("SearchRepository", "Fetching YTM album/playlist for browse ID: $browseId params: $params")
                
                val songs = fetchYouTubeMusicAlbum(browseId, params)
                if (songs.isNotEmpty()) {
                    // Cache the result
                    albumDetailsCache[url] = CacheEntry(songs, System.currentTimeMillis())
                    trimCache(albumDetailsCache)
                    return@withContext songs
                }
                
                // If VLLM returned empty, user might not be logged in or have no liked songs
                if (browseId == "VLLM") {
                    android.util.Log.w("SearchRepository", "Liked Music returned empty. User may not be logged in or has no liked songs.")
                    return@withContext emptyList()
                }
            }
            
            
            // Fallback to standard YouTube playlist extraction
            // NOTE: LM and VLLM are NOT valid for NewPipe fallback (YTM-only feature)
            var cleanUrl = url
            if (!cleanUrl.startsWith("http")) {
                // Skip fallback for auth-only playlists
                if (cleanUrl == "VLLM" || cleanUrl == "LM") {
                    android.util.Log.w("SearchRepository", "Cannot fallback for Liked Music - requires YTM authentication")
                    return@withContext emptyList()
                }
                
                // Construct proper URL from ID
                if (cleanUrl.startsWith("VL")) {
                    cleanUrl = "https://music.youtube.com/playlist?list=${cleanUrl.substring(2)}"
                } else if (cleanUrl.startsWith("PL") || cleanUrl.startsWith("RD")) {
                    cleanUrl = "https://music.youtube.com/playlist?list=$cleanUrl"
                } else {
                    // Try browsing
                    cleanUrl = "https://music.youtube.com/browse/$cleanUrl"
                }
            }
            
            cleanUrl = cleanUrl.replace("music.youtube.com", "www.youtube.com")
            android.util.Log.d("SearchRepository", "Fallback using NewPipe with URL: $cleanUrl")
            
            val service = ServiceList.YouTube
            val playlistExtractor = service.getPlaylistExtractor(cleanUrl)
            playlistExtractor.fetchPage()
            
            val items = (playlistExtractor as ListExtractor<*>).initialPage.items
            
            items.filterIsInstance<StreamInfoItem>().map { item: StreamInfoItem ->
                val thumbnailUrl = item.thumbnails?.firstOrNull()?.url ?: ""
                Song(
                    id = item.url.hashCode().toLong(),
                    title = item.name,
                    artist = item.uploaderName ?: "Unknown",
                    album = playlistExtractor.name,
                    duration = item.duration * 1000L,
                    albumArtUri = if (thumbnailUrl.isNotEmpty()) Uri.parse(thumbnailUrl) else null,
                    path = item.url,
                    dateAdded = System.currentTimeMillis(),
                    albumId = url.hashCode().toLong()
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("SearchRepository", "Fallback failed for $url", e)
            emptyList()
        }
    }
    
    private fun fetchYouTubeMusicAlbum(browseId: String, params: String? = null): List<Song> {
        val json = fetchYouTubeMusicAlbumRawJson(browseId, params)
        if (json.isEmpty()) return emptyList()
        return parseYouTubeMusicAlbumResponse(json)
    }

    private fun fetchYouTubeMusicAlbumRawJson(browseId: String, params: String? = null): String {
        try {
            val apiUrl = "https://music.youtube.com/youtubei/v1/browse?prettyPrint=false"
            
            // Request body using centralized YouTubeMusicClient config
            val locale = YouTubeMusicClient.getLocale()
            val paramsField = if (params != null) ", \"params\": \"$params\"" else ""
            
            val requestBody = """
                {
                    "context": {
                        "client": {
                            "clientName": "${YouTubeMusicClient.CLIENT_NAME}",
                            "clientVersion": "${YouTubeMusicClient.CLIENT_VERSION}",
                            "hl": "${locale.hl}",
                            "gl": "${locale.gl}",
                            "utcOffsetMinutes": ${YouTubeMusicClient.getUtcOffsetMinutes()},
                            "visitorData": "${YouTubeMusicClient.visitorData}"
                        }
                    },
                    "browseId": "$browseId"
                    $paramsField
                }
            """.trimIndent()
            
            
            val client = okhttp3.OkHttpClient()
            
            // Debug: Log auth state
            val authHeaders = YouTubeMusicClient.getAuthHeaders()
            val cookies = authHeaders["Cookie"] ?: ""
            val authorization = authHeaders["Authorization"] ?: ""
            android.util.Log.d("SearchRepository", "Auth state: isLoggedIn=${YouTubeMusicClient.isLoggedIn()}, hasCookies=${cookies.isNotEmpty()}, hasAuth=${authorization.isNotEmpty()}")
            if (cookies.isNotEmpty()) {
                android.util.Log.d("SearchRepository", "Cookie preview: ${cookies.take(50)}...")
            }
            
            val request = okhttp3.Request.Builder()
                .url(apiUrl)
                .post(requestBody.toRequestBody("application/json".toMediaTypeOrNull()))
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:88.0) Gecko/20100101 Firefox/88.0")
                .addHeader("Content-Type", "application/json")
                .addHeader("X-Goog-AuthUser", "0")
                .addHeader("Origin", "https://music.youtube.com")
                .apply {
                    if (cookies.isNotEmpty()) {
                        addHeader("Cookie", cookies)
                    }
                    if (authorization.isNotEmpty()) {
                        addHeader("Authorization", authorization)
                    }
                }
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            
            if (!response.isSuccessful) {
                android.util.Log.e("SearchRepository", "YTM Album API error: ${response.code} for browseId: $browseId")
                android.util.Log.e("SearchRepository", "Response: $responseBody")
            } else if (responseBody.contains("error")) {
                 android.util.Log.e("SearchRepository", "YTM Album API returned error in body: $responseBody")
            }
            
            return responseBody
            
        } catch (e: Exception) {
            android.util.Log.e("SearchRepository", "Error fetching YTM album", e)
            return ""
        }
    }
    
    private fun parseYouTubeMusicAlbumResponse(response: String): List<Song> {
        val songs = mutableListOf<Song>()
        
        try {
            val json = org.json.JSONObject(response)
            
            // 1. Find Header (recursively)
            var albumName = "Unknown Album"
            var albumArtist = "Unknown Artist"
            var albumThumbnail: String? = null
            
            val header = findJSONObject(json, "musicDetailHeaderRenderer") ?: findJSONObject(json, "musicResponsiveHeaderRenderer")
            
            if (header != null) {
                // Title
                albumName = header.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: albumName
                
                // Artist - Check straplineTextOne FIRST (where artists usually are for albums)
                val straplineRuns = header.optJSONObject("straplineTextOne")?.optJSONArray("runs")
                val subtitleRuns = header.optJSONObject("subtitle")?.optJSONArray("runs")
                
                // Priority 1: straplineTextOne with UC endpoint
                if (straplineRuns != null) {
                    for (i in 0 until straplineRuns.length()) {
                        val run = straplineRuns.optJSONObject(i)
                        val browseId = run?.optJSONObject("navigationEndpoint")
                            ?.optJSONObject("browseEndpoint")
                            ?.optString("browseId", "") ?: ""
                        if (browseId.startsWith("UC")) {
                            albumArtist = run?.optString("text") ?: albumArtist
                            android.util.Log.d("AlbumParse", "Found artist in strapline: $albumArtist")
                            break
                        }
                    }
                }
                
                // Priority 2: subtitle with UC endpoint
                if (albumArtist == "Unknown Artist" && subtitleRuns != null) {
                    for (i in 0 until subtitleRuns.length()) {
                        val run = subtitleRuns.optJSONObject(i)
                        val browseId = run?.optJSONObject("navigationEndpoint")
                            ?.optJSONObject("browseEndpoint")
                            ?.optString("browseId", "") ?: ""
                        if (browseId.startsWith("UC")) {
                            albumArtist = run?.optString("text") ?: albumArtist
                            android.util.Log.d("AlbumParse", "Found artist in subtitle: $albumArtist")
                            break
                        }
                    }
                }
                
                // Fallback: First non-type text from strapline or subtitle
                if (albumArtist == "Unknown Artist") {
                    val allRuns = (straplineRuns ?: subtitleRuns)
                    if (allRuns != null) {
                        for (i in 0 until allRuns.length()) {
                            val text = allRuns.optJSONObject(i)?.optString("text") ?: ""
                            if (text.isNotBlank() && 
                                !text.matches(Regex("^\\d{4}$")) &&
                                !text.contains("Album", ignoreCase = true) && 
                                !text.contains("Álbum", ignoreCase = true) &&
                                !text.contains("Single", ignoreCase = true) && 
                                !text.contains("Sencillo", ignoreCase = true) &&
                                !text.contains("EP", ignoreCase = true) &&
                                text != " • ") {
                                albumArtist = text
                                android.util.Log.d("AlbumParse", "Found artist via fallback: $albumArtist")
                                break
                            }
                        }
                    }
                }
                
                // Thumbnail
                val thumbnails = header.optJSONObject("thumbnail")?.optJSONObject("croppedSquareThumbnailRenderer")
                    ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails") 
                    ?: header.optJSONObject("thumbnail")?.optJSONObject("musicThumbnailRenderer")
                    ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                
                if (thumbnails != null && thumbnails.length() > 0) {
                    // Get the last thumbnail (usually highest res)
                    var url = thumbnails.optJSONObject(thumbnails.length() - 1)?.optString("url")
                    // Force high resolution by replacing dimensions
                    if (url != null) {
                        // Replace any size param like =w120-h120, =s540, =w540-h540, etc. with =s1200 (max res square)
                        url = url.replace(Regex("=[whs]\\d+(-[wh]\\d+)*"), "=w540-h540")
                        // Also handle cases where it might be /s120/ path segment
                        url = url.replace(Regex("/[whs]\\d+(-[wh]\\d+)*/"), "/s540/")
                        albumThumbnail = url
                    }
                }
            }
            
            android.util.Log.d("SearchRepository", "Parsed album: $albumName by $albumArtist")
            
            // 2. Find Tracks (recursively find all musicResponsiveListItemRenderer)
            val trackItems = findAllJSONObjects(json, "musicResponsiveListItemRenderer")
            
            for (item in trackItems) {
                // Check if it's a song (has videoId)
                val playlistItemData = item.optJSONObject("playlistItemData")
                val videoId = playlistItemData?.optString("videoId")
                
                // Some items might be headers or other things, ensure it has a videoId
                if (videoId != null && videoId.isNotEmpty()) {
                    val flexColumns = item.optJSONArray("flexColumns")
                    if (flexColumns != null && flexColumns.length() > 0) {
                        // Title from first column
                        val title = flexColumns.optJSONObject(0)
                            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                            ?.optJSONObject("text")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                        
                        if (title != null) {
                            // Duration
                            val duration = item.optJSONArray("fixedColumns")?.optJSONObject(0)
                                ?.optJSONObject("musicResponsiveListItemFixedColumnRenderer")
                                ?.optJSONObject("text")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                            val durationMs = parseDuration(duration ?: "0:00")
                            
                            // Artist for this track (might differ from album artist)
                            var trackArtist = albumArtist
                            if (flexColumns.length() > 1) {
                                val secondaryText = flexColumns.optJSONObject(1)
                                    ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                                    ?.optJSONObject("text")?.optJSONArray("runs")
                                
                                if (secondaryText != null) {
                                    var foundTrackArtist = false
                                    // 1. Try endpoint
                                    for (k in 0 until secondaryText.length()) {
                                        val run = secondaryText.optJSONObject(k)
                                        val endpoint = run?.optJSONObject("navigationEndpoint")
                                        if (endpoint?.optJSONObject("browseEndpoint")?.optString("browseId")?.startsWith("UC") == true) {
                                            trackArtist = run.optString("text")
                                            foundTrackArtist = true
                                            break
                                        }
                                    }
                                    // 2. Fallback text scan if different from album artist
                                    if (!foundTrackArtist) {
                                         for (k in 0 until secondaryText.length()) {
                                            val text = secondaryText.optJSONObject(k)?.optString("text") ?: ""
                                            if (text.isNotBlank() && text != " • " && !text.matches(Regex("^\\d{4}$")) && !text.contains(":")) {
                                                // If it's not the album artist, maybe it's a feature?
                                                // Actually, usually secondary text in album view is JUST artist.
                                                // If we didn't find a link, carefully accept text avoiding Year
                                                if (text != albumArtist && text != albumName) {
                                                     trackArtist = text
                                                     break
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Extract per-song thumbnail (important for playlists like "Liked Music")
                            var songThumbnail = albumThumbnail
                            val thumbnailRenderer = item.optJSONObject("thumbnail")
                                ?.optJSONObject("musicThumbnailRenderer")
                                ?.optJSONObject("thumbnail")
                                ?.optJSONArray("thumbnails")
                            
                            if (thumbnailRenderer != null && thumbnailRenderer.length() > 0) {
                                var thumbUrl = thumbnailRenderer.optJSONObject(thumbnailRenderer.length() - 1)?.optString("url")
                                if (thumbUrl != null && thumbUrl.isNotEmpty()) {
                                    // Force high resolution
                                    thumbUrl = thumbUrl.replace(Regex("=[whs]\\d+(-[wh]\\d+)*"), "=w540-h540")
                                    thumbUrl = thumbUrl.replace(Regex("/[whs]\\d+(-[wh]\\d+)*/"), "/s540/")
                                    songThumbnail = thumbUrl
                                }
                            }
                            
                            // Fallback: construct from videoId if no thumbnail found
                            if (songThumbnail == null || songThumbnail.isEmpty()) {
                                songThumbnail = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                            }

                            songs.add(Song(
                                id = videoId.hashCode().toLong(),
                                title = title,
                                artist = trackArtist,
                                album = albumName,
                                duration = durationMs,
                                albumArtUri = android.net.Uri.parse(songThumbnail),
                                path = "https://music.youtube.com/watch?v=$videoId",
                                dateAdded = System.currentTimeMillis(),
                                albumId = 0
                            ))
                        }
                    }
                }
            }
            
            android.util.Log.d("SearchRepository", "Parsed ${songs.size} tracks from album")
            
            // Fail-safe for "Moods" / Categories
            // If we found NO songs, but we see a grid of items (playlists), this is likely a Category page.
            if (songs.isEmpty()) {
                val gridItems = findAllJSONObjects(json, "gridRenderer")
                if (gridItems.isNotEmpty()) {
                    android.util.Log.d("SearchRepository", "No songs found but Grid detected. Trying to fetch first playlist from category.")
                    
                    // Try to find the first playlist browse endpoint
                    var firstPlaylistBrowseId: String? = null
                    
                    // Search in the grid items
                    val items = gridItems[0].optJSONArray("items")
                    if (items != null && items.length() > 0) {
                        for (i in 0 until items.length()) {
                            val item = items.optJSONObject(i)
                            // Look for musicTwoRowItemRenderer (Playlist card)
                            val renderer = item.optJSONObject("musicTwoRowItemRenderer") 
                                ?: item.optJSONObject("musicNavigationButtonRenderer") // Sometimes simpler buttons
                            
                            val endpoint = renderer?.optJSONObject("navigationEndpoint") 
                                ?: renderer?.optJSONObject("clickCommand")
                                
                            val browseId = endpoint?.optJSONObject("browseEndpoint")?.optString("browseId")
                            
                            if (!browseId.isNullOrEmpty() && (browseId.startsWith("VL") || browseId.startsWith("PL") || browseId.startsWith("MPRE"))) {
                                firstPlaylistBrowseId = browseId
                                break
                            }
                        }
                    }
                    
                    if (firstPlaylistBrowseId != null) {
                        android.util.Log.d("SearchRepository", "Auto-redirecting to first playlist: $firstPlaylistBrowseId")
                        // Recursive call to fetch the playlist content
                        return parseYouTubeMusicAlbumResponse(
                            fetchYouTubeMusicAlbumRawJson(firstPlaylistBrowseId)
                        )
                    }
                }
            }
            
        } catch (e: Exception) {
            android.util.Log.e("SearchRepository", "Error parsing YTM album response", e)
        }
        
        return songs
    }
    
    // Helper to recursively find a JSONObject by key
    private fun findJSONObject(json: org.json.JSONObject, key: String): org.json.JSONObject? {
        if (json.has(key)) return json.optJSONObject(key)
        
        val keys = json.keys()
        while (keys.hasNext()) {
            val nextKey = keys.next()
            val value = json.opt(nextKey)
            
            if (value is org.json.JSONObject) {
                val found = findJSONObject(value, key)
                if (found != null) return found
            } else if (value is org.json.JSONArray) {
                for (i in 0 until value.length()) {
                    val item = value.optJSONObject(i)
                    if (item != null) {
                        val found = findJSONObject(item, key)
                        if (found != null) return found
                    }
                }
            }
        }
        return null
    }

    // Helper to recursively find ALL JSONObjects by key
    private fun findAllJSONObjects(json: org.json.JSONObject, key: String): List<org.json.JSONObject> {
        val results = mutableListOf<org.json.JSONObject>()
        
        if (json.has(key)) {
            val obj = json.optJSONObject(key)
            if (obj != null) results.add(obj)
        }
        
        val keys = json.keys()
        while (keys.hasNext()) {
            val nextKey = keys.next()
            val value = json.opt(nextKey)
            
            if (value is org.json.JSONObject) {
                results.addAll(findAllJSONObjects(value, key))
            } else if (value is org.json.JSONArray) {
                for (i in 0 until value.length()) {
                    val item = value.optJSONObject(i)
                    if (item != null) {
                        results.addAll(findAllJSONObjects(item, key))
                    }
                }
            }
        }
        return results
    }
    
    private fun parseDuration(durationStr: String): Long {
        try {
            val parts = durationStr.split(":")
            return when (parts.size) {
                2 -> (parts[0].toInt() * 60 + parts[1].toInt()) * 1000L
                3 -> (parts[0].toInt() * 3600 + parts[1].toInt() * 60 + parts[2].toInt()) * 1000L
                else -> 0L
            }
        } catch (e: Exception) {
            return 0L
        }
    }
    
    // Implementation of search suggestions
    override suspend fun getSearchSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        try {
            SearchSuggestions.getSuggestions(query)
        } catch (e: Exception) {
            emptyList()
        }
    }



    override suspend fun getTrending(): List<Song> = withContext(Dispatchers.IO) {
        // Legacy: keep for now or redirect
        searchSongs("Global Top 50 Released")
    }

    // Continuation token for Home infinite scroll
    private var lastHomeContinuation: String? = null

    override suspend fun getOnlineHomeSections(): List<HomeSection> = withContext(Dispatchers.IO) {
        // Reset continuation on fresh load
        lastHomeContinuation = null
        
        try {
            // Initial load from FEmusic_home
            val (sections, continuation) = fetchHomeContent("FEmusic_home")
            lastHomeContinuation = continuation
            
            // If we have very few sections, maybe auto-load next page?
            // For now, just return what we have (usually 3-4 sections + quick picks)
            if (sections.isEmpty()) {
                 android.util.Log.w("SearchRepository", "Home returned empty sections")
            }
            sections
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    override suspend fun getContinueHome(continuation: String): Result<List<HomeSection>> = withContext(Dispatchers.IO) {
        try {
            // Use provided continuation or fallback to last known (though ViewModel should drive this)
            val token = if (continuation.isNotEmpty()) continuation else lastHomeContinuation
            
            if (token.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("No continuation token available"))
            }
            
            val (sections, newContinuation) = fetchHomeContent(browseId = null, params = null, continuation = token)
            lastHomeContinuation = newContinuation
            
            Result.success(sections)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Unified fetcher for Home content (Initial or Continuation)
     * Returns Pair<List<HomeSection>, NextContinuationToken?>
     */
    private fun fetchHomeContent(browseId: String? = null, params: String? = null, continuation: String? = null): Pair<List<HomeSection>, String?> {
        try {
             val apiUrl = "${YouTubeMusicClient.BASE_URL}browse?key=${YouTubeMusicClient.API_KEY}&prettyPrint=false"
             val locale = YouTubeMusicClient.getLocale()

             val requestBody = if (continuation != null) {
                 // Continuation Request
                 """
                 {
                     "context": {
                         "client": {
                             "clientName": "${YouTubeMusicClient.CLIENT_NAME}",
                             "clientVersion": "${YouTubeMusicClient.CLIENT_VERSION}",
                             "hl": "${locale.hl}",
                             "gl": "${locale.gl}",
                             "utcOffsetMinutes": ${YouTubeMusicClient.getUtcOffsetMinutes()},
                             "visitorData": "${YouTubeMusicClient.visitorData}"
                         }
                     },
                     "continuation": "$continuation"
                 }
                 """.trimIndent()
             } else {
                 // Initial Browse Request
                 """
                 {
                     "context": {
                         "client": {
                             "clientName": "${YouTubeMusicClient.CLIENT_NAME}",
                             "clientVersion": "${YouTubeMusicClient.CLIENT_VERSION}",
                             "hl": "${locale.hl}",
                             "gl": "${locale.gl}",
                             "utcOffsetMinutes": ${YouTubeMusicClient.getUtcOffsetMinutes()},
                             "visitorData": "${YouTubeMusicClient.visitorData}"
                         }
                     },
                     "browseId": "$browseId"
                     ${if (params != null) ", \"params\": \"$params\"" else ""}
                 }
                 """.trimIndent()
             }

             val mediaType = "application/json; charset=utf-8".toMediaType()
             val body = requestBody.toRequestBody(mediaType)
             
             val requestBuilder = okhttp3.Request.Builder()
                .url(apiUrl)
                .post(body)
                .header("Content-Type", "application/json")
                .header("User-Agent", YouTubeMusicClient.USER_AGENT)
                .header("X-Goog-Api-Format-Version", "1")
                .header("X-YouTube-Client-Name", YouTubeMusicClient.INNER_TUBE_NAME.toString())
                .header("X-YouTube-Client-Version", YouTubeMusicClient.CLIENT_VERSION)
                .header("X-Goog-Visitor-Id", YouTubeMusicClient.visitorData)
            
             // Add auth headers
             val authHeaders = YouTubeMusicClient.getAuthHeaders()
             authHeaders.forEach { (key, value) -> requestBuilder.header(key, value) }
            
             val request = requestBuilder.build()
             val client = com.amurayada.music.data.newpipe.NewPipeDownloader.getClient()
             val response = client.newCall(request).execute()
             
             if (!response.isSuccessful) return Pair(emptyList(), null)
             
             val responseString = response.body?.string() ?: ""
             return parseHomeResponseRobust(responseString)

        } catch (e: Exception) {
            android.util.Log.e("SearchRepository", "Error fetching Home", e)
            return Pair(emptyList(), null)
        }
    }

    /**
     * Robust Parser adapted from SimpMusic logic
     */
    private fun parseHomeResponseRobust(response: String): Pair<List<HomeSection>, String?> {
        val sections = mutableListOf<HomeSection>()
        var continuationToken: String? = null
        
        try {
            val json = org.json.JSONObject(response)
            
             // Update Visitor Data
            val newVisitorData = json.optJSONObject("responseContext")?.optString("visitorData")
            if (!newVisitorData.isNullOrEmpty()) {
                YouTubeMusicClient.visitorData = newVisitorData
            }

            // Locate Section List
            var sectionListRenderer = json.optJSONObject("contents")
                ?.optJSONObject("singleColumnBrowseResultsRenderer")
                ?.optJSONArray("tabs")?.optJSONObject(0)
                ?.optJSONObject("tabRenderer")?.optJSONObject("content")
                ?.optJSONObject("sectionListRenderer")
                
            // If checking continuation response
            if (sectionListRenderer == null) {
                sectionListRenderer = json.optJSONObject("continuationContents")
                    ?.optJSONObject("sectionListContinuation")
            }
            
            if (sectionListRenderer != null) {
                // Extract Continuations
                val continuations = sectionListRenderer.optJSONArray("continuations")
                if (continuations != null && continuations.length() > 0) {
                    continuationToken = continuations.optJSONObject(0)
                        ?.optJSONObject("nextContinuationData")?.optString("continuation")
                }
                
                // Parse Sections
                val contents = sectionListRenderer.optJSONArray("contents")
                if (contents != null) {
                    for (i in 0 until contents.length()) {
                        val item = contents.optJSONObject(i)
                        
                        // 1. Music Carousel Shelf (Horizontal Scroll)
                        val carousel = item.optJSONObject("musicCarouselShelfRenderer") 
                            ?: item.optJSONObject("musicImmersiveCarouselShelfRenderer")
                        
                        if (carousel != null) {
                            val section = parseCarouselShelf(carousel)
                            if (section != null) sections.add(section)
                        }
                        
                        // 2. Music Grid (Quick Picks often appears here)
                        val grid = item.optJSONObject("gridRenderer")
                        if (grid != null) {
                             val section = parseGridShelf(grid)
                             if (section != null) sections.add(section)
                        }
                        
                        // 2. Music Description Shelf (Text blocks, e.g. "Welcome")
                        // Ignored for now as we want playable content
                        
                        // 3. Music Playlist Shelf (Vertical lists often found in Charts)
                         // SimpMusic treats these as large items. We might skip or flatten.
                    }
                }
            }
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        return Pair(sections, continuationToken)
    }




    private fun parseGridShelf(renderer: org.json.JSONObject): HomeSection? {
        try {
            val header = renderer.optJSONObject("header")?.optJSONObject("gridHeaderRenderer")
            val title = header?.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: ""
            
            // Moods Categories might not have a header title in the renderer itself if it's the only thing?
            // Usually they do.
            
            val items = renderer.optJSONArray("items") ?: return null
            val albums = mutableListOf<Album>()
            
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i)
                val musicTwoRowItemRenderer = item?.optJSONObject("musicTwoRowItemRenderer")
                
                if (musicTwoRowItemRenderer != null) {
                    // Start: Optimized Check for Navigation Endpoint (Categories vs Albums)
                    // Categories navigate to 'browseEndpoint' with 'params'.
                    // We treat them as Albums for now (they will open as AlbumDetailOnline -> which processes URL).
                    // If URL is category, getAlbumDetails might fail or return songs.
                    // Ideally we should have Category Card.
                    // But using Album Card is fine for now.
                    
                    val album = parseMusicTwoRowItem(musicTwoRowItemRenderer, "YouTube Music")
                    if (album != null) {
                        albums.add(album)
                    }
                }
                // Handle musicNavigationButtonRenderer (often in Moods)
                val navButton = item?.optJSONObject("musicNavigationButtonRenderer")
                if (navButton != null) {
                    val buttonText = navButton.optJSONObject("buttonText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: ""
                     // This is heavily mood specific. 
                     // Color? Solid color usually.
                     // Making a fake album for it is hacky but consistent with UI.
                     val browseId = navButton.optJSONObject("clickCommand")?.optJSONObject("browseEndpoint")?.optString("browseId", "") ?: ""
                     val params = navButton.optJSONObject("clickCommand")?.optJSONObject("browseEndpoint")?.optString("params", "") ?: ""
                     
                     // Construct a special URL to trigger Mood fetch if clicked?
                     // Or just ignore "Moods" buttons if we can't render them separately.
                     // User asked for "Moods".
                     // Extract thumbnail if available
                     var categoryArtwork: String? = null
                     
                     // Try to get thumbnail from buttonText runs or from nested thumbnail
                     val thumbnailsArray = navButton.optJSONObject("thumbnail")
                         ?.optJSONObject("musicThumbnailRenderer")
                         ?.optJSONObject("thumbnail")
                         ?.optJSONArray("thumbnails")
                     
                     if (thumbnailsArray != null && thumbnailsArray.length() > 0) {
                         var url = thumbnailsArray.optJSONObject(thumbnailsArray.length() - 1)?.optString("url")
                         if (url != null && url.isNotEmpty()) {
                             // Force high resolution
                             url = url.replace(Regex("=[whs]\\d+(-[wh]\\d+)*"), "=w540-h540")
                             categoryArtwork = url
                         }
                     }
                     
                     // Categories use placeholder if no thumbnail (Moods removed from Home)
                     
                     if (buttonText.isNotEmpty() && browseId.isNotEmpty()) {
                          albums.add(Album(
                              id = browseId.hashCode().toLong(),
                              name = buttonText,
                              artist = "Category",
                              artworkUri = if (categoryArtwork != null) android.net.Uri.parse(categoryArtwork) else null,
                              path = "https://music.youtube.com/browse/$browseId" + (if(params.isNotEmpty()) "?params=$params" else "")
                          ))
                     }
                }
            }
            
            if (albums.isEmpty()) return null
            
            // If title is empty, maybe use "Categorías"
            val finalTitle = if (title.isEmpty()) "Categorías" else title
            
            return HomeSection(finalTitle, emptyList(), albums, emptyList())
            
        } catch (e: Exception) {
            return null
        }
    }

    private fun parseCarouselShelf(renderer: org.json.JSONObject): HomeSection? {
        try {
            // Get Header Title
            val header = renderer.optJSONObject("header")?.optJSONObject("musicCarouselShelfBasicHeaderRenderer") 
                        ?: renderer.optJSONObject("header")?.optJSONObject("musicImmersiveHeaderRenderer") // Fallback for immersive
            
            val title = header?.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") 
                ?: renderer.optJSONObject("header")?.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                ?: ""
            
            if (title.isEmpty()) return null

            val contents = renderer.optJSONArray("contents") ?: return null
            
            val songs = mutableListOf<Song>()
            val albums = mutableListOf<Album>()
            val artists = mutableListOf<Artist>()
            
            for (i in 0 until contents.length()) {
                val item = contents.optJSONObject(i)
                
                // Songs / Videos / Artists (in list format)
                val listItemRenderer = item?.optJSONObject("musicResponsiveListItemRenderer")
                if (listItemRenderer != null) {
                    // Check if it's an artist by navigation endpoint
                    val navEndpoint = listItemRenderer.optJSONObject("navigationEndpoint")
                    val browseId = navEndpoint?.optJSONObject("browseEndpoint")?.optString("browseId", "") ?: ""
                    
                    if (browseId.startsWith("UC")) {
                        // It's an artist!
                        // We need to parse artist from list item renderer
                        // Reuse existing Song/Artist parsers or ad-hoc?
                        // Let's do ad-hoc extraction for Home Artist List Item
                        val name = listItemRenderer.optJSONArray("flexColumns")?.optJSONObject(0)
                            ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                            ?.optJSONObject("text")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: "Unknown"
                            
                        val thumb = listItemRenderer.optJSONObject("thumbnail")?.optJSONObject("musicThumbnailRenderer")
                            ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                            ?.let { if (it.length() > 0) it.optJSONObject(it.length() - 1)?.optString("url") else null }
                            
                        artists.add(Artist(browseId.hashCode().toLong(), name, 0, 0, "https://music.youtube.com/channel/$browseId", thumb))
                    } else {
                        // It's a song/video
                        val song = parseMusicListItem(listItemRenderer, "Various Artists")
                        if (song != null) songs.add(song)
                    }
                }
                
                // Albums / Playlists / Artists (in card format)
                val twoRowRenderer = item?.optJSONObject("musicTwoRowItemRenderer")
                if (twoRowRenderer != null) {
                     val navEndpoint = twoRowRenderer.optJSONObject("navigationEndpoint")
                     val browseId = navEndpoint?.optJSONObject("browseEndpoint")?.optString("browseId", "") ?: ""
                     
                     if (browseId.startsWith("UC")) {
                         // It's an Artist Card
                         val title = twoRowRenderer.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: "Unknown"
                         val thumb = twoRowRenderer.optJSONObject("thumbnailRenderer")?.optJSONObject("musicThumbnailRenderer")
                             ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                             ?.let { if (it.length() > 0) it.optJSONObject(it.length() - 1)?.optString("url") else null }
                             
                         artists.add(Artist(browseId.hashCode().toLong(), title, 0, 0, "https://music.youtube.com/channel/$browseId", thumb))
                     } else {
                         // Album / Playlist
                         val album = parseMusicTwoRowItem(twoRowRenderer, "Unknown")
                         if (album != null) {
                             albums.add(album)
                         }
                     }
                }
            }
            
            if (songs.isEmpty() && albums.isEmpty() && artists.isEmpty()) return null
            
            return HomeSection(title, songs, albums, artists)
            
        } catch (e: Exception) {
            return null
        }
    }
    
    // Direct InnerTube Search Implementation using SimpMusic-style configuration
    private fun searchYtmDirect(query: String, params: String): List<Any> {
        try {
            val apiUrl = "${YouTubeMusicClient.BASE_URL}search?key=${YouTubeMusicClient.API_KEY}&prettyPrint=false"
            val locale = YouTubeMusicClient.getLocale()
            
            // Use YouTubeMusicClient configuration for proper headers
            val requestBody = """
                {
                    "context": {
                        "client": {
                            "clientName": "${YouTubeMusicClient.CLIENT_NAME}",
                            "clientVersion": "${YouTubeMusicClient.CLIENT_VERSION}",
                            "hl": "${locale.hl}",
                            "gl": "${locale.gl}",
                            "utcOffsetMinutes": ${YouTubeMusicClient.getUtcOffsetMinutes()},
                            "visitorData": "${YouTubeMusicClient.visitorData}"
                        },
                        "user": {
                            "enableSafetyMode": false,
                            "lockedSafetyMode": false
                        }
                    },
                    "racyCheckOk": true,
                    "contentCheckOk": true,
                    "query": "$query",
                    "params": "$params"
                }
            """.trimIndent()
            
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = requestBody.toRequestBody(mediaType)
            
            val request = okhttp3.Request.Builder()
                .url(apiUrl)
                .post(body)
                .header("Content-Type", "application/json")
                .header("User-Agent", YouTubeMusicClient.USER_AGENT)
                .header("Referer", YouTubeMusicClient.REFERER)
                .header("Origin", "https://music.youtube.com")
                .header("X-Goog-Api-Format-Version", "1")
                .header("X-YouTube-Client-Name", YouTubeMusicClient.INNER_TUBE_NAME.toString())
                .header("X-YouTube-Client-Version", YouTubeMusicClient.CLIENT_VERSION)
                .header("X-Goog-Visitor-Id", YouTubeMusicClient.visitorData)
                .header("x-origin", "https://music.youtube.com")
                .build()
            
            val client = com.amurayada.music.data.newpipe.NewPipeDownloader.getClient()
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                android.util.Log.e("SearchRepository", "YTM Direct API error: ${response.code}")
                response.close()
                return emptyList()
            }
            
            val responseString = response.body?.string() ?: ""
            return parseYtmSearchResponse(responseString)
            
        } catch (e: Exception) {
            android.util.Log.e("SearchRepository", "Error in direct YTM search", e)
            return emptyList()
        }
    }

    private fun parseYtmSearchResponse(jsonString: String): List<Any> {
        val results = mutableListOf<Any>()
        try {
            val json = org.json.JSONObject(jsonString)
            
            // Try different paths to find the sectionListRenderer
            var contents = json.optJSONObject("contents")
                ?.optJSONObject("tabbedSearchResultsRenderer")
                ?.optJSONArray("tabs")?.optJSONObject(0)
                ?.optJSONObject("tabRenderer")?.optJSONObject("content")
                ?.optJSONObject("sectionListRenderer")?.optJSONArray("contents")

            // Fallback for non-tabbed results (common in filtered searches)
            if (contents == null) {
                contents = json.optJSONObject("contents")
                    ?.optJSONObject("sectionListRenderer")?.optJSONArray("contents")
            }
            
            if (contents == null) {
                 // Try single column
                 contents = json.optJSONObject("contents")
                    ?.optJSONObject("singleColumnSearchResultsRenderer")
                    ?.optJSONObject("primaryContents")
                    ?.optJSONObject("sectionListRenderer")?.optJSONArray("contents")
            }

            android.util.Log.d("SearchDump", "JSON Response Length: ${jsonString.length}. Contents found: ${contents != null}")
            
            if (contents != null) {
                for (i in 0 until contents.length()) {
                    val shelf = contents.optJSONObject(i)?.optJSONObject("musicShelfRenderer")
                    val items = shelf?.optJSONArray("contents")
                    
                    if (items != null) {
                        for (j in 0 until items.length()) {
                            val item = items.optJSONObject(j)?.optJSONObject("musicResponsiveListItemRenderer")
                            if (item != null) {
                                val flexColumns = item.optJSONArray("flexColumns")
                                if (flexColumns == null || flexColumns.length() == 0) continue
                                
                                // Title
                                val title = flexColumns.optJSONObject(0)
                                    ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                                    ?.optJSONObject("text")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: continue

                                // Subtitle (Artist, Album, etc.)
                                val subtitleRuns = flexColumns.optJSONObject(1)
                                    ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                                    ?.optJSONObject("text")?.optJSONArray("runs")
                                
                                // Parse Duration from subtitle or fixed columns
                                // Duration usually comes in fixedColumns for songs
                                var durationMs = 0L
                                val fixedColumns = item.optJSONArray("fixedColumns")
                                if (fixedColumns != null && fixedColumns.length() > 0) {
                                    val text = fixedColumns.optJSONObject(0)
                                        ?.optJSONObject("musicResponsiveListItemFixedColumnRenderer")
                                        ?.optJSONObject("text")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
                                    if (text != null) {
                                        durationMs = parseDuration(text)
                                    }
                                }
                                
                                val subtitleParts = mutableListOf<String>()
                                var artistName = "Unknown"
                                var albumName = ""
                                var contentType = "unknown" // song, video, artist, album
                                
                                if (subtitleRuns != null) {
                                    for (k in 0 until subtitleRuns.length()) {
                                        val run = subtitleRuns.optJSONObject(k)
                                        val text = run?.optString("text") ?: ""
                                        
                                        if (text != " • " && text.isNotBlank()) {
                                            subtitleParts.add(text)
                                            // Also check subtitle for duration if not found in fixed columns
                                            if (durationMs == 0L && text.contains(":")) {
                                                durationMs = parseDuration(text)
                                            }
                                            
                                            // SMART PARSING: Check endpoints to identify Artist vs Album
                                            val browseEndpoint = run?.optJSONObject("navigationEndpoint")
                                                ?.optJSONObject("browseEndpoint")
                                            val browseId = browseEndpoint?.optString("browseId", "") ?: ""
                                            
                                            if (browseId.isNotEmpty()) {
                                                when {
                                                    browseId.startsWith("UC") || browseId.startsWith("F_") -> {
                                                        artistName = text
                                                    }
                                                    browseId.startsWith("MPRE") || browseId.startsWith("OLAK") -> {
                                                        albumName = text
                                                    }
                                                }
                                            } else if (artistName == "Unknown" && !text.contains(":") && text != "Song" && text != "Canción") {
                                                // If no browseId, but it's the first non-metadata part, assume it's artist
                                                val isMetadata = text.equals("Song", true) || text.equals("Canción", true) ||
                                                                text.equals("Video", true) || text.equals("Vídeo", true) ||
                                                                text.contains("views", true) || text.contains("vistas", true)
                                                if (!isMetadata && artistName == "Unknown") {
                                                    artistName = text
                                                }
                                            }
                                        }
                                    }
                                }
    
                                    // Detect content type from subtitle parts
                                    val lowerParts = subtitleParts.map { it.lowercase() }
                                    when {
                                        lowerParts.any { it == "song" || it == "canción" } -> contentType = "song"
                                        lowerParts.any { it == "video" || it == "vídeo" } -> contentType = "video"
                                        lowerParts.any { it == "artist" || it == "artista" } -> contentType = "artist"
                                        lowerParts.any { it == "album" || it == "álbum" || it == "ep" || it == "single" } -> contentType = "album"
                                        lowerParts.any { it == "playlist" || it == "lista" || it == "lista de reproducción" } -> contentType = "playlist"
                                        // Heuristic: If it mentions "views", "vistas", or "visualizaciones", it's likely a video/UGC/Interview
                                        lowerParts.any { it.contains("views") || it.contains("vistas") || it.contains("visualizaciones") } -> contentType = "video"
                                    }
                                
                                    // Identification by Navigation ID (More reliable)
                                    val ids = item.optJSONObject("navigationEndpoint")
                                        ?.optJSONObject("browseEndpoint")?.optString("browseId")
                                    val watchId = item.optJSONObject("playlistItemData")?.optString("videoId")
                                    
                                    if (ids != null) {
                                        if (ids.startsWith("UC")) contentType = "artist"
                                        else if (ids.startsWith("MPRE") || ids.startsWith("OLAK")) contentType = "album"
                                        else if (ids.startsWith("VL") || ids.startsWith("PL")) contentType = "playlist"
                                    }

                                    // Thumbnail
                                    val thumb = item.optJSONObject("thumbnail")?.optJSONObject("musicThumbnailRenderer")
                                        ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                                        ?.let { if (it.length() > 0) it.optJSONObject(it.length() - 1)?.optString("url") else null }
                                    val hqThumb = forceHighResThumbnail(thumb)

                                    when (contentType) {
                                        "song" -> {
                                           if (watchId != null && watchId.isNotEmpty()) {
                                               results.add(Song(watchId.hashCode().toLong(), title, artistName, albumName, durationMs, if (hqThumb.isNotEmpty()) android.net.Uri.parse(hqThumb) else null, "https://music.youtube.com/watch?v=$watchId", System.currentTimeMillis(), 0))
                                           }
                                        }
                                        "video" -> {
                                           if (watchId != null && watchId.isNotEmpty()) {
                                               // Treat videos as songs
                                               results.add(Song(watchId.hashCode().toLong(), title, artistName, albumName, durationMs, if (hqThumb.isNotEmpty()) android.net.Uri.parse(hqThumb) else null, "https://music.youtube.com/watch?v=$watchId", System.currentTimeMillis(), 0))
                                           }
                                        }
                                        "artist" -> {
                                             val id = ids ?: item.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId")
                                             if (id != null) {
                                                 // Correct order: path (channel URL), then imageUrl (thumbnail)
                                                 results.add(Artist(id.hashCode().toLong(), title, 0, 0, "https://music.youtube.com/channel/$id", hqThumb))
                                             }
                                        }
                                        "album" -> {
                                            val id = ids ?: item.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId")
                                            if (id != null) {
                                                // Try to reliably get artist name. If we found one in subtitle, use it.
                                                // Subtitle for albums is usually "Album • Artist • Year" or just "Artist"
                                                val finalArtist = if (artistName != "Unknown") artistName else subtitleParts.firstOrNull { it != "Album" && it != "EP" && it != "Single" && !it.matches(Regex("\\d{4}")) } ?: "Unknown"
                                                
                                                results.add(Album(id.hashCode().toLong(), title, finalArtist, if (hqThumb.isNotEmpty()) android.net.Uri.parse(hqThumb) else null, 0, 0, "https://music.youtube.com/browse/$id"))
                                            }
                                        }
                                        "playlist" -> {
                                            val id = ids ?: item.optJSONObject("navigationEndpoint")?.optJSONObject("browseEndpoint")?.optString("browseId")
                                            if (id != null) {
                                                // Parse song count if available (e.g. "50 songs")
                                                val countPart = subtitleParts.find { it.contains("songs") || it.contains("canciones") }
                                                val count = countPart?.filter { it.isDigit() }?.toIntOrNull() ?: 0
                                                val author = if (artistName != "Unknown") artistName else subtitleParts.firstOrNull { !it.contains("song") && !it.contains("cancion") && !it.contains("playlist") } 

                                                results.add(com.amurayada.music.data.model.Playlist(
                                                    id = id.hashCode().toLong(),
                                                    name = title,
                                                    songIds = emptyList(), // No IDs yet
                                                    remoteId = id,
                                                    artworkUri = hqThumb,
                                                    songCount = count,
                                                    author = author
                                                ))
                                            }
                                        }
                                    }
                                    
                                    // Fallback: Parse artist and album from parts if not found via endpoints
                                    // Filter out types, duration, and YEARS to avoid metadata swaps
                                    val filteredParts = subtitleParts.filter { part ->
                                        val lower = part.lowercase()
                                        lower != "song" && lower != "canción" && lower != "video" && lower != "vídeo" &&
                                        lower != "artist" && lower != "artista" && lower != "album" && lower != "álbum" &&
                                        lower != "ep" && lower != "single" &&
                                        !part.contains(":") && // Remove duration
                                        !part.matches(Regex("^\\d{4}$")) && // Remove Year (Critical Fix)
                                        !part.contains("views") && !part.contains("vistas") // Remove views
                                    }
                                    
                                    if (artistName == "Unknown" && filteredParts.isNotEmpty()) {
                                        // Taking first valid part as artist (avoid album if possible)
                                        artistName = filteredParts.firstOrNull { it != albumName && it.isNotBlank() } 
                                            ?: filteredParts[0]
                                    }
                                    if (albumName.isEmpty() && filteredParts.size > 1) {
                                        // Logic: If artistName was found via endpoint, check if filteredParts has it.
                                        // If filteredParts[0] == artistName, then album is [1].
                                        // If filteredParts[0] != artistName, it's ambiguous, but usually [0] is artist.
                                        
                                        if (filteredParts[0] != artistName) {
                                            // Weird case. Respect endpoint-found artist. 
                                            // Assume [0] might be album if artist was extracted but not present here (impossible)
                                            // Just assign [1] to album if [0] is artist
                                            albumName = filteredParts[0] 
                                        } else {
                                            albumName = filteredParts[1]
                                        }
                                    }
                                    
                                    // ID - Check multiple locations for videoId
                                val playlistItemData = item.optJSONObject("playlistItemData")
                                var videoId = playlistItemData?.optString("videoId")
                                
                                // Fallback 1: watchEndpoint in overlay (common for songs)
                                if (videoId.isNullOrEmpty()) {
                                    videoId = item.optJSONObject("overlay")
                                        ?.optJSONObject("musicItemThumbnailOverlayRenderer")
                                        ?.optJSONObject("content")
                                        ?.optJSONObject("musicPlayButtonRenderer")
                                        ?.optJSONObject("playNavigationEndpoint")
                                        ?.optJSONObject("watchEndpoint")
                                        ?.optString("videoId")
                                }
                                
                                // Fallback 2: Direct navigationEndpoint with watchEndpoint
                                if (videoId.isNullOrEmpty()) {
                                    videoId = item.optJSONObject("navigationEndpoint")
                                        ?.optJSONObject("watchEndpoint")
                                        ?.optString("videoId")
                                }
                                
                                val navigationEndpoint = item.optJSONObject("navigationEndpoint")
                                val browseId = navigationEndpoint?.optJSONObject("browseEndpoint")?.optString("browseId")
                                
                                // Thumbnail
                                val thumbnails = item.optJSONObject("thumbnail")?.optJSONObject("musicThumbnailRenderer")
                                    ?.optJSONObject("thumbnail")?.optJSONArray("thumbnails")
                                
                                var thumbnail = ""
                                if (thumbnails != null && thumbnails.length() > 0) {
                                    thumbnail = thumbnails.optJSONObject(thumbnails.length() - 1)?.optString("url") ?: ""
                                    thumbnail = forceHighResThumbnail(thumbnail)
                                }

                                if (videoId != null && videoId.isNotEmpty()) {
                                    // FILTER VIDEOS: Relaxed Logic
                                    // Previously we skipped all videos, but this hides results for queries like "Live" or specific versions
                                    // Now we only skip if duration is > 20 mins (Interviews)
                                    // But try to prioritize SONGS if possible.
                                    // The main offender for "junk" is interviews.
                                    
                                    // DURATION FILTER: Discard items > 20 minutes (1,200,000ms)
                                    // This kills 90-minute interviews/concerts
                                    val MAX_SONG_DURATION_MS = 20 * 60 * 1000L
                                    if (durationMs > MAX_SONG_DURATION_MS) {
                                        android.util.Log.d("SearchParse", "Skipping LONG ITEM ($durationMs ms): $title")
                                        continue
                                    }
                                    
                                    results.add(Song(
                                        id = videoId.hashCode().toLong(),
                                        title = title,
                                        artist = artistName,
                                        album = if (albumName.isNotEmpty()) albumName else "YouTube Music",
                                        duration = durationMs,
                                        albumArtUri = if (thumbnail.isNotEmpty()) Uri.parse(thumbnail) else null,
                                        path = "https://music.youtube.com/watch?v=$videoId",
                                        dateAdded = System.currentTimeMillis(),
                                        albumId = 0
                                    ))
                                } else if (browseId != null) {
                                    // Artist detection - STRICT
                                    // Must explicitly say "Artist" OR have "Subscribers" count
                                    // Just starting with "UC" is not enough (that includes random user channels)
                                    val isVerifiedArtist = contentType == "artist" || 
                                           subtitleParts.any { it.lowercase().contains("subscribers") || it.lowercase().contains("suscriptores") }

                                    if (isVerifiedArtist) {
                                        results.add(Artist(
                                            id = browseId.hashCode().toLong(),
                                            name = title,
                                            albumCount = 0,
                                            songCount = 0,
                                            path = "https://music.youtube.com/channel/$browseId",
                                            imageUrl = if (thumbnail.isNotEmpty()) thumbnail else null
                                        ))
                                    } else if (browseId.startsWith("MPREb_") || browseId.startsWith("OLAK5uy_") || contentType == "album") {
                                        // Album
                                        results.add(Album(
                                            id = browseId.hashCode().toLong(),
                                            name = title,
                                            artist = artistName,
                                            artworkUri = if (thumbnail.isNotEmpty()) android.net.Uri.parse(thumbnail) else null,
                                            year = 0,
                                            songCount = 0,
                                            path = "https://music.youtube.com/browse/$browseId"
                                        ))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return results
    }

    override suspend fun getUserPlaylists(): List<com.amurayada.music.data.model.Playlist> = withContext(Dispatchers.IO) {
        // Browse ID for "Your Likes" playlists is FEmusic_liked_playlists
        // But for "Your Library" playlists it's usually FEmusic_library_corp_landing or we check tabs.
        // Let's try FEmusic_liked_playlists first as it's the standard "Playlists" section.
        val browseId = "FEmusic_liked_playlists"
        
        try {
            val apiUrl = "${YouTubeMusicClient.BASE_URL}browse?key=${YouTubeMusicClient.API_KEY}&prettyPrint=false"
            val locale = YouTubeMusicClient.getLocale()
            
            val requestBody = """
                {
                    "context": {
                        "client": {
                            "clientName": "${YouTubeMusicClient.CLIENT_NAME}",
                            "clientVersion": "${YouTubeMusicClient.CLIENT_VERSION}",
                            "clientScreen": "WATCH",
                            "hl": "${locale.hl}",
                            "gl": "${locale.gl}",
                            "utcOffsetMinutes": ${YouTubeMusicClient.getUtcOffsetMinutes()},
                            "visitorData": "${YouTubeMusicClient.visitorData}"
                        },
                        "user": {
                            "enableSafetyMode": false,
                            "lockedSafetyMode": false
                        }
                    },
                    "browseId": "$browseId"
                }
            """.trimIndent()

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = requestBody.toRequestBody(mediaType)
            
            val requestBuilder = okhttp3.Request.Builder()
                .url(apiUrl)
                .post(body)
                .header("Content-Type", "application/json")
                .header("User-Agent", YouTubeMusicClient.USER_AGENT)
                .header("Referer", YouTubeMusicClient.REFERER)
                .header("Origin", "https://music.youtube.com")
                .header("X-Goog-Api-Format-Version", "1") // Important
                .header("X-YouTube-Client-Name", YouTubeMusicClient.INNER_TUBE_NAME.toString())
                .header("X-YouTube-Client-Version", YouTubeMusicClient.CLIENT_VERSION)
                .header("X-Goog-Visitor-Id", YouTubeMusicClient.visitorData)
            
            // Add auth headers
            val authHeaders = YouTubeMusicClient.getAuthHeaders()
            authHeaders.forEach { (key, value) ->
                requestBuilder.header(key, value)
            }
            
            val request = requestBuilder.build()
            val client = com.amurayada.music.data.newpipe.NewPipeDownloader.getClient()
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                response.close()
                return@withContext emptyList()
            }
            
            val responseString = response.body?.string() ?: ""
            
            // Parse Response
            val json = org.json.JSONObject(responseString)
            val items = json.optJSONObject("contents")
                ?.optJSONObject("singleColumnBrowseResultsRenderer")
                ?.optJSONArray("tabs")?.optJSONObject(0)
                ?.optJSONObject("tabRenderer")?.optJSONObject("content")
                ?.optJSONObject("sectionListRenderer")?.optJSONArray("contents")
                ?.optJSONObject(0) // Usually the first item is the Grid
                ?.optJSONObject("gridRenderer")
                ?.optJSONArray("items")
                
            val playlists = mutableListOf<com.amurayada.music.data.model.Playlist>()
            
            if (items != null) {
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i)?.optJSONObject("musicTwoRowItemRenderer") ?: continue
                    
                    val title = item.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: ""
                    val subtitleRuns = item.optJSONObject("subtitle")?.optJSONArray("runs")
                    val authorName = subtitleRuns?.optJSONObject(0)?.optString("text") ?: ""
                    
                    // Construct browseId from navigation endpoint
                    val navigationEndpoint = item.optJSONObject("navigationEndpoint")
                    val browseIdVal = navigationEndpoint?.optJSONObject("browseEndpoint")?.optString("browseId") 
                        ?: navigationEndpoint?.optJSONObject("watchEndpoint")?.optString("playlistId") // For "Liked Music" special playlist
                        
                    // Artwork
                    val thumbnails = item.optJSONObject("thumbnailRenderer")
                        ?.optJSONObject("musicThumbnailRenderer")
                        ?.optJSONObject("thumbnail")
                        ?.optJSONArray("thumbnails")
                    val thumbUrl = thumbnails?.optJSONObject(thumbnails.length() - 1)?.optString("url")
                    
                    if (title.isNotEmpty() && !browseIdVal.isNullOrEmpty()) {
                        playlists.add(
                            com.amurayada.music.data.model.Playlist(
                                id = browseIdVal.hashCode().toLong(),
                                name = title,
                                author = authorName,
                                artworkUri = thumbUrl,
                                remoteId = browseIdVal,
                                songCount = 0 // Parsing song count from "X songs" is tricky string parsing
                            )
                        )
                    }
                }
            }
            
            return@withContext playlists
            
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext emptyList()
        }
    }

    override suspend fun getCachedHomeSections(): List<HomeSection>? = withContext(Dispatchers.IO) {
        try {
            val file = java.io.File(context.cacheDir, "home_cache.json")
            if (!file.exists()) return@withContext null
            
            // Check expiry (e.g., 30 minutes)
            if (System.currentTimeMillis() - file.lastModified() > 30 * 60 * 1000) {
                return@withContext null
            }
            
            val content = file.readText()
            if (content.isEmpty()) return@withContext null
            
            val jsonArray = org.json.JSONArray(content)
            val sections = mutableListOf<HomeSection>()
            
            for (i in 0 until jsonArray.length()) {
                val sectionJson = jsonArray.optJSONObject(i)
                
                val title = sectionJson.optString("title")
                
                // Songs
                val songs = mutableListOf<Song>()
                val songsArray = sectionJson.optJSONArray("songs")
                if (songsArray != null) {
                    for (j in 0 until songsArray.length()) {
                        val s = songsArray.optJSONObject(j)
                        songs.add(Song(
                            id = s.optLong("id"),
                            title = s.optString("title"),
                            artist = s.optString("artist"),
                            album = s.optString("album"),
                            duration = s.optLong("duration"),
                            albumArtUri = if (s.has("art")) Uri.parse(s.optString("art")) else null,
                            path = "", 
                            dateAdded = 0
                        ))
                    }
                }
                
                // Albums
                val albums = mutableListOf<Album>()
                val albumsArray = sectionJson.optJSONArray("albums")
                if (albumsArray != null) {
                    for (j in 0 until albumsArray.length()) {
                        val a = albumsArray.optJSONObject(j)
                        albums.add(Album(
                            id = a.optLong("id"),
                            name = a.optString("name"),
                            artist = a.optString("artist"),
                            artworkUri = if (a.has("art")) Uri.parse(a.optString("art")) else null,
                            path = a.optString("path")
                        ))
                    }
                }
                
                // Playlists
                val playlists = mutableListOf<com.amurayada.music.data.model.Playlist>()
                val playlistsArray = sectionJson.optJSONArray("playlists")
                if (playlistsArray != null) {
                    for (j in 0 until playlistsArray.length()) {
                        val p = playlistsArray.optJSONObject(j)
                        playlists.add(com.amurayada.music.data.model.Playlist(
                            id = p.optLong("id"),
                            name = p.optString("name"),
                            songCount = p.optInt("count"),
                            artworkUri = if (p.has("art")) p.optString("art") else null,
                            remoteId = p.optString("remoteId")
                        ))
                    }
                }
                
                sections.add(HomeSection(title, songs, albums, emptyList(), playlists))
            }
            
            return@withContext sections
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    override suspend fun saveCachedHomeSections(sections: List<HomeSection>) = withContext(Dispatchers.IO) {
        try {
            val jsonArray = org.json.JSONArray()
            
            sections.forEach { section ->
                val json = org.json.JSONObject()
                json.put("title", section.title)
                
                // Songs
                val songsArray = org.json.JSONArray()
                section.songs.forEach { song ->
                    val s = org.json.JSONObject()
                    s.put("id", song.id)
                    s.put("title", song.title)
                    s.put("artist", song.artist)
                    s.put("album", song.album)
                    s.put("duration", song.duration)
                    if (song.albumArtUri != null) s.put("art", song.albumArtUri.toString())
                    songsArray.put(s)
                }
                json.put("songs", songsArray)
                
                // Albums
                val albumsArray = org.json.JSONArray()
                section.albums.forEach { album ->
                    val a = org.json.JSONObject()
                    a.put("id", album.id)
                    a.put("name", album.name)
                    a.put("artist", album.artist)
                    if (album.artworkUri != null) a.put("art", album.artworkUri.toString())
                    a.put("path", album.path ?: "")
                    albumsArray.put(a)
                }
                json.put("albums", albumsArray)
                
                // Playlists
                val playlistsArray = org.json.JSONArray()
                section.playlists.forEach { playlist ->
                    val p = org.json.JSONObject()
                    p.put("id", playlist.id)
                    p.put("name", playlist.name)
                    p.put("count", playlist.songCount)
                    if (playlist.artworkUri != null) p.put("art", playlist.artworkUri)
                    if (playlist.remoteId != null) p.put("remoteId", playlist.remoteId)
                    playlistsArray.put(p)
                }
                json.put("playlists", playlistsArray)
                
                jsonArray.put(json)
            }
            
            val file = java.io.File(context.cacheDir, "home_cache.json")
            file.writeText(jsonArray.toString())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
