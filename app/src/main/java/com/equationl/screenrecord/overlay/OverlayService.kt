package com.equationl.screenrecord.overlay

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntSize
import com.equationl.screenrecord.R
import com.equationl.screenrecord.utils.ScreenRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class OverlayService : ComposeOverlayViewService() {
    companion object {
        private const val TAG = "OverlayService"

        var resultData: Intent? = null

        fun setData(resultData: Intent) {
            this.resultData = resultData
        }
    }

    private var isRecord = false
    private lateinit var recorder: ScreenRecorder

    @Composable
    override fun Content() = OverlayDraggableContainer {
        var btnIcon by remember { mutableStateOf(Icons.Rounded.PlayArrow) }
        var btnIconColor by remember { mutableStateOf(0xFF000000) }


        Icon(
            imageVector = btnIcon,
            contentDescription = "Start record",
            modifier = Modifier
                .fillMaxSize()
                .clickable {
                    isRecord = !isRecord


                    onClick()

                    if (isRecord) {
                        btnIcon = Icons.Rounded.Done
                        btnIconColor = 0x80000000
                    } else {
                        btnIcon = Icons.Rounded.PlayArrow
                        btnIconColor = 0xFF000000
                    }
                },
            tint = Color(btnIconColor)
        )
    }


    private fun onClick() {
        if (isRecord) {
            initRunningTipNotification()

            val savePath = File(externalCacheDir, "${System.currentTimeMillis()}.mp4").absolutePath
            val screenSize = getScreenSize()
            val mediaProjection = getMediaProjection()

            // 这里如果直接使用屏幕尺寸会报错 java.lang.IllegalArgumentException
            recorder = ScreenRecorder(
                886, // screenSize.width,
                1920, // screenSize.height,
                24,
                1,
                mediaProjection,
                savePath
            )

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    recorder.start()
                } catch (tr: Throwable) {
                    Log.e(TAG, "startScreenRecorder: ", tr)
                    recorder.stop()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@OverlayService, "录制失败", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
        else {
            recorder.stop()
        }
    }

    private fun getScreenSize(): IntSize {
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val screenHeight = windowManager.currentWindowMetrics.bounds.height()
        val screenWidth = windowManager.currentWindowMetrics.bounds.width()

        return IntSize(screenWidth, screenHeight)
    }

    private fun getMediaProjection(): MediaProjection? {
        if (resultData == null) {
            Toast.makeText(this, "未初始化！", Toast.LENGTH_SHORT).show()
        } else {
            try {
                val mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                return mediaProjectionManager.getMediaProjection(Activity.RESULT_OK, resultData!!)
            } catch (e: IllegalStateException) {
                Log.e(TAG, "getMediaProjection: ", e)
                Toast.makeText(this, "ERR: ${e.stackTraceToString()}", Toast.LENGTH_LONG).show()
            }
            catch (e: NullPointerException) {
                Log.e(TAG, "getMediaProjection: ", e)
            }
            catch (tr: Throwable) {
                Log.e(TAG, "getMediaProjection: ", tr)
                Toast.makeText(this, "ERR: ${tr.stackTraceToString()}", Toast.LENGTH_LONG).show()
            }
        }

        return null
    }


    private fun initRunningTipNotification() {
        val builder = Notification.Builder(this, "running")

        builder.setContentText("录屏运行中")
            .setSmallIcon(R.drawable.ic_launcher_foreground)

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            "running",
            "显示录屏状态",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        notificationManager.createNotificationChannel(channel)
        builder.setChannelId("running")
        startForeground(100, builder.build())
    }

}