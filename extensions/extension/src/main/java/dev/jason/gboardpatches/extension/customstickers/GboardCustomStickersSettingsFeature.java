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
            List<GboardPatchesSettingsContract.Row> packRows = new ArrayList<>();
            for (GboardCustomStickersStore.Pack pack : GboardCustomStickersStore.load(hostContext)) {
                packRows.add(new GboardPatchesSettingsContract.NavigationRow(
                        pack.name, stickerCount(pack.stickers.size()), true,
                        () -> GboardPatchesSettingsContract.openFeature(
                                host, new PackFeature(pack.id))));
            }
            if (packRows.isEmpty()) {
                packRows.add(new GboardPatchesSettingsContract.InfoRow(
                        text(R.string.gboard_patches_custom_stickers_empty, "No sticker packs yet"),
                        "", false));
            }
            packRows.add(new GboardPatchesSettingsContract.ActionRow(
                    text(R.string.gboard_patches_custom_stickers_create, "Create sticker pack"),
                    "", true, () -> runSafely("create sticker pack", () -> createPack(host, hostContext))));
            List<GboardPatchesSettingsContract.Row> usageRows = Collections.singletonList(
                    new GboardPatchesSettingsContract.InfoRow(
                            text(R.string.gboard_patches_custom_stickers_usage_title, "How to use"),
                            text(R.string.gboard_patches_custom_stickers_usage_summary, ""), true));
            List<GboardPatchesSettingsContract.Section> sections = new ArrayList<>();
            sections.add(new GboardPatchesSettingsContract.Section(
                    text(R.string.gboard_patches_custom_stickers_section_packs, "Sticker packs"),
                    packRows));
            sections.add(new GboardPatchesSettingsContract.Section(
                    text(R.string.gboard_patches_custom_stickers_usage_title, "How to use"),
                    usageRows));
            return new GboardPatchesSettingsContract.Screen(
                    entryTitle, headerBadge, entryTitle, entrySummary,
                    Collections.emptyList(), sections,
                    GboardPatchesSettingsContract.RefreshPolicy.none(),
                    GboardPatchesSettingsContract.PanelStyle.FLAT);
        } catch (Throwable throwable) {
            Log.w(TAG, "Failed to render custom stickers settings", throwable);
            return errorScreen();
        }
    }

    private void createPack(GboardPatchesSettingsContract.FeatureHost host, Context hostContext) {
        GboardPatchesSettingsContract.showTextInputDialog(host,
                text(R.string.gboard_patches_custom_stickers_create, "Create sticker pack"),
                text(R.string.gboard_patches_custom_stickers_name_hint, "Pack name"), "",
                name -> runSafely("save new sticker pack", () -> {
                    try {
                        GboardCustomStickersStore.Pack pack =
                                GboardCustomStickersStore.createPack(hostContext, name);
                        GboardPatchesSettingsContract.refresh(host);
                        GboardPatchesSettingsContract.openFeature(host, new PackFeature(pack.id));
                    } catch (Throwable failure) {
                        Log.w(TAG, "Failed to create sticker pack", failure);
                        showSaveFailed(host);
                    }
                }));
    }

    /** One pack: add, rename, delete stickers, or delete the pack. */
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
                List<GboardPatchesSettingsContract.Row> packRows = new ArrayList<>();
                packRows.add(new GboardPatchesSettingsContract.ActionRow(
                        text(R.string.gboard_patches_custom_stickers_add, "Add sticker"),
                        text(R.string.gboard_patches_custom_stickers_add_summary, ""), true,
                        () -> runSafely("add sticker", () -> addSticker(host, hostContext))));
                packRows.add(new GboardPatchesSettingsContract.ActionRow(
                        text(R.string.gboard_patches_custom_stickers_rename, "Rename pack"),
                        pack.name, true,
                        () -> runSafely("rename sticker pack", () -> rename(host, hostContext, pack))));
                packRows.add(new GboardPatchesSettingsContract.DangerRow(
                        text(R.string.gboard_patches_custom_stickers_delete_pack, "Delete pack"),
                        "", true, () -> runSafely("delete sticker pack", () -> {
                            try {
                                GboardCustomStickersStore.deletePack(hostContext, packId);
                            } catch (Throwable failure) {
                                Log.w(TAG, "Failed to delete sticker pack", failure);
                                showSaveFailed(host);
                            }
                            GboardPatchesSettingsContract.refresh(host);
                        }),
                        text(R.string.gboard_patches_custom_stickers_delete_pack, "Delete pack"),
                        text(R.string.gboard_patches_custom_stickers_delete_pack_confirm, "")));
                List<GboardPatchesSettingsContract.Row> stickerRows = new ArrayList<>();
                for (int i = 0; i < pack.stickers.size(); i++) {
                    String fileName = pack.stickers.get(i);
                    String title = String.format(Locale.ROOT, text(
                            R.string.gboard_patches_custom_stickers_sticker_item, "Sticker %1$d"), i + 1);
                    stickerRows.add(new GboardPatchesSettingsContract.DangerRow(
                            title, "", true, () -> runSafely("delete sticker", () -> {
                                try {
                                    GboardCustomStickersStore.deleteSticker(
                                            hostContext, packId, fileName);
                                } catch (Throwable failure) {
                                    Log.w(TAG, "Failed to delete sticker", failure);
                                    showSaveFailed(host);
                                }
                                GboardPatchesSettingsContract.refresh(host);
                            }),
                            title,
                            text(R.string.gboard_patches_custom_stickers_delete_sticker_confirm, "")));
                }
                List<GboardPatchesSettingsContract.Section> sections = new ArrayList<>();
                sections.add(new GboardPatchesSettingsContract.Section(pack.name, packRows));
                if (!stickerRows.isEmpty()) {
                    sections.add(new GboardPatchesSettingsContract.Section(
                            text(R.string.gboard_patches_custom_stickers_section_stickers, "Stickers"),
                            stickerRows));
                }
                return new GboardPatchesSettingsContract.Screen(
                        pack.name, headerBadge, pack.name, stickerCount(pack.stickers.size()),
                        Collections.emptyList(), sections,
                        GboardPatchesSettingsContract.RefreshPolicy.none(),
                        GboardPatchesSettingsContract.PanelStyle.FLAT);
            } catch (Throwable throwable) {
                Log.w(TAG, "Failed to render sticker pack settings", throwable);
                return errorScreen();
            }
        }

        private void addSticker(GboardPatchesSettingsContract.FeatureHost host,
                Context hostContext) {
            GboardPatchesSettingsContract.openBinaryDocument(host, IMAGE_TYPES,
                    GboardCustomStickersStore.MAX_IMPORT_BYTES, document -> {
                        try {
                            GboardCustomStickersStore.addSticker(
                                    hostContext, packId, document.getData());
                            GboardPatchesSettingsContract.showMessage(host, text(
                                    R.string.gboard_patches_custom_stickers_added, "Sticker added."));
                        } catch (Throwable failure) {
                            Log.w(TAG, "Failed to add sticker", failure);
                            GboardPatchesSettingsContract.showMessage(host, text(
                                    R.string.gboard_patches_custom_stickers_add_failed,
                                    "Could not add this image."));
                        }
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
