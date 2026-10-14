package app.web.oneonone.data.api

import kotlinx.serialization.Serializable
import okhttp3.RequestBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PUT

@Serializable data class AvatarResult(val avatarId: String)
@Serializable data class AvatarUrls(val mine: String? = null, val other: String? = null)

/** Own profile photo (contract §9). Signed URLs: yours and your live partner's only. */
interface ProfileApi {
    @PUT("api/me/avatar") suspend fun putAvatar(@Body image: RequestBody): AvatarResult
    @DELETE("api/me/avatar") suspend fun deleteAvatar()
    @GET("api/me/avatars") suspend fun avatars(): AvatarUrls
}
