package dev.jason.gboardpatches.extension.customstickers;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Feature-drawn views for Custom Stickers: the pack list with covers and a pack's sticker grid. */
final class GboardCustomStickersViews {
    private static final String TAG = "GboardPatches";
    private static final int COLUMNS = 4;
    private static final int THUMBNAIL_PX = 192;
    private static final int TILE_COLOR = 0x14808080;
    private static final int MAX_OUTLINE_WIDTH = GboardCustomStickersStore.MAX_OUTLINE_WIDTH;
    // One shared worker: previews are rebuilt on every settings refresh and while dragging.
    private static final ExecutorService PREVIEW_RENDERER = Executors.newSingleThreadExecutor(
            runnable -> {
                Thread thread = new Thread(runnable, "GboardStickerPreview");
                thread.setDaemon(true);
                return thread;
            });

    interface StickerClick {
        void onClick(String fileName, int position);
    }

    interface PackClick {
        void onClick(String packId);
    }

    /** One row per pack: cover, name, and a summary line supplied by the caller. */
    static View createPackList(Context context, List<GboardCustomStickersStore.Pack> packs,
            List<String> summaries, PackClick click) {
        float density = density(context);
        int primary = resolveColor(context, android.R.attr.textColorPrimary,
                isNight(context) ? Color.WHITE : Color.BLACK);
        int secondary = resolveColor(context, android.R.attr.textColorSecondary,
                isNight(context) ? 0xFFBDBDBD : 0xFF5F6368);
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        List<ImageView> covers = new ArrayList<>();
        List<File> files = new ArrayList<>();
        for (int i = 0; i < packs.size(); i++) {
            GboardCustomStickersStore.Pack pack = packs.get(i);
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(px(density, 72));
            row.setPadding(px(density, 16), px(density, 8), px(density, 16), px(density, 8));
            row.setBackgroundResource(selectableBackground(context));
            row.setClickable(true);
            row.setFocusable(true);
            row.setOnClickListener(view -> click.onClick(pack.id));
            row.setContentDescription(pack.name + ", " + summaries.get(i));

            ImageView cover = new ImageView(context);
            cover.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int inset = px(density, 4);
            cover.setPadding(inset, inset, inset, inset);
            cover.setBackground(tile(density, 0, 0));
            cover.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.addView(cover, new LinearLayout.LayoutParams(px(density, 52), px(density, 52)));
            if (pack.cover != null) {
                covers.add(cover);
                files.add(GboardCustomStickersStore.stickerFile(context, pack.id, pack.cover));
            }

            LinearLayout texts = new LinearLayout(context);
            texts.setOrientation(LinearLayout.VERTICAL);
            texts.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            TextView name = new TextView(context);
            name.setText(pack.name);
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
            name.setTextColor(primary);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(name);
            TextView summary = new TextView(context);
            summary.setText(summaries.get(i));
            summary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
            summary.setTextColor(secondary);
            texts.addView(summary);
            LinearLayout.LayoutParams textParams =
                    new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            textParams.leftMargin = px(density, 16);
            row.addView(texts, textParams);

            TextView chevron = new TextView(context);
            chevron.setText("›");
            chevron.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f);
            chevron.setTextColor(secondary);
            chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            row.addView(chevron);
            list.addView(row);
        }
        loadThumbnails(covers, files);
        return list;
    }

    /** Square tiles, four per row; the cover sticker gets an accent outline. */
    static View createStickerGrid(Context context, GboardCustomStickersStore.Pack pack,
            String itemLabelFormat, String coverLabel, StickerClick click) {
        float density = density(context);
        int gap = px(density, 4);
        int accent = resolveColor(context, android.R.attr.colorAccent, 0xFF1A73E8);
        GridLayout grid = new GridLayout(context);
        grid.setColumnCount(COLUMNS);
        grid.setPadding(gap, gap, gap, gap);
        List<ImageView> images = new ArrayList<>();
        List<File> files = new ArrayList<>();
        for (int i = 0; i < pack.stickers.size(); i++) {
            String fileName = pack.stickers.get(i);
            int position = i;
            boolean isCover = fileName.equals(pack.cover);
            FrameLayout cell = new SquareFrame(context);
            cell.setBackground(tile(density, isCover ? px(density, 2) : 0, accent));
            cell.setPadding(gap * 2, gap * 2, gap * 2, gap * 2);
            String label = String.format(Locale.ROOT, itemLabelFormat, i + 1);
            cell.setContentDescription(isCover ? label + ", " + coverLabel : label);
            cell.setClickable(true);
            cell.setFocusable(true);
            int ripple = selectableBackground(context);
            if (ripple != 0) cell.setForeground(context.getDrawable(ripple));
            cell.setOnClickListener(view -> click.onClick(fileName, position));
            ImageView image = new ImageView(context);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            cell.addView(image, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER));
            if (isCover) {
                TextView badge = new TextView(context);
                badge.setText(coverLabel);
                badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f);
                badge.setTextColor(Color.WHITE);
                badge.setPadding(px(density, 6), px(density, 1), px(density, 6), px(density, 1));
                GradientDrawable badgeBackground = new GradientDrawable();
                badgeBackground.setCornerRadius(px(density, 8));
                badgeBackground.setColor(accent);
                badge.setBackground(badgeBackground);
                badge.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                cell.addView(badge, new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START));
            }
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                    GridLayout.spec(i / COLUMNS), GridLayout.spec(i % COLUMNS, 1f));
            params.width = 0;
            params.setMargins(gap, gap, gap, gap);
            grid.addView(cell, params);
            images.add(image);
            files.add(GboardCustomStickersStore.stickerFile(context, pack.id, fileName));
        }
        // An incomplete last row still needs its columns to keep their width.
        for (int i = pack.stickers.size(); i % COLUMNS != 0; i++) {
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                    GridLayout.spec(i / COLUMNS), GridLayout.spec(i % COLUMNS, 1f));
            params.width = 0;
            params.setMargins(gap, gap, gap, gap);
            grid.addView(new View(context), params);
        }
        loadThumbnails(images, files);
        return grid;
    }

    /**
     * Large preview of an edited sticker on a checkerboard, so transparent areas are visible.
     * Rendering runs off the UI thread with the same code that saves the sticker.
     */
    static View createEditorPreview(Context context, File file,
            GboardCustomStickersStore.Edits edits, String backgroundNotFoundText,
            String description, String cornerLabel, String outlineLabel,
            EditsChosen editsChosen) {
        float density = density(context);
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        column.setPadding(0, px(density, 12), 0, px(density, 12));

        ImageView image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int inset = px(density, 12);
        image.setPadding(inset, inset, inset, inset);
        image.setBackground(checkerboard(context, density));
        image.setContentDescription(description);
        int size = px(density, 240);
        column.addView(image, new LinearLayout.LayoutParams(size, size));

        TextView caption = new TextView(context);
        caption.setText(backgroundNotFoundText);
        caption.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        caption.setTextColor(resolveColor(context, android.R.attr.textColorSecondary,
                isNight(context) ? 0xFFBDBDBD : 0xFF5F6368));
        caption.setGravity(Gravity.CENTER);
        caption.setPadding(px(density, 16), px(density, 8), px(density, 16), 0);
        caption.setVisibility(View.GONE);
        column.addView(caption);

        // Slider values while dragging: corner percent, outline width.
        int[] values = {edits.cornerPercent, edits.outlineWidth};
        AtomicInteger latest = new AtomicInteger();
        Runnable render = () -> {
            // Only the newest request renders; older ones are skipped while the user drags.
            int ticket = latest.incrementAndGet();
            GboardCustomStickersStore.Edits current = new GboardCustomStickersStore.Edits(
                    edits.backgroundTolerance, values[0], values[1], edits.outlineColor);
            PREVIEW_RENDERER.execute(() -> {
                if (ticket != latest.get()) return;
                try {
                    GboardCustomStickersStore.Rendered rendered =
                            GboardCustomStickersStore.render(file, current);
                    image.post(() -> {
                        if (ticket != latest.get()) return;
                        image.setImageBitmap(rendered.bitmap);
                        caption.setVisibility(
                                rendered.backgroundNotFound ? View.VISIBLE : View.GONE);
                    });
                } catch (Throwable failure) {
                    Log.w(TAG, "Failed to render sticker preview", failure);
                }
            });
        };
        Runnable chosen = () -> editsChosen.onChosen(new GboardCustomStickersStore.Edits(
                edits.backgroundTolerance, values[0], values[1], edits.outlineColor));
        addSlider(column, density, cornerLabel, "%", 100, values, 0, render, chosen);
        addSlider(column, density, outlineLabel, "", MAX_OUTLINE_WIDTH, values, 1, render, chosen);
        render.run();
        return column;
    }

    interface EditsChosen {
        void onChosen(GboardCustomStickersStore.Edits edits);
    }

    /** A titled SeekBar writing {@code values[slot]}; previews while dragging, commits on release. */
    private static void addSlider(LinearLayout column, float density, String label, String unit,
            int max, int[] values, int slot, Runnable render, Runnable chosen) {
        Context context = column.getContext();
        TextView title = new TextView(context);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
        title.setTextColor(resolveColor(context, android.R.attr.textColorPrimary,
                isNight(context) ? Color.WHITE : Color.BLACK));
        title.setPadding(px(density, 20), px(density, 16), px(density, 20), 0);
        title.setText(label + ": " + values[slot] + unit);
        title.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        column.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        SeekBar bar = new SeekBar(context);
        bar.setMax(max);
        bar.setProgress(values[slot]);
        bar.setContentDescription(label);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, px(density, 48));
        params.setMargins(px(density, 8), 0, px(density, 8), 0);
        column.addView(bar, params);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress,
                    boolean fromUser) {
                values[slot] = progress;
                title.setText(label + ": " + progress + unit);
                if (fromUser) render.run();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                chosen.run();
            }
        });
    }

    private static android.graphics.drawable.Drawable checkerboard(Context context, float density) {
        int cell = px(density, 8);
        Bitmap tile = Bitmap.createBitmap(cell * 2, cell * 2, Bitmap.Config.ARGB_8888);
        int light = isNight(context) ? 0xFF3C3C3C : 0xFFFFFFFF;
        int dark = isNight(context) ? 0xFF2A2A2A : 0xFFE3E3E3;
        tile.eraseColor(light);
        android.graphics.Canvas canvas = new android.graphics.Canvas(tile);
        android.graphics.Paint paint = new android.graphics.Paint();
        paint.setColor(dark);
        canvas.drawRect(0, 0, cell, cell, paint);
        canvas.drawRect(cell, cell, cell * 2, cell * 2, paint);
        android.graphics.drawable.BitmapDrawable drawable =
                new android.graphics.drawable.BitmapDrawable(context.getResources(), tile);
        drawable.setTileModeXY(android.graphics.Shader.TileMode.REPEAT,
                android.graphics.Shader.TileMode.REPEAT);
        return drawable;
    }

    /** Decodes off the UI thread; a detached view simply ignores late posts. */
    private static void loadThumbnails(List<ImageView> images, List<File> files) {
        if (images.isEmpty()) return;
        new Thread(() -> {
            for (int i = 0; i < files.size(); i++) {
                Bitmap bitmap = decodeThumbnail(files.get(i));
                ImageView image = images.get(i);
                if (bitmap != null) image.post(() -> image.setImageBitmap(bitmap));
            }
        }, "GboardStickerThumbs").start();
    }

    private static Bitmap decodeThumbnail(File file) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), bounds);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 1;
            while (Math.max(bounds.outWidth, bounds.outHeight) / (options.inSampleSize * 2)
                    >= THUMBNAIL_PX) {
                options.inSampleSize *= 2;
            }
            return BitmapFactory.decodeFile(file.getPath(), options);
        } catch (Throwable failure) {
            Log.w(TAG, "Failed to decode sticker thumbnail", failure);
            return null;
        }
    }

    private static GradientDrawable tile(float density, int strokePx, int strokeColor) {
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(12 * density);
        background.setColor(TILE_COLOR);
        if (strokePx > 0) background.setStroke(strokePx, strokeColor);
        return background;
    }

    private static int selectableBackground(Context context) {
        TypedValue value = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true);
        return value.resourceId;
    }

    private static int resolveColor(Context context, int attribute, int fallback) {
        TypedValue value = new TypedValue();
        if (context.getTheme().resolveAttribute(attribute, value, true)) {
            if (value.resourceId != 0) {
                try {
                    return context.getColor(value.resourceId);
                } catch (Throwable ignored) {
                    return fallback;
                }
            }
            if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT
                    && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                return value.data;
            }
        }
        return fallback;
    }

    private static boolean isNight(Context context) {
        return (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    private static float density(Context context) {
        return context.getResources().getDisplayMetrics().density;
    }

    private static int px(float density, int dp) {
        return Math.round(dp * density);
    }

    private static final class SquareFrame extends FrameLayout {
        SquareFrame(Context context) {
            super(context);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, widthMeasureSpec);
        }
    }
}
