package dev.jason.gboardpatches.extension.customstickers;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {26, 35})
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class GboardCustomStickersStoreTest {
    private final Context context = RuntimeEnvironment.getApplication();

    @Test public void packLifecycleKeepsIndexAndFilesInSync() throws Exception {
        GboardCustomStickersStore.Pack pack = GboardCustomStickersStore.createPack(context, "  Cats  ");
        assertEquals("Cats", pack.name);

        GboardCustomStickersStore.addSticker(context, pack.id, png(1024, 512));
        GboardCustomStickersStore.Pack saved = GboardCustomStickersStore.findPack(context, pack.id);
        assertEquals(1, saved.stickers.size());
        File sticker = GboardCustomStickersStore.stickerFile(context, pack.id, saved.stickers.get(0));
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(sticker.getPath(), bounds);
        assertEquals(GboardCustomStickersStore.MAX_STICKER_EDGE_PX, bounds.outWidth);
        assertEquals(GboardCustomStickersStore.MAX_STICKER_EDGE_PX / 2, bounds.outHeight);

        GboardCustomStickersStore.renamePack(context, pack.id, "Dogs");
        assertEquals("Dogs", GboardCustomStickersStore.findPack(context, pack.id).name);

        GboardCustomStickersStore.deleteSticker(context, pack.id, saved.stickers.get(0));
        assertFalse(sticker.exists());
        assertTrue(GboardCustomStickersStore.findPack(context, pack.id).stickers.isEmpty());

        GboardCustomStickersStore.deletePack(context, pack.id);
        assertNull(GboardCustomStickersStore.findPack(context, pack.id));
    }

    @Test public void coverDefaultsToFirstStickerAndFallsBackWhenDeleted() throws Exception {
        GboardCustomStickersStore.Pack pack = GboardCustomStickersStore.createPack(context, "Cover");
        assertNull(pack.cover);
        GboardCustomStickersStore.addSticker(context, pack.id, png(64, 64));
        GboardCustomStickersStore.addSticker(context, pack.id, png(32, 32));
        List<String> stickers = GboardCustomStickersStore.findPack(context, pack.id).stickers;
        assertEquals(stickers.get(0), GboardCustomStickersStore.findPack(context, pack.id).cover);

        GboardCustomStickersStore.setCover(context, pack.id, stickers.get(1));
        GboardCustomStickersStore.renamePack(context, pack.id, "Renamed");
        assertEquals(stickers.get(1), GboardCustomStickersStore.findPack(context, pack.id).cover);
        assertThrows(java.io.IOException.class,
                () -> GboardCustomStickersStore.setCover(context, pack.id, "missing.png"));

        GboardCustomStickersStore.deleteSticker(context, pack.id, stickers.get(1));
        assertEquals(stickers.get(0), GboardCustomStickersStore.findPack(context, pack.id).cover);
    }

    @Test public void backgroundRemovalAddsCopyNextToOriginal() throws Exception {
        GboardCustomStickersStore.Pack pack = GboardCustomStickersStore.createPack(context, "Cut");
        Bitmap bitmap = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.WHITE);
        for (int y = 10; y < 30; y++) {
            for (int x = 10; x < 30; x++) bitmap.setPixel(x, y, Color.BLUE);
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
        GboardCustomStickersStore.addSticker(context, pack.id, output.toByteArray());
        GboardCustomStickersStore.addSticker(context, pack.id, png(8, 8));
        List<String> before = GboardCustomStickersStore.findPack(context, pack.id).stickers;

        GboardCustomStickersStore.addBackgroundRemovedCopy(context, pack.id, before.get(0), 48);

        List<String> after = GboardCustomStickersStore.findPack(context, pack.id).stickers;
        assertEquals(3, after.size());
        assertEquals(before.get(0), after.get(0));
        assertEquals(before.get(1), after.get(2));
        Bitmap cut = BitmapFactory.decodeFile(
                GboardCustomStickersStore.stickerFile(context, pack.id, after.get(1)).getPath());
        assertEquals(0, Color.alpha(cut.getPixel(0, 0)));
        assertEquals(Color.BLUE, cut.getPixel(20, 20));

        // A solid image has nothing that is not background.
        assertThrows(GboardCustomStickersStore.NoBackgroundException.class,
                () -> GboardCustomStickersStore.addBackgroundRemovedCopy(
                        context, pack.id, before.get(1), 48));
        assertEquals(3, GboardCustomStickersStore.findPack(context, pack.id).stickers.size());
    }

    @Test public void rejectsEmptyNamesAndNonImages() throws Exception {
        assertThrows(java.io.IOException.class,
                () -> GboardCustomStickersStore.createPack(context, "   "));
        GboardCustomStickersStore.Pack pack = GboardCustomStickersStore.createPack(context, "Pack");
        assertThrows(java.io.IOException.class,
                () -> GboardCustomStickersStore.addSticker(context, pack.id, new byte[] {1, 2, 3}));
        assertTrue(GboardCustomStickersStore.findPack(context, pack.id).stickers.isEmpty());
    }

    @Test public void parserDropsIdsAndFilesThatCouldEscapeTheFolder() throws Exception {
        String id = "0123456789abcdef0123456789abcdef";
        List<GboardCustomStickersStore.Pack> packs = GboardCustomStickersStore.fromJson(
                "{\"packs\":["
                        + "{\"id\":\"../../shared_prefs\",\"name\":\"bad\",\"stickers\":[]},"
                        + "{\"id\":\"" + id + "\",\"name\":\"ok\",\"stickers\":"
                        + "[\"../x.png\",\"" + id + ".png\",\"" + id + ".exe\"],"
                        + "\"cover\":\"../x.png\"}]}");
        assertEquals(1, packs.size());
        assertEquals(id, packs.get(0).id);
        assertEquals(List.of(id + ".png"), packs.get(0).stickers);
        assertEquals(id + ".png", packs.get(0).cover);
        assertEquals(1, GboardCustomStickersStore.fromJson(
                GboardCustomStickersStore.toJson(packs)).size());
    }

    private static byte[] png(int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.BLUE);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
        return output.toByteArray();
    }
}
