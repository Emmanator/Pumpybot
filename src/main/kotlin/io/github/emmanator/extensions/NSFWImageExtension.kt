package io.github.emmanator.extensions

import com.kotlindiscord.kord.extensions.checks.channelIsNsfw
import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.impl.defaultingStringChoice
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalString
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import com.kotlindiscord.kord.extensions.utils.delete
import dev.kord.rest.builder.message.create.embed
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import io.github.emmanator.TEST_SERVER_ID


class NSFWImageExtension : Extension() {
    override val name = "Feet Generator"
    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
            })
        }
    }

    override suspend fun setup() {
        listOf(
            NSFWImageCommands.Danbooru,
            NSFWImageCommands.E621,
            NSFWImageCommands.Gelbooru
        ).forEach { spec ->
            publicSlashCommand(NSFWImageExtension::NSFWCommandArguments) {
                name = spec.name
                description = spec.description

                check {
                    channelIsNsfw()
                }

                guild(TEST_SERVER_ID)

                action {
                    try {
                        when (val result =
                            spec.get(client, arguments.tags.orEmpty().split(',').map { it.trim() }, arguments.rating)) {
                            is ImageResult.Success -> {
                                respond {
                                    if (result.url.substringAfterLast(".") !in listOf("webm", "mp4")) {
                                        embed {
                                            title = "By ${result.artist ?: "unknown"}"
                                            image = result.url
                                        }
                                    } else {
                                        content = "By ${result.artist ?: "unknown"}: ${result.url}"
                                    }
                                }
                            }

                            is ImageResult.Failure -> {
                                respond {
                                    content = "Request failed (reason: ${result.reason})"
                                }.delete(5000)
                            }
                        }
                    } catch (_: Exception) {
                        respond {
                            content = "Error while looking up, image with tags may not exist"
                        }.delete(5000)
                    }
                }
            }
        }
    }

    sealed class NSFWImageCommands(
        val name: String,
        val description: String
    ) {
        abstract suspend fun get(client: HttpClient, tags: List<String>, rating: String): ImageResult

        object Danbooru : NSFWImageCommands("Danbooru", "get an explicit image from danbooru") {
            override suspend fun get(client: HttpClient, tags: List<String>, rating: String): ImageResult {
                val response: DanbooruResponse = client.get("https://danbooru.donmai.us/posts/random.json") {
                    val tagString = (tags + "rating:$rating").joinToString(separator = " ") {
                        it.replace(' ', '_')
                    }

                    parameter("tags", tagString)
                }.also { println(it.request.url) }.body()

                if (response.url.isBlank()) {
                    return ImageResult.Failure("Image has no URL or invalid tag")
                }

                return ImageResult.Success(
                    response.urlLarge.ifBlank { response.url },
                    response.artist.ifBlank { null }
                )
            }

            @Serializable
            data class DanbooruResponse(
                @SerialName("large_file_url") val urlLarge: String = "",
                @SerialName("file_url") val url: String = "",
                @SerialName("tag_string_artist") val artist: String = ""
            )

        }

        object E621 : NSFWImageCommands("E621", "get an explicit image from E621") {
            override suspend fun get(client: HttpClient, tags: List<String>, rating: String): ImageResult {
                val ratingTag = when (rating) {
                    "general" -> "safe"
                    "sensitive" -> "questionable"
                    else -> rating
                }
                val response: E6Response = client.get("https://e621.net/posts/random.json") {
                    val tagString = (tags + "rating:$ratingTag").joinToString(separator = " ") {
                        it.replace(' ', '_')
                    }

                    parameter("tags", tagString)
                }.also { println(it.request.url) }.body()

                if (response.post.file.url.isNullOrBlank()) {
                    return ImageResult.Failure("Image has no URL")
                }

                return ImageResult.Success(
                    response.post.file.url,
                    (response.post.tags.artist - "conditional_dnp").joinToString(", ").ifBlank { null }
                )
            }

            @Serializable
            data class E6Response(
                val post: E6Post
            )

            @Serializable
            data class E6Post(
                val file: E6File,
                val tags: E6Tags
            )

            @Serializable
            data class E6File(
                val url: String?
            )

            @Serializable
            data class E6Tags(
                val artist: List<String>
            )

        }

        object Gelbooru : NSFWImageCommands("Gelbooru", "get an explicit image from gelbooru") {
            override suspend fun get(client: HttpClient, tags: List<String>, rating: String): ImageResult {
                val response: GelResponse =
                    client.get("https://gelbooru.com/index.php?page=dapi&s=post&q=index&json=1&limit=1") {
                        val tagString =
                            (tags + "rating:$rating" + "sort:random").joinToString(separator = " ") {
                                it.replace(' ', '_')
                            }
                        parameter("tags", tagString)
                    }.also { println(it.request.url) }.body()

                if (response.post.isEmpty()) {
                    return ImageResult.Failure("No images found")
                }

                if (response.post[0].url.isBlank()) {
                    return ImageResult.Failure("Image has no URL")
                }

                val tagResponse: GelTags = client.get("https://gelbooru.com/index.php?page=dapi&s=tag&q=index&json=1") {
                    parameter("names", response.post[0].tags)
                }.body()

                val artist = tagResponse.tag.filter { it.type == 1 }.joinToString(", ") { it.name }

                return ImageResult.Success(
                    response.post[0].url,
                    artist.ifBlank { null }
                )
            }

            @Serializable
            data class GelResponse(
                val post: List<GelPost> = emptyList()
            )

            @Serializable
            data class GelPost(
                @SerialName("file_url") val url: String,
                val tags: String
            )

            @Serializable
            data class GelTags(
                val tag: List<GelTag>
            )

            @Serializable
            data class GelTag(
                val name: String,
                val type: Int
            )
        }
    }

    class NSFWCommandArguments : Arguments() {
        val tags by optionalString {
            name = "tags"
            description = "give tags seperated by commas or leave empty"
        }

        val rating by defaultingStringChoice {
            name = "Rating"
            description = "Select rating"

            defaultValue = "explicit"

            choices = listOf(
                "explicit",
                "questionable",
                "sensitive",
                "general"
            ).associateBy { it.uppercase() }.toMutableMap()
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



