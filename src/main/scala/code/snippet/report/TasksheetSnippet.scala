package code.snippet

import code.model.User
import code.service.ReportService
import code.service.UserService.nonAdmin
import code.snippet.Params.{parseInterval, parseMonths, parseUser, thisMonth}
import code.snippet.mixin.DateFunctions
import code.util.TaskSheetUtils
import code.util.TaskSheetUtils._
import com.github.nscala_time.time.Imports._
import net.liftweb.common.{Box, Full}
import net.liftweb.http.S
import net.liftweb.util.BindHelpers.strToCssBindPromoter
import net.liftweb.util.CssSel
import org.joda.time.{ReadablePartial, YearMonth}

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
    ("select" #> ("option" #> (dimensions.all map { case (value, text, _) =>
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

    ".dayHeader" #> dates(taskSheet).map(d => ".dayHeader *" #> dayOf(d).map(_.toString).getOrElse(d.toString)) &
        ".TaskRow" #> tasks(taskSheet).map { t =>
          ".taskFullName *" #> t.name & ".taskFullName [title]" #> t.name &
            ".dailyData" #> dates(taskSheet)
              .map(d => ".dailyData *" #> dimensions.print(duration(taskSheet, d, t)) & formatData(i, d)) &
            ".taskSum *" #> dimensions.print(sumByTasks(taskSheet)(t)) &
            ".taskRatio *" #> f"${ratioByTask(taskSheet)(t)}%1.2f"
        } &
        ".dailySum" #> dates(taskSheet).map(d => ".dailySum *" #> dimensions.print(sumByDates(taskSheet)(d))) &
        ".totalSum *" #> dimensions.print(sum(taskSheet))
  }

  def formatData[D <: ReadablePartial](i: Interval, d: D): CssSel =
    ".dailyData [class]" #> Some(d)
      .filter(hasDayFieldType)
      .flatMap(d => mapToDateTime(i, d))
      .filter(isWeekend)
      .map(_ => "colWeekend")
      .getOrElse("colWeekday")

  object dimensions {
    val minutes = ("minutes", "Minutes", (d: Duration) => d.minutes.toString)
    val hours = ("hours", "Hours", (d: Duration) => f"${d.minutes / 60.0d}%1.2f")
    val mandays = ("mandays", "Man-days", (d: Duration) => f"${(d.minutes / 60.0d) / 8.0d}%1.2f")

    val all = List(minutes, hours, mandays)

    def print(d: Duration): String = {
      (S.param("dimension") flatMap (s => all find (_._1 == s) map (_._3)) getOrElse minutes._3)(d)
    }
  }
}
