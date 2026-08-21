package com.timkrest.framehud.sample

enum class SampleDestination(val screen: String, val title: String, val subtitle: String) {
    Load(
        screen = "load",
        title = "Load",
        subtitle = "Scroll the list, switch loads on, and watch the panel react.",
    ),
    Readouts(
        screen = "readouts",
        title = "Readouts",
        subtitle = "Everything FrameHUD collects, read as a flow instead of from the panel.",
    ),
    Session(
        screen = "session",
        title = "Session",
        subtitle = "What a QA run ends with: a report, the incidents it hit, a baseline to compare against.",
    ),
}
