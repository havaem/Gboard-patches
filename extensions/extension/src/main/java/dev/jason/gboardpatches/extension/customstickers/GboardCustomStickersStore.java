package dev.jason.gboardpatches.extension.customstickers;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.util.AtomicFile;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Sticker packs live as image files under the app's files directory, with one small JSON index.
 * Images stay out of SharedPreferences so the keyboard process never loads them as text.
 */
public final class GboardCustomStickersStore {
    public static final int MAX_IMPORT_BYTES = 8 * 1024 * 1024;
    static final String DIRECTORY = "gboard_patches_custom_stickers";
    static final String INDEX_FILE = "packs.json";
    static final int MAX_STICKER_EDGE_PX = 512;
    static final int MAX_STICKER_BYTES = 1024 * 1024;
    static final int MAX_NAME_LENGTH = 60;
    private static final long MAX_SOURCE_PIXELS = 40_000_000L;
    // Ids and file names come back from disk; only these shapes may become paths.
    private static final Pattern PACK_ID = Pattern.compile("[0-9a-f]{32}");
    private static final Pattern STICKER_FILE = Pattern.compile("[0-9a-f]{32}\\.(png|webp)");

    private GboardCustomStickersStore() { }

    public static final class Pack {
        public final String id;
        public final String name;
        public final List<String> stickers;

        Pack(String id, String name, List<String> stickers) {
            this.id = id;
            this.name = name;
            this.stickers = Collections.unmodifiableList(new ArrayList<>(stickers));
        }
    }

    public static synchronized List<Pack> load(Context context) {
        File index = indexFile(context);
        if (!index.isFile()) return Collections.emptyList();
        try {
            byte[] bytes = new AtomicFile(index).readFully();
            return fromJson(new String(bytes, StandardCharsets.UTF_8));
        } catch (IOException | JSONException ignored) {
            return Collections.emptyList();
        }
    }

    public static synchronized Pack findPack(Context context, String packId) {
        for (Pack pack : load(context)) {
            if (pack.id.equals(packId)) return pack;
        }
        return null;
    }

    public static synchronized Pack createPack(Context context, String name) throws IOException {
        String cleaned = cleanName(name);
        if (cleaned.isEmpty()) throw new IOException("Pack name is empty");
        List<Pack> packs = new ArrayList<>(load(context));
        Pack pack = new Pack(newId(), cleaned, Collections.emptyList());
        packs.add(pack);
        save(context, packs);
        return pack;
    }

    public static synchronized void renamePack(Context context, String packId, String name)
            throws IOException {
        String cleaned = cleanName(name);
        if (cleaned.isEmpty()) throw new IOException("Pack name is empty");
        List<Pack> packs = new ArrayList<>(load(context));
        int index = indexOf(packs, packId);
        Pack old = packs.get(index);
        packs.set(index, new Pack(old.id, cleaned, old.stickers));
        save(context, packs);
    }

    public static synchronized void deletePack(Context context, String packId) throws IOException {
        List<Pack> packs = new ArrayList<>(load(context));
        packs.remove(indexOf(packs, packId));
        save(context, packs);
        deleteRecursively(packDirectory(context, packId));
    }

