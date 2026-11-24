package com.example.teampanthernew.data

import java.io.Serializable

data class AutoBetRecord(
    val periodId: String,
    val choice: String,         // "BIG" or "SMALL"
    val stake: Double,
    var status: String = "PENDING", // "PENDING", "WIN", "LOSS"
    var resultNum: Int = -1,
    var resultChoice: String = "",
    var profit: Double = 0.0,
    val level: Int = 1,
    val timestamp: String = ""
) : Serializable


