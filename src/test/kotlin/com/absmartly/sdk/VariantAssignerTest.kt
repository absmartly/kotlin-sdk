package com.absmartly.sdk

import kotlin.test.Test
import kotlin.test.assertEquals

class VariantAssignerTest {

    @Test
    fun assignIsDeterministicForBlehEmail() {
        val unitHash = Hashing.hashUnit("bleh@absmartly.com")
        val assigner = VariantAssigner(unitHash)

        val twoWaySplit = doubleArrayOf(0.5, 0.5)
        assertEquals(0, assigner.assign(twoWaySplit, 0x00000000, 0x00000000))
        assertEquals(1, assigner.assign(twoWaySplit, 0x00000000, 0x00000001))
        assertEquals(0, assigner.assign(twoWaySplit, 0x8015406f.toInt(), 0x7ef49b98.toInt()))
        assertEquals(0, assigner.assign(twoWaySplit, 0x3b2e7d90, 0xca87df4d.toInt()))
        assertEquals(0, assigner.assign(twoWaySplit, 0x52c1f657, 0xd248bb2e.toInt()))
        assertEquals(0, assigner.assign(twoWaySplit, 0x865a84d0.toInt(), 0xaa22d41a.toInt()))
        assertEquals(1, assigner.assign(twoWaySplit, 0x27d1dc86, 0x845461b9.toInt()))

        val threeWaySplit = doubleArrayOf(0.33, 0.33, 0.34)
        assertEquals(0, assigner.assign(threeWaySplit, 0x00000000, 0x00000000))
        assertEquals(2, assigner.assign(threeWaySplit, 0x00000000, 0x00000001))
        assertEquals(0, assigner.assign(threeWaySplit, 0x8015406f.toInt(), 0x7ef49b98.toInt()))
        assertEquals(0, assigner.assign(threeWaySplit, 0x3b2e7d90, 0xca87df4d.toInt()))
        assertEquals(0, assigner.assign(threeWaySplit, 0x52c1f657, 0xd248bb2e.toInt()))
        assertEquals(1, assigner.assign(threeWaySplit, 0x865a84d0.toInt(), 0xaa22d41a.toInt()))
        assertEquals(1, assigner.assign(threeWaySplit, 0x27d1dc86, 0x845461b9.toInt()))
    }

    @Test
    fun assignIsDeterministicForNumericUser() {
        val unitHash = Hashing.hashUnit("123456789")
        val assigner = VariantAssigner(unitHash)

        val twoWaySplit = doubleArrayOf(0.5, 0.5)
        assertEquals(1, assigner.assign(twoWaySplit, 0x00000000, 0x00000000))
        assertEquals(0, assigner.assign(twoWaySplit, 0x00000000, 0x00000001))
        assertEquals(1, assigner.assign(twoWaySplit, 0x8015406f.toInt(), 0x7ef49b98.toInt()))
        assertEquals(1, assigner.assign(twoWaySplit, 0x3b2e7d90, 0xca87df4d.toInt()))
        assertEquals(1, assigner.assign(twoWaySplit, 0x52c1f657, 0xd248bb2e.toInt()))
        assertEquals(0, assigner.assign(twoWaySplit, 0x865a84d0.toInt(), 0xaa22d41a.toInt()))
        assertEquals(0, assigner.assign(twoWaySplit, 0x27d1dc86, 0x845461b9.toInt()))

        val threeWaySplit = doubleArrayOf(0.33, 0.33, 0.34)
        assertEquals(2, assigner.assign(threeWaySplit, 0x00000000, 0x00000000))
        assertEquals(1, assigner.assign(threeWaySplit, 0x00000000, 0x00000001))
        assertEquals(2, assigner.assign(threeWaySplit, 0x8015406f.toInt(), 0x7ef49b98.toInt()))
        assertEquals(2, assigner.assign(threeWaySplit, 0x3b2e7d90, 0xca87df4d.toInt()))
        assertEquals(2, assigner.assign(threeWaySplit, 0x52c1f657, 0xd248bb2e.toInt()))
        assertEquals(0, assigner.assign(threeWaySplit, 0x865a84d0.toInt(), 0xaa22d41a.toInt()))
        assertEquals(0, assigner.assign(threeWaySplit, 0x27d1dc86, 0x845461b9.toInt()))
    }

