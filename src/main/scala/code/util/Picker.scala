package code.util

import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.time.{DayOfWeek, Month}
import java.util.Locale

import net.liftweb.http.S
import net.liftweb.http.js.JE._
import net.liftweb.http.js.JsObj
import org.joda.time.LocalDate
import org.joda.time.format.DateTimeFormat

protected class Picker {
  DateTimeFormat.shortDate().withLocale(S.locale)

  protected def dateShortPattern(l: Locale): String =
    // for the datepicker 'M' means short month name instead of month number
    DateTimeFormat.patternForStyle("S-", l).replaceAll("M", "m").replaceAll("yy", "y")

  protected def firstDayOfWeek(l: Locale): Num =
    Num((WeekFields.of(l).getFirstDayOfWeek.getValue + 1) % 7 - 1)

  protected def monthsOfYear(s: TextStyle, l: Locale): JsArray =
    JsArray(Month.values().toList map (_.getDisplayName(s, l)) map Str)

  protected def daysOfWeek(s: TextStyle, l: Locale): JsArray =
    JsArray((DayOfWeek.values().toList span (_ != DayOfWeek.SUNDAY) match {
      case (end, start) => start ++ end
    }) map (_.getDisplayName(s, l)) map Str)
}

object DatePicker extends Picker {

  def configuration: JsObj = JsObj(
    "altField" -> Str("[name='date']"),
    "altFormat" -> Str("yy-mm-dd"),
    "dateFormat" -> Str(dateShortPattern(S.locale)),
    "maxDate" -> Num(0),
    "firstDay" -> firstDayOfWeek(S.locale),
    "monthNames" -> monthsOfYear(TextStyle.FULL, S.locale),
    "monthNamesShort" -> monthsOfYear(TextStyle.SHORT, S.locale),
    "dayNames" -> daysOfWeek(TextStyle.FULL, S.locale),
    "dayNamesShort" -> daysOfWeek(TextStyle.SHORT, S.locale),
    "dayNamesMin" -> daysOfWeek(TextStyle.NARROW, S.locale),
    "nextText" -> Str(S.?("button.next")),
    "prevText" -> Str(S.?("button.previous"))
  )
}

object MonthPicker extends Picker {
  def configuration: JsObj = JsObj(
    "dateFormat" -> Str("yy-mm-dd"),
    "maxDate" -> Num(0),
    "monthNames" -> monthsOfYear(TextStyle.FULL, S.locale),
    "monthNamesShort" -> monthsOfYear(TextStyle.SHORT, S.locale),
    "nextText" -> Str(S.?("button.next")),
    "prevText" -> Str(S.?("button.previous")),
    "changeYear" -> true,
    "changeMonth" -> true
  )
}