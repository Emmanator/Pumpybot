package io.github.emmanator.extensions

import com.kotlindiscord.kord.extensions.checks.inChannel
import com.kotlindiscord.kord.extensions.checks.isNotBot
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.event
import com.kotlindiscord.kord.extensions.utils.env
import com.kotlindiscord.kord.extensions.utils.respond
import dev.kord.common.entity.Snowflake
import dev.kord.core.event.message.MessageCreateEvent
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

val FILTERED_CHANNEL = Snowflake(
    env("ALLOWED_WORD_CHANNEL")
)

private val ALLOWED_WORD = env("ALLOWED_WORD").trim().lowercase()

class FilteredChannelExtension : Extension() {
    override val name = "Filtered Channel"

    override suspend fun setup() {
        event<MessageCreateEvent> {
            check {
                inChannel(FILTERED_CHANNEL)

                isNotBot()
            }

            action {
                if (event.message.content.trim().lowercase() == ALLOWED_WORD) {
                    event.message.channel.createMessage(ALLOWED_WORD)
                } else {
                    val response = event.message.respond(pingInReply = false) {
                        content = "Only $ALLOWED_WORD!"
                    }

                    delay(5.seconds)

                    event.message.delete()
                    response.delete()
                }
            }
        }
    }
}