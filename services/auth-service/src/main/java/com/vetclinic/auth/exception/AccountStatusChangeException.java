package com.vetclinic.auth.exception;

/** Khoá tài khoản bị từ chối vì sẽ làm hệ thống mất quyền quản trị — 409. */
public class AccountStatusChangeException extends RuntimeException {

    public AccountStatusChangeException(String message) {
        super(message);
    }
}
