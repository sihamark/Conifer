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
import androidx.core.graphics.drawable.IconCompat
import eu.heha.conifer.ConversationBroadcastReceiver.Companion.EXTRA_PREVIOUS_BIT_IDS
import eu.heha.conifer.core.R
import eu.heha.conifer.model.BitsRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject


class NotificationController(private val baseContext: Context) : KoinComponent {

    private val context get() = baseContext.applicationContext
    private val notifications = NotificationManagerCompat.from(context)
    private val repository: BitsRepository by inject()

    private fun string(stringRes: Int): String = context.getString(stringRes)

    suspend fun conversationNotification(bitIds: List<String> = emptyList()) {
        notifications.createNotificationChannel(
            NotificationChannelCompat.Builder(CONVERSATION_CHANNEL_ID, IMPORTANCE_DEFAULT)
                .setName(string(R.string.notification_channel_conversations_name))
                .setDescription(string(R.string.notification_channel_conversations_description))
                .build()
        )

        val notificationBuilder = conversationsNotificationBuilder
            ?: createNewConversationNotification()

        conversationsNotificationBuilder = notificationBuilder

        val latestBitIds = bitIds.takeLast(3)
        val bitTexts = repository.getTextsOfBits(latestBitIds)
        notificationBuilder
            .clearActions()
            .addAction(createConversationReplyAction(latestBitIds))
            .apply {
                if (bitTexts.isNotEmpty()) {
                    setRemoteInputHistory(bitTexts.toTypedArray())
                }
            }

        if (isNotificationPermissionGranted(context)) {
            @SuppressLint("MissingPermission")
            notifications.notify(CONVERSATION_NOTIFICATION_ID, notificationBuilder.build())
        }
    }

    private fun createNewConversationNotification(): NotificationCompat.Builder =
        NotificationCompat.Builder(context, CONVERSATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(string(R.string.notification_conversation_title))
            .setContentText(string(R.string.notification_conversation_message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(
                PendingIntent.getActivity(
                    context.applicationContext,
                    100,
                    getAppIntent(),
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )

    private fun createConversationReplyAction(bitIds: List<String>): NotificationCompat.Action {
        val replyLabel = string(R.string.notification_conversation_action_enter)
        val remoteInput = RemoteInput.Builder(CONVERSATION_ENTER_KEY)
            .setLabel(replyLabel)
            .build()
        val pendingIntent = PendingIntent.getBroadcast(
            context.applicationContext,
            200,
            getConversationEnterIntent(bitIds),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val action = NotificationCompat.Action.Builder(
            IconCompat.createWithResource(context, R.drawable.ic_notification),
            replyLabel,
            pendingIntent
        ).addRemoteInput(remoteInput)
            .build()
        return action
    }

    private fun getConversationEnterIntent(bitIds: List<String>) =
        Intent(context, ConversationBroadcastReceiver::class.java).apply {
            if (bitIds.isNotEmpty()) {
                putExtra(EXTRA_PREVIOUS_BIT_IDS, bitIds.toTypedArray())
            }
        }

    private fun getAppIntent(): Intent {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        return launchIntent
            ?: error("Unable to get launch intent for package ${context.packageName}")
    }

    companion object {

        private const val CONVERSATION_CHANNEL_ID = "conversations"

        private const val CONVERSATION_NOTIFICATION_ID = 1

        const val CONVERSATION_ENTER_KEY = "conversation_enter"

        @SuppressLint("StaticFieldLeak")
        private var conversationsNotificationBuilder: NotificationCompat.Builder? = null

        fun isNotificationPermissionGranted(context: Context): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                checkSelfPermission(context, POST_NOTIFICATIONS) == PERMISSION_GRANTED
            } else {
                true
            }
    }
}