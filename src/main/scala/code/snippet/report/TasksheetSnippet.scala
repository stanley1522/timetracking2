package code.snippet

import code.model.User
import code.service.ReportService.TaskSheet
import code.service.UserService.nonAdmin
import code.service.{ReportService, TaskSheetItem}
import code.snippet.Params.{parseInterval, parseMonths, parseUser, thisMonth}
import code.snippet.mixin.DateFunctions
import code.util.TaskSheetUtils
import code.util.TaskSheetUtils._
import com.github.nscala_time.time.Imports._
import net.liftweb.common.Box
import net.liftweb.http.S
import net.liftweb.util.BindHelpers.strToCssBindPromoter
import net.liftweb.util.CssSel
import org.joda.time.DateTimeConstants.{SATURDAY, SUNDAY}
import org.joda.time.{DateTimeConstants, DateTimeFieldType, ReadablePartial, YearMonth}

import scala.util.Try
import scala.xml.NodeSeq


/**
 * Tasksheet displaying component.
 * @author David Csakvari
 */
class TasksheetSnippet extends DateFunctions {

  /**
   * Tasksheet download link.
   */
  def tasksheetExportLink(in: NodeSeq): NodeSeq = {
    val params = "interval" -> (parseMonths() getOrElse List(YearMonth.now()) mkString ";") ::
      (S.param("user") map (u => List("user" -> u)) getOrElse Nil)
    ("a [href]" #> s"/export/tasksheet?${ params map { case (k, v) => k + "=" + v } mkString "&" }").apply(in)
  }

  def title(in: NodeSeq): NodeSeq = {
    val (interval, scale) = parseInterval() getOrElse thisMonth()
    <span>{TaskSheetUtils.title(interval, scale)}</span>
  }

  def dimensionSelector(in: NodeSeq): NodeSeq = {
    ("select" #> ("option" #> (Durations.empty.all map { case (value, text, _) =>
      val option = "option *" #> text & "option [value]" #> value
      if (S.param("dimension").exists(_ == value)) option & "option [selected]" #> true else option
    }))) apply in
  }

  def tasksheet(in: NodeSeq): NodeSeq = {
    val (interval, scale) = parseInterval() getOrElse thisMonth
    val user = User.currentUser filter nonAdmin or parseUser()

    renderTaskSheet(interval, scale, user)(in)
  }

  def renderTaskSheet[D <: ReadablePartial](i: Interval, f: LocalDate => D, u: Box[User]): CssSel = {
    val taskSheet = ReportService.taskSheetData(i, f, u)
    val durations = new Durations(taskSheet)

    ".dayHeader" #> dates(taskSheet).map(d => ".dayHeader *" #> printDateHeader(d)) &
        ".TaskRow" #> tasks(taskSheet).map { t =>
          ".taskFullName *" #> t.name & ".taskFullName [title]" #> t.name &
            ".dailyData" #> dates(taskSheet)
              .map(d => ".dailyData *" #> durations.print(duration(taskSheet, d, t)) & formatCell(d)) &
            ".taskSum *" #> durations.print(sumByTasks(taskSheet)(t)) &
            ".taskRatio *" #> f"${(sumByTasks(taskSheet)(t).getMillis * 100.0d) / sum(taskSheet).getMillis}%1.2f"
        } &
        ".dailySum" #> dates(taskSheet).map(d => ".dailySum *" #> durations.print(sumByDates(taskSheet)(d))) &
        ".totalSum *" #> durations.print(sum(taskSheet))
  }

  def printDateHeader[D <: ReadablePartial](d: D): String =
    Try(d.get(DateTimeFieldType.dayOfMonth())) map (_.toString) getOrElse d.toString

  def formatCell[D <: ReadablePartial](d: D): CssSel =
    Try(d.get(DateTimeFieldType.dayOfWeek())).toOption flatMap { i =>
      if (i == SATURDAY || i == SUNDAY) Some(".dailyData [class]" #> "colWeekend") else None
    } getOrElse ".dailyData [class]" #> "colWeekday"

  class Durations[D <: ReadablePartial](ts: TaskSheet[D]) {
    val minutes = ("minutes", S.?("dimensions.minutes"), (d: Duration) => d.minutes.toString)
    val hours = ("hours", S.?("dimensions.hours"), (d: Duration) => f"${d.minutes / 60.0d}%1.2f")
    val manDays = ("manDays", S.?("dimensions.manDays"), (d: Duration) => f"${(d.minutes / 60.0d) / 8.0d}%1.2f")

    val all = List(minutes, hours, manDays)

    def print(d: Duration): String = {
      (S.param("dimension") flatMap (s => all find (_._1 == s) map (_._3)) getOrElse minutes._3)(d)
    }
  }

  object Durations {
    val empty = new Durations(Map.empty[ReadablePartial, Map[TaskSheetItem, Duration]])
  }
}
