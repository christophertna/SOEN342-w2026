# Iteration 3

This folder contains the final cumulative Java task management system for iterations 1 to 3.

## Implemented Requirements

- Task lifecycle support:
  - create, update, complete, cancel, reopen, delete, and view tasks
  - task activity history
- Recurring task support:
  - daily, weekly, monthly, and custom recurrence
  - generated occurrences over a start/end range
  - completing one occurrence does not complete future occurrences
  - unique task name and due-date combinations
- Project and collaboration support:
  - create/manage projects
  - assign tasks to projects
  - link collaborators to project tasks
  - collaborator linking automatically creates a linked subtask
  - overloaded collaborator reporting using the required limits
- Subtask and tag support:
  - add, complete, reopen, and remove subtasks
  - add and remove tags
- Search and filtering:
  - keyword, status, priority, project, collaborator, collaborator category, tag
  - day-of-week and period filtering
  - if no criteria are supplied, all open tasks are listed
- Data exchange:
  - import tasks from CSV
  - export the database or filtered results to CSV
  - export a single task, all tasks in a project, or filtered tasks to iCalendar (`.ics`)
  - tasks without due dates are ignored during iCalendar export
- Persistence:
  - the full domain is persisted through the application persistence layer

## Source Location

Runnable Java source files are located in `Task Management System/`.

## Build And Run

From the `Task Management System` folder:

```powershell
javac *.java
java TaskSystem
```

At startup, provide a persistence file path such as `data.ptms`.

CSV import/export is still available from the menu for the iteration 2 requirements.

## Collaborator Limits

- `Junior`: 10 open assignments
- `Intermediate`: 5 open assignments
- `Senior`: 2 open assignments

## Included Iteration 3 Artifacts

This folder currently includes the following iteration 3 deliverables:

- `Use case diagram FINAL.pdf`
- `UML class diagram Iteration 3 updated.pdf`
- `Iteration 3 SSD export functionality.pdf`
- `OCL constraints.pdf`
- `README.md`
