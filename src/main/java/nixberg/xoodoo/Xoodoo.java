package nixberg.xoodoo;

/**
 * Hello world!
 *
 */
public final class Xoodoo {

    private byte[] bytes;

    private static final int[] roundConstants = { 0x058, 0x038, 0x3c0, 0x0d0, 0x120, 0x014, 0x060, 0x02c, 0x380, 0x0f0,
            0x1a0, 0x012, };

    public Xoodoo() {
        bytes = new byte[48];
    }

    public byte get(int index) {
        return bytes[index];
    }

    public void xor(int index, byte value) {
        bytes[index] ^= value;
    }

    public void permute() {
        var s = new int[12];
        var e = new int[4];
        int r, temp;

        for (int i = 0, j = 0; i < 12; i++) {
            s[i] = Byte.toUnsignedInt(bytes[j++]);
            s[i] |= Byte.toUnsignedInt(bytes[j++]) << 8;
            s[i] |= Byte.toUnsignedInt(bytes[j++]) << 16;
            s[i] |= Byte.toUnsignedInt(bytes[j++]) << 24;
        }

        for (int roundConstant : roundConstants) {
            for (int i = 0; i < 4; i++) {
                r = s[i] ^ s[i + 4] ^ s[i + 8];
                e[i] = (r >>> 18) | (r << 14) & 0xFFFF_FFFF;
                r = e[i];
                e[i] ^= (r >>> 9) | (r << 23) & 0xFFFF_FFFF;
            }

            for (int i = 0; i < 12; i++) {
                s[i] ^= e[(i - 1) & 3];
            }

            temp = s[7];
            s[7] = s[4];
            s[4] = temp;
            temp = s[7];
            s[7] = s[5];
            s[5] = temp;
            temp = s[7];
            s[7] = s[6];
            s[6] = temp;
            s[0] ^= roundConstant;

            for (int i = 0; i < 4; i++) {
                final var a = s[i];
                final var b = s[i + 4];
                r = s[i + 8];
                final var c = (r >>> 21) | (r << 11) & 0xFFFF_FFFF;

                r = (b & ~a) ^ c;
                s[i + 8] = (r >>> 24) | (r << 8) & 0xFFFF_FFFF;
                r = (a & ~c) ^ b;
                s[i + 4] = (r >>> 31) | (r << 1) & 0xFFFF_FFFF;
                s[i] ^= c & ~b;
            }

            temp = s[8];
            s[8] = s[10];
            s[10] = temp;
            temp = s[9];
            s[9] = s[11];
            s[11] = temp;
        }

        for (int i = 0, j = 0; i < 12; i++) {
            bytes[j++] = (byte) s[i];
            bytes[j++] = (byte) (s[i] >>> 8);
            bytes[j++] = (byte) (s[i] >>> 16);
            bytes[j++] = (byte) (s[i] >>> 24);
        }
    }
}
