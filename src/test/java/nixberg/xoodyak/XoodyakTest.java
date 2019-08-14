package nixberg.xoodyak;

import java.util.Arrays;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import com.google.gson.Gson;
import com.google.common.io.BaseEncoding;

import org.junit.Test;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

public class XoodyakTest {
    final class HashKAT {
        final String msg;
        final String md;

        HashKAT(String msg, String md) {
            this.msg = msg;
            this.md = md;
        }
    }

    @Test
    public void hash() throws Exception {
        var url = XoodyakTest.class.getResource("hash.json");
        java.nio.file.Path resPath = java.nio.file.Paths.get(url.toURI());
        var json = new String(Files.readAllBytes(resPath), "UTF8");
        var kats = new Gson().fromJson(json, HashKAT[].class);

        for (HashKAT kat : kats) {
            var msg = BaseEncoding.base16().decode(kat.msg);
            var md = BaseEncoding.base16().decode(kat.md);

            var msgStream = new ByteArrayInputStream(msg);

            var xoodyak = new Xoodyak();
            xoodyak.absorb(msgStream, msg.length);
            var newMD = xoodyak.squeeze(md.length).toByteArray();

            assertEquals(md.length, newMD.length);
            assertTrue(Arrays.equals(md, newMD));
        }
    }

    final class AEADKAT {
        final String key;
        final String nonce;
        final String pt;
        final String ad;
        final String ct;

        AEADKAT(String key, String nonce, String pt, String ad, String ct) {
            this.key = key;
            this.nonce = nonce;
            this.pt = pt;
            this.ad = ad;
            this.ct = ct;
        }
    }

    @Test
    public void aead() throws Exception {
        var url = XoodyakTest.class.getResource("aead.json");
        java.nio.file.Path resPath = java.nio.file.Paths.get(url.toURI());
        var json = new String(Files.readAllBytes(resPath), "UTF8");
        var kats = new Gson().fromJson(json, AEADKAT[].class);

        for (AEADKAT kat : kats) {
            var key = BaseEncoding.base16().decode(kat.key);
            var nonce = BaseEncoding.base16().decode(kat.nonce);
            var pt = BaseEncoding.base16().decode(kat.pt);
            var ad = BaseEncoding.base16().decode(kat.ad);
            var ct = BaseEncoding.base16().decode(kat.ct);
            var tag = Arrays.copyOfRange(ct, pt.length, ct.length);

            var nonceStream = new ByteArrayInputStream(nonce);
            var ptStream = new ByteArrayInputStream(pt);
            var adStream = new ByteArrayInputStream(ad);
            var ctStream = new ByteArrayInputStream(ct);

            var newCTStream = new ByteArrayOutputStream(ct.length);

            var xoodyak = new Xoodyak(key, null, null);
            xoodyak.absorb(nonceStream, nonce.length);
            xoodyak.absorb(adStream, ad.length);
            xoodyak.encrypt(ptStream, newCTStream, pt.length);
            xoodyak.squeeze(newCTStream, tag.length);

            var newCT = newCTStream.toByteArray();
            assertEquals(ct.length, newCT.length);
            assertTrue(Arrays.equals(ct, newCT));

            nonceStream.reset();
            ptStream.reset();
            adStream.reset();
            ctStream.reset();

            var newPTStream = new ByteArrayOutputStream(pt.length);
            var newTagStream = new ByteArrayOutputStream(tag.length);

            xoodyak = new Xoodyak(key, null, null);
            xoodyak.absorb(nonceStream, nonce.length);
            xoodyak.absorb(adStream, ad.length);
            xoodyak.decrypt(ctStream, newPTStream, pt.length);
            xoodyak.squeeze(newTagStream, tag.length);

            var newPT = newPTStream.toByteArray();
            assertEquals(pt.length, newPT.length);
            assertTrue(Arrays.equals(pt, newPT));

            var newTag = newTagStream.toByteArray();
            assertEquals(tag.length, newTag.length);
            assertTrue(Arrays.equals(tag, newTag));
        }
    }
}
