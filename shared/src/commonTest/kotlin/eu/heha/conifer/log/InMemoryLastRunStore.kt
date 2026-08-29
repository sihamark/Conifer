package eu.heha.conifer.log

/** A [LastRunStore] with no file behind it, for the tests of what gets written into one. */
class InMemoryLastRunStore(private var stored: String? = null) : LastRunStore {
    var writes = 0
        private set

    override fun write(text: String) {
        writes++
        stored = text
    }

    override fun read(): String? = stored
}
