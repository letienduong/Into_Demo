package com.example.into_demo;

import android.content.Context;
import android.hardware.Camera;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class GBTakePictureNoPreview {
    private Camera camera;
    private SurfaceHolder surfaceHolder;
    private boolean useFrontCamera = false;
    private boolean portrait = false;
    private String fileName;

    // Khởi tạo với Context và SurfaceView (để lấy SurfaceHolder cho camera)
    public GBTakePictureNoPreview(Context ctx, SurfaceView surfaceView) {
        this.surfaceHolder = surfaceView.getHolder();
    }

    // Chọn sử dụng camera trước
    public void setUseFrontCamera(boolean useFront) {
        this.useFrontCamera = useFront;
    }

    // Chọn xoay dọc ảnh (nếu cần thiết khi dùng camera trước)
    public void setPortrait() {
        this.portrait = true;
    }

    // Kiểm tra và mở camera
    public boolean cameraIsOk() {
        try {
            // Xác định id camera: nếu useFrontCamera=true, chọn camera trước; ngược lại chọn camera sau
            int cameraId = 0;
            if (useFrontCamera) {
                // Tìm ID của camera trước
                Camera.CameraInfo camInfo = new Camera.CameraInfo();
                for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
                    Camera.getCameraInfo(i, camInfo);
                    if (camInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                        cameraId = i;
                        break;
                    }
                }
            }
            camera = Camera.open(cameraId);
            // Thiết lập surface holder cho camera để không cần hiển thị preview
            camera.setPreviewDisplay(surfaceHolder);
            camera.startPreview();
            return true;
        } catch (Exception e) {
            // Mở camera thất bại (ví dụ camera đã bị ứng dụng khác dùng)
            return false;
        }
    }

    // Thực hiện chụp ảnh và lưu file
    public void takePicture() {
        if (camera == null) return;
        try {
            camera.takePicture(null, null, new Camera.PictureCallback() {
                @Override
                public void onPictureTaken(byte[] data, Camera cam) {
                    // Lưu dữ liệu ảnh chụp (mảng byte) vào file (hoặc bộ nhớ)
                    // Placeholder: bỏ qua chi tiết lưu file ảnh
                    // ...
                    // Giải phóng camera sau khi chụp
                    camera.release();
                }
            });
        } catch (Exception e) {
            // Xử lý lỗi chụp ảnh (nếu có)
        }
    }
}
