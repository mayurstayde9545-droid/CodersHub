# SmartGroup Team Analyzer

A browser-based team planning and analysis demo with separate Team Leader and Team Member accounts.

## Start the app

1. Extract this folder.
2. Open `index.html` in a current browser.
3. Choose **Create account** and select a role.
4. Create the first Team Leader account. The first leader sets a leader access code; additional leaders need that code.
5. Save the recovery key shown once after registration. Use **Forgot password?** on the sign-in form with your email, recovery key and new password. Older demo accounts receive a recovery key the next time they sign in.
6. To create a Team Member account, enter the member's name, email, skills, skill level and password. The new profile is added to the roster with those skills.

## What it includes

- Leader and member sign-up and sign-in screens, with recovery-key password reset.
- A dashboard, project list, task board, team roster and timeline.
- A refreshed dashboard with a welcome panel, progress ring, KPI cards, project progress, team capacity, upcoming tasks and smart alerts.
- Select any project card to open its project overview, including completion, schedule, health, risks and project facts; a member-by-member breakdown shows completed and unfinished work, followed by the full task register.
- Rule-based team analysis with team and individual pros/cons, skill coverage, workload, schedule estimates and project risks.
- A separate AI Suggester reviews all active projects and open work, then prioritizes actions for overdue or unassigned tasks, workload balance, missing project skills, projects without task plans, incomplete project goals/deadlines and project risks. Leaders can add members directly from skill and capacity recommendations. Its finish estimate uses task-priority effort assumptions and available team capacity.
- The AI Suggester shows completed/open task counts and its last-update time, refreshes from the latest workspace data, and syncs local changes from other open tabs.
- Project-card auto-assignment routes open tasks only to members marked Available, ranking skill match, experience level and workload. For projects with no tasks, **Create & assign** makes starter tasks from the project tech stack and assigns them to available skill matches; unmatched tasks remain unassigned.
- A local team assistant answers common questions about members, pros and cons, skills, capacity, tasks, deadlines, risks and projects, and explains how to use the app.
- Members can update the status of their own assigned tasks. Leaders can manage team members, projects and tasks.
- Members receive in-app bell notifications when tasks are assigned to them, including the task, project, priority and due date; notifications can be marked read.
- Light and dark teal themes, reminders and downloadable project analysis reports.

## Demo data and storage

The browser version saves demo accounts, password hashes, recovery-key hashes and workspace data in that browser's local storage. Different browsers or devices do not sync. A recovery key is shown only once and must be kept by the user; this offline demo cannot send reset emails. This makes it suitable for a local demo; shared multi-user deployment needs a connected server and database. The included Java files are API source and are not connected to the browser demo by default. Team analysis and assistant responses use local rules, do not call an external AI service, and cannot answer live or unrelated world-knowledge questions.

The starter workspace opens with sample members, projects and tasks. Create a leader account first to explore the management controls.
