package ai.byak.app.data.security

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class EncryptedStateSerializer(
    private val cipher: KeystoreCipher,
    private val json: Json,
) : Serializer<SecureState> {
    override val defaultValue: SecureState = SecureState()

    override suspend fun readFrom(input: InputStream): SecureState {
        try {
            val source = DataInputStream(input)
            val version = source.readUnsignedByte()
            if (version != FORMAT_VERSION) throw CorruptionException("Unsupported secure store version")
            val ivSize = source.readUnsignedByte()
            if (ivSize !in 12..32) throw CorruptionException("Invalid secure store IV")
            val iv = ByteArray(ivSize).also(source::readFully)
            val cipherText = source.readBytes()
            if (cipherText.isEmpty()) throw CorruptionException("Empty secure store payload")
            val plainText = cipher.decrypt(iv, cipherText).decodeToString()
            return json.decodeFromString<SecureState>(plainText)
        } catch (error: CorruptionException) {
            throw error
        } catch (error: SerializationException) {
            throw CorruptionException("Secure store could not be decoded", error)
        } catch (error: Exception) {
            throw CorruptionException("Secure store could not be decrypted", error)
        }
    }

    override suspend fun writeTo(t: SecureState, output: OutputStream) {
        val encrypted = cipher.encrypt(json.encodeToString(t).encodeToByteArray())
        DataOutputStream(output).use { sink ->
            sink.writeByte(FORMAT_VERSION)
            sink.writeByte(encrypted.iv.size)
            sink.write(encrypted.iv)
            sink.write(encrypted.cipherText)
        }
    }

    private companion object {
        const val FORMAT_VERSION = 1
    }
}
