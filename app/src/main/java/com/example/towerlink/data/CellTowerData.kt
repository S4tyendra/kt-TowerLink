package com.example.towerlink.data

data class CellTowerData(
    val type: String,
    val status: String,
    val mcc: String,
    val mnc: String,
    val lac: Long,
    val cid: Long,
    val signalDbm: Int,
    val timestamp: Long = System.currentTimeMillis()
) {
    val apiKeyFormat: String get() = "${mcc}_${mnc}_${lac}_${cid}"
}