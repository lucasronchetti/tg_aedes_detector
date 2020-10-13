package com.example.aedesdetector.utils

import android.content.Context
import android.content.DialogInterface
import androidx.appcompat.app.AlertDialog

class AlertUtils {

    companion object {
        fun displayAlert(
            context: Context,
            title: String,
            text: String,
            positiveButtonText: String,
            positiveButtonAction: DialogInterface.OnClickListener,
            negativeButtonText: String?,
            negativeButtonAction: DialogInterface.OnClickListener?) {

            val alertBuilder = AlertDialog.Builder(context)
            alertBuilder.setTitle(title)
            alertBuilder.setMessage(text)

            alertBuilder.setPositiveButton(positiveButtonText, positiveButtonAction)

            if (negativeButtonText != null) {
                alertBuilder.setNegativeButton(
                    negativeButtonText, negativeButtonAction
                )
            }

            alertBuilder.show()
        }
    }


}