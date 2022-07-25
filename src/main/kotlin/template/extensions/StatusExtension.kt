package template.extensions

import com.kotlindiscord.kord.extensions.checks.isNotBot
import com.kotlindiscord.kord.extensions.checks.notInChannel
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.event
import com.kotlindiscord.kord.extensions.utils.env
import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.channel.MessageChannel
import dev.kord.core.event.message.MessageCreateEvent
import kotlin.random.Random

private val TESTING_CHANNEL_ID = Snowflake(env("TESTING_CHANNEL_ID"))

class StatusExtension : Extension() {
    override val name = "Status I Guess"

    override suspend fun setup() {
        event<MessageCreateEvent> {
            check {
                notInChannel(FILTERED_CHANNEL)

                isNotBot()

                failIfNot("Random event skip") {
                    Random.nextInt(3) == 0
                }
            }

            action {
                val status = event.message.content
                    .replace("<(?:a?:\\w+:|@!*&*|#)\\d+>".toRegex(), "")
                    .replace("https?://\\S+".toRegex(), "")
                if (status.isBlank()) {
                    return@action
                }
                val displayName = event.message.getAuthorAsMember()?.displayName ?: "unknown"

                kord.getChannelOf<MessageChannel>(TESTING_CHANNEL_ID)
                    ?.createMessage("Message from $displayName: ${event.message.content}")

                kord.editPresence {
                    val types = listOf(
                        ::playing,
                        ::watching,
                        { streaming(it, "https://haha.ball") },
                        ::listening,
                        ::competing,
                    )

                    types.random().invoke(status)
                }
            }
        }
    }
}