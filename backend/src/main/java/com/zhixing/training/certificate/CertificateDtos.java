package com.zhixing.training.certificate;

import java.time.Instant;

public final class CertificateDtos {
    private CertificateDtos() { }
    public record CertificateView(long id, String certificateNo, long planId, String planTitle,
                                  String recipientName, String employeeNo, String title,
                                  Instant issuedAt, Instant revokedAt, String status) { }
}
