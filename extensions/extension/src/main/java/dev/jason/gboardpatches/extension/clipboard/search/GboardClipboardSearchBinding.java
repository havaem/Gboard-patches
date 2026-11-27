package dev.jason.gboardpatches.extension.clipboard.search;

import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.Consumer;

/**
 * Reuses Gboard's "add clip" keyboard as the query field. Its save callback is wrapped while a
 * search is pending, so Done submits the query instead of saving a clip; with no search pending
 * the wrapper forwards to the native save unchanged.
 */
public final class GboardClipboardSearchBinding {
    // ~2 s of frames: the add-entry switch can go through a background hop first.
    private static final int INSTALL_ATTEMPTS = 120;

    private final Method openAddEntry, reload, inKeyboardEditing;
    private final Field controller, keyboardHost, adapter, addEntry, addEntryType, saver,
            queryField, titleField;
    private final Class<?> saverType;
    private final Object addEntryKind;

    public GboardClipboardSearchBinding(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> keyboard = Class.forName(
                "com.google.android.apps.inputmethod.libs.clipboard.ClipboardKeyboard", false, loader);
        Class<?> addEntryKeyboard = Class.forName(
                "com.google.android.apps.inputmethod.libs.clipboard.ClipboardAddEntryKeyboard",
                false, loader);
        Class<?> abstractKeyboard = Class.forName(
                "com.google.android.libraries.inputmethod.keyboard.impl.AbstractKeyboard",
                false, loader);
        Class<?> clip = Class.forName("fpk", false, loader);
        Class<?> adapterClass = Class.forName("fqn", false, loader);
        Class<?> controllerClass = Class.forName("frc", false, loader);
        Class<?> host = Class.forName("pkz", false, loader);
        Class<?> keyboardType = Class.forName("qch", false, loader);
        saverType = Class.forName("fqo", false, loader);
        if (!saverType.isInterface() || !saverType.isAssignableFrom(controllerClass)) {
            throw new NoSuchFieldException("Clipboard add-entry saver mismatch");
        }
        openAddEntry = method(keyboard, "N", void.class, clip);
        reload = method(adapterClass, "J", void.class);
        inKeyboardEditing = method(host, "ax", boolean.class);
        controller = field(keyboard, "b", Class.forName("frh", false, loader));
        keyboardHost = field(abstractKeyboard, "A", host);
        adapter = field(keyboard, "c", adapterClass);
        addEntry = field(controllerClass, "e", addEntryKeyboard);
        addEntryType = field(controllerClass, "c", keyboardType);
        saver = field(addEntryKeyboard, "b", saverType);
        queryField = field(addEntryKeyboard, "a",
                Class.forName("com.google.android.libraries.inputmethod.widgets.EditTextOnKeyboard",
                        false, loader));
        titleField = field(addEntryKeyboard, "e", TextView.class);
        Field kind = Class.forName("fsd", false, loader).getDeclaredField("b");
        kind.setAccessible(true);
        addEntryKind = kind.get(null);
        if (!EditText.class.isAssignableFrom(queryField.getType())) {
            throw new NoSuchFieldException("Clipboard add-entry field is not an EditText");
        }
    }

    /** Search needs the in-keyboard add-entry field; the popup variant saves another way. */
    public boolean available(Object keyboard) throws ReflectiveOperationException {
        Object value = keyboardHost.get(keyboard);
        return value != null && (Boolean) inKeyboardEditing.invoke(value);
    }

    /** Opens the add-entry keyboard as a query field; {@code view} schedules the hand-off. */
    public void open(Object keyboard, View view, String title, Consumer<Throwable> report)
            throws ReflectiveOperationException {
        Object owner = controller.get(keyboard);
        if (owner == null) return;
        GboardClipboardSearch.begin();
        try {
            openAddEntry.invoke(keyboard, (Object) null);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            GboardClipboardSearch.clear();
            throw failure;
        }
        view.post(new Installer(owner, view, title, report));
    }

    /** Clears the filter and reloads the visible list. */
    public void clear(Object keyboard) throws ReflectiveOperationException {
        GboardClipboardSearch.clear();
        Object list = adapter.get(keyboard);
        if (list != null) reload.invoke(list);
    }

    /** The keyboard switch is asynchronous: retry on later frames until the field is live. */
    private final class Installer implements Runnable {
        final Object owner;
        final View view;
        final String title;
        final Consumer<Throwable> report;
        int attempts;

        Installer(Object owner, View view, String title, Consumer<Throwable> report) {
            this.owner = owner;
            this.view = view;
            this.title = title;
            this.report = report;
        }

        @Override public void run() {
            try {
                if (!GboardClipboardSearch.pending()) return;
                if (!install()) {
                    if (++attempts < INSTALL_ATTEMPTS) view.postDelayed(this, 16L);
                    else GboardClipboardSearch.clear();
                }
            } catch (Throwable failure) {
                GboardClipboardSearch.clear();
                report.accept(failure);
            }
        }

        private boolean install() throws ReflectiveOperationException {
            Object entry = addEntry.get(owner);
            if (entry == null || addEntryType.get(owner) != addEntryKind) return false;
            Object field = queryField.get(entry);
            Object stock = saver.get(entry);
            if (!(field instanceof EditText text) || !text.isAttachedToWindow() || stock == null) {
                return false;
            }
            if (!Proxy.isProxyClass(stock.getClass())) saver.set(entry, wrap(stock));
            if (titleField.get(entry) instanceof TextView label) label.setText(title);
            return true;
        }
    }

    private Object wrap(Object target) {
        return Proxy.newProxyInstance(saverType.getClassLoader(), new Class<?>[] {saverType},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "equals": return proxy == args[0];
                        case "hashCode": return System.identityHashCode(proxy);
                        case "toString": return "ClipboardSearchSaver";
                        default: break;
                    }
                    if (method.getDeclaringClass() == saverType && args != null && args.length == 3
                            && GboardClipboardSearch.submit((CharSequence) args[2])) {
                        return null;
                    }
                    try {
                        return method.invoke(target, args);
                    } catch (java.lang.reflect.InvocationTargetException failure) {
                        throw failure.getCause();
                    }
                });
    }

    private static Field field(Class<?> owner, String name, Class<?> type)
            throws NoSuchFieldException {
        Field value = owner.getDeclaredField(name);
        if (value.getType() != type) throw new NoSuchFieldException(owner.getName() + "." + name);
        value.setAccessible(true);
        return value;
    }

    private static Method method(Class<?> owner, String name, Class<?> result, Class<?>... params)
            throws NoSuchMethodException {
        Method value = owner.getDeclaredMethod(name, params);
        if (value.getReturnType() != result) {
            throw new NoSuchMethodException(owner.getName() + "." + name);
        }
        value.setAccessible(true);
        return value;
    }
}
