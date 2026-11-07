package eu.kanade.tachiyomi.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.JavaScriptCore.JSContext
import platform.JavaScriptCore.JSValue

/**
 * iOS implementation of Mihon's [JavaScriptEngine] using Apple's native `JavaScriptCore` framework.
 * Provides fast, sandboxed JavaScript execution for source scrapers, deobfuscators, and JS extensions
 * without violating iOS App Store / JIT restrictions.
 */
class IosJavaScriptEngine {

    /**
     * Evaluates an arbitrary JavaScript script and returns the result coerced to [String],
     * or throws an [IllegalStateException] if a JS exception occurred.
     */
    suspend fun evaluate(script: String): String = withContext(Dispatchers.Default) {
        val context = JSContext() ?: error("Failed to initialize iOS JSContext")
        var jsExceptionMessage: String? = null
        context.exceptionHandler = { _, exception ->
            jsExceptionMessage = exception?.toString() ?: "Unknown JavaScript error"
        }

        // Inject standard base64 helpers (atob / btoa) commonly expected by manga scrapers
        context.evaluateScript(POLYFILL_SCRIPT)

        val result: JSValue? = context.evaluateScript(script)
        if (jsExceptionMessage != null) {
            throw IllegalStateException("JavaScript evaluation failed: $jsExceptionMessage")
        }
        result?.toString() ?: ""
    }

    /**
     * Evaluates a script and calls a global function with JSON-serialized arguments.
     */
    suspend fun invokeFunction(
        moduleScript: String,
        functionName: String,
        args: List<Any?> = emptyList(),
    ): String = withContext(Dispatchers.Default) {
        val context = JSContext() ?: error("Failed to initialize iOS JSContext")
        var jsExceptionMessage: String? = null
        context.exceptionHandler = { _, exception ->
            jsExceptionMessage = exception?.toString() ?: "Unknown JavaScript error"
        }

        context.evaluateScript(POLYFILL_SCRIPT)
        context.evaluateScript(moduleScript)

        if (jsExceptionMessage != null) {
            throw IllegalStateException("JavaScript module load failed: $jsExceptionMessage")
        }

        val fn = context.objectForKeyedSubscript(functionName)
        val result = fn?.callWithArguments(args)
        if (jsExceptionMessage != null) {
            throw IllegalStateException("JavaScript function '$functionName' failed: $jsExceptionMessage")
        }
        result?.toString() ?: ""
    }

    companion object {
        private val POLYFILL_SCRIPT = """
            (function(global) {
                var chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=';
                if (typeof global.btoa === 'undefined') {
                    global.btoa = function(input) {
                        var str = String(input);
                        var output = '';
                        for (
                            var block = 0, charCode, i = 0, map = chars;
                            str.charAt(i | 0) || (map = '=', i % 1);
                            output += map.charAt(63 & block >> 8 - i % 1 * 8)
                        ) {
                            charCode = str.charCodeAt(i += 3/4);
                            block = block << 8 | charCode;
                        }
                        return output;
                    };
                }
                if (typeof global.atob === 'undefined') {
                    global.atob = function(input) {
                        var str = String(input).replace(/=+$/, '');
                        var output = '';
                        for (
                            var bc = 0, bs = 0, buffer, i = 0;
                            buffer = str.charAt(i++);
                            ~buffer && (bs = bc % 4 ? bs * 64 + buffer : buffer, bc++ % 4)
                                ? output += String.fromCharCode(255 & bs >> (-2 * bc & 6))
                                : 0
                        ) {
                            buffer = chars.indexOf(buffer);
                        }
                        return output;
                    };
                }
            })(this);
        """.trimIndent()
    }
}
