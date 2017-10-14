package com.soywiz.korio.time

import com.soywiz.korio.KorioNative
import com.soywiz.korio.error.invalidOp
import com.soywiz.korio.lang.format
import com.soywiz.korio.lang.splitKeep
import com.soywiz.korio.math.clamp
import com.soywiz.korio.util.substr
import kotlin.math.abs

enum class DayOfWeek(val index: Int) {
	Sunday(0), Monday(1), Tuesday(2), Wednesday(3), Thursday(4), Friday(5), Saturday(6);

	companion object {
		val BY_INDEX = values()
		operator fun get(index: Int) = BY_INDEX[index]
	}
}

class Year(val year: Int) {
	companion object {
		fun check(year: Int) = run { if (year !in 1..9999) throw DateException("Year $year not in 1..9999") }

		fun isLeap(year: Int): Boolean {
			check(year)
			return year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
		}
	}
}

enum class Month(val index: Int) {
	January(1), // 31
	February(2), // 28/29
	March(3), // 31
	April(4), // 30
	May(5), // 31
	June(6), // 30
	July(7), // 31
	August(8), // 31
	September(9), // 30
	October(10), // 31
	November(11), // 30
	December(12); // 31

	val index0: Int get() = index - 1

	fun days(isLeap: Boolean): Int = days(index, isLeap)
	fun daysToStart(isLeap: Boolean): Int = daysToStart(index, isLeap)
	fun daysToEnd(isLeap: Boolean): Int = daysToEnd(index, isLeap)

	fun days(year: Int): Int = days(index, year)
	fun daysToStart(year: Int): Int = daysToStart(index, year)
	fun daysToEnd(year: Int): Int = daysToEnd(index, year)

	companion object {
		val BY_INDEX0 = values()
		operator fun get(index1: Int) = BY_INDEX0[index1 - 1]

		fun check(month: Int) = run { if (month !in 1..12) throw DateException("Month $month not in 1..12") }

		fun days(month: Int, isLeap: Boolean): Int {
			Month.check(month)
			val days = DAYS_TO_MONTH(isLeap)
			return days[month] - days[month - 1]
		}

		fun daysToStart(month: Int, isLeap: Boolean): Int = DAYS_TO_MONTH(isLeap)[month - 1]
		fun daysToEnd(month: Int, isLeap: Boolean): Int = DAYS_TO_MONTH(isLeap)[month]

		fun days(month: Int, year: Int): Int = days(month, Year.isLeap(year))
		fun daysToStart(month: Int, year: Int): Int = daysToStart(month, Year.isLeap(year))
		fun daysToEnd(month: Int, year: Int): Int = daysToEnd(month, Year.isLeap(year))

		fun DAYS_TO_MONTH(isLeap: Boolean): IntArray = if (isLeap) DAYS_TO_MONTH_366 else DAYS_TO_MONTH_365

		val DAYS_TO_MONTH_366 = intArrayOf(0, 31, 60, 91, 121, 152, 182, 213, 244, 274, 305, 335, 366)
		val DAYS_TO_MONTH_365 = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334, 365)
	}
}

class DateException(msg: String) : RuntimeException(msg)

interface DateTime {
	val year: Int
	val month: Int
	val dayOfWeek: DayOfWeek
	val dayOfMonth: Int
	val dayOfYear: Int
	val hours: Int
	val minutes: Int
	val seconds: Int
	val milliseconds: Int
	val timeZone: String
	val unix: Long
	val offset: Int
	val utc: UtcDateTime
	fun add(deltaMonths: Int, deltaMilliseconds: Long): DateTime

	val month0: Int get() = month - 1
	val month1: Int get() = month
	val monthEnum: Month get() = Month[month1]

