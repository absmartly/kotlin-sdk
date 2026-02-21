package com.absmartly.sdk

internal object Buffers {
    fun putUInt32(buf: ByteArray, offset: Int, x: Int) {
        buf[offset] = (x and 0xff).toByte()
        buf[offset + 1] = ((x shr 8) and 0xff).toByte()
        buf[offset + 2] = ((x shr 16) and 0xff).toByte()
        buf[offset + 3] = ((x shr 24) and 0xff).toByte()
    }

    fun getUInt32(buf: ByteArray, offset: Int): Int {
        return (buf[offset].toInt() and 0xff) or
                ((buf[offset + 1].toInt() and 0xff) shl 8) or
                ((buf[offset + 2].toInt() and 0xff) shl 16) or
                ((buf[offset + 3].toInt() and 0xff) shl 24)
    }

    fun getUInt24(buf: ByteArray, offset: Int): Int {
        return (buf[offset].toInt() and 0xff) or
                ((buf[offset + 1].toInt() and 0xff) shl 8) or
                ((buf[offset + 2].toInt() and 0xff) shl 16)
    }

    fun getUInt16(buf: ByteArray, offset: Int): Int {
        return (buf[offset].toInt() and 0xff) or
                ((buf[offset + 1].toInt() and 0xff) shl 8)
    }

    fun getUInt8(buf: ByteArray, offset: Int): Int {
        return buf[offset].toInt() and 0xff
    }

    fun encodeUTF8(buf: ByteArray, offset: Int, value: CharSequence): Int {
        var out = offset
        for (i in 0 until value.length) {
            val c = value[i].code
            if (c < 0x80) {
                buf[out++] = c.toByte()
            } else if (c < 0x800) {
                buf[out++] = ((c shr 6) or 192).toByte()
                buf[out++] = ((c and 63) or 128).toByte()
            } else {
                buf[out++] = ((c shr 12) or 224).toByte()
                buf[out++] = (((c shr 6) and 63) or 128).toByte()
                buf[out++] = ((c and 63) or 128).toByte()
            }
        }
        return out - offset
    }
}

internal object Murmur3 {
    fun digest(key: ByteArray, seed: Int): Int = digest(key, 0, key.size, seed)

    fun digest(key: ByteArray, offset: Int, len: Int, seed: Int): Int {
        val n = offset + (len and 3.inv())
        var hash = seed
        var i = offset

        while (i < n) {
            val chunk = Buffers.getUInt32(key, i)
            hash = hash xor scramble32(chunk)
            hash = Integer.rotateLeft(hash, 13)
            hash = (hash * 5) + 0xe6546b64.toInt()
            i += 4
        }

        when (len and 3) {
            3 -> hash = hash xor scramble32(Buffers.getUInt24(key, i))
            2 -> hash = hash xor scramble32(Buffers.getUInt16(key, i))
            1 -> hash = hash xor scramble32(Buffers.getUInt8(key, i))
        }

        hash = hash xor len
        hash = fmix32(hash)
        return hash
    }

    private fun fmix32(h: Int): Int {
        var v = h
        v = v xor (v ushr 16)
        v *= 0x85ebca6b.toInt()
        v = v xor (v ushr 13)
        v *= 0xc2b2ae35.toInt()
        v = v xor (v ushr 16)
        return v
    }

    private fun scramble32(block: Int): Int {
        return Integer.rotateLeft(block * 0xcc9e2d51.toInt(), 15) * 0x1b873593
    }
}

internal object MD5 {
    private val base64Chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_".toByteArray()