    @Test
    fun assignIsDeterministicForSessionHash() {
        val unitHash = Hashing.hashUnit("e791e240fcd3df7d238cfc285f475e8152fcc0ec")
        val assigner = VariantAssigner(unitHash)

        val twoWaySplit = doubleArrayOf(0.5, 0.5)
        assertEquals(1, assigner.assign(twoWaySplit, 0x00000000, 0x00000000))
        assertEquals(0, assigner.assign(twoWaySplit, 0x00000000, 0x00000001))
        assertEquals(1, assigner.assign(twoWaySplit, 0x8015406f.toInt(), 0x7ef49b98.toInt()))
        assertEquals(1, assigner.assign(twoWaySplit, 0x3b2e7d90, 0xca87df4d.toInt()))
        assertEquals(0, assigner.assign(twoWaySplit, 0x52c1f657, 0xd248bb2e.toInt()))
        assertEquals(0, assigner.assign(twoWaySplit, 0x865a84d0.toInt(), 0xaa22d41a.toInt()))
        assertEquals(0, assigner.assign(twoWaySplit, 0x27d1dc86, 0x845461b9.toInt()))

        val threeWaySplit = doubleArrayOf(0.33, 0.33, 0.34)
        assertEquals(2, assigner.assign(threeWaySplit, 0x00000000, 0x00000000))
        assertEquals(0, assigner.assign(threeWaySplit, 0x00000000, 0x00000001))
        assertEquals(2, assigner.assign(threeWaySplit, 0x8015406f.toInt(), 0x7ef49b98.toInt()))
        assertEquals(1, assigner.assign(threeWaySplit, 0x3b2e7d90, 0xca87df4d.toInt()))
        assertEquals(0, assigner.assign(threeWaySplit, 0x52c1f657, 0xd248bb2e.toInt()))
        assertEquals(0, assigner.assign(threeWaySplit, 0x865a84d0.toInt(), 0xaa22d41a.toInt()))
        assertEquals(1, assigner.assign(threeWaySplit, 0x27d1dc86, 0x845461b9.toInt()))
    }

    @Test
    fun chooseVariantReturnsCorrectIndex() {
        assertEquals(0, VariantAssigner.chooseVariant(doubleArrayOf(1.0), 0.0))
        assertEquals(0, VariantAssigner.chooseVariant(doubleArrayOf(1.0), 0.5))
        assertEquals(0, VariantAssigner.chooseVariant(doubleArrayOf(1.0), 1.0))

        assertEquals(0, VariantAssigner.chooseVariant(doubleArrayOf(0.5, 0.5), 0.0))
        assertEquals(0, VariantAssigner.chooseVariant(doubleArrayOf(0.5, 0.5), 0.25))
        assertEquals(1, VariantAssigner.chooseVariant(doubleArrayOf(0.5, 0.5), 0.5))
        assertEquals(1, VariantAssigner.chooseVariant(doubleArrayOf(0.5, 0.5), 0.75))

        assertEquals(0, VariantAssigner.chooseVariant(doubleArrayOf(0.33, 0.33, 0.34), 0.0))
        assertEquals(0, VariantAssigner.chooseVariant(doubleArrayOf(0.33, 0.33, 0.34), 0.15))
        assertEquals(1, VariantAssigner.chooseVariant(doubleArrayOf(0.33, 0.33, 0.34), 0.33))
        assertEquals(1, VariantAssigner.chooseVariant(doubleArrayOf(0.33, 0.33, 0.34), 0.5))
        assertEquals(2, VariantAssigner.chooseVariant(doubleArrayOf(0.33, 0.33, 0.34), 0.66))
        assertEquals(2, VariantAssigner.chooseVariant(doubleArrayOf(0.33, 0.33, 0.34), 0.99))
    }
}
