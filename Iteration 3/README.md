# Iteration 3

This folder contains the final cumulative system for the software side of the project, with the iteration 2 prototype functionality carried forward into iteration 3. The OCL deliverable is excluded here.

## Implemented Features

- Persistent storage through a CSV-backed repository layer.
- Menu-driven task management with create, edit, delete, view, search, and subtask management flows.
- Import from CSV and export to CSV, preserving the prototype functionality from iteration 2.
- Overloaded collaborator reporting based on category task limits.
- iCalendar (`.ics`) export for:
  - a single task,
  - all tasks in a project,
  - a filtered task list.
- Export ignores tasks without due dates, as required.
- Export includes task title, description, due date, status, priority, project name, and subtask summary.

## Source Location

Runnable Java source files are located in `Task Management System/`.

## Build And Run

From the `Task Management System` folder:

```powershell
javac *.java
java TaskSystem
```

At startup, provide the path to the CSV data file to load/save task data.

## Collaborator Limits

The collaborator limits now match the iteration 2 specification:

- `Junior`: 10 open tasks
- `Intermediate`: 5 open tasks
- `Senior`: 2 open tasks

These values are defined in `TaskSystem.java`.
