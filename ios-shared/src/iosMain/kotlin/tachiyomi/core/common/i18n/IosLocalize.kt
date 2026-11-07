package tachiyomi.core.common.i18n

import dev.icerock.moko.resources.PluralsResource
import dev.icerock.moko.resources.StringResource
import dev.icerock.moko.resources.desc.Plural
import dev.icerock.moko.resources.desc.PluralFormatted
import dev.icerock.moko.resources.desc.Resource
import dev.icerock.moko.resources.desc.ResourceFormatted
import dev.icerock.moko.resources.desc.StringDesc

fun localizeString(resource: StringResource): String {
    return StringDesc.Resource(resource).localized().fixed()
}

fun localizeString(resource: StringResource, vararg args: Any): String {
    return StringDesc.ResourceFormatted(resource, *args).localized().fixed()
}

fun localizePlural(resource: PluralsResource, count: Int): String {
    return StringDesc.Plural(resource, count).localized().fixed()
}

fun localizePlural(resource: PluralsResource, count: Int, vararg args: Any): String {
    return StringDesc.PluralFormatted(resource, count, *args).localized().fixed()
}

private fun String.fixed() = this.replace("""\""", """"""")
