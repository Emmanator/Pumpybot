package io.github.emmanator.extensions

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.ChoiceEnum
import com.kotlindiscord.kord.extensions.commands.application.slash.converters.impl.defaultingEnumChoice
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalString
import com.kotlindiscord.kord.extensions.components.components
import com.kotlindiscord.kord.extensions.components.publicButton
import com.kotlindiscord.kord.extensions.components.types.emoji
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.publicSlashCommand
import com.kotlindiscord.kord.extensions.types.respond
import com.kotlindiscord.kord.extensions.types.respondEphemeral
import com.kotlindiscord.kord.extensions.utils.capitalizeWords
import com.kotlindiscord.kord.extensions.utils.hasNotStatus
import dev.kord.common.entity.ButtonStyle
import dev.kord.common.entity.ChannelType
import dev.kord.core.behavior.edit
import dev.kord.core.behavior.interaction.followup.edit
import dev.kord.core.entity.Message
import dev.kord.rest.builder.message.modify.MessageModifyBuilder
import dev.kord.rest.builder.message.modify.embed
import dev.kord.rest.request.RestRequestException
import io.ktor.client.*
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.time.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration


class NSFWImageExtension : Extension() {
    private val logger = KotlinLogging.logger {}

    override val name = "Feet Generator"

    private val client = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
            })
        }
    }

    override suspend fun setup() {
        publicSlashCommand {
            name = "feet"
        }

        listOf(
            NSFWImageCommands.Moebooru("https://danbooru.donmai.us/posts", "Danbooru"),
            NSFWImageCommands.Boorulike("https://konachan.com", "KonaChan"),
            NSFWImageCommands.Boorulike("https://yande.re", "Yandere"),
            //NSFWImageCommands.Boorulike("https://xbooru.com", "xbooru"), This should work if the site had a proper API
            NSFWImageCommands.E621,
            NSFWImageCommands.Gelbooru
            //NSFWImageCommands.Gelbooru("https://hypnohub.net", "hypnohub"), This site would also just work if it had a proper random function
            //NSFWImageCommands.Gelbooru("https://api.rule34.xxx/", "rule34"), Same as above, no working random function
        ).forEach { spec ->
            publicSlashCommand(NSFWImageExtension::NSFWCommandArguments) {
                name = spec.name
                description = spec.description

                action {
                    val channelObj = channel.asChannel()

                    val nsfw = channelObj.data.nsfw.orElse(channelObj.type == ChannelType.DM)

                    if (arguments.rating != Rating.GENERAL && !nsfw) {
                        val message = respond {
                            content =  "https://tenor.com/view/powerful-head-slap-anime-death-tragic-gif-14358509"
                        }
                        delay(10.seconds.toJavaDuration())
                        message.delete()
                        return@action
                    }


                    val commandUser = user


                    lateinit var job: Job

                    val message = respond {
                        content = "Please wait..."
                        components {
                            publicButton {
                                emoji("\uD83D\uDD01")
                                style = ButtonStyle.Secondary

                                action {
                                    if (user == commandUser) {
                                        job.cancel()
                                        job = createComponentRemovalJob(message)

                                        message.edit {
                                            setContent(spec, arguments)
                                        }
                                    } else {
                                        respondEphemeral {
                                            content = "Not your embed"
                                        }
                                    }
                                }
                            }

                            publicButton {
                                emoji("\uD83D\uDEAE")
                                style = ButtonStyle.Danger

                                action {
                                    if (user == commandUser) {
                                        job.cancel()
                                        message.delete()
                                    } else {
                                        respondEphemeral {
                                            content = "Not your embed"
                                        }
                                    }
                                }
                            }
                        }
                    }

                    job = createComponentRemovalJob(message.message)

                    message.edit {
                        setContent(spec, arguments)
                    }
                }
            }
        }
    }

    private fun createComponentRemovalJob(message: Message): Job {
        return kord.launch {
            delay(10.seconds.toJavaDuration())

            try {
                message.edit {
                    components = mutableListOf()
                }
            } catch (e: RestRequestException) {
                if (e.hasNotStatus(HttpStatusCode.NotFound)) {
                    throw e
                }
            }
        }
    }

    private suspend fun MessageModifyBuilder.setContent(spec: NSFWImageCommands, arguments: NSFWCommandArguments) {
        try {
            when (val result =
                spec.get(
                    client,
                    arguments.tags.orEmpty().split(',').map { it.trim() },
                    arguments.rating
                )) {
                is ImageResult.Success -> {
                    if (result.url.substringAfterLast(".") !in listOf("webm", "mp4")) {
                        content = null

                        embed {
                            title = "By ${result.artist ?: "unknown"}"
                            image = result.url
                        }
                    } else {
                        embeds = mutableListOf()
                        content = "By ${result.artist ?: "unknown"}: ${result.url}"
                    }
                }

                is ImageResult.Failure -> {
                    embeds = mutableListOf()
                    content = "Request failed (reason: ${result.reason})"
                }
            }
        } catch (e: Exception) {
            logger.debug(e) { "Error while requesting image" }

            embeds = mutableListOf()
            content = "Error while looking up, image with tags may not exist"

        }
    }

    sealed class NSFWImageCommands(
        val name: String,
        val description: String
    ) {
        abstract suspend fun get(client: HttpClient, tags: List<String>, rating: Rating): ImageResult

        class Moebooru(private val baseUrl: String, name: String) :
            NSFWImageCommands(name, "get an image from $name") {
            override suspend fun get(client: HttpClient, tags: List<String>, rating: Rating): ImageResult {
                val response: MoebooruResponse = client.get("$baseUrl/random.json") {
                    val tagString = (tags + "rating:${rating.name.lowercase()}").joinToString(separator = " ") {
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
            data class MoebooruResponse(
                @SerialName("large_file_url") val urlLarge: String = "",
                @SerialName("file_url") val url: String = "",
                @SerialName("tag_string_artist") val artist: String = ""
            )

        }

        class Boorulike(private val baseUrl: String, name: String) :
            NSFWImageCommands(name, "get an image from $name") {
            override suspend fun get(client: HttpClient, tags: List<String>, rating: Rating): ImageResult {
                val ratingTag = when (rating) {
                    Rating.GENERAL -> 's'
                    Rating.SENSITIVE -> 'q'
                    Rating.QUESTIONABLE -> 'q'
                    Rating.EXPLICIT -> 'e'
                }
                val response: List<KonaChanResponse> = client.get("$baseUrl/post.json") {
                    val tagString = (tags + "rating:$ratingTag" + "order:random").joinToString(separator = " ") {
                        it.replace(' ', '_')
                    }

                    parameter("tags", tagString)
                    parameter("limit", 1)
                }.also { println(it.request.url) }.body()

                val image = response.firstOrNull()
                    ?: return ImageResult.Failure("No images with given tags")

                if (image.url.isBlank()) {
                    return ImageResult.Failure("Image has no URL or invalid tag")
                }

                return ImageResult.Success(
                    response.first().url,
                    null
                )
            }

            @Serializable
            data class KonaChanResponse(
                @SerialName("file_url") val url: String = "",
            )
        }

        object E621 : NSFWImageCommands("E621", "get an image from E621") {
            override suspend fun get(client: HttpClient, tags: List<String>, rating: Rating): ImageResult {
                val ratingTag = when (rating) {
                    Rating.GENERAL -> "safe"
                    Rating.SENSITIVE -> "questionable"
                    else -> rating.name.lowercase()
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

        object Gelbooru : NSFWImageCommands("Gelbooru", "get an image from gelbooru") {
            override suspend fun get(client: HttpClient, tags: List<String>, rating: Rating): ImageResult {
                val response: GelResponse =
                    client.get("https://gelbooru.com/index.php?page=dapi&s=post&q=index&json=1&limit=1") {
                        val tagString =
                            (tags + "rating:${rating.name.lowercase()}" + "sort:random").joinToString(separator = " ") {
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
            description = "Give tags seperated by commas or leave empty."
        }

        val rating by defaultingEnumChoice<Rating> {
            name = "rating"
            description = "Select rating."

            defaultValue = Rating.GENERAL

            typeName = Rating::class.java.typeName
        }
    }

    enum class Rating : ChoiceEnum {
        GENERAL,
        SENSITIVE,
        QUESTIONABLE,
        EXPLICIT;

        override val readableName = name.lowercase().capitalizeWords()
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
