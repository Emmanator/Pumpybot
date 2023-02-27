package io.github.emmanator.extensions

import com.kotlindiscord.kord.extensions.commands.Arguments
import com.kotlindiscord.kord.extensions.commands.application.slash.ephemeralSubCommand
import com.kotlindiscord.kord.extensions.commands.converters.impl.duration
import com.kotlindiscord.kord.extensions.commands.converters.impl.optionalDuration
import com.kotlindiscord.kord.extensions.commands.converters.impl.string
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.extensions.ephemeralSlashCommand
import com.kotlindiscord.kord.extensions.time.TimestampType
import com.kotlindiscord.kord.extensions.time.toDiscord
import com.kotlindiscord.kord.extensions.types.respond
import com.kotlindiscord.kord.extensions.utils.FilterStrategy
import com.kotlindiscord.kord.extensions.utils.suggestStringCollection
import com.kotlindiscord.kord.extensions.utils.toDuration
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.interaction.suggestString
import dev.kord.core.entity.channel.MessageChannel
import dev.kord.rest.builder.message.create.allowedMentions
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.time.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimePeriod
import kotlinx.datetime.TimeZone
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

class ReminderExtension : Extension() {
    override val name = "Reminder"

    private val reminders: MutableMap<Snowflake, MutableMap<String, Job>> = mutableMapOf()

    override suspend fun setup() {
        ephemeralSlashCommand {
            name = "remind"
            description = "Manage your reminders"

            ephemeralSubCommand(ReminderExtension::ReminderArgs) {
                name = "me"
                description = "tells Pumpybot to remind you of something... most likely"


                action {
                    val delay = arguments.time.toDuration(TimeZone.currentSystemDefault())
                    val repeat = arguments.repeatEvery?.toDuration(TimeZone.currentSystemDefault())

                    val userReminders = reminders.getOrPut(user.id, ::mutableMapOf)

                    val id = generateSequence { Random.nextInt(100_000, 1_000_000).toString() }
                        .dropWhile { it in userReminders }
                        .first()

                    respond {
                        content = if (repeat == null) {
                            "Will remind ${(Clock.System.now() + delay).toDiscord(TimestampType.RelativeTime)} (id: `$id`)"
                        } else {
                            "Will remind ${(Clock.System.now() + delay).toDiscord(TimestampType.RelativeTime)}" +
                                    " and then every ${arguments.repeatEvery!!.toPretty()} (id: `$id`)"
                        }
                    }

                    val job = createReminderJob(delay, repeat, user.id, channel.id, id, arguments.description)
                    userReminders[id] = job
                }
            }

            ephemeralSubCommand<CancelReminderArgs>({ CancelReminderArgs() }) {
                name = "cancel"
                description = "Cancel a previously set reminder"


                action {
                    val userReminders = reminders.getOrPut(user.id, ::mutableMapOf)

                    val id = arguments.id.trim()
                    val job = userReminders[id]

                    if (job == null) {
                        respond {
                            content = "You don't have a reminder with ID `$id`"
                        }
                    } else {
                        job.cancel()
                        userReminders -= id

                        respond {
                            content = "Reminder with ID `$id` cancelled"
                        }
                    }
                }
            }

            ephemeralSubCommand {
                name = "list"
                description = "List currently running reminder IDs"

                action {
                    val userReminders = reminders.getOrPut(user.id, ::mutableMapOf)

                    respond {
                        content = if (userReminders.isEmpty()) {
                            "You have no active reminders"
                        } else {
                            userReminders.keys.joinToString(
                                prefix = "${userReminders.size} active reminder(s): ",
                                separator = ", "
                            ) { "`$it`" }
                        }
                    }
                }
            }
        }
    }

    private suspend fun createReminderJob(
        initialDelay: Duration,
        repeatFrequency: Duration?,
        userId: Snowflake,
        channelId: Snowflake,
        reminderId: String,
        reminderDescription: String
    ): Job {
        return kord.launch {
            delay(initialDelay.toJavaDuration())

            if (repeatFrequency != null) {
                while (true) {
                    kord.getChannelOf<MessageChannel>(channelId)?.createMessage {
                        content = "<@${userId.value}> Reminder: ${reminderDescription}! (id: `$reminderId`)"

                        allowedMentions {
                            users += userId
                        }
                    }

                    delay(repeatFrequency.toJavaDuration())
                }
            } else {
                kord.getChannelOf<MessageChannel>(channelId)?.createMessage {
                    content = "<@${userId.value}> Reminder: ${reminderDescription}! (id: `$reminderId`)"

                    allowedMentions {
                        users += userId
                    }
                }
            }
        }
    }

    private fun DateTimePeriod.toPretty(): String {
        return listOf(
            years to "years",
            days to "days",
            hours to "hours",
            minutes to "minutes",
            seconds to "seconds",
            nanoseconds to "nanoseconds"
        ).filter {
            it.first > 0
        }.joinToString(", ") { "${it.first} ${it.second}" }
    }

    class ReminderArgs : Arguments() {
        val time by duration {
            name = "time"
            description = "Delay until reminder"

            longHelp = true
            positiveOnly = true
        }

        val description by string {
            name = "description"
            description = "Reminder message"
        }

        val repeatEvery by optionalDuration {
            name = "frequency"
            description = "How often to repeat the reminder"

            validate {
                failIf(
                    value != null && value!!.toDuration(TimeZone.currentSystemDefault()) < 10.seconds,
                    "Repeats too often, minimum 10 seconds"
                )
            }

            longHelp = true
            positiveOnly = true
        }
    }

    inner class CancelReminderArgs : Arguments() {
        val id by string {
            name = "id"
            description = "ID of the reminder to cancel"

            autoComplete {
                val userReminders = reminders.getOrPut(user.id, ::mutableMapOf)

                if (userReminders.isEmpty()) {
                    suggestString {
                        choice("No reminders set", "No reminders set")
                    }
                } else {
                    suggestStringCollection(userReminders.keys, strategy = FilterStrategy.Contains)
                }
            }
        }
    }
}
