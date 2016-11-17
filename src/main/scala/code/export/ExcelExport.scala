package code
package export

import java.io._
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

import scala.collection.JavaConversions._
import scala.util.Try
import org.apache.poi.hssf.usermodel._
import org.apache.poi.poifs.filesystem.POIFSFileSystem
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.IndexedColors
import org.apache.poi.ss.util.CellReference
import org.joda.time.{DateTime, Interval, ReadablePartial}
import code.commons.TimeUtils
import code.model._
import net.liftweb.util.Props
import net.liftweb.http.S
import org.apache.poi.ss.usermodel.CellStyle
import org.apache.poi.ss.usermodel.Font
import org.apache.poi.ss.util.CellRangeAddress
import code.service.ReportService
import code.service.ReportService.TaskSheet
import code.util.TaskSheetUtils._

/**
 * Excel export features.
 */
object ExcelExport {

  val templatePath = Props.get("export.excel.timesheet_template").openOrThrowException("No Excel template defined for Timesheets!")

  /**
   * Timesheet Excel export.
   */
  def exportTimesheet(user: User, offset: Int) = {
    var fos: ByteArrayOutputStream = null;
    var array: Array[Byte] = null
    try {

      // template
      val fs = new POIFSFileSystem(new FileInputStream(templatePath))
      val workbook = new HSSFWorkbook(fs, true)

      // parameters
      val userName = user.lastName + " " + user.firstName
      val monthText = TimeUtils.currentYear(offset) + " " + TimeUtils.monthNumberToText(TimeUtils.currentMonth(offset))
      val dates = ReportService.getTimesheetData(offset)

      // spreadsheet to export
      val sheet = workbook.getSheet("Timesheet")

      /**
       * Finds and returns the first cell in the sheet that contains the given text.
       */
      def findCell(text: String): Option[Cell] = {
        for (row <- sheet.rowIterator(); cell <- row) {
          if (cell.getCellType() == 1 && text.equals(cell.getStringCellValue())) {
            return Some(cell)
          }
        }
        None
      }

      // localize texts
      findCell("{ta_text_date}") foreach { cell => cell.setCellValue(S.?("timesheet.date")) }
      findCell("{ta_text_arrival}") foreach { cell => cell.setCellValue(S.?("timesheet.arrival")) }
      findCell("{ta_text_leave}") foreach { cell => cell.setCellValue(S.?("timesheet.leave")) }
      findCell("{ta_text_time_sum_hour}") foreach { cell => cell.setCellValue(S.?("timesheet.time_sum_hour")) }

      // insert data
      findCell("{ta_name}") foreach { cell => cell.setCellValue(userName) }
      findCell("{ta_month}") foreach { cell => cell.setCellValue(monthText) }
      for (i <- 1 to 31) {
        val data = if (dates.size >= i) dates(i - 1) else (null, null, null, null)
        findCell("{ta_date_" + i + "}") foreach { cell => cell.setCellValue(data._1) }
        findCell("{ta_arrive_" + i + "}") foreach { cell => cell.setCellValue(data._2) }
        findCell("{ta_leave_" + i + "}") foreach { cell => cell.setCellValue(data._3) }
      }

      // write sheet
      fos = new ByteArrayOutputStream()
      workbook.write(fos)
    } catch {
      case e: Exception => e.printStackTrace
    } finally {
      if (fos != null) {
        try {
          fos.flush();
          array = fos.toByteArray
          fos.close();
        } catch {
          case e: IOException => e.printStackTrace
        }
      }
    }

    val contentStream = new ByteArrayInputStream(array)
    val name = "timesheet_" + TimeUtils.currentYear(offset.toInt) + "-" + (TimeUtils.currentMonth(offset.toInt) + 1) + ".xls";

    (contentStream, name)
  }

  def exportTasksheet(blank: Boolean, user: User, offset: Int): (InputStream, String) = {
    var fos: ByteArrayOutputStream = null
    var array: Array[Byte] = null

    val date = new DateTime(TimeUtils.currentDayStartInMs(offset))

    try {
      // Initialize workbook
      val workbook = new HSSFWorkbook
      val sheet = workbook.createSheet("Tasksheet")

      val interval = date.monthOfYear().toInterval
      val taskSheet = ReportService.taskSheetData(User.currentUser, interval, d => d)

      val ds = dates(taskSheet)

      //FIXME: title text (month index)
      renderTaskSheetTitle(workbook, sheet, date.getYear + ". " + TimeUtils.monthNumberToText(date.getMonthOfYear - 1), rowNum = 0, dates(taskSheet).length + 1)
      renderTaskSheetFieldNames(workbook, sheet, ds, interval, rowNum = 1)
      val rowNum = renderContent(workbook, sheet, taskSheet, interval, 2)
      renderSummary(workbook, sheet, rowNum, ds.length)

      sheet.createFreezePane(1, 2)

      fos = new ByteArrayOutputStream()
      workbook.write(fos)

    } catch {
      case e: Exception => e.printStackTrace
    } finally {
      if (fos != null) {
        try {
          fos.flush();
          array = fos.toByteArray
          fos.close();
        } catch {
          case e: IOException => e.printStackTrace
        }
      }
    }

    val contentStream = new ByteArrayInputStream(array)
    val fileName = "tasksheet_" + date.getYear + "-" + date.getMonthOfYear + "_" + user.firstName.get.toLowerCase + user.lastName.get.toLowerCase + ".xls"

    (contentStream, fileName)
  }

