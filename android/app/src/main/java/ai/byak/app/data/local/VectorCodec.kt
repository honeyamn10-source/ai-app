package ai.byak.app.data.local

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

object VectorCodec {
    fun encode(vector: FloatArray): ByteArray = ByteBuffer
        .allocate(vector.size * Float.SIZE_BYTES)
        .order(ByteOrder.LITTLE_ENDIAN)
        .apply { vector.forEach(::putFloat) }
        .array()

    fun decode(bytes: ByteArray): FloatArray {
        require(bytes.size % Float.SIZE_BYTES == 0) { "Invalid embedding payload" }
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(bytes.size / Float.SIZE_BYTES) { buffer.float }
    }

    fun cosineSimilarity(left: FloatArray, right: FloatArray): Float {
        if (left.size != right.size || left.isEmpty()) return 0f
        var dot = 0.0
        var leftNorm = 0.0
        var rightNorm = 0.0
        for (index in left.indices) {
            dot += left[index] * right[index]
            leftNorm += left[index] * left[index]
            rightNorm += right[index] * right[index]
        }
        if (leftNorm == 0.0 || rightNorm == 0.0) return 0f
        return (dot / (sqrt(leftNorm) * sqrt(rightNorm))).toFloat()
    }
}
