package dev.jason.gboardpatches.extension.customstickers;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.inputmethod.EditorInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import dev.jason.gboardpatches.extension.settings.GboardPatchesSettings;

/** Chooses which expression tab the emoji key opens. */
public final class GboardCustomStickersOpeningTab {
    private static final String TAG = "GboardPatches";
    public static final String PREF_KEY = "pref_custom_stickers_opening_tab";
    /** Empty keeps Gboard's own choice, the emoji tab. */
    public static final String STOCK = "";
    public static final String STICKER = "sticker_search_result";
    public static final String GIF = "gif_search_result";
    public static final String EMOTICON = "emoticon";
    public static final List<String> VALUES = Arrays.asList(STOCK, STICKER, GIF, EMOTICON);

    private static volatile Handles cachedHandles;

    private GboardCustomStickersOpeningTab() { }

    public static String read(Context context) {
        SharedPreferences preferences = GboardPatchesSettings.preferencesOrNull(context);
        String value = preferences == null ? STOCK : preferences.getString(PREF_KEY, STOCK);
        return VALUES.contains(value) ? value : STOCK;
    }

    public static boolean write(Context context, String value) {
        SharedPreferences preferences = GboardPatchesSettings.preferencesOrNull(context);
        return preferences != null && VALUES.contains(value)
                && preferences.edit().putString(PREF_KEY, value).commit();
    }

    /**
     * Patched into ArtExtension.onActivate. Returns the keyboard type to open, or null to keep
     * stock selection. A tab is used only when Gboard itself offers it for this editor, with the
     * same checks Gboard applies when reopening the last used tab.
     */
    public static Object resolveOpeningKeyboard(Object corpusManager, EditorInfo editorInfo,
            boolean internalEditor, boolean activationFlag) {
        try {
            if (corpusManager == null) return null;
            Handles handles = handles(corpusManager.getClass().getClassLoader());
            Object context = handles.managerContext.get(corpusManager);
            if (!(context instanceof Context)) return null;
            String wanted = read((Context) context);
            if (STOCK.equals(wanted)) return null;
            List<?> corpus = (List<?>) handles.availableCorpus.invoke(
                    corpusManager, editorInfo, internalEditor);
            for (Object item : corpus) {
                if (!handles.corpusItem.isInstance(item)) continue;
                Object keyboardType = handles.itemKeyboardType.get(item);
                if (keyboardType == null
                        || !wanted.equals(handles.keyboardTypeName.get(keyboardType))) {
                    continue;
                }
                boolean enabled = handles.itemState.get(item) == handles.enabledState;
                boolean allowed = activationFlag || handles.itemInternalEditors.getBoolean(item);
                return enabled && allowed ? keyboardType : null;
            }
            return null;
        } catch (Throwable failure) {
            Log.w(TAG, "Failed to choose the opening expression tab", failure);
            return null;
        }
    }

    private static Handles handles(ClassLoader loader) throws ReflectiveOperationException {
        Handles handles = cachedHandles;
        if (handles == null || handles.loader != loader) {
            handles = new Handles(loader);
            cachedHandles = handles;
        }
        return handles;
    }

    /**
     * shortcut: obfuscated Gboard 18.5.4 names (gyo ExpressionCorpusManager, ohn corpus item,
     * ohm availability, qch keyboard type); move to version bindings when porting.
     */
    private static final class Handles {
        final ClassLoader loader;
        final Field managerContext;
        final Method availableCorpus;
        final Class<?> corpusItem;
        final Field itemKeyboardType;
        final Field itemState;
        final Field itemInternalEditors;
        final Field keyboardTypeName;
        final Object enabledState;

        Handles(ClassLoader loader) throws ReflectiveOperationException {
            this.loader = loader;
            Class<?> manager = Class.forName("gyo", false, loader);
            managerContext = accessible(manager.getDeclaredField("c"));
            availableCorpus = manager.getDeclaredMethod("a", EditorInfo.class, boolean.class);
            availableCorpus.setAccessible(true);
            corpusItem = Class.forName("ohn", false, loader);
            itemKeyboardType = accessible(corpusItem.getDeclaredField("c"));
            itemState = accessible(corpusItem.getDeclaredField("e"));
            itemInternalEditors = accessible(corpusItem.getDeclaredField("h"));
            keyboardTypeName = accessible(Class.forName("qch", false, loader).getDeclaredField("m"));
            enabledState = accessible(Class.forName("ohm", false, loader).getDeclaredField("a"))
                    .get(null);
        }

        private static Field accessible(Field field) {
            field.setAccessible(true);
            return field;
        }
    }
}
