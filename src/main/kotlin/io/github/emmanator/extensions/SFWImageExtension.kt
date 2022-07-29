package io.github.emmanator.extensions

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.impl.defaultingStringChoice
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import com.kotlindiscord.kord.extensions.utils.capitalizeWords
import dev.kord.rest.builder.message.create.embed
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class SFWImageExtension : Extension() {
    override val name = "Sample"
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
            })
        }
    }

    override suspend fun setup() {
        listOf(
            CommandSpec.Catboy,
            CommandSpec.Neko
        ).forEach {
            createCommandForSpec(it)
        }
    }

    private suspend fun <T : Arguments> createCommandForSpec(spec: CommandSpec<T>) {
        publicSlashCommand(spec::createArgs) {
            name = spec.name
            description = spec.description

            action {
                when (val result = spec.get(client, arguments)) {
                    is ImageResult.Success -> {
                        respond {
                            embed {
                                title = "Image by ${result.artist ?: "unknown"}"
                                image = result.url
                            }
                        }
                    }
                    is ImageResult.Failure -> {
                        respond {
                            content = "Request failed (reason: ${result.reason})"
                        }
                    }
                }

            }
        }
    }

    sealed class CommandSpec<T : Arguments>(
        val name: String,
        val description: String
    ) {
        abstract suspend fun get(client: HttpClient, arguments: T): ImageResult

        abstract fun createArgs(): T

        object Catboy : CommandSpec<Arguments>("catboy", "Random catboy!") {
            override suspend fun get(client: HttpClient, arguments: Arguments): ImageResult {
                val response: CatboyResponse = client.get("https://api.catboys.com/img").body()

                return ImageResult.Success(
                    response.url,
                    response.artist
                )
            }

            override fun createArgs() = Arguments()

            @Serializable
            data class CatboyResponse(
                val url: String,
                val artist: String
            )
        }

        object Neko : CommandSpec<Neko.NekoArgs>("neko", "Random neko!") {
            override suspend fun get(client: HttpClient, arguments: NekoArgs): ImageResult {
                val response: NekoResponse = client.get("https://neko-love.xyz/api/v1/${arguments.type}").body()

                return ImageResult.Success(
                    response.url,
                    null
                )
            }

            override fun createArgs() = NekoArgs()

            @Serializable
            data class NekoResponse(
                val url: String
            )

            class NekoArgs : Arguments() {
                val type by defaultingStringChoice {
                    name = "Type"
                    description = "Image type"

                    choices = listOf(
                        "neko",
                        "kitsune",
                        "pat",
                        "hug",
                        "waifu",
                        "cry",
                        "kiss",
                        "slap",
                        "smug",
                        "punch"
                    ).associateBy { it.capitalizeWords() }.toMutableMap()

                    defaultValue = "neko"
                }
            }
        }
    }

    sealed class ImageResult {
        class Success(
            val url: String,
            val artist: String?
        ) : ImageResult()

        class Failure(
            val reason: String
        ) : ImageResult()
    }
}