package dev.jason.gboardpatches.extension.customstickers;

import android.content.Context;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

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
                                summaries, packId -> runSafely("open sticker pack",
                                        () -> GboardPatchesSettingsContract.openFeature(
                                                host, new PackFeature(packId))))));
            }
            List<GboardPatchesSettingsContract.Section> sections = new ArrayList<>();
            sections.add(new GboardPatchesSettingsContract.Section(
                    text(R.string.gboard_patches_custom_stickers_section_packs, "Sticker packs"),
                    packRows));
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

    /** One pack: its sticker grid, keyboard visibility, rename and delete. */
    final class PackFeature implements GboardPatchesSettingsContract.Feature {
        private final String packId;

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
                List<GboardPatchesSettingsContract.Section> sections = new ArrayList<>();
                List<GboardPatchesSettingsContract.Row> stickerRows = new ArrayList<>();
                if (pack.stickers.isEmpty()) {
                    stickerRows.add(new GboardPatchesSettingsContract.InfoRow(
                            text(R.string.gboard_patches_custom_stickers_no_stickers,
                                    "No stickers yet"),
                            text(R.string.gboard_patches_custom_stickers_add_summary, ""), true));
                } else {
                    String itemLabel = text(
                            R.string.gboard_patches_custom_stickers_sticker_item, "Sticker %1$d");
                    String coverLabel = text(
                            R.string.gboard_patches_custom_stickers_cover, "Cover");
                    stickerRows.add(new GboardPatchesSettingsContract.CustomViewRow(viewHost ->
                            GboardCustomStickersViews.createStickerGrid(viewHost.getContext(),
                                    pack, itemLabel, coverLabel, (fileName, position) ->
                                            runSafely("open sticker editor",
                                                    () -> GboardPatchesSettingsContract.openFeature(
                                                            host, new EditorFeature(
                                                                    packId, fileName))))));
                }
                sections.add(new GboardPatchesSettingsContract.Section(
                        text(R.string.gboard_patches_custom_stickers_section_stickers, "Stickers"),
                        pack.stickers.isEmpty() ? null
                                : text(R.string.gboard_patches_custom_stickers_grid_hint, ""),
                        GboardPatchesSettingsContract.SectionStyle.DEFAULT, stickerRows));

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
                packRows.add(new GboardPatchesSettingsContract.ActionRow(
                        text(R.string.gboard_patches_custom_stickers_rename, "Rename pack"),
                        pack.name, true,
                        () -> runSafely("rename sticker pack", () -> rename(host, hostContext, pack))));
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

                return new GboardPatchesSettingsContract.Screen(
                        pack.name, headerBadge, pack.name, stickerCount(pack.stickers.size()),
                        Collections.emptyList(), sections,
                        GboardPatchesSettingsContract.RefreshPolicy.none(),
                        GboardPatchesSettingsContract.PanelStyle.FLAT,
                        new GboardPatchesSettingsContract.CommandRow(
                                text(R.string.gboard_patches_custom_stickers_add, "Add stickers"),
                                "", true,
                                () -> runSafely("add stickers",
                                        () -> addStickers(host, hostContext))));
            } catch (Throwable throwable) {
                Log.w(TAG, "Failed to render sticker pack settings", throwable);
                return errorScreen();
            }
        }

        void addStickers(GboardPatchesSettingsContract.FeatureHost host, Context hostContext) {
            // Documents arrive one by one on a single worker thread, then completion runs there.
            int[] counts = new int[2];
            GboardPatchesSettingsContract.openBinaryDocuments(host, IMAGE_TYPES,
                    GboardCustomStickersStore.MAX_IMPORT_BYTES, document -> {
                        try {
                            GboardCustomStickersStore.addSticker(
                                    hostContext, packId, document.getData());
                            counts[0]++;
                        } catch (Throwable failure) {
                            Log.w(TAG, "Failed to add sticker", failure);
                            counts[1]++;
                        }
                    }, () -> {
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

    /** One sticker: live preview, background removal, outline, cover and delete. */
    final class EditorFeature implements GboardPatchesSettingsContract.Feature {
        private final String packId;
        private final String fileName;
        // Kept across refreshes of this screen; nothing is written until Save.
        private boolean loaded;
        private int backgroundTolerance;
        private int cornerPercent;
        private int outlineWidth;
        private int outlineColor = OUTLINE_COLORS[0];

        EditorFeature(String packId, String fileName) {
            this.packId = packId;
            this.fileName = fileName;
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

        @Override
        public GboardPatchesSettingsContract.Screen buildScreen(
                GboardPatchesSettingsContract.FeatureHost host) {
            try {
                Context hostContext = host == null ? null : host.getContext();
                if (hostContext == null) return errorScreen();
                GboardCustomStickersStore.Pack pack =
                        GboardCustomStickersStore.findPack(hostContext, packId);
                int position = pack == null ? -1 : pack.stickers.indexOf(fileName);
                if (position < 0) {
                    return statusScreen(entryTitle, text(
                            R.string.gboard_patches_custom_stickers_sticker_missing,
                            "This sticker no longer exists."));
                }
                GboardCustomStickersStore.Edits saved = pack.editsOf(fileName);
                if (!loaded) {
                    // Reopening an edited sticker continues from its saved settings.
                    loaded = true;
                    backgroundTolerance = saved.backgroundTolerance;
                    cornerPercent = saved.cornerPercent;
                    outlineWidth = saved.outlineWidth;
                    if (saved.outlineWidth > 0) outlineColor = saved.outlineColor;
                }
                String itemLabel = text(
                        R.string.gboard_patches_custom_stickers_sticker_item, "Sticker %1$d");
                String title = String.format(Locale.ROOT, itemLabel, position + 1);
                GboardCustomStickersStore.Edits edits = edits();
                boolean changed = !edits.equals(saved);
                List<GboardPatchesSettingsContract.Section> sections = new ArrayList<>();
                sections.add(new GboardPatchesSettingsContract.Section(null,
                        Collections.singletonList(new GboardPatchesSettingsContract.CustomViewRow(
                                viewHost -> GboardCustomStickersViews.createEditorPreview(
                                        viewHost.getContext(),
                                        GboardCustomStickersStore.stickerFile(
                                                hostContext, packId, pack.sourceOf(fileName)),
                                        edits,
                                        text(R.string.gboard_patches_custom_stickers_background_not_found,
                                                "No plain background found at the image edges."),
                                        title,
                                        text(R.string.gboard_patches_custom_stickers_corners,
                                                "Round corners"),
                                        text(R.string.gboard_patches_custom_stickers_outline,
                                                "Outline"),
                                        chosen -> runSafely("set sticker shape", () -> {
                                            cornerPercent = chosen.cornerPercent;
                                            outlineWidth = chosen.outlineWidth;
                                            GboardPatchesSettingsContract.refresh(host);
                                        }))))));

                List<GboardPatchesSettingsContract.Row> editRows = new ArrayList<>();
                editRows.add(selector(host,
                        text(R.string.gboard_patches_custom_stickers_remove_background,
                                "Remove background"),
                        text(R.string.gboard_patches_custom_stickers_remove_background_summary, ""),
                        new String[]{
                                text(R.string.gboard_patches_custom_stickers_off, "Off"),
                                text(R.string.gboard_patches_custom_stickers_strength_light, "Light"),
                                text(R.string.gboard_patches_custom_stickers_strength_medium, "Medium"),
                                text(R.string.gboard_patches_custom_stickers_strength_strong, "Strong")},
                        new String[]{OFF, "24", "48", "80"},
                        String.valueOf(backgroundTolerance),
                        value -> backgroundTolerance = Integer.parseInt(value)));
                if (outlineWidth > 0) {
                    String[] colorValues = new String[OUTLINE_COLORS.length];
                    for (int i = 0; i < colorValues.length; i++) {
                        colorValues[i] = Integer.toHexString(OUTLINE_COLORS[i]);
                    }
                    editRows.add(selector(host,
                            text(R.string.gboard_patches_custom_stickers_outline_color,
                                    "Outline color"),
                            "",
                            new String[]{
                                    text(R.string.gboard_patches_custom_stickers_color_white, "White"),
                                    text(R.string.gboard_patches_custom_stickers_color_black, "Black"),
                                    text(R.string.gboard_patches_custom_stickers_color_yellow, "Yellow"),
                                    text(R.string.gboard_patches_custom_stickers_color_pink, "Pink")},
                            colorValues,
                            Integer.toHexString(outlineColor),
                            value -> outlineColor = (int) Long.parseLong(value, 16)));
                }
                if (pack.stickers.size() > 1) {
                    editRows.add(new GboardPatchesSettingsContract.ActionRow(
                            text(R.string.gboard_patches_custom_stickers_apply_others,
                                    "Apply to other stickers"),
                            text(R.string.gboard_patches_custom_stickers_apply_others_summary, ""),
                            true,
                            () -> runSafely("choose stickers to edit",
                                    () -> chooseOthers(host, hostContext, pack, itemLabel))));
                }
                sections.add(new GboardPatchesSettingsContract.Section(
                        text(R.string.gboard_patches_custom_stickers_section_edit, "Edit"),
                        editRows));

                List<GboardPatchesSettingsContract.Row> stickerRows = new ArrayList<>();
                boolean isCover = fileName.equals(pack.cover);
                stickerRows.add(new GboardPatchesSettingsContract.ActionRow(
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
                stickerRows.add(new GboardPatchesSettingsContract.DangerRow(
                        text(R.string.gboard_patches_custom_stickers_delete_sticker,
                                "Delete sticker"),
                        "", true, () -> runSafely("delete sticker", () -> {
                            try {
                                GboardCustomStickersStore.deleteSticker(
                                        hostContext, packId, fileName);
                                GboardPatchesSettingsContract.closeFeature(host);
                            } catch (Throwable failure) {
                                Log.w(TAG, "Failed to delete sticker", failure);
                                showSaveFailed(host);
                            }
                            GboardPatchesSettingsContract.refresh(host);
                        }),
                        text(R.string.gboard_patches_custom_stickers_delete_sticker,
                                "Delete sticker"),
                        text(R.string.gboard_patches_custom_stickers_delete_sticker_confirm,
                                "Delete this sticker?")));
                sections.add(new GboardPatchesSettingsContract.Section(
                        text(R.string.gboard_patches_custom_stickers_section_sticker, "Sticker"),
                        stickerRows));

                return new GboardPatchesSettingsContract.Screen(
                        title, headerBadge, title, pack.name,
                        Collections.emptyList(), sections,
                        GboardPatchesSettingsContract.RefreshPolicy.none(),
                        GboardPatchesSettingsContract.PanelStyle.FLAT,
                        new GboardPatchesSettingsContract.CommandRow(
                                text(R.string.gboard_patches_custom_stickers_save, "Save"),
                                "", changed,
                                () -> runSafely("save sticker", () -> save(host, hostContext,
                                        Collections.singletonList(fileName)))));
            } catch (Throwable throwable) {
                Log.w(TAG, "Failed to render sticker editor", throwable);
                return errorScreen();
            }
        }

        private GboardPatchesSettingsContract.Row selector(
                GboardPatchesSettingsContract.FeatureHost host, String title, String summary,
                String[] labels, String[] values, String current,
                GboardPatchesSettingsContract.StringValueConsumer apply) {
            int index = java.util.Arrays.asList(values).indexOf(current);
            return new GboardPatchesSettingsContract.SelectorRow(title, summary,
                    labels[Math.max(0, index)], true,
                    () -> runSafely("choose sticker edit", () ->
                            GboardPatchesSettingsContract.showChoiceDialog(host, title, labels,
                                    values, current, null, null, value -> runSafely(
                                            "apply sticker edit", () -> {
                                                apply.accept(value);
                                                GboardPatchesSettingsContract.refresh(host);
                                            }))));
        }

        /** Lets the user pick other stickers of the pack; all are preselected. */
        private void chooseOthers(GboardPatchesSettingsContract.FeatureHost host,
                Context hostContext, GboardCustomStickersStore.Pack pack, String itemLabel) {
            List<String> others = new ArrayList<>();
            List<String> labels = new ArrayList<>();
            for (int i = 0; i < pack.stickers.size(); i++) {
                if (pack.stickers.get(i).equals(fileName)) continue;
                others.add(pack.stickers.get(i));
                labels.add(String.format(Locale.ROOT, itemLabel, i + 1));
            }
            boolean[] selected = new boolean[others.size()];
            java.util.Arrays.fill(selected, true);
            GboardPatchesSettingsContract.showMultiChoiceDialog(host,
                    text(R.string.gboard_patches_custom_stickers_apply_others,
                            "Apply to other stickers"),
                    labels.toArray(new String[0]), others.toArray(new String[0]), selected,
                    text(R.string.gboard_patches_custom_stickers_save, "Save"),
                    chosen -> runSafely("apply edits to stickers", () -> {
                        List<String> targets = new ArrayList<>();
                        targets.add(fileName);
                        targets.addAll(chosen);
                        save(host, hostContext, targets);
                    }));
        }

        /** Re-renders each sticker from its own original with this screen's settings. */
        private void save(GboardPatchesSettingsContract.FeatureHost host, Context hostContext,
                List<String> fileNames) {
            GboardCustomStickersStore.Edits edits = edits();
            // Decoding, editing and encoding 512 px stickers is too slow for the UI thread.
            new Thread(() -> runSafely("save edited stickers", () -> {
                int failed = GboardCustomStickersStore.saveEdited(
                        hostContext, packId, fileNames, edits);
                GboardPatchesSettingsContract.showMessage(host, failed == 0
                        ? String.format(Locale.ROOT, text(
                                R.string.gboard_patches_custom_stickers_saved,
                                "%1$d stickers saved."), fileNames.size())
                        : String.format(Locale.ROOT, text(
                                R.string.gboard_patches_custom_stickers_save_partial,
                                "%1$d saved, %2$d could not be saved."),
                                fileNames.size() - failed, failed));
                // Saved stickers get new file names, so this editor is done.
                GboardPatchesSettingsContract.closeFeature(host);
                GboardPatchesSettingsContract.refresh(host);
            }), "GboardStickerSave").start();
        }
    }

    private static final String OFF = "0";
    // White, black, yellow, pink.
    private static final int[] OUTLINE_COLORS = {0xFFFFFFFF, 0xFF000000, 0xFFFFD600, 0xFFFF4081};

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
