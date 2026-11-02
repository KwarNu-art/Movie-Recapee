package com.movierecapee.di

import androidx.room.Room
import com.movierecapee.data.local.AppDatabase
import com.movierecapee.data.local.ProjectRepository
import com.movierecapee.data.local.SecureSettings
import com.movierecapee.data.media.TransformRunner
import com.movierecapee.data.media.VideoExporter
import com.movierecapee.data.remote.GeminiApi
import com.movierecapee.data.remote.GroqApi
import com.movierecapee.data.remote.MyMemoryApi
import com.movierecapee.data.repo.AsrRepository
import com.movierecapee.data.repo.ProcessingPipeline
import com.movierecapee.data.repo.TranslationRepository
import com.movierecapee.data.repo.TtsRepository
import com.movierecapee.ui.editor.EditorViewModel
import com.movierecapee.ui.home.HomeViewModel
import com.movierecapee.ui.settings.SettingsViewModel
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

private fun retrofit(client: OkHttpClient, baseUrl: String): Retrofit =
    Retrofit.Builder().baseUrl(baseUrl).client(client).addConverterFactory(GsonConverterFactory.create()).build()

val appModule = module {
    single { SecureSettings(androidContext()) }
    single { Room.databaseBuilder(androidContext(), AppDatabase::class.java, "movie_recapee.db").build() }
    single { get<AppDatabase>().projects() }
    single { ProjectRepository(androidContext(), get()) }

    single {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(180, TimeUnit.SECONDS)
            .build()
    }
    single<GroqApi> { retrofit(get(), "https://api.groq.com/").create(GroqApi::class.java) }
    single<GeminiApi> { retrofit(get(), "https://generativelanguage.googleapis.com/").create(GeminiApi::class.java) }
    single<MyMemoryApi> { retrofit(get(), "https://api.mymemory.translated.net/").create(MyMemoryApi::class.java) }

    single { AsrRepository(get(), get(), get()) }
    single { TranslationRepository(get(), get(), get(), get()) }
    single { TtsRepository(androidContext(), get(), get()) }
    single { TransformRunner(androidContext()) }
    single { VideoExporter(get()) }
    single { ProcessingPipeline(androidContext(), get(), get(), get(), get(), get(), get()) }

    viewModel { HomeViewModel(androidContext(), get()) }
    viewModel { SettingsViewModel(get()) }
    viewModel { (id: Long) -> EditorViewModel(id, androidContext(), get(), get(), get(), get()) }
}
