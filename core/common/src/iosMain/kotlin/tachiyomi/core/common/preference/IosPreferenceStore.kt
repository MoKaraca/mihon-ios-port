package tachiyomi.core.common.preference

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import platform.Foundation.NSUserDefaults

/**
 * iOS implementation of [PreferenceStore] backed by [NSUserDefaults].
 * Supports sharing preferences with the iOS WidgetKit extension via the
 * "group.app.mihon" App Group suite when available.
 */
class IosPreferenceStore(
    suiteName: String? = APP_GROUP_SUITE,
) : PreferenceStore {

    private val userDefaults: NSUserDefaults = if (suiteName != null) {
        NSUserDefaults(suiteName = suiteName) ?: NSUserDefaults.standardUserDefaults
    } else {
        NSUserDefaults.standardUserDefaults
    }

    private val keyChangesFlow = MutableSharedFlow<String>(extraBufferCapacity = 64)

    internal fun notifyKeyChanged(key: String) {
        keyChangesFlow.tryEmit(key)
    }

    override fun getString(key: String, defaultValue: String): Preference<String> {
        return IosPreference(
            userDefaults = userDefaults,
            keyChangesFlow = keyChangesFlow,
            key = key,
            defaultValue = defaultValue,
            getter = { defaults, k, def -> defaults.stringForKey(k) ?: def },
            setter = { defaults, k, value -> defaults.setObject(value, forKey = k) },
        )
    }

    override fun getLong(key: String, defaultValue: Long): Preference<Long> {
        return IosPreference(
            userDefaults = userDefaults,
            keyChangesFlow = keyChangesFlow,
            key = key,
            defaultValue = defaultValue,
            getter = { defaults, k, _ -> defaults.integerForKey(k) },
            setter = { defaults, k, value -> defaults.setInteger(value, forKey = k) },
        )
    }

    override fun getInt(key: String, defaultValue: Int): Preference<Int> {
        return IosPreference(
            userDefaults = userDefaults,
            keyChangesFlow = keyChangesFlow,
            key = key,
            defaultValue = defaultValue,
            getter = { defaults, k, _ -> defaults.integerForKey(k).toInt() },
            setter = { defaults, k, value -> defaults.setInteger(value.toLong(), forKey = k) },
        )
    }

    override fun getFloat(key: String, defaultValue: Float): Preference<Float> {
        return IosPreference(
            userDefaults = userDefaults,
            keyChangesFlow = keyChangesFlow,
            key = key,
            defaultValue = defaultValue,
            getter = { defaults, k, _ -> defaults.floatForKey(k) },
            setter = { defaults, k, value -> defaults.setFloat(value, forKey = k) },
        )
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Preference<Boolean> {
        return IosPreference(
            userDefaults = userDefaults,
            keyChangesFlow = keyChangesFlow,
            key = key,
            defaultValue = defaultValue,
            getter = { defaults, k, _ -> defaults.boolForKey(k) },
            setter = { defaults, k, value -> defaults.setBool(value, forKey = k) },
        )
    }

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defaultValue: Set<String>): Preference<Set<String>> {
        return IosPreference(
            userDefaults = userDefaults,
            keyChangesFlow = keyChangesFlow,
            key = key,
            defaultValue = defaultValue,
            getter = { defaults, k, def ->
                val list = defaults.stringArrayForKey(k) as? List<String>
                list?.toSet() ?: def
            },
            setter = { defaults, k, value ->
                defaults.setObject(value.toList(), forKey = k)
            },
        )
    }

    override fun <T> getObjectFromString(
        key: String,
        defaultValue: T,
        serializer: (T) -> String,
        deserializer: (String) -> T,
    ): Preference<T> {
        return IosPreference(
            userDefaults = userDefaults,
            keyChangesFlow = keyChangesFlow,
            key = key,
            defaultValue = defaultValue,
            getter = { defaults, k, def ->
                defaults.stringForKey(k)?.let(deserializer) ?: def
            },
            setter = { defaults, k, value ->
                defaults.setObject(serializer(value), forKey = k)
            },
        )
    }

    override fun <T> getObjectFromInt(
        key: String,
        defaultValue: T,
        serializer: (T) -> Int,
        deserializer: (Int) -> T,
    ): Preference<T> {
        return IosPreference(
            userDefaults = userDefaults,
            keyChangesFlow = keyChangesFlow,
            key = key,
            defaultValue = defaultValue,
            getter = { defaults, k, _ ->
                deserializer(defaults.integerForKey(k).toInt())
            },
            setter = { defaults, k, value ->
                defaults.setInteger(serializer(value).toLong(), forKey = k)
            },
        )
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> getObjectSetFromStringSet(
        key: String,
        defaultValue: Set<T>,
        serializer: (T) -> String,
        deserializer: (String) -> T?,
    ): Preference<Set<T>> {
        return IosPreference(
            userDefaults = userDefaults,
            keyChangesFlow = keyChangesFlow,
            key = key,
            defaultValue = defaultValue,
            getter = { defaults, k, def ->
                val list = defaults.stringArrayForKey(k) as? List<String>
                list?.mapNotNull(deserializer)?.toSet() ?: def
            },
            setter = { defaults, k, value ->
                defaults.setObject(value.map(serializer), forKey = k)
            },
        )
    }

    @Suppress("UNCHECKED_CAST")
    override fun getAll(): Map<String, *> {
        val dict = userDefaults.dictionaryRepresentation()
        val result = mutableMapOf<String, Any?>()
        for ((k, v) in dict) {
            if (k is String) {
                result[k] = v
            }
        }
        return result
    }

    companion object {
        const val APP_GROUP_SUITE = "group.app.mihon"
    }
}

private class IosPreference<T>(
    private val userDefaults: NSUserDefaults,
    private val keyChangesFlow: MutableSharedFlow<String>,
    private val key: String,
    private val defaultValue: T,
    private val getter: (NSUserDefaults, String, T) -> T,
    private val setter: (NSUserDefaults, String, T) -> Unit,
) : Preference<T> {

    override fun key(): String = key

    override fun get(): T {
        return if (isSet()) {
            try {
                getter(userDefaults, key, defaultValue)
            } catch (_: Throwable) {
                defaultValue
            }
        } else {
            defaultValue
        }
    }

    override fun set(value: T) {
        setter(userDefaults, key, value)
        userDefaults.synchronize()
        keyChangesFlow.tryEmit(key)
    }

    override fun isSet(): Boolean {
        return userDefaults.objectForKey(key) != null
    }

    override fun delete() {
        userDefaults.removeObjectForKey(key)
        userDefaults.synchronize()
        keyChangesFlow.tryEmit(key)
    }

    override fun defaultValue(): T = defaultValue

    override fun changes(): Flow<T> {
        return keyChangesFlow
            .filter { it == key }
            .onStart { emit(key) }
            .map { get() }
    }

    override fun stateIn(scope: CoroutineScope): StateFlow<T> {
        return changes().stateIn(scope, SharingStarted.Eagerly, get())
    }
}

