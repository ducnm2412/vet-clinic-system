package com.vetclinic.order.payment;

import java.io.IOException;

/** Gửi một yêu cầu JSON tới VNPAY. Tách riêng để test thay bằng bản giả, không gọi mạng thật. */
public interface VnpayHttp {

    /** @return thân phản hồi (JSON) khi VNPAY trả HTTP 2xx */
    String postJson(String url, String json) throws IOException, InterruptedException;
}