  def renderTaskSheetTitle(workbook: HSSFWorkbook, sheet: HSSFSheet, title: String, rowNum: Int, rowLength: Int): Unit = {
    val row = sheet.createRow(rowNum)
    val cell = row.createCell(1)
    cell.setCellStyle(Styles.centeredBoldCell(workbook))
    cell.setCellValue(title)
    sheet.addMergedRegion(new CellRangeAddress(0, 0, 1, rowLength))
  }

  def renderTaskSheetFieldNames(workbook: HSSFWorkbook, sheet: HSSFSheet, dates: List[ReadablePartial], interval: Interval, rowNum: Int): Unit = {
    val row = sheet.createRow(rowNum)
    val taskHeader = row.createCell(0)
    taskHeader.setCellStyle(Styles.headerCell(workbook))
    taskHeader.setCellValue(S.?("export.tasksheet.project_identifier"))

    dates.zip(1 to dates.length).foreach { case (d, index) =>
      val dateHeader = row.createCell(index)
      dateHeader.setCellValue(dayOf(d).map(_.toString).getOrElse(d.toString))
      if (mapToDateTime(interval, d).exists(isWeekend)) {
        dateHeader.setCellStyle(Styles.weekendHeader(workbook))
      } else {
        dateHeader.setCellStyle(Styles.headerCell(workbook))
      }
    }
    val sumHeader = row.createCell(dates.length)
    sumHeader.setCellStyle(Styles.headerCell(workbook))
    sumHeader.setCellValue(S.?("export.tasksheet.sum"))
  }

  def renderContent[D <: ReadablePartial](workbook: HSSFWorkbook, sheet: HSSFSheet, taskSheet: TaskSheet[D], interval: Interval, rowNum: Int): Int = {
    var tmpRowNum = rowNum
    tasks(taskSheet).foreach { t =>
      val itemRow = sheet.createRow(tmpRowNum)
      val taskNameCell = itemRow.createCell(0)
      taskNameCell.setCellValue(t.name)

      val ds = dates(taskSheet)
      ds.zip(1 to ds.length).foreach { case (d, index) =>
        val dayCell = itemRow.createCell(index)
        dayCell.setCellType(Cell.CELL_TYPE_NUMERIC)

        Try(formattedDurationInMinutes(taskSheet, d, t).toDouble).foreach(dayCell.setCellValue)
        if (mapToDateTime(interval, d).exists(isWeekend)) {
          dayCell.setCellStyle(Styles.weekendCell(workbook))
        } else {
          dayCell.setCellStyle(Styles.boldCell(workbook))
        }
      }
      val sumCell = itemRow.createCell(ds.length)
      sumCell.setCellType(Cell.CELL_TYPE_FORMULA)
      sumCell.setCellStyle(Styles.boldCell(workbook))
      tmpRowNum += 1
      sumCell.setCellFormula("SUM(" + CellReference.convertNumToColString(2) + tmpRowNum + ":" + CellReference.convertNumToColString(ds.length - 1) + tmpRowNum + ")")
    }
    tmpRowNum
  }

  def renderSummary(workbook: HSSFWorkbook, sheet: HSSFSheet, rowNum: Int, rowLength: Int): Unit = {
    val summaryRow = sheet.createRow(rowNum)
    val summaryTextCell = summaryRow.createCell(0)
    summaryTextCell.setCellStyle(Styles.footerCell(workbook))
    summaryTextCell.setCellValue(S.?("export.tasksheet.sum"))
    sheet.addMergedRegion(new CellRangeAddress(rowNum, rowNum, 0, 0))
    for (i <- 1 to rowLength) {
      val sumCell = summaryRow.createCell(i)
      sumCell.setCellStyle(Styles.footerCell(workbook))
      sumCell.setCellType(Cell.CELL_TYPE_FORMULA)
      val colName = CellReference.convertNumToColString(i)
      sumCell.setCellFormula("SUM(" + colName + "3:" + colName + "" + rowNum + ")")
    }
  }

  object Styles {
    def boldFont(workbook: HSSFWorkbook): HSSFFont = {
      val font = workbook.createFont()
      font.setBoldweight(Font.BOLDWEIGHT_BOLD)
      font
    }

    def weekendCell(workbook: HSSFWorkbook): HSSFCellStyle = {
      val style = workbook.createCellStyle()
      style.setFont(boldFont(workbook))
      style.setFillPattern(CellStyle.SOLID_FOREGROUND)
      style.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex())
      style
    }

    def boldCell(workbook: HSSFWorkbook): HSSFCellStyle = {
      val style = workbook.createCellStyle()
      style.setFont(boldFont(workbook));
      style
    }

    def centeredBoldCell(workbook: HSSFWorkbook): HSSFCellStyle = {
      val style = workbook.createCellStyle()
      style.cloneStyleFrom(boldCell(workbook))
      style.setAlignment(CellStyle.ALIGN_CENTER)
      style
    }

    def weekendHeader(workbook: HSSFWorkbook): HSSFCellStyle = {
      val style = workbook.createCellStyle()
      style.cloneStyleFrom(weekendCell(workbook))
      style.setBorderBottom(CellStyle.BORDER_THIN)
      style
    }

    def footerCell(workbook: HSSFWorkbook): HSSFCellStyle = {
      val style = workbook.createCellStyle()
      style.cloneStyleFrom(boldCell(workbook))
      style.setBorderTop(CellStyle.BORDER_THIN)
      style
    }

    def headerCell(workbook: HSSFWorkbook): HSSFCellStyle = {
      val style = workbook.createCellStyle()
      style.cloneStyleFrom(boldCell(workbook))
      style.setBorderBottom(CellStyle.BORDER_THIN)
      style
    }
  }
}
