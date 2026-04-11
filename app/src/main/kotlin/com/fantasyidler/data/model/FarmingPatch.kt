package com.fantasyidler.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "farming_patches")
data class FarmingPatch(
    @PrimaryKey
    @ColumnInfo(name = "patch_number")
    val patchNumber: Int,

    /** Crop item key, or null if the patch is empty */
    @ColumnInfo(name = "crop_type")
    val cropType: String? = null,

    /** Epoch ms when the crop was planted, or null if empty */
    @ColumnInfo(name = "planted_at")
    val plantedAt: Long? = null,
)
