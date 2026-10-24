package com.example

import android.app.Application
import com.example.ai.VertexAiClient
import com.example.ai.VertexOAuthTokenProvider
import com.example.ai.VfsAiTools
import com.example.data.local.VertexChatDatabase
import com.example.data.preferences.UserPreferencesRepository
import com.example.data.security.KeystoreServiceAccountVault
import com.example.data.vfs.VirtualFileSystemManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppContainer(context: Application) {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: VertexChatDatabase by lazy {
        VertexChatDatabase.getInstance(context)
    }

    val preferencesRepository: UserPreferencesRepository by lazy {
        UserPreferencesRepository(context)
    }

    val keystoreVault: KeystoreServiceAccountVault by lazy {
        KeystoreServiceAccountVault(context)
    }

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    val tokenProvider: VertexOAuthTokenProvider by lazy {
        VertexOAuthTokenProvider(okHttpClient)
    }

    val vfsManager: VirtualFileSystemManager by lazy {
        VirtualFileSystemManager(context, database.dao())
    }

    val vfsAiTools: VfsAiTools by lazy {
        VfsAiTools(vfsManager)
    }

    val vertexAiClient: VertexAiClient by lazy {
        VertexAiClient(
            okHttpClient = okHttpClient,
            vault = keystoreVault,
            tokenProvider = tokenProvider
        )
    }

    init {
        applicationScope.launch {
            val dao = database.dao()
            if (dao.getModelsOnce().isEmpty()) {
                dao.upsertModels(VertexChatDatabase.DEFAULT_MODELS)
            }
        }
    }
}

class VertexChatApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
