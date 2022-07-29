package io.github.emmanator.extensions

import com.kotlindiscord.kord.extensions.checks.isNotBot
import com.kotlindiscord.kord.extensions.checks.notInChannel
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.event
import dev.kord.core.event.message.MessageCreateEvent
import kotlin.random.Random


class StatusExtension : Extension() {
    override val name = "Status I Guess"

    override suspend fun setup() {
        event<MessageCreateEvent> {
            check {
                notInChannel(FILTERED_CHANNEL)

                isNotBot()

                failIfNot("Random event skip") {
                    Random.nextInt(5) == 0
                }
            }

            action {
                kord.editPresence {
                    val types = listOf(
                        { playing("haha ball") },
                        { watching("haha ball") },
                        { streaming("haha ball", "https://www.twitch.tv/pumpy_gumpy") },
                        { listening("haha ball") },
                        { competing("haha ball") },
                    )

                    types.random().invoke()
                }
            }
        }
    }
}