    fun digestBase64UrlNoPadding(key: ByteArray, offset: Int, len: Int): ByteArray {
        val state = md5state(key, offset, len)
        val a = state[0]
        val b = state[1]
        val c = state[2]
        val d = state[3]
        val result = ByteArray(22)

        var t = a
        result[0] = base64Chars[(t ushr 2) and 63]
        result[1] = base64Chars[((t and 3) shl 4) or ((t ushr 12) and 15)]
        result[2] = base64Chars[(((t ushr 8) and 15) shl 2) or ((t ushr 22) and 3)]
        result[3] = base64Chars[(t ushr 16) and 63]

        t = (a ushr 24) or (b shl 8)
        result[4] = base64Chars[(t ushr 2) and 63]
        result[5] = base64Chars[((t and 3) shl 4) or ((t ushr 12) and 15)]
        result[6] = base64Chars[(((t ushr 8) and 15) shl 2) or ((t ushr 22) and 3)]
        result[7] = base64Chars[(t ushr 16) and 63]

        t = (b ushr 16) or (c shl 16)
        result[8] = base64Chars[(t ushr 2) and 63]
        result[9] = base64Chars[((t and 3) shl 4) or ((t ushr 12) and 15)]
        result[10] = base64Chars[(((t ushr 8) and 15) shl 2) or ((t ushr 22) and 3)]
        result[11] = base64Chars[(t ushr 16) and 63]

        t = c ushr 8
        result[12] = base64Chars[(t ushr 2) and 63]
        result[13] = base64Chars[((t and 3) shl 4) or ((t ushr 12) and 15)]
        result[14] = base64Chars[(((t ushr 8) and 15) shl 2) or ((t ushr 22) and 3)]
        result[15] = base64Chars[(t ushr 16) and 63]

        t = d
        result[16] = base64Chars[(t ushr 2) and 63]
        result[17] = base64Chars[((t and 3) shl 4) or ((t ushr 12) and 15)]
        result[18] = base64Chars[(((t ushr 8) and 15) shl 2) or ((t ushr 22) and 3)]
        result[19] = base64Chars[(t ushr 16) and 63]

        t = d ushr 24
        result[20] = base64Chars[(t ushr 2) and 63]
        result[21] = base64Chars[(t and 3) shl 4]

        return result
    }

    private fun cmn(q: Int, a: Int, b: Int, x: Int, s: Int, t: Int): Int {
        val v = a + q + x + t
        return Integer.rotateLeft(v, s) + b
    }

    private fun ff(a: Int, b: Int, c: Int, d: Int, x: Int, s: Int, t: Int) = cmn((b and c) or (b.inv() and d), a, b, x, s, t)
    private fun gg(a: Int, b: Int, c: Int, d: Int, x: Int, s: Int, t: Int) = cmn((b and d) or (c and d.inv()), a, b, x, s, t)
    private fun hh(a: Int, b: Int, c: Int, d: Int, x: Int, s: Int, t: Int) = cmn(b xor c xor d, a, b, x, s, t)
    private fun ii(a: Int, b: Int, c: Int, d: Int, x: Int, s: Int, t: Int) = cmn(c xor (b or d.inv()), a, b, x, s, t)

