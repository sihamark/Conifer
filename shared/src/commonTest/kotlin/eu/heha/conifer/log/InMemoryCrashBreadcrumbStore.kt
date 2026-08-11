package eu.heha.conifer.log

/** A [CrashBreadcrumbStore] with no file behind it, for the tests of what gets written into one. */
class InMemoryCrashBreadcrumbStore(private var stored: String? = null) : CrashBreadcrumbStore {
    var writes = 0
        private set

    override fun write(text: String) {
        writes++
        stored = text
    }

    override fun read(): String? = stored

    override fun clear() {
        stored = null
    }
}
