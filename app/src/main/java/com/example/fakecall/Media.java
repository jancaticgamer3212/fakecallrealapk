package com.example.fakecall;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** The caller photo and the "answer" audio: picked once, copied into app storage, used later. */
final class Media {

    static final String K_IMG_NAME = "img_name";
    static final String K_AUDIO_NAME = "audio_name";

    private static final int MAX_IMAGE_PX = 1024;

    private Media() {
    }

    static File imageFile(Context c) {
        return new File(c.getFilesDir(), "caller.jpg");
    }

    static File audioFile(Context c) {
        return new File(c.getFilesDir(), "answer_audio");
    }

    static void clearImage(Context c) {
        imageFile(c).delete();
    }

    static void clearAudio(Context c) {
        audioFile(c).delete();
    }

    /** File name shown to the user for a picked document. */
    static String displayName(Context c, Uri uri) {
        Cursor cur = null;
        try {
            cur = c.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME},
                    null, null, null);
            if (cur != null && cur.moveToFirst()) {
                String n = cur.getString(0);
                if (n != null) return n;
            }
        } catch (Exception ignored) {
        } finally {
            if (cur != null) cur.close();
        }
        String last = uri.getLastPathSegment();
        return last == null ? "file" : last;
    }

    private static void copy(InputStream in, File out) throws IOException {
        OutputStream os = new FileOutputStream(out);
        try {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
        } finally {
            os.close();
            in.close();
        }
    }

    private static String describe(Throwable t) {
        String m = t.getMessage();
        return t.getClass().getSimpleName() + (m == null ? "" : ": " + m);
    }

    /** Copies the picked audio into app storage. Returns null on success, else the reason. */
    static String saveAudio(Context c, Uri uri) {
        File out = audioFile(c);
        try {
            InputStream in = c.getContentResolver().openInputStream(uri);
            if (in == null) return "cannot open the file";
            copy(in, out);
            return null;
        } catch (Exception e) {
            out.delete();
            return describe(e);
        }
    }

    /** Shrinks and rotates the picked photo and stores it as a JPEG. Returns null on success. */
    static String saveImage(Context c, Uri uri) {
        File tmp = new File(c.getCacheDir(), "pick.tmp");
        try {
            InputStream in = c.getContentResolver().openInputStream(uri);
            if (in == null) return "cannot open the file";
            copy(in, tmp);

            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(tmp.getPath(), bounds);
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return "that is not a readable image";

            int sample = 1;
            while (Math.max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_IMAGE_PX) {
                sample *= 2;
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            Bitmap bmp = BitmapFactory.decodeFile(tmp.getPath(), opts);
            if (bmp == null) return "that is not a readable image";

            int degrees = 0;
            try {
                int o = new ExifInterface(tmp.getPath()).getAttributeInt(
                        ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                if (o == ExifInterface.ORIENTATION_ROTATE_90) degrees = 90;
                else if (o == ExifInterface.ORIENTATION_ROTATE_180) degrees = 180;
                else if (o == ExifInterface.ORIENTATION_ROTATE_270) degrees = 270;
            } catch (IOException ignored) {
            }
            if (degrees != 0) {
                Matrix m = new Matrix();
                m.postRotate(degrees);
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
            }

            FileOutputStream out = new FileOutputStream(imageFile(c));
            try {
                bmp.compress(Bitmap.CompressFormat.JPEG, 90, out);
            } finally {
                out.close();
            }
            return null;
        } catch (Exception | OutOfMemoryError e) {
            imageFile(c).delete();
            return describe(e);
        } finally {
            tmp.delete();
        }
    }

    /** The stored caller photo, decoded to roughly targetPx on its short side, or null. */
    static Bitmap loadCaller(Context c, int targetPx) {
        File f = imageFile(c);
        if (!f.exists()) return null;
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), bounds);
            int sample = 1;
            while (Math.min(bounds.outWidth, bounds.outHeight) / (sample * 2) >= targetPx) {
                sample *= 2;
            }
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = sample;
            return BitmapFactory.decodeFile(f.getPath(), opts);
        } catch (Throwable t) {
            return null;
        }
    }

    /** Centre-crops to a square and masks it to a circle of the given size. */
    static Bitmap circle(Bitmap src, int px) {
        int side = Math.min(src.getWidth(), src.getHeight());
        Bitmap square = Bitmap.createBitmap(src, (src.getWidth() - side) / 2,
                (src.getHeight() - side) / 2, side, side);
        Bitmap scaled = Bitmap.createScaledBitmap(square, px, px, true);
        Bitmap out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setShader(new BitmapShader(scaled, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        new Canvas(out).drawCircle(px / 2f, px / 2f, px / 2f, p);
        return out;
    }

    /**
     * A round avatar: the caller photo if one is set, otherwise a grey circle with the
     * initial. The caller sets the layout params.
     */
    static View avatarView(Context c, String name, int sizePx) {
        Bitmap photo = loadCaller(c, sizePx);
        if (photo != null) {
            ImageView iv = new ImageView(c);
            iv.setImageBitmap(circle(photo, sizePx));
            return iv;
        }
        TextView t = new TextView(c);
        String initial = (name == null || name.trim().isEmpty())
                ? "?" : name.trim().substring(0, 1).toUpperCase();
        t.setText(initial);
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, sizePx * 0.45f);
        t.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0xFF5F6B7A);
        t.setBackground(bg);
        return t;
    }
}
