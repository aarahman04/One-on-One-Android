package app.web.oneonone.data.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PATCH
import retrofit2.http.Path

// Gate timestamps are server truth (contract §10); null until accepted, absent on older backends.
@Serializable data class Me(val userId: String, val connectionCode: String, val ageVerifiedAt: String? = null, val termsAcceptedAt: String? = null)
@Serializable data class GatesResult(val ageVerifiedAt: String? = null, val termsAcceptedAt: String? = null)
@Serializable data class CodeResult(val connectionCode: String)
@Serializable data class ConnectionRequest(val connectionCode: String)
@Serializable data class TokenBody(val token: String)
@Serializable data class PushTokenBody(val token: String, val platform: String)
@Serializable data class BlockedUser(val blockedUserId: String, val createdAt: String)
@Serializable data class BlocksResult(val blocks: List<BlockedUser>)
@Serializable data class CurrentResult(val connection: CurrentConnection?)
@Serializable data class NicknameBody(val nickname: String)
@Serializable data class WallpaperBody(val wallpaper: String)
@Serializable data class ReportBody(val category: String, val reason: String)
@Serializable data class CurrentConnection(
    val id: String,
    val status: String,
    val myUserId: String,
    val isRequester: Boolean,
    val otherNickname: String?,
    val otherConnectionCode: String,
    val otherLastReadAt: String?,
    val otherLastDeliveredAt: String?,
    val wallpaper: String,
    val messageStyle: String,
    val pinnedMessageId: String? = null,
    val pinnedBy: String? = null,
    val pinnedAt: String? = null,
    // Opaque photo ids (contract §9); a new id means refetch the signed URL.
    val myAvatarId: String? = null,
    val otherAvatarId: String? = null,
    // Only for status "ended": the partner's read-only window until purgeAt (contract §10). Legacy leave-step fields are ignored.
    val endedBy: String? = null,
    val endedAt: String? = null,
    val purgeAt: String? = null,
)

interface AccountApi {
    @GET("api/me") suspend fun me(): Me
    /** Body has only `ageVerified: true` and/or `termsAccepted: true`; any other key or value is 400. */
    @POST("api/me/gates") suspend fun gates(@Body body: JsonObject): GatesResult
    @POST("api/me/connection-code/regenerate") suspend fun regenerate(): CodeResult
    @DELETE("api/me") suspend fun deleteAccount()
    @GET("api/me/blocks") suspend fun blocks(): BlocksResult
    @DELETE("api/me/blocks/{id}") suspend fun unblock(@Path("id") id: String)
    @GET("api/connections/current") suspend fun current(): CurrentResult
    @POST("api/connections/request") suspend fun request(@Body body: ConnectionRequest): JsonObject
    @POST("api/connections/{id}/accept") suspend fun accept(@Path("id") id: String): JsonObject
    @POST("api/connections/{id}/decline") suspend fun decline(@Path("id") id: String): JsonObject
    @POST("api/connections/{id}/cancel") suspend fun cancel(@Path("id") id: String): JsonObject
    @POST("api/push/token/unregister") suspend fun unregister(@Body body: TokenBody)
    @POST("api/push/token") suspend fun register(@Body body: PushTokenBody)
    @PATCH("api/connections/{id}/nickname") suspend fun nickname(@Path("id") id: String, @Body body: NicknameBody)
    /** Ends the connection at once (contract §10); the body is not needed. */
    @POST("api/connections/{id}/leave") suspend fun leave(@Path("id") id: String)
    @POST("api/connections/{id}/acknowledge-end") suspend fun acknowledgeEnd(@Path("id") id: String)
    @PATCH("api/connections/{id}/wallpaper") suspend fun wallpaper(@Path("id") id: String, @Body body: WallpaperBody)
    @POST("api/connections/{id}/block") suspend fun block(@Path("id") id: String)
    @POST("api/connections/{id}/report") suspend fun reportConnection(@Path("id") id: String, @Body body: ReportBody)
    @POST("api/messages/{id}/report") suspend fun reportMessage(@Path("id") id: String, @Body body: ReportBody)
}