    /** Called off the UI thread with the picked document's bytes. */
    public static synchronized void addSticker(Context context, String packId, byte[] source)
            throws IOException {
        List<Pack> packs = new ArrayList<>(load(context));
        int index = indexOf(packs, packId);
        Encoded encoded = encodeSticker(source);
        String fileName = newId() + "." + encoded.extension;
        File directory = packDirectory(context, packId);
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Sticker folder could not be created");
        }
        File target = new File(directory, fileName);
        try (FileOutputStream output = new FileOutputStream(target)) {
            output.write(encoded.bytes);
        }
        Pack old = packs.get(index);
        List<String> stickers = new ArrayList<>(old.stickers);
        stickers.add(fileName);
        packs.set(index, new Pack(old.id, old.name, stickers));
        try {
            save(context, packs);
        } catch (IOException failure) {
            //noinspection ResultOfMethodCallIgnored
            target.delete();
            throw failure;
        }
    }

    public static synchronized void deleteSticker(Context context, String packId, String fileName)
            throws IOException {
        List<Pack> packs = new ArrayList<>(load(context));
        int index = indexOf(packs, packId);
        Pack old = packs.get(index);
        List<String> stickers = new ArrayList<>(old.stickers);
        if (!stickers.remove(fileName)) throw new IOException("Unknown sticker");
        packs.set(index, new Pack(old.id, old.name, stickers));
        save(context, packs);
        //noinspection ResultOfMethodCallIgnored
        new File(packDirectory(context, packId), fileName).delete();
    }

    public static File stickerFile(Context context, String packId, String fileName) {
        return new File(packDirectory(context, packId), fileName);
    }

    static String toJson(List<Pack> packs) throws JSONException {
        JSONArray array = new JSONArray();
        for (Pack pack : packs) {
            JSONArray stickers = new JSONArray();
            for (String sticker : pack.stickers) stickers.put(sticker);
            array.put(new JSONObject()
                    .put("id", pack.id)
                    .put("name", pack.name)
                    .put("stickers", stickers));
        }
        return new JSONObject().put("version", 1).put("packs", array).toString();
    }

    /** Drops entries whose id or file name could escape the sticker folder. */
    static List<Pack> fromJson(String json) throws JSONException {
        JSONArray array = new JSONObject(json).optJSONArray("packs");
        if (array == null) return Collections.emptyList();
        List<Pack> packs = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            String id = item.optString("id", "");
            String name = cleanName(item.optString("name", ""));
            if (!PACK_ID.matcher(id).matches() || name.isEmpty()) continue;
            List<String> stickers = new ArrayList<>();
            JSONArray files = item.optJSONArray("stickers");
            for (int j = 0; files != null && j < files.length(); j++) {
                String file = files.optString(j, "");
                if (STICKER_FILE.matcher(file).matches()) stickers.add(file);
            }
            packs.add(new Pack(id, name, stickers));
        }
        return packs;
    }

    static String cleanName(String name) {
        String trimmed = name == null ? "" : name.trim();
        return trimmed.length() > MAX_NAME_LENGTH ? trimmed.substring(0, MAX_NAME_LENGTH) : trimmed;
    }

    private static void save(Context context, List<Pack> packs) throws IOException {
        File directory = rootDirectory(context);
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Sticker folder could not be created");
        }
        AtomicFile file = new AtomicFile(indexFile(context));
        FileOutputStream output = file.startWrite();
        try {
            output.write(toJson(packs).getBytes(StandardCharsets.UTF_8));
            file.finishWrite(output);
        } catch (IOException | JSONException | RuntimeException failure) {
            file.failWrite(output);
            throw new IOException("Sticker packs could not be saved", failure);
        }
    }

    private static int indexOf(List<Pack> packs, String packId) throws IOException {
        for (int i = 0; i < packs.size(); i++) {
            if (packs.get(i).id.equals(packId)) return i;
        }
        throw new IOException("Unknown sticker pack");
    }

    private static File rootDirectory(Context context) {
        return new File(context.getFilesDir(), DIRECTORY);
    }

    private static File indexFile(Context context) {
        return new File(rootDirectory(context), INDEX_FILE);
    }

    private static File packDirectory(Context context, String packId) {
        return new File(rootDirectory(context), packId);
    }

    private static String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteRecursively(child);
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    static final class Encoded {
        final byte[] bytes;
        final String extension;

        Encoded(byte[] bytes, String extension) {
            this.bytes = bytes;
            this.extension = extension;
        }
    }

    /** Fits the image inside MAX_STICKER_EDGE_PX, keeping alpha; never enlarges. */
    static Encoded encodeSticker(byte[] source) throws IOException {
        if (source == null || source.length == 0 || source.length > MAX_IMPORT_BYTES) {
            throw new IOException("Image is empty or too large");
        }
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(source, 0, source.length, bounds);
        if (!("image/png".equals(bounds.outMimeType)
                || "image/jpeg".equals(bounds.outMimeType)
                || "image/webp".equals(bounds.outMimeType)
                || "image/gif".equals(bounds.outMimeType))
                || bounds.outWidth <= 0 || bounds.outHeight <= 0
                || (long) bounds.outWidth * bounds.outHeight > MAX_SOURCE_PIXELS) {
            throw new IOException("Unsupported image or dimensions");
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inSampleSize = 1;
        while (Math.max(bounds.outWidth, bounds.outHeight) / (options.inSampleSize * 2)
                >= MAX_STICKER_EDGE_PX * 2) {
            options.inSampleSize *= 2;
        }
        Bitmap decoded = BitmapFactory.decodeByteArray(source, 0, source.length, options);
        if (decoded == null) throw new IOException("Image could not be decoded");
        Bitmap scaled = decoded;
        try {
            float scale = Math.min(1f, (float) MAX_STICKER_EDGE_PX
                    / Math.max(decoded.getWidth(), decoded.getHeight()));
            if (scale < 1f) {
                scaled = Bitmap.createScaledBitmap(decoded,
                        Math.max(1, Math.round(decoded.getWidth() * scale)),
                        Math.max(1, Math.round(decoded.getHeight() * scale)), true);
            }
            byte[] png = compress(scaled, Bitmap.CompressFormat.PNG, 100);
            if (png != null && png.length <= MAX_STICKER_BYTES) return new Encoded(png, "png");
            Bitmap.CompressFormat webp = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                    ? Bitmap.CompressFormat.WEBP_LOSSY : Bitmap.CompressFormat.WEBP;
            for (int quality = 90; quality >= 50; quality -= 10) {
                byte[] encoded = compress(scaled, webp, quality);
                if (encoded != null && encoded.length <= MAX_STICKER_BYTES) {
                    return new Encoded(encoded, "webp");
                }
            }
            throw new IOException("Image could not be saved");
        } finally {
            if (scaled != decoded) scaled.recycle();
            decoded.recycle();
        }
    }

    private static byte[] compress(Bitmap bitmap, Bitmap.CompressFormat format, int quality) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        return bitmap.compress(format, quality, output) ? output.toByteArray() : null;
    }
}
