package eu.heha.conifer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import eu.heha.conifer.NotificationController.Companion.CONVERSATION_ENTER_KEY
import eu.heha.conifer.model.database.Bit
import io.github.aakira.napier.Napier
import kotlinx.coroutines.runBlocking

class ConversationBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val results = RemoteInput.getResultsFromIntent(intent)
        if (results == null) {
            Napier.w { "no remote input results found" }
            return
        }

        val enteredText = results.getCharSequence(CONVERSATION_ENTER_KEY)?.toString()
        if (enteredText == null) {
            Napier.w { "no entered text found in remote input results, results: $results" }
            return
        }
        runBlocking {
            ConiferApp.repository.add(Bit(text = enteredText))
        }
    }
}