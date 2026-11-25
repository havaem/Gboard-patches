package dev.jason.gboardpatches.extension.customstickers;

import android.app.AlertDialog;
import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import dev.jason.gboardpatches.extension.R;
import dev.jason.gboardpatches.extension.settings.GboardPatchesFeatureAvailability;
import dev.jason.gboardpatches.extension.settings.GboardPatchesSettingsContract;
import dev.jason.gboardpatches.extension.settings.GboardSettingsText;

public final class GboardCustomStickersSettingsFeature
        implements GboardPatchesSettingsContract.Feature {
    private static final String TAG = "GboardPatches";
    private static final String[] IMAGE_TYPES = {
            "image/png", "image/webp", "image/jpeg", "image/gif"
    };
    private static final String[] ARCHIVE_TYPES = {
            "application/zip", "application/x-zip-compressed", "application/octet-stream"
    };
    // White, black, yellow, pink.
    private static final int[] OUTLINE_COLORS = {0xFFFFFFFF, 0xFF000000, 0xFFFFD600, 0xFFFF4081};

    private final Context context;
    private final String entryTitle;
    private final String entrySummary;
    private final String headerBadge;

    public GboardCustomStickersSettingsFeature(Context context) {
        this.context = context;
        entryTitle = text(R.string.gboard_patches_custom_stickers_title, "Custom Stickers");
        entrySummary = text(R.string.gboard_patches_custom_stickers_summary, "");
        headerBadge = text(R.string.gboard_patches_header_badge, "GBOARD");
    }

    @Override
    public String getEntryTitle() {
        return entryTitle;
    }

    @Override
    public String getEntrySummary() {
        return entrySummary;
    }

    @Override
    public boolean isAvailable(Context context) {
        return GboardPatchesFeatureAvailability.hasFeature(
                context, GboardPatchesFeatureAvailability.FEATURE_CUSTOM_STICKERS);
    }

    @Override
    public GboardPatchesSettingsContract.Screen buildScreen(
            GboardPatchesSettingsContract.FeatureHost host) {
        try {
            Context hostContext = host == null ? null : host.getContext();
            if (hostContext == null) return errorScreen();
            List<GboardCustomStickersStore.Pack> packs = GboardCustomStickersStore.load(hostContext);
            List<GboardPatchesSettingsContract.Row> packRows = new ArrayList<>();
            if (packs.isEmpty()) {
                packRows.add(new GboardPatchesSettingsContract.InfoRow(
                        text(R.string.gboard_patches_custom_stickers_empty, "No sticker packs yet"),
                        text(R.string.gboard_patches_custom_stickers_empty_summary, ""), true));
            } else {
                List<String> summaries = new ArrayList<>();
                for (GboardCustomStickersStore.Pack pack : packs) {
                    summaries.add(packSummary(hostContext, pack));
                }
                packRows.add(new GboardPatchesSettingsContract.CustomViewRow(viewHost ->
                        GboardCustomStickersViews.createPackList(viewHost.getContext(), packs,
                                summaries,
                                packId -> runSafely("open sticker pack",
                                        () -> GboardPatchesSettingsContract.openFeature(
                                                host, new PackFeature(packId))),
                                (packId, target) -> runSafely("move sticker pack", () -> {
                                    try {
                                        GboardCustomStickersStore.movePack(
                                                hostContext, packId, target);
                                    } catch (Throwable failure) {
                                        Log.w(TAG, "Failed to move sticker pack", failure);
                                        showSaveFailed(host);
                                    }
                                    GboardPatchesSettingsContract.refresh(host);
                                }))));
            }
            packRows.add(new GboardPatchesSettingsContract.ActionRow(
                    text(R.string.gboard_patches_custom_stickers_import_pack, "Import pack"),
                    text(R.string.gboard_patches_custom_stickers_import_pack_summary, ""), true,
                    () -> runSafely("import sticker pack", () -> importPack(host, hostContext))));
            List<GboardPatchesSettingsContract.Section> sections = new ArrayList<>();
            sections.add(new GboardPatchesSettingsContract.Section(
                    text(R.string.gboard_patches_custom_stickers_section_packs, "Sticker packs"),
                    packs.isEmpty() ? null
                            : text(R.string.gboard_patches_custom_stickers_pack_list_hint, ""),
                    GboardPatchesSettingsContract.SectionStyle.DEFAULT, packRows));
            String[] tabLabels = openingTabLabels();
            String openingTab = GboardCustomStickersOpeningTab.read(hostContext);
            String openingTitle = text(R.string.gboard_patches_custom_stickers_opening_tab,
                    "Tab opened by the emoji key");
            sections.add(new GboardPatchesSettingsContract.Section(
                    text(R.string.gboard_patches_custom_stickers_section_keyboard, "Keyboard"),
                    Collections.singletonList(new GboardPatchesSettingsContract.SelectorRow(
                            openingTitle,
                            text(R.string.gboard_patches_custom_stickers_opening_tab_summary, ""),
                            tabLabels[GboardCustomStickersOpeningTab.VALUES.indexOf(openingTab)],
                            true,
                            () -> runSafely("choose opening tab", () ->
                                    GboardPatchesSettingsContract.showChoiceDialog(host,
                                            openingTitle, tabLabels,
                                            GboardCustomStickersOpeningTab.VALUES.toArray(
                                                    new String[0]),
                                            openingTab, null, null,
                                            value -> runSafely("save opening tab", () -> {
                                                if (!GboardCustomStickersOpeningTab.write(
                                                        hostContext, value)) {
                                                    showSaveFailed(host);
                                                }
                                                GboardPatchesSettingsContract.refresh(host);
                                            })))))));
            sections.add(new GboardPatchesSettingsContract.Section(
                    text(R.string.gboard_patches_custom_stickers_usage_title, "How to use"),
                    Collections.singletonList(new GboardPatchesSettingsContract.InfoRow(
                            text(R.string.gboard_patches_custom_stickers_usage_title, "How to use"),
                            text(R.string.gboard_patches_custom_stickers_usage_summary, ""),
                            true))));
            return new GboardPatchesSettingsContract.Screen(
                    entryTitle, headerBadge, entryTitle, entrySummary,
                    Collections.emptyList(), sections,
                    GboardPatchesSettingsContract.RefreshPolicy.none(),
                    GboardPatchesSettingsContract.PanelStyle.FLAT,
                    new GboardPatchesSettingsContract.CommandRow(
                            text(R.string.gboard_patches_custom_stickers_create,
                                    "Create sticker pack"),
                            "", true,
                            () -> runSafely("create sticker pack",
                                    () -> createPack(host, hostContext))));
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to render custom stickers settings", throwable);
            return errorScreen();
        }
    }

    /** Same order as {@link GboardCustomStickersOpeningTab#VALUES}. */
    private String[] openingTabLabels() {
        return new String[]{
                text(R.string.gboard_patches_custom_stickers_opening_tab_stock, "Emoji (default)"),
                text(R.string.gboard_patches_custom_stickers_opening_tab_sticker, "Stickers"),
                text(R.string.gboard_patches_custom_stickers_opening_tab_gif, "GIFs"),
                text(R.string.gboard_patches_custom_stickers_opening_tab_emoticon, "Emoticons"),
        };
    }

    private String packSummary(Context hostContext, GboardCustomStickersStore.Pack pack) {
        if (pack.stickers.isEmpty()) {
            return text(R.string.gboard_patches_custom_stickers_pack_empty_summary,
                    "Empty, add stickers to show it in the keyboard");
        }
        String count = stickerCount(pack.stickers.size());
        if (Boolean.FALSE.equals(GboardCustomStickersRuntime.isShownInKeyboard(hostContext, pack.id))) {
            return count + " · " + text(R.string.gboard_patches_custom_stickers_hidden,
                    "Not shown in keyboard");
        }
        return count;
    }

    private void createPack(GboardPatchesSettingsContract.FeatureHost host, Context hostContext) {
        GboardPatchesSettingsContract.showTextInputDialog(host,
                text(R.string.gboard_patches_custom_stickers_create, "Create sticker pack"),
                text(R.string.gboard_patches_custom_stickers_name_hint, "Pack name"), "",
                name -> runSafely("save new sticker pack", () -> {
                    try {
                        GboardCustomStickersStore.Pack pack =
                                GboardCustomStickersStore.createPack(hostContext, name);
                        // Gboard's header lists only "added" packs; new packs start added.
                        GboardCustomStickersRuntime.setShownInKeyboard(hostContext, pack.id, true);
                        GboardPatchesSettingsContract.refresh(host);
                        PackFeature packFeature = new PackFeature(pack.id);
                        GboardPatchesSettingsContract.openFeature(host, packFeature);
                        packFeature.addStickers(host, hostContext);
                    } catch (Throwable failure) {
                        Log.w(TAG, "Failed to create sticker pack", failure);
                        showSaveFailed(host);
                    }
                }));
    }

    private void importPack(GboardPatchesSettingsContract.FeatureHost host, Context hostContext) {
        // The bounded picker reads the file on a worker thread.
        GboardPatchesSettingsContract.openBinaryDocument(host, ARCHIVE_TYPES,
                GboardCustomStickersStore.MAX_ARCHIVE_BYTES, document -> {
                    String message;
                    try {
                        GboardCustomStickersStore.Pack pack = GboardCustomStickersStore.importPack(
                                hostContext, document.getData());
                        GboardCustomStickersRuntime.setShownInKeyboard(hostContext, pack.id, true);
                        message = String.format(Locale.ROOT, text(
                                R.string.gboard_patches_custom_stickers_imported,
                                "Imported pack %1$s."), pack.name);
                    } catch (Throwable failure) {
                        Log.w(TAG, "Failed to import sticker pack", failure);
                        message = text(R.string.gboard_patches_custom_stickers_import_failed,
                                "This file is not a sticker pack.");
                    }
                    GboardPatchesSettingsContract.showMessage(host, message);
                    GboardPatchesSettingsContract.refresh(host);
                });
    }

    /** One pack: its sticker grid, selection, keyboard visibility, defaults, export, delete. */
    final class PackFeature implements GboardPatchesSettingsContract.Feature {
        private final String packId;
        // Null when not selecting.
        private Set<String> selection;
        // Progress text while stickers are being added; null when idle.
        private volatile String busy;

        PackFeature(String packId) {
            this.packId = packId;
        }

        @Override
        public String getEntryTitle() {
            return entryTitle;
        }

        @Override
        public String getEntrySummary() {
            return "";
        }

        @Override
        public boolean onBackRequested(GboardPatchesSettingsContract.FeatureHost host) {
            if (selection == null) return false;
            selection = null;
            GboardPatchesSettingsContract.refresh(host);
            return true;
        }

        @Override
        public GboardPatchesSettingsContract.Screen buildScreen(
                GboardPatchesSettingsContract.FeatureHost host) {
            try {
                Context hostContext = host == null ? null : host.getContext();
                if (hostContext == null) return errorScreen();
                GboardCustomStickersStore.Pack pack =
                        GboardCustomStickersStore.findPack(hostContext, packId);
                if (pack == null) {
                    return statusScreen(entryTitle, text(
                            R.string.gboard_patches_custom_stickers_pack_missing,
                            "This sticker pack no longer exists."));
                }
                if (selection != null) selection.retainAll(pack.stickers);
                List<GboardPatchesSettingsContract.Section> sections = new ArrayList<>();
                List<GboardPatchesSettingsContract.Row> stickerRows = new ArrayList<>();
                if (pack.stickers.isEmpty()) {
                    stickerRows.add(new GboardPatchesSettingsContract.InfoRow(
                            text(R.string.gboard_patches_custom_stickers_no_stickers,
                                    "No stickers yet"),
                            text(R.string.gboard_patches_custom_stickers_add_summary, ""), true));
                } else {
                    GboardCustomStickersViews.GridLabels labels =
                            new GboardCustomStickersViews.GridLabels(
                                    text(R.string.gboard_patches_custom_stickers_sticker_item,
                                            "Sticker %1$d"),
                                    text(R.string.gboard_patches_custom_stickers_cover, "Cover"),
                                    text(R.string.gboard_patches_custom_stickers_photo, "Photo"),
                                    text(R.string.gboard_patches_custom_stickers_selected,
                                            "Selected"));
                    Set<String> selected = selection;
                    stickerRows.add(new GboardPatchesSettingsContract.CustomViewRow(viewHost ->
                            GboardCustomStickersViews.createStickerGrid(viewHost.getContext(),
                                    pack, labels, selected,
                                    (fileName, position) -> runSafely("tap sticker", () -> {
                                        if (selection != null) {
                                            if (!selection.remove(fileName)) selection.add(fileName);
                                            GboardPatchesSettingsContract.refresh(host);
                                        } else {
                                            GboardPatchesSettingsContract.openFeature(host,
                                                    new EditorFeature(packId,
                                                            Collections.singletonList(fileName),
                                                            false));
                                        }
                                    }),
                                    (fileName, target) -> runSafely("move sticker", () -> {
                                        try {
                                            GboardCustomStickersStore.moveStickers(hostContext,
                                                    packId, Collections.singletonList(fileName),
                                                    target);
                                        } catch (Throwable failure) {
                                            Log.w(TAG, "Failed to move sticker", failure);
                                            showSaveFailed(host);
                                        }
                                        GboardPatchesSettingsContract.refresh(host);
                                    }))));
                    if (selection == null) {
                        stickerRows.add(new GboardPatchesSettingsContract.ActionRow(
                                text(R.string.gboard_patches_custom_stickers_select,
                                        "Select stickers"),
                                "", true, () -> runSafely("start selecting", () -> {
                                    selection = new LinkedHashSet<>();
                                    GboardPatchesSettingsContract.refresh(host);
                                })));
                    }
                }
                sections.add(new GboardPatchesSettingsContract.Section(
                        text(R.string.gboard_patches_custom_stickers_section_stickers, "Stickers"),
                        pack.stickers.isEmpty() ? null
                                : text(R.string.gboard_patches_custom_stickers_grid_hint, ""),
                        GboardPatchesSettingsContract.SectionStyle.DEFAULT, stickerRows));
                if (selection != null) sections.add(selectionSection(host, hostContext, pack));

                List<GboardPatchesSettingsContract.Row> packRows = new ArrayList<>();
                Boolean shown = GboardCustomStickersRuntime.isShownInKeyboard(hostContext, packId);
                if (shown != null) {
                    packRows.add(new GboardPatchesSettingsContract.ToggleRow(
                            text(R.string.gboard_patches_custom_stickers_show_in_keyboard,
                                    "Show in keyboard"),
                            text(R.string.gboard_patches_custom_stickers_show_in_keyboard_summary,
                                    ""),
                            true, shown, checked -> {
                                if (!GboardCustomStickersRuntime.setShownInKeyboard(
                                        hostContext, packId, checked)) {
                                    showSaveFailed(host);
                                }
                            }));
                }
                packRows.add(new GboardPatchesSettingsContract.NavigationRow(
                        text(R.string.gboard_patches_custom_stickers_defaults,
                                "Default edits for new stickers"),
                        pack.defaults.isEmpty()
                                ? text(R.string.gboard_patches_custom_stickers_defaults_off,
                                        "Off")
                                : text(R.string.gboard_patches_custom_stickers_defaults_on,
                                        "Applied automatically to stickers you add"),
                        !pack.stickers.isEmpty(),
                        () -> runSafely("open default edits",
                                () -> GboardPatchesSettingsContract.openFeature(host,
                                        new EditorFeature(packId,
                                                Collections.emptyList(), true)))));
                packRows.add(new GboardPatchesSettingsContract.ActionRow(
                        text(R.string.gboard_patches_custom_stickers_rename, "Rename pack"),
                        pack.name, true,
                        () -> runSafely("rename sticker pack", () -> rename(host, hostContext, pack))));
                packRows.add(new GboardPatchesSettingsContract.ActionRow(
                        text(R.string.gboard_patches_custom_stickers_export_pack, "Export pack"),
                        text(R.string.gboard_patches_custom_stickers_export_pack_summary, ""),
                        !pack.stickers.isEmpty(),
                        () -> runSafely("export sticker pack",
                                () -> exportPack(host, hostContext, pack))));
                packRows.add(new GboardPatchesSettingsContract.DangerRow(
                        text(R.string.gboard_patches_custom_stickers_delete_pack, "Delete pack"),
                        "", true, () -> runSafely("delete sticker pack", () -> {
                            try {
                                GboardCustomStickersStore.deletePack(hostContext, packId);
                                GboardCustomStickersRuntime.setShownInKeyboard(
                                        hostContext, packId, false);
                            } catch (Throwable failure) {
                                Log.w(TAG, "Failed to delete sticker pack", failure);
                                showSaveFailed(host);
                            }
                            GboardPatchesSettingsContract.refresh(host);
                        }),
                        text(R.string.gboard_patches_custom_stickers_delete_pack, "Delete pack"),
                        text(R.string.gboard_patches_custom_stickers_delete_pack_confirm, "")));
                sections.add(new GboardPatchesSettingsContract.Section(
                        text(R.string.gboard_patches_custom_stickers_section_pack, "Pack"),
                        packRows));

                String progress = busy;
                return new GboardPatchesSettingsContract.Screen(
                        pack.name, headerBadge, pack.name, stickerCount(pack.stickers.size()),
                        progressBlocks(progress), sections,
                        GboardPatchesSettingsContract.RefreshPolicy.none(),
                        GboardPatchesSettingsContract.PanelStyle.FLAT,
                        new GboardPatchesSettingsContract.CommandRow(
                                text(R.string.gboard_patches_custom_stickers_add, "Add stickers"),
                                "", progress == null,
                                () -> runSafely("add stickers",
                                        () -> addStickers(host, hostContext))));
            } catch (Throwable throwable) {
                Log.w(TAG, "Failed to render sticker pack settings", throwable);
                return errorScreen();
            }
        }

        private GboardPatchesSettingsContract.Section selectionSection(
                GboardPatchesSettingsContract.FeatureHost host, Context hostContext,
                GboardCustomStickersStore.Pack pack) {
            List<String> picked = new ArrayList<>();
            for (String sticker : pack.stickers) {
                if (selection.contains(sticker)) picked.add(sticker);
            }
            boolean any = !picked.isEmpty();
            boolean all = picked.size() == pack.stickers.size();
            List<GboardPatchesSettingsContract.Row> rows = new ArrayList<>();
            rows.add(new GboardPatchesSettingsContract.ActionRow(
                    text(R.string.gboard_patches_custom_stickers_edit_selected, "Edit selected"),
                    "", any, () -> runSafely("edit selected stickers", () -> {
                        selection = null;
                        GboardPatchesSettingsContract.openFeature(host,
                                new EditorFeature(packId, picked, false));
                    })));
            rows.add(new GboardPatchesSettingsContract.ActionRow(
                    all ? text(R.string.gboard_patches_custom_stickers_clear_selection,
                                    "Clear selection")
                            : text(R.string.gboard_patches_custom_stickers_select_all,
                                    "Select all"),
                    "", true, () -> runSafely("change selection", () -> {
                        if (all) selection.clear(); else selection.addAll(pack.stickers);
                        GboardPatchesSettingsContract.refresh(host);
                    })));
            rows.add(new GboardPatchesSettingsContract.DangerRow(
                    text(R.string.gboard_patches_custom_stickers_delete_selected,
                            "Delete selected"),
                    "", any, () -> runSafely("delete selected stickers", () -> {
                        int failed = GboardCustomStickersStore.deleteStickers(
                                hostContext, packId, picked);
                        if (failed > 0) showSaveFailed(host);
                        selection = null;
                        GboardPatchesSettingsContract.refresh(host);
                    }),
                    text(R.string.gboard_patches_custom_stickers_delete_selected,
                            "Delete selected"),
                    text(R.string.gboard_patches_custom_stickers_delete_selected_confirm,
                            "Delete the selected stickers?")));
            rows.add(new GboardPatchesSettingsContract.ActionRow(
                    text(R.string.gboard_patches_custom_stickers_done, "Done"), "", true,
                    () -> runSafely("stop selecting", () -> {
                        selection = null;
                        GboardPatchesSettingsContract.refresh(host);
                    })));
            return new GboardPatchesSettingsContract.Section(
                    String.format(Locale.ROOT, text(
                            R.string.gboard_patches_custom_stickers_selected_count,
                            "%1$d selected"), picked.size()),
                    rows);
        }

        void addStickers(GboardPatchesSettingsContract.FeatureHost host, Context hostContext) {
            // Documents arrive one by one on a single worker thread, then completion runs there.
            int[] counts = new int[2];
            GboardPatchesSettingsContract.openBinaryDocuments(host, IMAGE_TYPES,
                    GboardCustomStickersStore.MAX_IMPORT_BYTES, document -> {
                        busy = String.format(Locale.ROOT, text(
                                R.string.gboard_patches_custom_stickers_adding_progress,
                                "Adding stickers: %1$d done"), counts[0] + counts[1]);
                        GboardPatchesSettingsContract.refresh(host);
                        try {
                            GboardCustomStickersStore.addSticker(
                                    hostContext, packId, document.getData());
                            counts[0]++;
                        } catch (Throwable failure) {
                            Log.w(TAG, "Failed to add sticker", failure);
                            counts[1]++;
                        }
                    }, () -> {
                        busy = null;
                        String message = counts[1] == 0
                                ? String.format(Locale.ROOT, text(
                                        R.string.gboard_patches_custom_stickers_added,
                                        "%1$d stickers added."), counts[0])
                                : String.format(Locale.ROOT, text(
                                        R.string.gboard_patches_custom_stickers_add_failed,
                                        "%1$d added, %2$d could not be added."),
                                        counts[0], counts[1]);
                        GboardPatchesSettingsContract.showMessage(host, message);
                        GboardPatchesSettingsContract.refresh(host);
                    });
        }

        private void exportPack(GboardPatchesSettingsContract.FeatureHost host,
                Context hostContext, GboardCustomStickersStore.Pack pack) {
            new Thread(() -> runSafely("export sticker pack", () -> {
                try {
                    byte[] archive = GboardCustomStickersStore.exportPack(hostContext, packId);
                    String fileName = pack.name.replaceAll("[\\\\/:*?\"<>|]", "_") + ".zip";
                    GboardPatchesSettingsContract.createBinaryDocument(host, fileName,
                            "application/zip", archive,
                            () -> GboardPatchesSettingsContract.showMessage(host, text(
                                    R.string.gboard_patches_custom_stickers_exported,
                                    "Pack exported.")));
                } catch (Throwable failure) {
                    Log.w(TAG, "Failed to export sticker pack", failure);
                    showSaveFailed(host);
                }
            }), "GboardStickerExport").start();
        }

        private void rename(GboardPatchesSettingsContract.FeatureHost host, Context hostContext,
                GboardCustomStickersStore.Pack pack) {
            GboardPatchesSettingsContract.showTextInputDialog(host,
                    text(R.string.gboard_patches_custom_stickers_rename, "Rename pack"),
                    text(R.string.gboard_patches_custom_stickers_name_hint, "Pack name"), pack.name,
                    name -> runSafely("save sticker pack name", () -> {
                        try {
                            GboardCustomStickersStore.renamePack(hostContext, packId, name);
                        } catch (Throwable failure) {
                            Log.w(TAG, "Failed to rename sticker pack", failure);
                            showSaveFailed(host);
                        }
                        GboardPatchesSettingsContract.refresh(host);
                    }));
        }
    }

    /**
     * Edits one sticker, several stickers at once, or the pack's defaults for new stickers.
     * Stickers are always rendered from their own original image.
     */
    final class EditorFeature implements GboardPatchesSettingsContract.Feature {
        private final String packId;
        private final List<String> targets;
        private final boolean defaultsMode;
        // Kept across refreshes of this screen; nothing is written until Save.
        private boolean loaded;
        private GboardCustomStickersStore.Edits saved = GboardCustomStickersStore.Edits.NONE;
        private int backgroundTolerance;
        private int cornerPercent;
        private int outlineWidth;
        private int outlineColor = OUTLINE_COLORS[0];
        private volatile String busy;

        EditorFeature(String packId, List<String> targets, boolean defaultsMode) {
            this.packId = packId;
            this.targets = new ArrayList<>(targets);
            this.defaultsMode = defaultsMode;
        }

        @Override
        public String getEntryTitle() {
            return entryTitle;
        }

        @Override
        public String getEntrySummary() {
            return "";
        }

        private GboardCustomStickersStore.Edits edits() {
            return new GboardCustomStickersStore.Edits(
                    backgroundTolerance, cornerPercent, outlineWidth, outlineColor);
        }

        private boolean changed() {
            return loaded && !edits().equals(saved);
        }

        @Override
        public boolean onBackRequested(GboardPatchesSettingsContract.FeatureHost host) {
            if (busy != null) return true;
            if (!changed()) return false;
            GboardPatchesSettingsContract.showManagedDialog(host, onDismiss -> {
                AlertDialog dialog = new AlertDialog.Builder(host.getContext())
                        .setTitle(text(R.string.gboard_patches_custom_stickers_discard_title,
                                "Discard changes?"))
                        .setMessage(text(R.string.gboard_patches_custom_stickers_discard_message,
                                "Your edits have not been saved."))
                        .setPositiveButton(text(R.string.gboard_patches_custom_stickers_discard,
                                "Discard"), (button, which) ->
                                GboardPatchesSettingsContract.closeFeature(host))
                        .setNegativeButton(text(
                                R.string.gboard_patches_custom_stickers_keep_editing,
                                "Keep editing"), null)
                        .create();
                dialog.setOnDismissListener(ignored -> onDismiss.run());
                dialog.show();
                return true;
            });
            return true;
        }

        @Override
        public GboardPatchesSettingsContract.Screen buildScreen(
                GboardPatchesSettingsContract.FeatureHost host) {
            try {
                Context hostContext = host == null ? null : host.getContext();
                if (hostContext == null) return errorScreen();
                GboardCustomStickersStore.Pack pack =
                        GboardCustomStickersStore.findPack(hostContext, packId);
                List<String> live = new ArrayList<>(targets);
                if (pack != null) live.retainAll(pack.stickers);
                String sample = pack == null ? null
                        : defaultsMode ? (pack.stickers.isEmpty() ? null : pack.stickers.get(0))
                        : live.isEmpty() ? null : live.get(0);
                if (sample == null) {
                    return statusScreen(entryTitle, text(defaultsMode
                            ? R.string.gboard_patches_custom_stickers_defaults_needs_sticker
                            : R.string.gboard_patches_custom_stickers_sticker_missing,
                            "This sticker no longer exists."));
                }
                if (!loaded) {
                    // Reopening continues from the saved settings.
                    loaded = true;
                    saved = defaultsMode ? pack.defaults : pack.editsOf(sample);
                    backgroundTolerance = saved.backgroundTolerance;
                    cornerPercent = saved.cornerPercent;
                    outlineWidth = saved.outlineWidth;
                    if (saved.outlineWidth > 0) outlineColor = saved.outlineColor;
                }
                String itemLabel = text(
                        R.string.gboard_patches_custom_stickers_sticker_item, "Sticker %1$d");
                String title = defaultsMode
                        ? text(R.string.gboard_patches_custom_stickers_defaults_title,
                                "Default edits")
                        : live.size() > 1
                                ? String.format(Locale.ROOT, text(
                                        R.string.gboard_patches_custom_stickers_edit_count,
                                        "Edit %1$d stickers"), live.size())
                                : String.format(Locale.ROOT, itemLabel,
                                        pack.stickers.indexOf(sample) + 1);
                List<GboardPatchesSettingsContract.Section> sections = new ArrayList<>();
                GboardCustomStickersViews.EditorLabels labels =
                        new GboardCustomStickersViews.EditorLabels(title,
                                text(R.string.gboard_patches_custom_stickers_hold_hint,
                                        "Hold the image to see the original."),
                                text(R.string.gboard_patches_custom_stickers_background_not_found,
                                        "No plain background found at the image edges."),
                                text(R.string.gboard_patches_custom_stickers_remove_background,
                                        "Remove background"),
                                text(R.string.gboard_patches_custom_stickers_corners,
                                        "Round corners"),
                                text(R.string.gboard_patches_custom_stickers_outline, "Outline"));
                GboardCustomStickersStore.Edits edits = edits();
                sections.add(new GboardPatchesSettingsContract.Section(null,
                        Collections.singletonList(new GboardPatchesSettingsContract.CustomViewRow(
                                viewHost -> GboardCustomStickersViews.createEditorPreview(
                                        viewHost.getContext(),
                                        GboardCustomStickersStore.stickerFile(
                                                hostContext, packId, pack.sourceOf(sample)),
                                        edits, labels,
                                        chosen -> runSafely("set sticker edits", () -> {
                                            backgroundTolerance = chosen.backgroundTolerance;
                                            cornerPercent = chosen.cornerPercent;
                                            outlineWidth = chosen.outlineWidth;
                                            GboardPatchesSettingsContract.refresh(host);
                                        }))))));

                if (outlineWidth > 0) {
                    String[] colorValues = new String[OUTLINE_COLORS.length];
                    for (int i = 0; i < colorValues.length; i++) {
                        colorValues[i] = Integer.toHexString(OUTLINE_COLORS[i]);
                    }
                    sections.add(new GboardPatchesSettingsContract.Section(
                            text(R.string.gboard_patches_custom_stickers_section_edit, "Edit"),
                            Collections.singletonList(selector(host,
                                    text(R.string.gboard_patches_custom_stickers_outline_color,
                                            "Outline color"),
                                    new String[]{
                                            text(R.string.gboard_patches_custom_stickers_color_white, "White"),
                                            text(R.string.gboard_patches_custom_stickers_color_black, "Black"),
                                            text(R.string.gboard_patches_custom_stickers_color_yellow, "Yellow"),
                                            text(R.string.gboard_patches_custom_stickers_color_pink, "Pink")},
                                    colorValues,
                                    Integer.toHexString(outlineColor),
                                    value -> outlineColor = (int) Long.parseLong(value, 16)))));
                }
                if (!defaultsMode && live.size() == 1) {
                    sections.add(stickerSection(host, hostContext, pack, sample));
                }

                String progress = busy;
                return new GboardPatchesSettingsContract.Screen(
                        title, headerBadge, title, pack.name,
                        progressBlocks(progress), sections,
                        GboardPatchesSettingsContract.RefreshPolicy.none(),
                        GboardPatchesSettingsContract.PanelStyle.FLAT,
                        new GboardPatchesSettingsContract.CommandRow(
                                text(R.string.gboard_patches_custom_stickers_save, "Save"),
                                "", changed() && progress == null,
                                () -> runSafely("save sticker edits",
                                        () -> save(host, hostContext, live))));
            } catch (Throwable throwable) {
                Log.w(TAG, "Failed to render sticker editor", throwable);
                return errorScreen();
            }
        }

        private GboardPatchesSettingsContract.Section stickerSection(
                GboardPatchesSettingsContract.FeatureHost host, Context hostContext,
                GboardCustomStickersStore.Pack pack, String fileName) {
            List<GboardPatchesSettingsContract.Row> rows = new ArrayList<>();
            boolean isCover = fileName.equals(pack.cover);
            rows.add(new GboardPatchesSettingsContract.ActionRow(
                    text(R.string.gboard_patches_custom_stickers_set_cover, "Set as cover"),
                    isCover ? text(R.string.gboard_patches_custom_stickers_already_cover,
                            "This sticker is the pack cover.") : "",
                    !isCover,
                    () -> runSafely("set sticker cover", () -> {
                        try {
                            GboardCustomStickersStore.setCover(hostContext, packId, fileName);
                        } catch (Throwable failure) {
                            Log.w(TAG, "Failed to set sticker cover", failure);
                            showSaveFailed(host);
                        }
                        GboardPatchesSettingsContract.refresh(host);
                    })));
            rows.add(new GboardPatchesSettingsContract.DangerRow(
                    text(R.string.gboard_patches_custom_stickers_delete_sticker, "Delete sticker"),
                    "", true, () -> runSafely("delete sticker", () -> {
                        try {
                            GboardCustomStickersStore.deleteSticker(hostContext, packId, fileName);
                            GboardPatchesSettingsContract.closeFeature(host);
                        } catch (Throwable failure) {
                            Log.w(TAG, "Failed to delete sticker", failure);
                            showSaveFailed(host);
                        }
                        GboardPatchesSettingsContract.refresh(host);
                    }),
                    text(R.string.gboard_patches_custom_stickers_delete_sticker, "Delete sticker"),
                    text(R.string.gboard_patches_custom_stickers_delete_sticker_confirm,
                            "Delete this sticker?")));
            return new GboardPatchesSettingsContract.Section(
                    text(R.string.gboard_patches_custom_stickers_section_sticker, "Sticker"), rows);
        }

        private GboardPatchesSettingsContract.Row selector(
                GboardPatchesSettingsContract.FeatureHost host, String title,
                String[] labels, String[] values, String current,
                GboardPatchesSettingsContract.StringValueConsumer apply) {
            int index = java.util.Arrays.asList(values).indexOf(current);
            return new GboardPatchesSettingsContract.SelectorRow(title, "",
                    labels[Math.max(0, index)], true,
                    () -> runSafely("choose sticker edit", () ->
                            GboardPatchesSettingsContract.showChoiceDialog(host, title, labels,
                                    values, current, null, null, value -> runSafely(
                                            "apply sticker edit", () -> {
                                                apply.accept(value);
                                                GboardPatchesSettingsContract.refresh(host);
                                            }))));
        }

        /** Saves the defaults, or re-renders each sticker from its own original. */
        private void save(GboardPatchesSettingsContract.FeatureHost host, Context hostContext,
                List<String> fileNames) {
            GboardCustomStickersStore.Edits edits = edits();
            busy = "";
            GboardPatchesSettingsContract.refresh(host);
            // Decoding, editing and encoding 512 px stickers is too slow for the UI thread.
            new Thread(() -> runSafely("save sticker edits", () -> {
                String message;
                if (defaultsMode) {
                    try {
                        GboardCustomStickersStore.setDefaults(hostContext, packId, edits);
                        message = text(R.string.gboard_patches_custom_stickers_defaults_saved,
                                "Default edits saved.");
                    } catch (Throwable failure) {
                        Log.w(TAG, "Failed to save default edits", failure);
                        message = text(R.string.gboard_patches_custom_stickers_save_failed,
                                "Could not save the change.");
                    }
                } else {
                    int failed = 0;
                    for (int i = 0; i < fileNames.size(); i++) {
                        busy = String.format(Locale.ROOT, text(
                                R.string.gboard_patches_custom_stickers_saving_progress,
                                "Saving %1$d of %2$d"), i + 1, fileNames.size());
                        GboardPatchesSettingsContract.refresh(host);
                        try {
                            GboardCustomStickersStore.saveEdited(
                                    hostContext, packId, fileNames.get(i), edits);
                        } catch (Throwable failure) {
                            Log.w(TAG, "Failed to save edited sticker", failure);
                            failed++;
                        }
                    }
                    message = failed == 0
                            ? String.format(Locale.ROOT, text(
                                    R.string.gboard_patches_custom_stickers_saved,
                                    "%1$d stickers saved."), fileNames.size())
                            : String.format(Locale.ROOT, text(
                                    R.string.gboard_patches_custom_stickers_save_partial,
                                    "%1$d saved, %2$d could not be saved."),
                                    fileNames.size() - failed, failed);
                }
                busy = null;
                saved = edits;
                GboardPatchesSettingsContract.showMessage(host, message);
                // Saved stickers get new file names, so this editor is done.
                GboardPatchesSettingsContract.closeFeature(host);
                GboardPatchesSettingsContract.refresh(host);
            }), "GboardStickerSave").start();
        }
    }

    private List<GboardPatchesSettingsContract.StatusBlock> progressBlocks(String progress) {
        return progress == null ? Collections.emptyList() : Collections.singletonList(
                new GboardPatchesSettingsContract.StatusBlock(
                        text(R.string.gboard_patches_custom_stickers_working, "Working…"),
                        progress, GboardPatchesSettingsContract.StatusTone.INFO));
    }

    private String stickerCount(int count) {
        return String.format(Locale.ROOT,
                text(R.string.gboard_patches_custom_stickers_sticker_count, "%1$d stickers"), count);
    }

    private void showSaveFailed(GboardPatchesSettingsContract.FeatureHost host) {
        GboardPatchesSettingsContract.showMessage(host, text(
                R.string.gboard_patches_custom_stickers_save_failed, "Could not save the change."));
    }

    private GboardPatchesSettingsContract.Screen errorScreen() {
        return statusScreen(
                text(R.string.gboard_patches_custom_stickers_error_title, "Custom Stickers unavailable"),
                text(R.string.gboard_patches_custom_stickers_error_summary, ""));
    }

    private GboardPatchesSettingsContract.Screen statusScreen(String title, String summary) {
        return new GboardPatchesSettingsContract.Screen(
                entryTitle, headerBadge, entryTitle, "",
                Collections.singletonList(new GboardPatchesSettingsContract.StatusBlock(
                        title, summary, GboardPatchesSettingsContract.StatusTone.WARNING)),
                Collections.emptyList());
    }

    private String text(int resourceId, String fallback) {
        try {
            return GboardSettingsText.get(context, resourceId);
        } catch (Throwable throwable) {
            return fallback;
        }
    }

    private static void runSafely(String operation, Runnable action) {
        try {
            action.run();
        } catch (Throwable throwable) {
            try {
                Log.w(TAG, "Failed to " + operation, throwable);
            } catch (Throwable ignored) {
                // Feature callbacks must not propagate into the settings host.
            }
        }
    }
}
