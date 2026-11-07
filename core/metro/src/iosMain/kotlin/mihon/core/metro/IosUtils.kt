package mihon.core.metro

object IosGraphHolder {
    var graphProvider: GraphProvider<*>? = null

    @Suppress("UNCHECKED_CAST")
    fun <T> metroGraph(): T = (requireNotNull(graphProvider) { "IosGraphHolder not initialized" } as GraphProvider<T>).graph
}

