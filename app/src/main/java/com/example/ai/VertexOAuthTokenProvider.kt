package com.example.ai

import android.util.Base64
import com.example.data.security.ServiceAccountCredentialsData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec

@Serializable
private data class OAuthTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresInSeconds: Long = 3600L,
    @SerialName("token_type") val tokenType: String = "Bearer"
)

data class CachedAccessToken(
    val token: String,
    val expiresAtEpochMillis: Long,
    val clientEmail: String
) {
    fun isValid(nowMillis: Long = System.currentTimeMillis()): Boolean {
        // Refresh 5 minutes before actual expiration
        return nowMillis + 300_000L < expiresAtEpochMillis
    }
}

/**
 * Poskytovatel OAuth2 Access Tokenů pro Google Cloud Vertex AI ze Service Account JSONu.
 * Používá RS256 (SHA256withRSA) podepisování JWT a cachuje access token v RAM paměti.
 * Žádné tokeny ani privátní klíče se nikdy nelogují.
 */
class VertexOAuthTokenProvider(
    private val okHttpClient: OkHttpClient
) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cachedToken: CachedAccessToken? = null

    fun getCachedTokenStatus(): CachedAccessToken? = cachedToken

    fun invalidateCache() {
        cachedToken = null
    }

    suspend fun getValidAccessToken(
        credentials: ServiceAccountCredentialsData,
        forceRefresh: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val current = cachedToken
            if (!forceRefresh && current != null &&
                current.clientEmail == credentials.clientEmail &&
                current.isValid()
            ) {
                return@withLock Result.success(current.token)
            }

            runCatching {
                val nowSeconds = System.currentTimeMillis() / 1000L
                val expSeconds = nowSeconds + 3600L
                val signedJwt = createSignedJwtAssertion(
                    credentials = credentials,
                    issuedAtSeconds = nowSeconds,
                    expiresAtSeconds = expSeconds
                )

                val formBody = FormBody.Builder()
                    .add("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
                    .add("assertion", signedJwt)
                    .build()

                val request = Request.Builder()
                    .url(credentials.tokenUri.ifBlank { "https://oauth2.googleapis.com/token" })
                    .post(formBody)
                    .header("Accept", "application/json")
                    .build()

                okHttpClient.newCall(request).execute().use { response ->
                    val bodyString = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        error("OAuth2 chyba (${response.code}): Nepodařilo se získat přístupový token pro Service Account.")
                    }
                    val tokenResponse = json.decodeFromString<OAuthTokenResponse>(bodyString)
                    val expiresAtMillis = System.currentTimeMillis() + (tokenResponse.expiresInSeconds * 1000L)
                    val newCached = CachedAccessToken(
                        token = tokenResponse.accessToken,
                        expiresAtEpochMillis = expiresAtMillis,
                        clientEmail = credentials.clientEmail
                    )
                    cachedToken = newCached
                    newCached.token
                }
            }
        }
    }

    internal fun createSignedJwtAssertion(
        credentials: ServiceAccountCredentialsData,
        issuedAtSeconds: Long,
        expiresAtSeconds: Long
    ): String {
        val headerJson = buildJsonObject {
            put("alg", "RS256")
            put("typ", "JWT")
            if (credentials.privateKeyId.isNotBlank()) {
                put("kid", credentials.privateKeyId)
            }
        }.toString()

        val payloadJson = buildJsonObject {
            put("iss", credentials.clientEmail)
            put("sub", credentials.clientEmail)
            put("scope", VERTEX_CLOUD_PLATFORM_SCOPE)
            put("aud", credentials.tokenUri.ifBlank { "https://oauth2.googleapis.com/token" })
            put("iat", issuedAtSeconds)
            put("exp", expiresAtSeconds)
        }.toString()

        val headerB64 = base64UrlEncode(headerJson.toByteArray(StandardCharsets.UTF_8))
        val payloadB64 = base64UrlEncode(payloadJson.toByteArray(StandardCharsets.UTF_8))
        val signingInput = "$headerB64.$payloadB64"

        val privateKey = parsePkcs8PrivateKey(credentials.privateKeyPem)
        val signature = Signature.getInstance("SHA256withRSA")
        signature.initSign(privateKey)
        signature.update(signingInput.toByteArray(StandardCharsets.UTF_8))
        val signatureBytes = signature.sign()
        val signatureB64 = base64UrlEncode(signatureBytes)

        return "$signingInput.$signatureB64"
    }

    private fun parsePkcs8PrivateKey(pem: String): PrivateKey {
        val normalizedPem = pem
            .replace("\\n", "\n")
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("-----BEGIN RSA PRIVATE KEY-----", "")
            .replace("-----END RSA PRIVATE KEY-----", "")
            .replace(Regex("\\s+"), "")

        val keyBytes = Base64.decode(normalizedPem, Base64.DEFAULT)
        val keySpec = PKCS8EncodedKeySpec(keyBytes)
        return KeyFactory.getInstance("RSA").generatePrivate(keySpec)
    }

    private fun base64UrlEncode(input: ByteArray): String {
        return Base64.encodeToString(
            input,
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
    }

    companion object {
        const val VERTEX_CLOUD_PLATFORM_SCOPE = "https://www.googleapis.com/auth/cloud-platform"
    }
}
