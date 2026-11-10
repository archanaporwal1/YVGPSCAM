package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.PhotoRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photo_records ORDER BY timestamp DESC")
    fun getAllPhotos(): Flow<List<PhotoRecord>>

    @Query("SELECT * FROM photo_records WHERE id = :id")
    suspend fun getPhotoById(id: Long): PhotoRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhoto(photo: PhotoRecord): Long

    @Delete
    suspend fun deletePhoto(photo: PhotoRecord)

    @Query("DELETE FROM photo_records WHERE id IN (:ids)")
    suspend fun deletePhotosByIds(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM photo_records")
    fun getPhotoCount(): Flow<Int>
}
