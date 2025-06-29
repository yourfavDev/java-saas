# Java SaaS Dashboard

This project offers a simple dashboard for running user supplied Java code in a Docker
container. The dashboard has been updated to emphasize a smoother user experience.

## User Experience Requirements

1. **Immediate feedback when a job is running.** A colored status indicator and spinner show the current state and auto-update as the job progresses.
2. **Automatic scrolling of output.** The output panel always scrolls to the most recent line so users do not have to scroll manually.
3. **Ability to stop execution.** A Stop button appears during execution so the job can be canceled at any time.

These requirements guide the UI and the implementation in `JobService` and the front-end script.
