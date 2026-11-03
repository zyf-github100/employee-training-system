package com.zhixing.training.certificate;

import java.time.Instant;
import jakarta.validation.constraints.*;

public final class CertificateDtos {
    private CertificateDtos() { }
    public record CertificateView(long id, String certificateNo, long planId, String planTitle,
                                  String recipientName, String employeeNo, String title,
                                  Instant issuedAt, Instant revokedAt, String status,
                                  String issuerName, Instant completedAt, String verificationCode,
                                  long version, String revocationReason, String revokedByName, boolean legacySnapshot,
                                  String verificationUrl) { }
    public record RevokeRequest(@NotBlank @Size(max=500) String reason,@Min(0) long version) { }
    public record VerificationView(String certificateNo,String recipientName,String employeeNoMasked,
                                   String planTitle,String issuerName,Instant completedAt,Instant issuedAt,
                                   String status,Instant revokedAt,boolean legacySnapshot,Instant checkedAt) { }
}
