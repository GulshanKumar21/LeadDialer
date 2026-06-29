package com.adyapan.leaddialer

import android.content.Context
import android.util.Log
import com.android.volley.Request
import com.android.volley.toolbox.JsonObjectRequest
import com.android.volley.toolbox.Volley
import org.json.JSONObject

object GasNotificationSender {

    fun sendNotification(
        context: Context,
        token: String,
        title: String,
        body: String
    ) {

        try {

            val url =
                "https://script.google.com/macros/s/AKfycbxdo6J3g_i3JXcX6MkSSIBoQyDniqOc6_0tpC8GZ4wwtCV-EIzLnoHdowu0e3GvRAVIHA/exec"

            val json = JSONObject()

            json.put("token", token)
            json.put("title", title)
            json.put("body", body)

            val request = JsonObjectRequest(

                Request.Method.POST,
                url,
                json,

                {

                    Log.d(
                        "GAS_NOTIFICATION",
                        "Success"
                    )
                },

                {

                    Log.e(
                        "GAS_NOTIFICATION",
                        "Failed"
                    )
                }
            )

            Volley.newRequestQueue(context)
                .add(request)

        } catch(t: Throwable) {

            t.printStackTrace()
        }
    }
}