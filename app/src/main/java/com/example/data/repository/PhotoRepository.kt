package com.example.data.repository

import com.example.data.local.PhotoDao
import com.example.data.model.PhotoRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class PhotoRepository(private val photoDao: PhotoDao) {

    val allPhotos: Flow<List<PhotoRecord>> = photoDao.getAllPhotos()
    val photoCount: Flow<Int> = photoDao.getPhotoCount()

    suspend fun insertPhoto(photo: PhotoRecord): Long = withContext(Dispatchers.IO) {
        photoDao.insertPhoto(photo)
    }

    suspend fun getPhotoById(id: Long): PhotoRecord? = withContext(Dispatchers.IO) {
        photoDao.getPhotoById(id)
    }

    suspend fun deletePhoto(photo: PhotoRecord) = withContext(Dispatchers.IO) {
        try {
            File(photo.filePath).takeIf { it.exists() }?.delete()
            File(photo.thumbnailPath).takeIf { it.exists() }?.delete()
        } catch (_: Exception) {}
        photoDao.deletePhoto(photo)
    }

    suspend fun deletePhotosByIds(photos: List<PhotoRecord>) = withContext(Dispatchers.IO) {
        photos.forEach { photo ->
            try {
                File(photo.filePath).takeIf { it.exists() }?.delete()
                File(photo.thumbnailPath).takeIf { it.exists() }?.delete()
            } catch (_: Exception) {}
        }
        photoDao.deletePhotosByIds(photos.map { it.id })
    }
}
