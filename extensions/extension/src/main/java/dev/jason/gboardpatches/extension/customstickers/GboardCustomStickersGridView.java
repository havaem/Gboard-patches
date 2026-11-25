package dev.jason.gboardpatches.extension.customstickers;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Thumbnail grid of one pack's stickers; the cover gets an accent outline. */
final class GboardCustomStickersGridView {
    private static final String TAG = "GboardPatches";
    private static final int COLUMNS = 4;
    private static final int THUMBNAIL_PX = 192;

    interface StickerClick {
        void onClick(String fileName);
    }

    private GboardCustomStickersGridView() { }

    static View create(Context context, GboardCustomStickersStore.Pack pack,
            String itemLabelFormat, StickerClick click) {
        float density = context.getResources().getDisplayMetrics().density;
        int gap = Math.round(4 * density);
        int accent = accentColor(context);
        GridLayout grid = new GridLayout(context);
        grid.setColumnCount(COLUMNS);
        grid.setPadding(gap, gap, gap, gap);
        List<ImageView> images = new ArrayList<>();
        List<File> files = new ArrayList<>();
        for (int i = 0; i < pack.stickers.size(); i++) {
            String fileName = pack.stickers.get(i);
            FrameLayout cell = new SquareFrame(context);
            GradientDrawable background = new GradientDrawable();
            background.setCornerRadius(12 * density);
            background.setColor(0x14808080);
            if (fileName.equals(pack.cover)) background.setStroke(Math.round(2 * density), accent);
            cell.setBackground(background);
            cell.setPadding(gap * 2, gap * 2, gap * 2, gap * 2);
            cell.setContentDescription(String.format(Locale.ROOT, itemLabelFormat, i + 1));
            cell.setClickable(true);
            cell.setFocusable(true);
            cell.setOnClickListener(view -> click.onClick(fileName));
            ImageView image = new ImageView(context);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            cell.addView(image, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER));
            GridLayout.LayoutParams params = new GridLayout.LayoutParams(
                    GridLayout.spec(i / COLUMNS), GridLayout.spec(i % COLUMNS, 1f));
            params.width = 0;
            params.setMargins(gap, gap, gap, gap);
            grid.addView(cell, params);
            images.add(image);
            files.add(GboardCustomStickersStore.stickerFile(context, pack.id, fileName));
        }
        // Decode off the UI thread; a detached view simply ignores late posts.
        new Thread(() -> {
            for (int i = 0; i < files.size(); i++) {
                Bitmap bitmap = decodeThumbnail(files.get(i));
                ImageView image = images.get(i);
                if (bitmap != null) image.post(() -> image.setImageBitmap(bitmap));
            }
        }, "GboardStickerThumbs").start();
        return grid;
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

    private static int accentColor(Context context) {
        TypedValue value = new TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.colorAccent, value, true)
                && value.type >= TypedValue.TYPE_FIRST_COLOR_INT
                && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
            return value.data;
        }
        return 0xFF1A73E8;
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
