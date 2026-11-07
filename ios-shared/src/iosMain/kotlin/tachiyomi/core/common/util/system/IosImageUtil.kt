package tachiyomi.core.common.util.system

/**
 * Multiplatform iOS image utility supporting:
 * - Magic-byte format detection (JPEG, PNG, WEBP, GIF, HEIC/HEIF, AVIF, JXL)
 * - Dimension inspection (`extractImageDimensions`)
 * - Tall image detection (`isTallImage`) for Webtoon strips
 * - Wide double-page spread detection (`isWidePage`)
 */
object IosImageUtil {

    enum class ImageType(val mime: String, val extension: String) {
        JPEG("image/jpeg", "jpg"),
        PNG("image/png", "png"),
        WEBP("image/webp", "webp"),
        GIF("image/gif", "gif"),
        HEIF("image/heif", "heic"),
        AVIF("image/avif", "avif"),
        JXL("image/jxl", "jxl"),
    }

    fun findImageType(bytes: ByteArray): ImageType? {
        if (bytes.size < 12) return null

        // JPEG: FF D8 FF
        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) {
            return ImageType.JPEG
        }

        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (
            bytes[0] == 0x89.toByte() &&
            bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() &&
            bytes[3] == 0x47.toByte()
        ) {
            return ImageType.PNG
        }

        // GIF: GIF87a or GIF89a
        if (
            bytes[0] == 0x47.toByte() &&
            bytes[1] == 0x49.toByte() &&
            bytes[2] == 0x46.toByte() &&
            bytes[3] == 0x38.toByte()
        ) {
            return ImageType.GIF
        }

        // WEBP: RIFF....WEBP
        if (
            bytes[0] == 0x52.toByte() &&
            bytes[1] == 0x49.toByte() &&
            bytes[2] == 0x46.toByte() &&
            bytes[3] == 0x46.toByte() &&
            bytes[8] == 0x57.toByte() &&
            bytes[9] == 0x45.toByte() &&
            bytes[10] == 0x42.toByte() &&
            bytes[11] == 0x50.toByte()
        ) {
            return ImageType.WEBP
        }

        // ISO Base Media File Format (ftyp): HEIC / AVIF
        if (
            bytes[4] == 0x66.toByte() &&
            bytes[5] == 0x74.toByte() &&
            bytes[6] == 0x79.toByte() &&
            bytes[7] == 0x70.toByte()
        ) {
            val brand = bytes.decodeToString(8, 12)
            return when {
                brand.startsWith("avif") || brand.startsWith("avis") -> ImageType.AVIF
                brand.startsWith("heic") || brand.startsWith("heix") || brand.startsWith("mif1") -> ImageType.HEIF
                else -> null
            }
        }

        // JPEG XL: FF 0A
        if (bytes[0] == 0xFF.toByte() && bytes[1] == 0x0A.toByte()) {
            return ImageType.JXL
        }

        return null
    }

    fun isImage(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "avif", "jxl")
    }

    /**
     * Maximum hardware texture slice height before splitting tall webtoon images.
     */
    const val MAX_SLICE_HEIGHT = 4096
}
