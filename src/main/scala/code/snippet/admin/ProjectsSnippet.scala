package code
package snippet

import java.text.Collator

import _root_.net.liftweb.util.CssSel
import code.model._
import code.service._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.http.js.JE.JsRaw
import net.liftweb.http.js.JsCmd
import net.liftweb.http.js.JsCmds.SetHtml
import net.liftweb.http.{S, SHtml, _}
import net.liftweb.mapper.By
import net.liftweb.util.Helpers._

import scala.util.Sorting
import scala.xml.NodeSeq

/**
 * Task administration snippet.
 * @author David Csakvari
 */
class ProjectsSnippet {

  private val collator: Collator = Collator.getInstance(S.locale)

  object showInactiveTasks extends SessionVar(false)

  object selectedTask extends SessionVar[Box[Task]](Empty)

  def toggleInactiveView: CssSel = {
    "type=submit [value]" #> (if (showInactiveTasks.get) S.?("projects.hide_inactive") else S.?("projects.show_inactive")) &
      "type=submit" #> SHtml.onSubmitUnit(() => {
        showInactiveTasks.set(!showInactiveTasks.get)
        net.liftweb.http.js.JsCmds.Reload
      })
  }

  def addRoot(): CssSel = {
    ".add-root [onclick]" #> SHtml.ajaxInvoke(() => addChild(Empty)).toJsCmd
  }

  def moveToRoot: CssSel = {
    def submit: JsCmd = {
      selectedTask.is.flatMap { sp =>
        TaskService.moveToRoot(sp)
        selectedTask.set(Empty)
      }
      rerenderTree
    }

    "a [onclick]" #> SHtml.ajaxInvoke(submit _).toJsCmd
  }

  def tasks(in: NodeSeq): NodeSeq = {
    taskTemplate
  }

  private val taskTemplate: NodeSeq =
    <div class="lift:projectsSnippet.taskList">
      <div class="task-root">
        <div class="parentId" style="display:none;"></div>
        <span class="task-inner">
          <span class="dropdown">
            <a href="#" class="dropdown-toggle" data-toggle="dropdown" role="button" aria-haspopup="true" aria-expanded="false">
              <span class="name"></span>
              <span class="caret"></span>
            </a>
            <ul class="dropdown-menu">
              <li><a class="edit"><lift:loc>projects.edit</lift:loc></a></li>
              <li><a class="add-subtask"><lift:loc>projects.add_task</lift:loc></a></li>
              <li><a class="delete"><lift:loc>projects.delete</lift:loc></a></li>
              <li><a class="select"><lift:loc>projects.select</lift:loc></a></li>
              <li><a class="moveto"><lift:loc>projects.moveto</lift:loc></a></li>
              <li><a class="merge"><lift:loc>projects.mergeinto</lift:loc></a></li>
            </ul>
          </span>
        </span>
        <div class="subtasks"></div>
      </div>
    </div>

  def taskList(in: NodeSeq): NodeSeq = {
    val parentId: Long = {
      try {
        (in \ "div" \ "div").filter(_.attribute("class").get.text == "parentId").text.toLong
      } catch {
        case _: Exception => -1
      }
    }

    val tasks = if (parentId != -1L) {
      val parent = Task.find(By(Task.id, parentId))

      if (!showInactiveTasks.get) {
        Task.findAll(By(Task.parent, parent), By(Task.active, true))
      } else {
        Task.findAll(By(Task.parent, parent))
      }
    } else {
      if (!showInactiveTasks.get) {
        Task.findAll(By(Task.parent, Empty), By(Task.active, true))
      } else {
        Task.findAll(By(Task.parent, Empty))
      }
    }

    val data = tasks.toArray

    Sorting.quickSort(data)(new Ordering[Task] {
      def compare(x: Task, y: Task): Int = {
        if (x.selectable.get && !y.selectable.get) {
          1
        } else if (!x.selectable.get && y.selectable.get) {
          -1
        } else {
          collator.compare(x.name.get, y.name.get)
        }
      }
    })

    data.toSeq.flatMap(task => renderTask(task)(in))
  }

  private def renderTask(task: Task):CssSel = {
    val displayName =
      if (task.active.get)
        task.name.get
      else
        task.name.get + " (" + S.?("projects.inactive") + ")"

    val rootClass =
      if (selectedTask.get === task)
        "task selected"
      else
        "task"

    val innerClass =
      if (task.active.get)
        if (task.selectable.get)
          "taskName"
        else
          "projectName"
      else
        if (task.selectable.get)
          "taskName inactive"
        else
          "projectName inactive"


    val subsCssSel:CssSel = ".parentId *" #> task.id.toString

    ".name" #> displayName &
    ".task-root [class]" #> rootClass &
    ".task-inner [class]" #> innerClass &
    ".edit [onclick]" #> SHtml.ajaxInvoke(() => editor(task)).toJsCmd &
    ".delete [onclick]" #> SHtml.ajaxInvoke(() => deleteTask(task)).toJsCmd &
    ".select [onclick]" #> SHtml.ajaxInvoke(() => selectTask(task)).toJsCmd &
    ".add-subtask [onclick]" #> SHtml.ajaxInvoke(() => addChild(Full(task))).toJsCmd &
    ".merge [onclick]" #> SHtml.ajaxInvoke(() => mergeTask(task)).toJsCmd &
    ".moveto [onclick]" #> SHtml.ajaxInvoke(() => moveTo(task)).toJsCmd &
    ".subtasks *" #> subsCssSel(taskTemplate)
  }

  private def editor(task: Task): JsCmd = {
    object name extends TransientRequestVar(task.name.get)
    object description extends TransientRequestVar(task.description.get)
    object color extends TransientRequestVar(task.color.get)
    object active extends TransientRequestVar(task.active.get)
    object specifiable extends TransientRequestVar(task.specifiable.get)
    object selectable extends TransientRequestVar(task.selectable.get)
    object useGeneratedColor extends TransientRequestVar(
      if (!task.color.get.isEmpty)
        S.?("projects.popup.use_custom_color")
      else
        S.?("projects.popup.use_generated_color")
      )

    def submit: JsCmd = {
      val selectedColor = if (useGeneratedColor.get == S.?("projects.popup.use_custom_color")) color.get else ""
      Task.findByKey(task.id.get).openOrThrowException("Item must be defined!")
        .name(name.get)
        .description(description.get)
        .color(selectedColor)
        .active(active.get)
        .specifiable(specifiable.get)
        .selectable(selectable.get)
        .save

      rerenderTree &
      closeDialog
    }

    val defaultFieldBindings =
      renderProperty(
        ".name *" #> S.?("projects.popup.name") &
        ".field" #> SHtml.textElem(name, "class" -> "form-control")) ++
      renderProperty(
        ".name *" #> S.?("projects.popup.description") &
        ".field" #> SHtml.textElem(description, "class" -> "form-control")) ++
      renderProperty(
        ".name *" #> S.?("projects.popup.color") &
        ".field" #> (
          <br/> ++
          SHtml.radioElem(List(
            S.?("projects.popup.use_generated_color"),
            S.?("projects.popup.use_custom_color")),
            Full(useGeneratedColor.get)) {
            _.map(v => useGeneratedColor.set(v))
          }.toForm ++
          SHtml.textElem(color, "type" -> "color"))
      )

    val fieldbindingsWithActive =
      if (task.active.get)
        defaultFieldBindings
      else
        defaultFieldBindings ++
        renderProperty(
          ".name *" #> S.?("projects.popup.active") &
          ".field" #> SHtml.checkboxElem(active))

    val fieldBindings =
      fieldbindingsWithActive ++
      renderProperty(
          ".name *" #> S.?("projects.popup.specifiable") &
          ".field" #> SHtml.checkboxElem(specifiable)) ++
      renderProperty(
          ".name *" #> S.?("projects.popup.selectable") &
          ".field" #> SHtml.checkboxElem(selectable))

    SetHtml("inject",
      (
        ".fields *" #> fieldBindings &
        ".title *" #> S.?("projects.edit") &
        ".submit-button" #> SHtml.ajaxSubmit(S.?("button.save"), submit _, "class" -> "btn btn-primary") &
        ".close-button" #> SHtml.ajaxSubmit(S.?("button.close"), closeDialog _, "class" -> "btn btn-default")
      )(editorTemplate)
    ) &
    openDialog
  }

  private def renderProperty(cssSel: CssSel): NodeSeq = {
    cssSel(editorPropertyTemplate)
  }

  private def addChild(parent: Box[Task]): JsCmd = {
    object name extends TransientRequestVar("")
    object description extends TransientRequestVar("")
    object specifiable extends TransientRequestVar(true)
    object selectable extends TransientRequestVar(true)

    def submit: JsCmd = {
      Task.create
        .parent(parent)
        .name(name.get)
        .description(description.get)
        .active(true)
        .specifiable(specifiable.get)
        .selectable(selectable.get)
        .save

      rerenderTree &
      closeDialog
    }

    SetHtml("inject",
      (
        ".fields *" #>
          (
            renderProperty(
              ".name *" #> S.?("projects.popup.name") &
              ".field" #> SHtml.textElem(name, "class" -> "form-control")) ++
            renderProperty(
              ".name" #> S.?("projects.popup.description") &
              ".field" #> SHtml.textElem(description, "class" -> "form-control")) ++
            renderProperty(
                ".name *" #> S.?("projects.popup.specifiable") &
                ".field" #> SHtml.checkboxElem(specifiable)) ++
            renderProperty(
                ".name *" #> S.?("projects.popup.selectable") &
                ".field" #> SHtml.checkboxElem(selectable))
            ) &
        ".title *" #> S.?("projects.add_task") &
        ".submit-button" #> SHtml.ajaxSubmit(S.?("button.save"), submit _, "class" -> "btn btn-primary") &
        ".close-button" #> SHtml.ajaxSubmit(S.?("button.close"), closeDialog _, "class" -> "btn btn-default")
      )(editorTemplate)
    ) &
    openDialog
  }

  private def moveTo(task: Task): JsCmd = {
    selectedTask.is.flatMap { st =>
      TaskService.move(st, task)
      selectedTask.set(Empty)
    }

    rerenderTree
  }

  private def mergeTask(task: Task): JsCmd = {
    selectedTask.is.flatMap { st =>
      TaskService.merge(st, task)
      selectedTask.set(Empty)
    }

    rerenderTree
  }

  private def selectTask(task: Task): JsCmd = {
    selectedTask.is match {
      case Full(st) =>
        if (st == task) {
          selectedTask.set(Empty)
        } else {
          selectedTask.set(Some(task))
        }
      case Empty => selectedTask.set(Some(task))
    }
    rerenderTree
  }

  private def deleteTask(task: Task): JsCmd = {
    def submit: JsCmd = {
      try {
        TaskService.delete(task)
        rerenderTree &
        closeDialog
      } catch {
        case e: Exception => net.liftweb.http.js.JsCmds.Alert(e.getMessage)
      }
    }

    SetHtml("inject",
      (
        ".fields *" #>
          (
            renderProperty(
              ".name *" #> S.?("projects.popup.name") &
              ".field" #> task.name) ++
            renderProperty(
              ".name" #> S.?("projects.popup.description") &
              ".field" #> task.description)) &
        ".title *" #> S.?("projects.delete") &
        ".submit-button" #> SHtml.ajaxSubmit(S.?("button.delete"), submit _, "class" -> "btn btn-primary") &
        ".close-button" #> SHtml.ajaxSubmit(S.?("button.close"), closeDialog _, "class" -> "btn btn-default")

      )(editorTemplate)
    ) &
    openDialog
  }

  def closeDialog: JsCmd = JsRaw("$('.modal').modal('hide')").cmd

  def openDialog: JsCmd = JsRaw("$('.modal').modal()").cmd

  def rerenderTree: JsCmd = SetHtml("project-tree", tasks(NodeSeq.Empty))

  val editorTemplate: NodeSeq =
    <div class="modal fade" data-backdrop="static" data-keyboard="false">
      <div class="modal-dialog">
        <form class="lift:form.ajax" role="form">
          <div class="modal-content">
            <div class="modal-header">
              <h4 class="modal-title title"></h4>
            </div>
            <div class="modal-body fields"></div>
            <div class="modal-footer">
              <input class="submit-button"/>
              <input class="close-button"/>
            </div>
          </div>
        </form>
      </div>
    </div>

  val editorPropertyTemplate: NodeSeq =
    <div class="form-group">
      <label class="name"></label>
      <input class="field"/>
    </div>

}
