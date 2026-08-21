# Security

Report a vulnerability through
[GitHub's private advisory form](https://github.com/timkrest/FrameHUD/security/advisories/new)
rather than a public issue. Fixes land in the latest release; older ones are not patched.

## What the panel can reach

FrameHUD reads `FrameMetrics`, the heap counters of its own process and the thermal status of the
device. It uploads nothing. It writes to disk only when asked: `exportSession` and `saveBaseline`
put files under the app's own files directory, external when the device reports one and internal
otherwise, and write nothing outside it.

With `SYSTEM_ALERT_WINDOW` granted the panel draws in a system window, which sits above other apps.
That window wraps its own content rather than the screen, never takes focus, and lets touches
outside its bounds reach whatever is underneath, so none of the app's input passes through FrameHUD.
Set `overlayMode = APP_WINDOW` to keep the panel inside your own window and leave the permission
unused.

`debugImplementation` keeps the artifact, its provider and the permission out of a release build, so
none of the above reaches the app you ship.
