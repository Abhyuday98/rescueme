package com.myapplication.rescueme

import android.content.Context
import java.security.MessageDigest

class Helper {
    companion object {
        fun String.toMD5(): String {
            val bytes = MessageDigest.getInstance("MD5").digest(this.toByteArray())
            return bytes.toHex()
        }

        fun ByteArray.toHex(): String {
            return joinToString("") { "%02x".format(it) }
        }

        fun fileExist(context : Context, fname: String?): Boolean {
            val file = context.getFileStreamPath(fname)
            return file.exists()
        }

        // join any spaces, add +65 in front if no prefix starting with +.
        fun formatContactNumber(contactNumber : String) : String {
            var result = ""

            if (contactNumber.substring(0, 1) != "+") {
                result = "+65$contactNumber"
            } else {
                result = contactNumber
            }

            result = result.split(" ").joinToString("")
            return result
        }
    }
}