	fun toUtc(): DateTime = utc
	fun toLocal() = OffsetDateTime(this, KorioNative.getLocalTimezoneOffset(unix))
	fun addOffset(offset: Int) = OffsetDateTime(this, this.offset + offset)
	fun toOffset(offset: Int) = OffsetDateTime(this, offset)
	fun addYears(delta: Int): DateTime = add(delta * 12, 0L)
	fun addMonths(delta: Int): DateTime = add(delta, 0L)
	fun addDays(delta: Double): DateTime = add(0, (delta * UtcDateTime.MILLIS_PER_DAY).toLong())
	fun addHours(delta: Double): DateTime = add(0, (delta * UtcDateTime.MILLIS_PER_HOUR).toLong())
	fun addMinutes(delta: Double): DateTime = add(0, (delta * UtcDateTime.MILLIS_PER_MINUTE).toLong())
	fun addSeconds(delta: Double): DateTime = add(0, (delta * UtcDateTime.MILLIS_PER_SECOND).toLong())
	fun addMilliseconds(delta: Double): DateTime = if (delta == 0.0) this else add(0, delta.toLong())
	fun addMilliseconds(delta: Long): DateTime = if (delta == 0L) this else add(0, delta)

	operator fun plus(delta: TimeDistance): DateTime = this.add(delta.years * 12 + delta.months, (delta.days * UtcDateTime.MILLIS_PER_DAY + delta.hours * UtcDateTime.MILLIS_PER_HOUR + delta.minutes * UtcDateTime.MILLIS_PER_MINUTE + delta.seconds * UtcDateTime.MILLIS_PER_SECOND + delta.milliseconds).toLong())
	operator fun minus(delta: TimeDistance): DateTime = this + -delta

	companion object {
		val EPOCH by lazy { DateTime(1970, 1, 1, 0, 0, 0) as UtcDateTime }
		internal val EPOCH_INTERNAL_MILLIS by lazy { EPOCH.internalMillis }

		operator fun invoke(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0, second: Int = 0, milliseconds: Int = 0): DateTime {
			return UtcDateTime(UtcDateTime.dateToMillis(year, month, day) + UtcDateTime.timeToMillis(hour, minute, second) + milliseconds, true)
		}

		operator fun invoke(time: Long) = fromUnix(time)

		fun fromUnix(time: Long): DateTime = UtcDateTime(EPOCH_INTERNAL_MILLIS + time, true)
		fun fromUnixLocal(time: Long): DateTime = UtcDateTime(EPOCH_INTERNAL_MILLIS + time, true).toLocal()

		fun nowUnix() = KorioNative.currentTimeMillis()
		fun now() = fromUnix(nowUnix())
		fun nowLocal() = fromUnix(nowUnix()).toLocal()

		fun createAdjusted(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0, second: Int = 0, milliseconds: Int = 0): DateTime {
			val dy = clamp(year, 1, 9999)
			val dm = clamp(month, 1, 12)
			val dd = clamp(day, 1, daysInMonth(dm, dy))
			val th = clamp(hour, 0, 23)
			val tm = clamp(minute, 0, 59)
			val ts = clamp(second, 0, 59)
			return DateTime(dy, dm, dd, th, tm, ts, milliseconds)
		}

		fun isLeapYear(year: Int): Boolean {
			Year.check(year)
			return year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)
		}

		fun daysInMonth(month: Int, isLeap: Boolean): Int = Month.days(month, isLeap)
		fun daysInMonth(month: Int, year: Int): Int = daysInMonth(month, isLeapYear(year))
	}
}

class OffsetDateTime private constructor(
	override val utc: UtcDateTime,
	override val offset: Int,
	val adjusted: DateTime = utc.addMinutes(offset.toDouble())
) : DateTime by adjusted {
	private val deltaTotalMinutesAbs: Int = abs(offset)
	val positive: Boolean get() = offset >= 0
	val deltaHoursAbs: Int get() = deltaTotalMinutesAbs / 60
	val deltaMinutesAbs: Int get() = deltaTotalMinutesAbs % 60

	companion object {
		//operator fun invoke(utc: DateTime, offset: Int) = OffsetDateTime(utc.utc, utc.offsetTotalMinutes + offset)
		operator fun invoke(utc: DateTime, offset: Int) = OffsetDateTime(utc.utc, offset)
	}

	override val timeZone: String = "GMT%s%02d%02d".format(
		if (positive) "+" else "-",
		deltaHoursAbs,
		deltaMinutesAbs
	)

	override fun add(deltaMonths: Int, deltaMilliseconds: Long): DateTime =
		OffsetDateTime(utc.add(deltaMonths, deltaMilliseconds), offset)

	override fun toString(): String = SimplerDateFormat.DEFAULT_FORMAT.format(this)
	fun toString(format: String): String = SimplerDateFormat(format).format(this)
}

