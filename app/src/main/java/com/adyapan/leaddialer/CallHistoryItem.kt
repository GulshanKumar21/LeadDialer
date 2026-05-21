package com.adyapan.leaddialer


sealed class CallHistoryItem {

    data class Header(
        val title: String
    ) : CallHistoryItem()

    data class CallItem(
        val record: CallRecord
    ) : CallHistoryItem()

    /** Gap row between two consecutive calls (time gap after previous call ended). */
    data class GapItem(
        val gapSeconds: Long   // seconds of idle time between call-end and next call-start
    ) : CallHistoryItem()
}