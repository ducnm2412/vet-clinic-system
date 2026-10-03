package com.vetclinic.auth.exception;

/** Nguoi dung tu choi cap quyen "email" luc dang nhap Facebook - Graph API /me tra ve khong
 * co truong email, khong the tao/tim tai khoan (email la khoa duy nhat cua users). */
public class FacebookEmailRequiredException extends RuntimeException {

    public FacebookEmailRequiredException() {
        super("Facebook login requires the email permission");
    }
}