// From .NET DateTime
class UtcDateTime internal constructor(internal val internalMillis: Long, dummy: Boolean) : DateTime {
	companion object {
		internal const val MILLIS_PER_SECOND = 1000
		internal const val MILLIS_PER_MINUTE = this.MILLIS_PER_SECOND * 60
		internal const val MILLIS_PER_HOUR = this.MILLIS_PER_MINUTE * 60
		internal const val MILLIS_PER_DAY = this.MILLIS_PER_HOUR * 24

		internal const val DAYS_PER_YEAR = 365
		internal const val DAYS_PER_4_YEARS = DAYS_PER_YEAR * 4 + 1
		internal const val DAYS_PER_100_YEARS = DAYS_PER_4_YEARS * 25 - 1
		internal const val DAYS_PER_400_YEARS = DAYS_PER_100_YEARS * 4 + 1

		internal const val DATE_PART_YEAR = 0
		internal const val DATE_PART_DAY_OF_YEAR = 1
		internal const val DATE_PART_MONTH = 2
		internal const val DATE_PART_DAY = 3

		internal fun dateToMillis(year: Int, month: Int, day: Int): Long {
			Year.check(year)
			Month.check(month)
			if (day !in 1..Month.days(month, year)) throw DateException("Day $day not valid for year=$year and month=$month")
			val y = year - 1
			Month.daysToStart(month, year)
			val n = y * 365 + y / 4 - y / 100 + y / 400 + Month.daysToStart(month, year) + day - 1
			return n.toLong() * this.MILLIS_PER_DAY.toLong()
		}

		internal fun timeToMillis(hour: Int, minute: Int, second: Int): Long {
			if (hour !in 0..23) throw DateException("Hour $hour not in 0..23")
			if (minute !in 0..59) throw DateException("Minute $minute not in 0..59")
			if (second !in 0..59) throw DateException("Second $second not in 0..59")
			val totalSeconds = hour.toLong() * 3600 + minute.toLong() * 60 + second.toLong()
			return totalSeconds * this.MILLIS_PER_SECOND
		}

		internal fun getDatePart(millis: Long, part: Int): Int {
			var n = (millis / this.MILLIS_PER_DAY).toInt()
			val y400 = n / DAYS_PER_400_YEARS
			n -= y400 * DAYS_PER_400_YEARS
			var y100 = n / DAYS_PER_100_YEARS
			if (y100 == 4) y100 = 3
			n -= y100 * DAYS_PER_100_YEARS
			val y4 = n / DAYS_PER_4_YEARS
			n -= y4 * DAYS_PER_4_YEARS
			var y1 = n / DAYS_PER_YEAR
			if (y1 == 4) y1 = 3
			if (part == DATE_PART_YEAR) return y400 * 400 + y100 * 100 + y4 * 4 + y1 + 1
			n -= y1 * DAYS_PER_YEAR
			if (part == DATE_PART_DAY_OF_YEAR) return n + 1
			val leapYear = y1 == 3 && (y4 != 24 || y100 == 3)
			var m = n shr 5 + 1
			while (n >= Month.daysToEnd(m, leapYear)) m++
			return if (part == DATE_PART_MONTH) m else n - Month.daysToStart(m, leapYear) + 1
		}
	}

	private fun getDatePart(part: Int): Int = Companion.getDatePart(internalMillis, part)
	override val offset: Int = 0

