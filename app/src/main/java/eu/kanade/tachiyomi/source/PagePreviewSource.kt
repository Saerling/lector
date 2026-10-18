package eu.kanade.tachiyomi.source

import eu.kanade.tachiyomi.network.ProgressListener
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.CacheControl
import okhttp3.Response

interface PagePreviewSource : Source {

    suspend fun getPagePreviewList(manga: SManga, chapters: List<SChapter>, page: Int): PagePreviewPage

    suspend fun fetchPreviewImage(page: PagePreviewInfo, cacheControl: CacheControl? = null): Response
}

data class PagePreviewPage(
    val page: Int,
    val pagePreviews: List<PagePreviewInfo>,
    val hasNextPage: Boolean,
    val pagePreviewPages: Int?,
)

data class PagePreviewInfo(
    val index: Int,
    val imageUrl: String,
    private val _progress: MutableStateFlow<Int> = MutableStateFlow(-1),
) : ProgressListener {
    val progress = _progress.asStateFlow()

    override fun update(bytesRead: Long, contentLength: Long, done: Boolean) {
        _progress.value = if (contentLength > 0) {
            (100 * bytesRead / contentLength).toInt()
        } else {
            -1
        }
    }
}
