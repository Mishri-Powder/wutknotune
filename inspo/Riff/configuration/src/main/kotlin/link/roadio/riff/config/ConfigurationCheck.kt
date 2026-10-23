package link.roadio.riff.config

/** Invoked by both Android preBuild gates, not by the Android main thread. */
fun main() {
    val document = PackagedConfiguration.document
    check(document["activeSkin"] == "default")
    check(document.getValue("skins").objectValue().keys == setOf("default"))
    val checked = ProjectConfiguration.resolve(document)
    check(checked.activeSkin == "default")
    println("Schema v3: complete default skin and all resolved variants validated")
}
