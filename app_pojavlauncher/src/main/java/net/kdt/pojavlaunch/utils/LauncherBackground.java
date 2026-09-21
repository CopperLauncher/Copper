package net.kdt.pojavlaunch.utils;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import net.kdt.pojavlaunch.Tools;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * The custom launcher background: an image (static or animated GIF/WebP) or a video (MP4).
 * The picked file is copied into the launcher data folder, and shown on the home screen of the
 * right pane of the two-pane landscape main menu.
 */
public final class LauncherBackground {
    public static final String PREF_KIND = "custom_launcher_bg_kind";
    public static final String KIND_IMAGE = "image";
    public static final String KIND_VIDEO = "video";

    /** Larger images are scaled down while decoding, a background never needs more than this */
    private static final int MAX_IMAGE_SIDE = 2048;

    private LauncherBackground() {}

    @NonNull
    public static File getFile() {
        // DIR_DATA is only known at runtime, so this can't be a constant
        return new File(Tools.DIR_DATA, "custom_launcher_bg");
    }

    public static boolean exists() {
        return getFile().isFile();
    }

    /** @return KIND_IMAGE or KIND_VIDEO, null if there is no background */
    @Nullable
    public static String getKind(@NonNull Context context) {
        if (!exists()) return null;
        return PreferenceManager.getDefaultSharedPreferences(context).getString(PREF_KIND, KIND_IMAGE);
    }

    public static void remove(@NonNull Context context) {
        //noinspection ResultOfMethodCallIgnored
        getFile().delete();
        PreferenceManager.getDefaultSharedPreferences(context).edit().remove(PREF_KIND).apply();
    }

    /**
     * Copies the picked file and makes it the background. Blocking, do not call it on the UI thread.
     * @return whether the file was usable, the previous background is kept if it wasn't
     */
    public static boolean setFromUri(@NonNull Context context, @NonNull Uri uri) {
        boolean video = isVideo(context, uri);
        File target = getFile();
        File temporary = new File(target.getParentFile(), target.getName() + ".tmp");
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("Can't open " + uri);
            try (OutputStream out = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            }
            if (!isUsable(temporary, video)) throw new IOException("Unusable background");
            //noinspection ResultOfMethodCallIgnored
            target.delete();
            if (!temporary.renameTo(target)) throw new IOException("Can't move the background");
        } catch (Exception e) {
            //noinspection ResultOfMethodCallIgnored
            temporary.delete();
            return false;
        }
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(PREF_KIND, video ? KIND_VIDEO : KIND_IMAGE)
                .apply();
        return true;
    }

    private static boolean isVideo(Context context, Uri uri) {
        String mimeType = context.getContentResolver().getType(uri);
        if (mimeType == null) {
            String extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString());
            if (extension != null) mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.toLowerCase());
        }
        return mimeType != null && mimeType.startsWith("video/");
    }

    private static boolean isUsable(File file, boolean video) {
        if (video) {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            try {
                retriever.setDataSource(file.getAbsolutePath());
                return "yes".equals(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO));
            } catch (Exception e) {
                return false;
            } finally {
                try {
                    retriever.release();
                } catch (Exception ignored) {}
            }
        }
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        return options.outWidth > 0 && options.outHeight > 0;
    }

    /**
     * Decodes the background image, scaled down if it is huge. Animated images (GIF, animated WebP)
     * come out as a self-animating drawable on Android 9 and newer, they show their first frame
     * before that. Blocking, do not call it on the UI thread.
     */
    @Nullable
    public static Drawable decodeImage(@NonNull File file) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return ImageDecoder.decodeDrawable(ImageDecoder.createSource(file), (decoder, info, source) -> {
                    int largest = Math.max(info.getSize().getWidth(), info.getSize().getHeight());
                    int sample = 1;
                    while (largest / (sample * 2) >= MAX_IMAGE_SIDE) sample *= 2;
                    if (sample > 1) decoder.setTargetSampleSize(sample);
                });
            }
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), bounds);
            int sample = 1;
            while (Math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_IMAGE_SIDE) sample *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            android.graphics.Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
            return bitmap == null ? null : new android.graphics.drawable.BitmapDrawable(null, bitmap);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