    private fun md5cycle(x: IntArray, k: IntArray) {
        var a = x[0]; var b = x[1]; var c = x[2]; var d = x[3]

        a = ff(a, b, c, d, k[0], 7, -680876936); d = ff(d, a, b, c, k[1], 12, -389564586)
        c = ff(c, d, a, b, k[2], 17, 606105819); b = ff(b, c, d, a, k[3], 22, -1044525330)
        a = ff(a, b, c, d, k[4], 7, -176418897); d = ff(d, a, b, c, k[5], 12, 1200080426)
        c = ff(c, d, a, b, k[6], 17, -1473231341); b = ff(b, c, d, a, k[7], 22, -45705983)
        a = ff(a, b, c, d, k[8], 7, 1770035416); d = ff(d, a, b, c, k[9], 12, -1958414417)
        c = ff(c, d, a, b, k[10], 17, -42063); b = ff(b, c, d, a, k[11], 22, -1990404162)
        a = ff(a, b, c, d, k[12], 7, 1804603682); d = ff(d, a, b, c, k[13], 12, -40341101)
        c = ff(c, d, a, b, k[14], 17, -1502002290); b = ff(b, c, d, a, k[15], 22, 1236535329)

        a = gg(a, b, c, d, k[1], 5, -165796510); d = gg(d, a, b, c, k[6], 9, -1069501632)
        c = gg(c, d, a, b, k[11], 14, 643717713); b = gg(b, c, d, a, k[0], 20, -373897302)
        a = gg(a, b, c, d, k[5], 5, -701558691); d = gg(d, a, b, c, k[10], 9, 38016083)
        c = gg(c, d, a, b, k[15], 14, -660478335); b = gg(b, c, d, a, k[4], 20, -405537848)
        a = gg(a, b, c, d, k[9], 5, 568446438); d = gg(d, a, b, c, k[14], 9, -1019803690)
        c = gg(c, d, a, b, k[3], 14, -187363961); b = gg(b, c, d, a, k[8], 20, 1163531501)
        a = gg(a, b, c, d, k[13], 5, -1444681467); d = gg(d, a, b, c, k[2], 9, -51403784)
        c = gg(c, d, a, b, k[7], 14, 1735328473); b = gg(b, c, d, a, k[12], 20, -1926607734)

        a = hh(a, b, c, d, k[5], 4, -378558); d = hh(d, a, b, c, k[8], 11, -2022574463)
        c = hh(c, d, a, b, k[11], 16, 1839030562); b = hh(b, c, d, a, k[14], 23, -35309556)
        a = hh(a, b, c, d, k[1], 4, -1530992060); d = hh(d, a, b, c, k[4], 11, 1272893353)
        c = hh(c, d, a, b, k[7], 16, -155497632); b = hh(b, c, d, a, k[10], 23, -1094730640)
        a = hh(a, b, c, d, k[13], 4, 681279174); d = hh(d, a, b, c, k[0], 11, -358537222)
        c = hh(c, d, a, b, k[3], 16, -722521979); b = hh(b, c, d, a, k[6], 23, 76029189)
        a = hh(a, b, c, d, k[9], 4, -640364487); d = hh(d, a, b, c, k[12], 11, -421815835)
        c = hh(c, d, a, b, k[15], 16, 530742520); b = hh(b, c, d, a, k[2], 23, -995338651)

        a = ii(a, b, c, d, k[0], 6, -198630844); d = ii(d, a, b, c, k[7], 10, 1126891415)
        c = ii(c, d, a, b, k[14], 15, -1416354905); b = ii(b, c, d, a, k[5], 21, -57434055)
        a = ii(a, b, c, d, k[12], 6, 1700485571); d = ii(d, a, b, c, k[3], 10, -1894986606)
        c = ii(c, d, a, b, k[10], 15, -1051523); b = ii(b, c, d, a, k[1], 21, -2054922799)
        a = ii(a, b, c, d, k[8], 6, 1873313359); d = ii(d, a, b, c, k[15], 10, -30611744)
        c = ii(c, d, a, b, k[6], 15, -1560198380); b = ii(b, c, d, a, k[13], 21, 1309151649)
        a = ii(a, b, c, d, k[4], 6, -145523070); d = ii(d, a, b, c, k[11], 10, -1120210379)
        c = ii(c, d, a, b, k[2], 15, 718787259); b = ii(b, c, d, a, k[9], 21, -343485551)

        x[0] += a; x[1] += b; x[2] += c; x[3] += d
    }

    private fun md5state(key: ByteArray, offset: Int, len: Int): IntArray {
        val n = offset + (len and 63.inv())
        val block = IntArray(16)
        val state = intArrayOf(1732584193, -271733879, -1732584194, 271733878)

        var i = offset
        while (i < n) {
            for (w in 0 until 16) {
                block[w] = Buffers.getUInt32(key, i + (w shl 2))
            }
            md5cycle(state, block)
            i += 64
        }

        val m = len and 3.inv()
        var w = 0
        while (i < m) {
            block[w++] = Buffers.getUInt32(key, i)
            i += 4
        }

        when (len and 3) {
            3 -> block[w++] = Buffers.getUInt24(key, i) or 0x80000000.toInt()
            2 -> block[w++] = Buffers.getUInt16(key, i) or 0x800000
            1 -> block[w++] = Buffers.getUInt8(key, i) or 0x8000
            else -> block[w++] = 0x80
        }

        if (w > 14) {
            if (w < 16) block[w] = 0
            md5cycle(state, block)
            w = 0
        }

        while (w < 16) {
            block[w] = 0
            w++
        }

        block[14] = len shl 3
        md5cycle(state, block)
        return state
    }
}

internal object Hashing {
    private val threadBuffer = ThreadLocal.withInitial { ByteArray(512) }

    fun hashUnit(unit: CharSequence): ByteArray {
        val n = unit.length
        val bufferLen = n shl 1

        var buffer = threadBuffer.get()
        if (buffer.size < bufferLen) {
            val bit = 32 - Integer.numberOfLeadingZeros(bufferLen - 1)
            buffer = ByteArray(1 shl bit)
            threadBuffer.set(buffer)
        }

        val encoded = Buffers.encodeUTF8(buffer, 0, unit)
        return MD5.digestBase64UrlNoPadding(buffer, 0, encoded)
    }
}
