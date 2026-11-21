package com.vetclinic.profile.exception;

/** Không đẩy được ảnh lên Cloudinary (mạng, khoá sai, Cloudinary từ chối). Người dùng thử lại sau. */
public class PhotoUploadException extends RuntimeException {

    public PhotoUploadException(String message, Throwable cause) {
        super(message, cause);
    }
}
