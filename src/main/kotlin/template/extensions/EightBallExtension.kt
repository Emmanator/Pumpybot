package template.extensions

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalString
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import com.kotlindiscord.kord.extensions.utils.env

private val EXTRA_OPTION = env("EXTRA_OPTION")

private val responses = listOf(
    "It is certain",
    "Outlook good",
    "You may rely on it",
    "Ask again later",
    "Concentrate and ask again",
    "Reply hazy, try again",
    "My reply is no",
    "My sources say no",
    EXTRA_OPTION
)

class EightBallExtension : Extension() {
    override val name = "8 Ball"

    override suspend fun setup() {
        publicSlashCommand(::EightBallArgs) {
            name = "ball"
            description = "Ask the magic ball for an answer"

            action {
                val response = responses.random()

                if (response == EXTRA_OPTION) {
                    respond {
                        content = EXTRA_OPTION
                    }
                } else {
                    respond {
                        content = buildString {
                            if (arguments.request != null) {
                                append("Your question was: ")
                                append(arguments.request)
                                append('\n')
                            }

                            append("The magic 8 ball says: ")
                            append(response)
                        }
                    }
                }
            }
        }
    }

    class EightBallArgs : Arguments() {
        val request by optionalString {
            name = "request"
            description = "What do you want to ask the 8 ball"
        }
    }
}