	override val utc: UtcDateTime = this
	override val unix: Long get() = (internalMillis - DateTime.EPOCH.internalMillis)
	val time: Long get() = unix
	override val year: Int get() = getDatePart(DATE_PART_YEAR)
	override val month: Int get() = getDatePart(DATE_PART_MONTH)
	override val dayOfMonth: Int get() = getDatePart(DATE_PART_DAY)
	val dayOfWeekInt: Int get() = ((internalMillis / MILLIS_PER_DAY + 1) % 7).toInt()
	override val dayOfWeek: DayOfWeek get() = DayOfWeek[dayOfWeekInt]
	override val dayOfYear: Int get() = getDatePart(DATE_PART_DAY_OF_YEAR)
	override val hours: Int get() = (((internalMillis / MILLIS_PER_HOUR) % 24).toInt())
	override val minutes: Int get() = ((internalMillis / MILLIS_PER_MINUTE) % 60).toInt()
	override val seconds: Int get() = ((internalMillis / MILLIS_PER_SECOND) % 60).toInt()
	override val milliseconds: Int get() = ((internalMillis) % 1000).toInt()
	override val timeZone: String get() = "UTC"

	override fun add(deltaMonths: Int, deltaMilliseconds: Long): DateTime = when {
		deltaMonths == 0 && deltaMilliseconds == 0L -> this
		deltaMonths == 0 -> UtcDateTime(this.internalMillis + deltaMilliseconds, true)
		else -> {
			var year = this.year
			var month = this.month
			var day = this.dayOfMonth
			val i = month - 1 + deltaMonths
			if (i >= 0) {
				month = i % 12 + 1
				year += i / 12
			} else {
				month = 12 + (i + 1) % 12
				year += (i - 11) / 12
			}
			Year.check(year)
			val days = Month.days(month, year)
			if (day > days) day = days

			UtcDateTime(dateToMillis(year, month, day) + (internalMillis % MILLIS_PER_DAY) + deltaMilliseconds, true)
		}
	}

	operator fun compareTo(other: DateTime): Int = this.unix.compareTo(other.unix)
	override fun hashCode(): Int = internalMillis.hashCode()
	override fun equals(other: Any?): Boolean = this.unix == (other as? DateTime?)?.unix
	override fun toString(): String = SimplerDateFormat.DEFAULT_FORMAT.format(this)
	fun toString(format: String): String = SimplerDateFormat(format).format(this)
}

data class TimeDistance(val years: Int = 0, val months: Int = 0, val days: Double = 0.0, val hours: Double = 0.0, val minutes: Double = 0.0, val seconds: Double = 0.0, val milliseconds: Double = 0.0) {
	operator fun unaryMinus() = TimeDistance(-years, -months, -days, -hours, -minutes, -seconds, -milliseconds)

	operator fun minus(other: TimeDistance) = this + -other

	operator fun plus(other: TimeDistance) = TimeDistance(
		years + other.years,
		months + other.months,
		days + other.days,
		hours + other.hours,
		minutes + other.minutes,
		seconds + other.seconds,
		milliseconds + other.milliseconds
	)

	operator fun times(times: Double) = TimeDistance(
		(years * times).toInt(),
		(months * times).toInt(),
		days * times,
		hours * times,
		minutes * times,
		seconds * times,
		milliseconds * times
	)
}

inline val Int.years get() = TimeDistance(years = this)
inline val Int.months get() = TimeDistance(months = this)
inline val Number.days get() = TimeDistance(days = this.toDouble())
inline val Number.hours get() = TimeDistance(hours = this.toDouble())
inline val Number.minutes get() = TimeDistance(minutes = this.toDouble())
//inline val Number.seconds get() = TimeAdd(seconds = this.toDouble())

@Suppress("DataClassPrivateConstructor")
//data class TimeSpan private constructor(val ms: Int) : Comparable<TimeSpan>, Interpolable<TimeSpan> {
data class TimeSpan private constructor(val ms: Int) : Comparable<TimeSpan> {
	val milliseconds: Int get() = this.ms
	val seconds: Double get() = this.ms.toDouble() / 1000.0

	companion object {
		val ZERO = TimeSpan(0)
		@PublishedApi internal fun fromMilliseconds(ms: Int) = when (ms) {
			0 -> ZERO
			else -> TimeSpan(ms)
		}
	}

