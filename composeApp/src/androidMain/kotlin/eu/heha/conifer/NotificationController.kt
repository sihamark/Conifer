package eu.heha.conifer

import android.Manifest.permission.POST_NOTIFICATIONS
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.NotificationManagerCompat.IMPORTANCE_DEFAULT
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat.checkSelfPermission


class NotificationController(private val context: Context) {
    private val notifications = NotificationManagerCompat.from(context)

    private fun string(stringRes: Int): String = context.getString(stringRes)

    fun conversationNotification() {
        //TODO: improve by making the builder a property in the application, when replying use the same reference to the add history
        notifications.createNotificationChannel(
            NotificationChannelCompat.Builder(CONVERSATION_CHANNEL_ID, IMPORTANCE_DEFAULT)
                .setName(string(R.string.notification_channel_conversations_name))
                .setDescription(string(R.string.notification_channel_conversations_description))
                .build()
        )

        val replyLabel = string(R.string.notification_conversation_action_enter)
        val remoteInput = RemoteInput.Builder(CONVERSATION_ENTER_KEY)
            .setLabel(replyLabel)
            .build()
        val pendingIntent = PendingIntent.getBroadcast(
            context.applicationContext,
            100,
            getConversationEnterIntent(),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val action = NotificationCompat.Action.Builder(
            R.drawable.ic_notification, replyLabel, pendingIntent
        ).addRemoteInput(remoteInput)
            .build()

        val notification = NotificationCompat.Builder(context, CONVERSATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(string(R.string.notification_conversation_title))
            .setContentText(string(R.string.notification_conversation_message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(action)
            .build()

        if (isNotificationPermissionGranted(context)) {
            @SuppressLint("MissingPermission")
            notifications.notify(CONVERSATION_NOTIFICATION_ID, notification)
        }
    }

    private fun getConversationEnterIntent() =
        Intent(context, ConversationBroadcastReceiver::class.java)

    companion object {

        private const val CONVERSATION_CHANNEL_ID = "conversations"

        private const val CONVERSATION_NOTIFICATION_ID = 1

        const val CONVERSATION_ENTER_KEY = "conversation_enter"

        fun isNotificationPermissionGranted(context: Context): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                checkSelfPermission(context, POST_NOTIFICATIONS) == PERMISSION_GRANTED
            } else {
                true
            }
    }
}