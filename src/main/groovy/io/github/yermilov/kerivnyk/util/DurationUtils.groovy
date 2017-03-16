package io.github.yermilov.kerivnyk.util

import org.joda.time.Period
import org.joda.time.format.PeriodFormatter
import org.joda.time.format.PeriodFormatterBuilder

class DurationUtils {

    static final PeriodFormatter TIME_LIMIT_FORMAT = new PeriodFormatterBuilder()
            .appendHours().appendSuffix('h')
            .appendMinutes().appendSuffix('min')
            .appendSeconds().appendSuffix('sec')
            .toFormatter()

    static String toDurationString(long duration) {
        String durationString = TIME_LIMIT_FORMAT.print(new Period(duration))
        if (durationString.empty) {
            return '0sec'
        } else {
            return durationString
        }
    }

    static long fromDurationString(String durationAsString) {
        TIME_LIMIT_FORMAT.parsePeriod(durationAsString).toStandardDuration().getStandardSeconds() * 1000
    }
}
