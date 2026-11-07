package tachiyomi.core.common.preference

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Multiplatform in-memory implementation of [PreferenceStore] for unit testing and previews.
 */
class InMemoryPreferenceStore(
    initialPreferences: Sequence<InMemoryPreference<*>> = sequenceOf(),
) : PreferenceStore {

    private val preferences: MutableMap<String, InMemoryPreference<*>> =
        initialPreferences.toList().associateBy { it.key() }.toMutableMap()

    @Suppress("UNCHECKED_CAST")
    private fun <T> getOrCreate(key: String, defaultValue: T): Preference<T> {
        val existing = preferences[key] as? InMemoryPreference<T>
        if (existing != null) return existing
        val created = InMemoryPreference(key, null, defaultValue)
        preferences[key] = created
        return created
    }

    override fun getString(key: String, defaultValue: String): Preference<String> =
        getOrCreate(key, defaultValue)

    override fun getLong(key: String, defaultValue: Long): Preference<Long> =
        getOrCreate(key, defaultValue)

    override fun getInt(key: String, defaultValue: Int): Preference<Int> =
        getOrCreate(key, defaultValue)

    override fun getFloat(key: String, defaultValue: Float): Preference<Float> =
        getOrCreate(key, defaultValue)

    override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> =
        getOrCreate(key, defaultValue)

    override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> =
        getOrCreate(key, defaultValue)

    override fun <T> getObjectFromString(
        key: String,
        defaultValue: T,
        serializer: (T) -> String,
        deserializer: (String) -> T,
    ): Preference<T> = getOrCreate(key, defaultValue)

    override fun <T> getObjectFromInt(
        key: String,
        defaultValue: T,
        serializer: (T) -> Int,
        deserializer: (Int) -> T,
    ): Preference<T> = getOrCreate(key, defaultValue)

    override fun <T> getObjectSetFromStringSet(
        key: String,
        defaultValue: Set<T>,
        serializer: (T) -> String,
        deserializer: (String) -> T?,
    ): Preference<Set<T>> = getOrCreate(key, defaultValue)

    override fun getAll(): Map<String, *> = preferences.mapValues { it.value.get() }

    class InMemoryPreference<T>(
        private val key: String,
        private var data: T?,
        private val defaultValue: T,
    ) : Preference<T> {
        private val stateFlow = MutableStateFlow(data ?: defaultValue)

        override fun key(): String = key

        override fun get(): T = data ?: defaultValue

        override fun isSet(): Boolean = data != null

        override fun delete() {
            data = null
            stateFlow.value = defaultValue
        }

        override fun defaultValue(): T = defaultValue

        override fun changes(): Flow<T> = stateFlow.asStateFlow()

        override fun stateIn(scope: CoroutineScope): StateFlow<T> = stateFlow.asStateFlow()

        override fun set(value: T) {
            data = value
            stateFlow.value = value
        }
    }
}
