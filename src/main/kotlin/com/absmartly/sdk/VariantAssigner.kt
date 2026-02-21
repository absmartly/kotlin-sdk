package com.absmartly.sdk

internal class VariantAssigner(unitHash: ByteArray) {
    private val unitHash: Int = Murmur3.digest(unitHash, 0)

    fun assign(split: DoubleArray, seedHi: Int, seedLo: Int): Int {
        val prob = probability(seedHi, seedLo)
        return chooseVariant(split, prob)
    }

    private fun probability(seedHi: Int, seedLo: Int): Double {
        val buffer = ByteArray(12)
        Buffers.putUInt32(buffer, 0, seedLo)
        Buffers.putUInt32(buffer, 4, seedHi)
        Buffers.putUInt32(buffer, 8, unitHash)
        val hash = Murmur3.digest(buffer, 0)
        return (hash.toLong() and 0xffffffffL) * NORMALIZER
    }

    companion object {
        private const val NORMALIZER: Double = 1.0 / 0xffffffffL

        fun chooseVariant(split: DoubleArray, prob: Double): Int {
            var cumSum = 0.0
            for (i in split.indices) {
                cumSum += split[i]
                if (prob < cumSum) return i
            }
            return split.size - 1
        }
    }
}
