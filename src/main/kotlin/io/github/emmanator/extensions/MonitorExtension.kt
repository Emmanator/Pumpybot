package io.github.emmanator.extensions

import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.event
import com.kotlindiscord.kord.extensions.utils.env
import com.kotlindiscord.kord.extensions.utils.respond
import dev.kord.common.entity.Snowflake
import dev.kord.core.event.message.MessageCreateEvent
import mu.KotlinLogging
import kotlin.random.Random

private val MONITORED_ID = Snowflake(
    env("MONITORED_ID")
)

private val REPLY_TEXT = env("REPLY_TEXT")

class MonitorExtension : Extension() {
    private val logger = KotlinLogging.logger {}

    override val name = "Monitor Extension"

    override suspend fun setup() {


        event<MessageCreateEvent> {
            check {
                failIfNot("Not correct person") {
                    event.message.author?.id == MONITORED_ID
                }

                failIfNot("Random event skip") {
                    Random.nextInt(10).also { logger.debug("Random was $it") } == 0
                }
            }

            action {
                val filteredContent = event.message.content
                    .replace("<(?:a?:\\w+:|@!*&*|#)\\d+>".toRegex(), "")
                    .replace("https?://\\S+".toRegex(), "")

                val words = filteredContent.split(' ').filter { it.isNotEmpty() }

                if (words.isNotEmpty()) {
                    val lastWord = words.last()
                        .replace("^[!.\\-,?]*".toRegex(), "")
                        .replace("[!.\\-,?]*\$".toRegex(), "")

                    event.message.respond(pingInReply = false) {
                        content = "$lastWord $REPLY_TEXT"
                    }
                }
            }
        }
    }
}