package nixberg.xoodyak;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import nixberg.xoodoo.Xoodoo;

/**
 * Hello world!
 *
 */
public final class Xoodyak {

    private enum Flag {
        ZERO(0x00), ABSORB_KEY(0x02), ABSORB(0x03), RATCHET(0x10), SQUEEZE_KEY(0x20), SQUEEZE(0x40), CRYPT(0x80);

        private final byte value;

        Flag(int value) {
            this.value = (byte) value;
        }
    }

    private enum Mode {
        HASH, KEYED
    }

    private final class Rates {
        static final int HASH = 16;
        static final int INPUT = 44;
        static final int OUTPUT = 24;
        static final int RATCHET = 16;

        final int absorb;
        final int squeeze;

        private Rates(int absorb, int squeeze) {
            this.absorb = absorb;
            this.squeeze = squeeze;
        }
    }

    private enum Phase {
        UP, DOWN
    }

    final Mode mode;
    final Rates rates;
    Phase phase = Phase.UP;
    Xoodoo xoodoo = new Xoodoo();

    public Xoodyak() {
        mode = Mode.HASH;
        rates = new Rates(Rates.HASH, Rates.HASH);
    }

    public Xoodyak(byte[] key, byte[] id, byte[] counter) {
        mode = Mode.KEYED;
        rates = new Rates(Rates.INPUT, Rates.OUTPUT);

        if (id == null) {
            id = new byte[0];
        }

        var buffer = new byte[key.length + id.length + 1];
        assert buffer.length <= Rates.INPUT;
        System.arraycopy(key, 0, buffer, 0, key.length);
        System.arraycopy(id, 0, buffer, key.length, id.length);
        buffer[buffer.length - 1] = (byte) id.length;
        var bufferStream = new ByteArrayInputStream(buffer);

        try {
            absorbAny(bufferStream, buffer.length, rates.absorb, Flag.ABSORB_KEY);
            if (counter != null && counter.length > 0) {
                var counterStream = new ByteArrayInputStream(counter);
                absorbAny(counterStream, counter.length, 1, Flag.ZERO);
            }
        } catch (IOException e) {
            System.err.println(e);
            assert false;
        }
    }

    private void absorbAny(InputStream input, int length, int rate, Flag downFlag) throws IOException {
        var block = new byte[rate];

        do {
            var blockSize = Math.min(length, rate);
            if (blockSize > 0) {
                var bytesRead = input.read(block, 0, blockSize);
                assert blockSize == bytesRead;
            }

            if (phase != Phase.UP) {
                up(null, 0, Flag.ZERO);
            }

            down(block, blockSize, downFlag);
            downFlag = Flag.ZERO;
            length -= blockSize;

        } while (length > 0);
    }

    private void crypt(InputStream input, OutputStream output, int length, boolean decrypt) throws IOException {
        var inputBlock = new byte[Rates.OUTPUT];
        var outputBlock = new byte[Rates.OUTPUT];
        var flag = Flag.CRYPT;

        do {
            var blockSize = Math.min(length, Rates.OUTPUT);
            if (length > 0) {
                var bytesRead = input.read(inputBlock, 0, blockSize);
                assert blockSize == bytesRead;
            }

            up(null, 0, flag);
            flag = Flag.ZERO;

            for (int i = 0; i < blockSize; i++) {
                outputBlock[i] = (byte) (inputBlock[i] ^ xoodoo.get(i));
            }

            if (decrypt) {
                down(outputBlock, blockSize, Flag.ZERO);
            } else {
                down(inputBlock, blockSize, Flag.ZERO);
            }

            output.write(outputBlock, 0, blockSize);
            length -= blockSize;

        } while (length > 0);
    }

    private void squeezeAny(OutputStream output, int length, Flag upFlag) throws IOException {
        var blockSize = Math.min(length, rates.squeeze);
        up(output, blockSize, upFlag);
        length -= blockSize;

        while (length > 0) {
            down(null, 0, Flag.ZERO);
            blockSize = Math.min(length, rates.squeeze);
            up(output, blockSize, Flag.ZERO);
            length -= blockSize;
        }
    }

    private void down(byte[] block, int length, Flag flag) {
        phase = Phase.DOWN;
        for (int i = 0; i < length; i++) {
            xoodoo.xor(i, block[i]);
        }
        xoodoo.xor(length, (byte) 0x01);
        if (mode == Mode.HASH) {
            xoodoo.xor(47, (byte) (flag.value & 0x01));
        } else {
            xoodoo.xor(47, (byte) flag.value);
        }
    }

    private void up(OutputStream output, int length, Flag flag) throws IOException {
        phase = Phase.UP;
        if (mode != Mode.HASH) {
            xoodoo.xor(47, (byte) flag.value);
        }
        xoodoo.permute();
        for (int i = 0; i < length; i++) {
            output.write(xoodoo.get(i));
        }
    }

    public void absorb(InputStream input, int length) throws IOException {
        absorbAny(input, length, rates.absorb, Flag.ABSORB);
    }

    public void encrypt(InputStream plaintext, OutputStream ciphertext, int length) throws IOException {
        assert mode == Mode.KEYED;
        crypt(plaintext, ciphertext, length, false);
    }

    public OutputStream encrypt(InputStream plaintext, int length) {
        var ciphertext = new ByteArrayOutputStream(length + 16);
        try {
            encrypt(plaintext, ciphertext, length);
        } catch (IOException e) {
            System.err.println(e);
            assert false;
        }
        return ciphertext;
    }

    public void decrypt(InputStream ciphertext, OutputStream plaintext, int length) throws IOException {
        assert mode == Mode.KEYED;
        crypt(ciphertext, plaintext, length, true);
    }

    public OutputStream decrypt(InputStream ciphertext, int length) {
        var plaintext = new ByteArrayOutputStream(length);
        try {
            decrypt(ciphertext, plaintext, length);
        } catch (IOException e) {
            System.err.println(e);
            assert false;
        }
        return plaintext;
    }

    public void squeeze(OutputStream output, int length) throws IOException {
        squeezeAny(output, length, Flag.SQUEEZE);
    }

    public ByteArrayOutputStream squeeze(int length) {
        var output = new ByteArrayOutputStream(length);
        try {
            squeeze(output, length);
        } catch (IOException e) {
            System.err.println(e);
            assert false;
        }
        return output;
    }

    public void squeezeKey(OutputStream output, int length) throws IOException {
        assert mode == Mode.KEYED;
        squeezeAny(output, length, Flag.SQUEEZE_KEY);
    }

    public ByteArrayOutputStream squeezeKey(int length) {
        var output = new ByteArrayOutputStream(length);
        try {
            squeezeKey(output, length);
        } catch (IOException e) {
            System.err.println(e);
            assert false;
        }
        return output;
    }

    public void ratchet() {
        assert mode == Mode.KEYED;
        var output = new ByteArrayOutputStream(Rates.RATCHET);
        try {
            squeezeAny(output, Rates.RATCHET, Flag.RATCHET);
            var buffer = output.toByteArray();
            var input = new ByteArrayInputStream(buffer);
            absorbAny(input, Rates.RATCHET, Rates.RATCHET, Flag.ZERO);
        } catch (IOException e) {
            System.err.println(e);
            assert false;
        }
    }
}
