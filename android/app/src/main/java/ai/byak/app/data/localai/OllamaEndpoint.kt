package ai.byak.app.data.localai

import java.net.URI

data class ValidatedOllamaEndpoint(val baseUrl: String)

fun validateOllamaEndpoint(raw: String, allowPrivateHttp: Boolean): ValidatedOllamaEndpoint {
    val input = raw.trim().trimEnd('/')
    require(input.isNotBlank()) { "Enter the Ollama address, for example http://192.168.1.20:11434." }
    val uri = runCatching { URI(input) }.getOrElse {
        error("The Ollama address is not valid. Use http://private-ip:11434 or an HTTPS URL.")
    }
    require(uri.scheme == "https" || uri.scheme == "http") { "Ollama must use an http:// or https:// address." }
    require(!uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null) {
        "The Ollama address must contain only a host and optional port/path."
    }
    if (uri.scheme == "http") {
        require(allowPrivateHttp && uri.host.isPrivateHost()) {
            "For safety, unencrypted Ollama works only on a private Wi-Fi address in the test APK. Use HTTPS for public or Play builds."
        }
    }
    return ValidatedOllamaEndpoint(input)
}

private fun String.isPrivateHost(): Boolean {
    val value = trim('[', ']').lowercase()
    if (value == "localhost" || value == "::1" || value.endsWith(".local")) return true
    val octets = value.split('.').mapNotNull(String::toIntOrNull)
    if (octets.size != 4 || octets.any { it !in 0..255 }) return false
    return octets[0] == 10 ||
        octets[0] == 127 ||
        (octets[0] == 192 && octets[1] == 168) ||
        (octets[0] == 172 && octets[1] in 16..31) ||
        (octets[0] == 169 && octets[1] == 254)
}
