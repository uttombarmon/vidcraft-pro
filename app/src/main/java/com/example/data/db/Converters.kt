package com.example.data.db

import androidx.room.TypeConverter
import android.net.Uri
import com.example.data.VideoProject
import com.squareup.moshi.FromJson
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class Converters {
    private val moshi = Moshi.Builder()
        .add(UriAdapter())
        .add(KotlinJsonAdapterFactory())
        .build()
    private val adapter = moshi.adapter(VideoProject::class.java)

    @TypeConverter
    fun fromVideoProject(project: VideoProject): String {
        return adapter.toJson(project)
    }

    @TypeConverter
    fun toVideoProject(json: String): VideoProject? {
        return adapter.fromJson(json)
    }
}

class UriAdapter {
    @ToJson
    fun toJson(uri: Uri): String = uri.toString()

    @FromJson
    fun fromJson(uriString: String): Uri = Uri.parse(uriString)
}