	override fun compareTo(other: TimeSpan): Int = this.ms.compareTo(other.ms)

	operator fun plus(other: TimeSpan): TimeSpan = TimeSpan(this.ms + other.ms)
	operator fun minus(other: TimeSpan): TimeSpan = TimeSpan(this.ms - other.ms)
	operator fun times(scale: Int): TimeSpan = TimeSpan(this.ms * scale)
	operator fun times(scale: Double): TimeSpan = TimeSpan((this.ms * scale).toInt())

	//override fun interpolateWith(other: TimeSpan, ratio: Double): TimeSpan = TimeSpan(ratio.interpolate(this.ms, other.ms))
}

inline val Number.milliseconds get() = TimeSpan.fromMilliseconds(this.toInt())
inline val Number.seconds get() = TimeSpan.fromMilliseconds((this.toDouble() * 1000.0).toInt())

class SimplerDateFormat(val format: String) {
	companion object {
		private val rx = Regex("[\\w]+")
		private val englishDaysOfWeek = listOf(
			"sunday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday"
		)
		private val englishMonths = listOf(
			"january", "february", "march", "april", "may", "june",
			"july", "august", "september", "october", "november", "december"
		)
		private val englishMonths3 = englishMonths.map { it.substr(0, 3) }

		val DEFAULT_FORMAT by lazy { SimplerDateFormat("EEE, dd MMM yyyy HH:mm:ss z") }
	}

	private val parts = arrayListOf<String>()
	//val escapedFormat = Regex.escape(format)
	private val escapedFormat = Regex.escapeReplacement(format)

	private val rx2: Regex = Regex("^" + escapedFormat.replace(rx) { result ->
		parts += result.groupValues[0]
		"([\\w\\+\\-]+)"
	} + "$")

	private val parts2 = escapedFormat.splitKeep(rx)

	// EEE, dd MMM yyyy HH:mm:ss z -- > Sun, 06 Nov 1994 08:49:37 GMT
	// YYYY-MM-dd HH:mm:ss

	fun format(date: Long): String = format(DateTime.fromUnix(date))

	fun format(dd: DateTime): String {
		var out = ""
		for (name in parts2) {
			out += when (name) {
				"EEE" -> englishDaysOfWeek[dd.dayOfWeek.index].substr(0, 3).capitalize()
				"z", "zzz" -> dd.timeZone
				"d" -> "%d".format(dd.dayOfMonth)
				"dd" -> "%02d".format(dd.dayOfMonth)
				"MM" -> "%02d".format(dd.month1)
				"MMM" -> englishMonths[dd.month0].substr(0, 3).capitalize()
				"yyyy" -> "%04d".format(dd.year)
				"YYYY" -> "%04d".format(dd.year)
				"HH" -> "%02d".format(dd.hours)
				"mm" -> "%02d".format(dd.minutes)
				"ss" -> "%02d".format(dd.seconds)
				else -> name
			}
		}
		return out
	}

	fun parse(str: String): Long = parseDate(str).unix

	fun parseDate(str: String): DateTime {
		var second = 0
		var minute = 0
		var hour = 0
		var day = 1
		var month = 1
		var fullYear = 1970
		val result = rx2.find(str) ?: invalidOp("Not a valid format: '$str' for '$format'")
		for ((name, value) in parts.zip(result.groupValues.drop(1))) {
			when (name) {
				"EEE" -> Unit // day of week (Sun)
				"z", "zzz" -> Unit // timezone (GMT)
				"d", "dd" -> day = value.toInt()
				"MM" -> month = value.toInt()
				"MMM" -> month = englishMonths3.indexOf(value.toLowerCase()) + 1
				"yyyy", "YYYY" -> fullYear = value.toInt()
				"HH" -> hour = value.toInt()
				"mm" -> minute = value.toInt()
				"ss" -> second = value.toInt()
				else -> {
					// ...
				}
			}
		}
		return DateTime(fullYear, month, day, hour, minute, second)
	}
}
