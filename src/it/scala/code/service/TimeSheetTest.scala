package code.service

import code.commons.TimeUtils.deltaInDays
import code.model.{Task, TaskItem, User}
import code.service.TaskItemService.IntervalQuery
import code.test.utils.BaseSuite
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.mapper.By
import org.joda.time.{LocalDate, LocalTime, YearMonth}

class TimeSheetTest extends BaseSuite {
  describe("Time sheet data for any month") {
    lazy val ts = ReportService.getTimesheetData(IntervalQuery(yearMonth(2016, 1).toInterval))

    it("should have log entries subtracted by the breaks") { withS(Empty, defaultUser()) {
      ts map { t => (t._1, t._2, t._3, f"${t._4}%1.1f") } shouldBe List(
        ("29", "08:30", "16:30", "8.0"),
        ("30", "17:00", "23:29", "6.5"),
        ("31", "00:00", "00:30", "0.5")
      )
    }}
  }

  describe("Time sheet data for any month with disabled break subtraction") {
    lazy val ts = {
      UserPreferenceService.setUserPreference(UserPreferenceNames.timesheetLeaveOfftime, "false")
      ReportService.getTimesheetData(IntervalQuery(yearMonth(2016, 1).toInterval))
    }

    it("should have log entries with breaks") { withS(Empty, defaultUser()) {
      ts map { t => (t._1, t._2, t._3, f"${t._4}%1.1f") } shouldBe List(
        ("29", "08:30", "17:00", "8.5"),
        ("30", "17:00", "23:59", "7.0"),
        ("31", "00:00", "00:30", "0.5")
      )
    }}
  }

  describe("Time sheet data for any month with specified additional leave time") {
    lazy val ts = {
      UserPreferenceService.setUserPreference(UserPreferenceNames.timesheetLeaveOfftime, "false")
      UserPreferenceService.setUserPreference(UserPreferenceNames.timesheetLeaveAdditionalTime, "-15")
      ReportService.getTimesheetData(IntervalQuery(yearMonth(2016, 1).toInterval))
    }

    it("should have log entries subtracted by the given time") { withS(Empty, defaultUser()) {
      ts map { t => (t._1, t._2, t._3, f"${t._4}%1.2f") } shouldBe List(
        ("29", "08:30", "16:45", "8.25"),
        ("30", "17:00", "23:44", "6.75"),
        ("31", "00:00", "00:15", "0.25")
      )
    }}
  }

  def defaultUser(): Box[User] = User.find(By(User.email, "default@mail.com"))

  given {
    val u1 :: _ = givenUsers("default") map (_.saveMe())

    val p1 :: p11 :: p12 :: p2 :: _ = traverse(
      project("p1",
        project("p11"),
        project("p12")),
      project("p2")) map (_.saveMe())

    val t1 :: t2 :: t3 :: t4 :: t5 :: t6 :: t7 :: _ = list(
      task("t1", p1), task("t2", p1), task("t3", p11), task("t4", p12), task("t5", p2), task("t6", p2), task("t7", p2)
    ) map (_.saveMe()) map (Full(_))

    val pause = Empty

    givenTaskItems(u1, date(2016, 1, 29),
      t1 -> time(8, 30), t2 -> time(10, 25), pause -> time(12, 0), t3 -> time(12, 30), pause -> time(17, 0)
    ) :::
      givenTaskItems(u1, date(2016, 1, 30),
      t3 -> time(17, 0), pause -> time(22, 30), t7 -> time(23, 0)
    ) :::
      givenTaskItems(u1, date(2016, 1, 31),
        pause -> time(0, 30)
      ) :::
      givenTaskItems(u1, date(2016, 2, 1),
        pause -> time(0, 30), t4 -> time(8, 30), t5 -> time(10, 25), pause -> time(12, 0), t6 -> time(12, 30), pause -> time(17, 0)
      ) foreach (_.save())
  }

  def givenUsers(names: String*): List[User] =
    names map { n => User.create.firstName(n).lastName(n).email(n + "@mail.com").password("abc123").validated(true).superUser(true) } toList

  def givenTaskItems(u: User, ld: LocalDate, tis: (Box[Task], LocalTime)*): List[TaskItem] =
    tis map { case (t, lt) => TaskItem.create.user(u).task(t).start(ld.toDateTime(lt).getMillis) } toList

  def date(year: Int, monthOfYear: Int, dayOfMonth: Int): LocalDate = new LocalDate(year, monthOfYear, dayOfMonth)

  def yearMonth(year: Int, monthOfYear: Int): YearMonth = new YearMonth(year, monthOfYear)

  def time(hourOfDay: Int, minuteOfHour: Int): LocalTime = new LocalTime(hourOfDay, minuteOfHour)
}
