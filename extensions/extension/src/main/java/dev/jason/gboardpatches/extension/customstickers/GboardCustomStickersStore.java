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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    /** Outline width for a 512 px sticker. */
    static final int MAX_OUTLINE_WIDTH = 32;
    private static final long MAX_SOURCE_PIXELS = 40_000_000L;
    // Ids and file names come back from disk; only these shapes may become paths.
    private static final Pattern PACK_ID = Pattern.compile("[0-9a-f]{32}");
    private static final Pattern STICKER_FILE = Pattern.compile("[0-9a-f]{32}\\.(png|webp)");

    private GboardCustomStickersStore() { }

    public static final class Pack {
        public final String id;
        public final String name;
        public final List<String> stickers;
        /** The pack's cover sticker; the first sticker when none was chosen. Null if empty. */
        public final String cover;
        /** Edited stickers by shown file name: their original image and settings. */
        public final Map<String, Edited> edited;

        Pack(String id, String name, List<String> stickers, String cover,
                Map<String, Edited> edited) {
            this.id = id;
            this.name = name;
            this.stickers = Collections.unmodifiableList(new ArrayList<>(stickers));
            this.cover = cover != null && stickers.contains(cover) ? cover
                    : stickers.isEmpty() ? null : stickers.get(0);
            Map<String, Edited> kept = new LinkedHashMap<>();
            for (Map.Entry<String, Edited> entry : edited.entrySet()) {
                String source = entry.getValue().source;
                if (stickers.contains(entry.getKey()) && STICKER_FILE.matcher(source).matches()
                        && !source.equals(entry.getKey())) {
                    kept.put(entry.getKey(), entry.getValue());
                }
            }
            this.edited = Collections.unmodifiableMap(kept);
        }

        public Edits editsOf(String fileName) {
            Edited record = edited.get(fileName);
            return record == null ? Edits.NONE : record.edits;
        }

        /** The unedited image behind a shown sticker. */
        public String sourceOf(String fileName) {
            Edited record = edited.get(fileName);
            return record == null ? fileName : record.source;
        }
    }

    public static final class Edited {
        public final String source;
        public final Edits edits;

        Edited(String source, Edits edits) {
            this.source = source;
            this.edits = edits;
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
        Pack pack = new Pack(newId(), cleaned, Collections.emptyList(), null,
                Collections.emptyMap());
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
        packs.set(index, new Pack(old.id, cleaned, old.stickers, old.cover, old.edited));
        save(context, packs);
    }

    public static synchronized void setCover(Context context, String packId, String fileName)
            throws IOException {
        List<Pack> packs = new ArrayList<>(load(context));
        int index = indexOf(packs, packId);
        Pack old = packs.get(index);
        if (!old.stickers.contains(fileName)) throw new IOException("Unknown sticker");
        packs.set(index, new Pack(old.id, old.name, old.stickers, fileName, old.edited));
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
        insertSticker(context, packs, index, packs.get(index).stickers.size(),
                encodeSticker(source));
    }

    /** Editor settings; outline width is given for a 512 px sticker and scaled to its size. */
    public static final class Edits {
        public final int backgroundTolerance;
        /** 0 to 100; 100 rounds the shorter side fully. */
        public final int cornerPercent;
        public final int outlineWidth;
        public final int outlineColor;

        public Edits(int backgroundTolerance, int cornerPercent, int outlineWidth,
                int outlineColor) {
            this.backgroundTolerance = backgroundTolerance;
            this.cornerPercent = cornerPercent;
            this.outlineWidth = outlineWidth;
            this.outlineColor = outlineColor;
        }

        public static final Edits NONE = new Edits(0, 0, 0, 0xFFFFFFFF);

        public boolean isEmpty() {
            return backgroundTolerance <= 0 && cornerPercent <= 0 && outlineWidth <= 0;
        }

        @Override public boolean equals(Object other) {
            if (!(other instanceof Edits)) return false;
            Edits that = (Edits) other;
            if (isEmpty() && that.isEmpty()) return true;
            return backgroundTolerance == that.backgroundTolerance
                    && cornerPercent == that.cornerPercent
                    && outlineWidth == that.outlineWidth
                    && (outlineWidth == 0 || outlineColor == that.outlineColor);
        }

        @Override public int hashCode() {
            return isEmpty() ? 0 : java.util.Objects.hash(
                    backgroundTolerance, cornerPercent, outlineWidth,
                    outlineWidth == 0 ? 0 : outlineColor);
        }
    }

    public static final class Rendered {
        public final Bitmap bitmap;
        /** Background removal was asked for but the edges had no plain colour. */
        public final boolean backgroundNotFound;

        Rendered(Bitmap bitmap, boolean backgroundNotFound) {
            this.bitmap = bitmap;
            this.backgroundNotFound = backgroundNotFound;
        }
    }

    /** Applies the edits to a sticker file; used for both the live preview and saving. */
    public static Rendered render(File file, Edits edits) throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap source = BitmapFactory.decodeFile(file.getPath(), options);
        if (source == null) throw new IOException("Sticker could not be decoded");
        int width = source.getWidth();
        int height = source.getHeight();
        int[] pixels = new int[width * height];
        source.getPixels(pixels, 0, width, 0, 0, width, height);
        source.recycle();
        boolean backgroundNotFound = false;
        if (edits.backgroundTolerance > 0) {
            int[] original = pixels.clone();
            int removed = GboardCustomStickersBackgroundRemover.remove(
                    pixels, width, height, edits.backgroundTolerance);
            if (removed == 0 || removed == pixels.length) {
                pixels = original;
                backgroundNotFound = true;
            }
        }
        // Drop empty transparent margins so the content fills the sticker after scaling below.
        int[] bounds = GboardCustomStickersBackgroundRemover.visibleBounds(pixels, width, height);
        if (bounds != null && (bounds[2] - bounds[0] < width || bounds[3] - bounds[1] < height)) {
            int croppedWidth = bounds[2] - bounds[0];
            int croppedHeight = bounds[3] - bounds[1];
            int[] cropped = new int[croppedWidth * croppedHeight];
            for (int y = 0; y < croppedHeight; y++) {
                System.arraycopy(pixels, (bounds[1] + y) * width + bounds[0],
                        cropped, y * croppedWidth, croppedWidth);
            }
            pixels = cropped;
            width = croppedWidth;
            height = croppedHeight;
        }
        if (edits.cornerPercent > 0) {
            GboardCustomStickersCorners.round(pixels, width, height, edits.cornerPercent);
        }
        if (edits.outlineWidth > 0) {
            int radius = Math.max(1, Math.round(
                    edits.outlineWidth * Math.max(width, height) / (float) MAX_STICKER_EDGE_PX));
            pixels = GboardCustomStickersOutline.apply(
                    pixels, width, height, radius, edits.outlineColor);
            width += 2 * radius;
            height += 2 * radius;
        }
        Bitmap result = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888);
        // Edited stickers always fill the 512 px box, enlarging small or cropped content.
        float scale = (float) MAX_STICKER_EDGE_PX / Math.max(width, height);
        if (scale != 1f) {
            Bitmap scaled = Bitmap.createScaledBitmap(result,
                    Math.max(1, Math.round(width * scale)),
                    Math.max(1, Math.round(height * scale)), true);
            result.recycle();
            result = scaled;
        }
        return new Rendered(result, backgroundNotFound);
    }

    /**
     * Re-renders the sticker from its original image with {@code edits}, in place: it keeps its
     * position and cover role, and the original stays on disk so it can be edited again.
     * Empty edits restore the original. Returns the sticker's new file name. Off the UI thread.
     */
    public static synchronized String saveEdited(Context context, String packId, String fileName,
            Edits edits) throws IOException {
        List<Pack> packs = new ArrayList<>(load(context));
        int index = indexOf(packs, packId);
        Pack old = packs.get(index);
        int position = old.stickers.indexOf(fileName);
        if (position < 0) throw new IOException("Unknown sticker");
        if (old.editsOf(fileName).equals(edits)) return fileName;
        String source = old.sourceOf(fileName);
        Map<String, Edited> edited = new LinkedHashMap<>(old.edited);
        edited.remove(fileName);
        File written = null;
        String shown = source;
        if (!edits.isEmpty()) {
            Rendered rendered = render(stickerFile(context, packId, source), edits);
            try {
                written = writeSticker(context, packId, encodeBitmap(rendered.bitmap));
            } finally {
                rendered.bitmap.recycle();
            }
            shown = written.getName();
            edited.put(shown, new Edited(source, edits));
        }
        List<String> stickers = new ArrayList<>(old.stickers);
        stickers.set(position, shown);
        String cover = fileName.equals(old.cover) ? shown : old.cover;
        packs.set(index, new Pack(old.id, old.name, stickers, cover, edited));
        if (written != null) {
            saveOrDelete(context, packs, written);
        } else {
            save(context, packs);
        }
        // The previous render is no longer referenced; the original is never deleted here.
        if (!fileName.equals(source)) {
            //noinspection ResultOfMethodCallIgnored
            stickerFile(context, packId, fileName).delete();
        }
        return shown;
    }

    /** Applies the same edits to several stickers; returns how many could not be saved. */
    public static int saveEdited(Context context, String packId, List<String> fileNames,
            Edits edits) {
        int failed = 0;
        for (String fileName : fileNames) {
            try {
                saveEdited(context, packId, fileName, edits);
            } catch (IOException | RuntimeException failure) {
                failed++;
            }
        }
        return failed;
    }

    private static void insertSticker(Context context, List<Pack> packs, int index, int position,
            Encoded encoded) throws IOException {
        Pack old = packs.get(index);
        File target = writeSticker(context, old.id, encoded);
        List<String> stickers = new ArrayList<>(old.stickers);
        stickers.add(position, target.getName());
        packs.set(index, new Pack(old.id, old.name, stickers, old.cover, old.edited));
        saveOrDelete(context, packs, target);
    }

    private static File writeSticker(Context context, String packId, Encoded encoded)
            throws IOException {
        File directory = packDirectory(context, packId);
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Sticker folder could not be created");
        }
        File target = new File(directory, newId() + "." + encoded.extension);
        try (FileOutputStream output = new FileOutputStream(target)) {
            output.write(encoded.bytes);
        }
        return target;
    }

    private static void saveOrDelete(Context context, List<Pack> packs, File written)
            throws IOException {
        try {
            save(context, packs);
        } catch (IOException failure) {
            //noinspection ResultOfMethodCallIgnored
            written.delete();
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
        Map<String, Edited> edited = new LinkedHashMap<>(old.edited);
        edited.remove(fileName);
        packs.set(index, new Pack(old.id, old.name, stickers, old.cover, edited));
        save(context, packs);
        //noinspection ResultOfMethodCallIgnored
        stickerFile(context, packId, fileName).delete();
        String source = old.sourceOf(fileName);
        if (!source.equals(fileName)) {
            //noinspection ResultOfMethodCallIgnored
            stickerFile(context, packId, source).delete();
        }
    }

    public static File stickerFile(Context context, String packId, String fileName) {
        return new File(packDirectory(context, packId), fileName);
    }

    static String toJson(List<Pack> packs) throws JSONException {
        JSONArray array = new JSONArray();
        for (Pack pack : packs) {
            JSONArray stickers = new JSONArray();
            for (String sticker : pack.stickers) stickers.put(sticker);
            JSONObject item = new JSONObject()
                    .put("id", pack.id)
                    .put("name", pack.name)
                    .put("stickers", stickers);
            if (pack.cover != null) item.put("cover", pack.cover);
            if (!pack.edited.isEmpty()) {
                JSONObject edited = new JSONObject();
                for (Map.Entry<String, Edited> entry : pack.edited.entrySet()) {
                    Edits edits = entry.getValue().edits;
                    edited.put(entry.getKey(), new JSONObject()
                            .put("source", entry.getValue().source)
                            .put("background", edits.backgroundTolerance)
                            .put("corners", edits.cornerPercent)
                            .put("outline", edits.outlineWidth)
                            .put("color", edits.outlineColor));
                }
                item.put("edited", edited);
            }
            array.put(item);
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
            Map<String, Edited> edited = new LinkedHashMap<>();
            JSONObject editedJson = item.optJSONObject("edited");
            if (editedJson != null) {
                java.util.Iterator<String> keys = editedJson.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    JSONObject record = editedJson.optJSONObject(key);
                    if (record == null) continue;
                    // Clamped: these values size buffers when the sticker is re-rendered.
                    edited.put(key, new Edited(record.optString("source", ""), new Edits(
                            clamp(record.optInt("background"), 0, 255),
                            clamp(record.optInt("corners"), 0, 100),
                            clamp(record.optInt("outline"), 0, MAX_OUTLINE_WIDTH),
                            record.optInt("color", 0xFFFFFFFF))));
                }
            }
            // Pack keeps only a cover and edit records that point at validated sticker files.
            packs.add(new Pack(id, name, stickers, item.optString("cover", null), edited));
        }
        return packs;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
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
            return encodeBitmap(scaled);
        } finally {
            if (scaled != decoded) scaled.recycle();
            decoded.recycle();
        }
    }

    /** Lossless PNG when it fits MAX_STICKER_BYTES, otherwise lossy WebP keeping alpha. */
    private static Encoded encodeBitmap(Bitmap bitmap) throws IOException {
        byte[] png = compress(bitmap, Bitmap.CompressFormat.PNG, 100);
        if (png != null && png.length <= MAX_STICKER_BYTES) return new Encoded(png, "png");
        Bitmap.CompressFormat webp = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
                ? Bitmap.CompressFormat.WEBP_LOSSY : Bitmap.CompressFormat.WEBP;
        for (int quality = 90; quality >= 50; quality -= 10) {
            byte[] encoded = compress(bitmap, webp, quality);
            if (encoded != null && encoded.length <= MAX_STICKER_BYTES) {
                return new Encoded(encoded, "webp");
            }
        }
        throw new IOException("Image could not be saved");
    }

    private static byte[] compress(Bitmap bitmap, Bitmap.CompressFormat format, int quality) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        return bitmap.compress(format, quality, output) ? output.toByteArray() : null;
    }
}
