# Security

Do not include OTP secrets, private snippets, screenshots of confidential material, repository tokens or signing keys in public reports. Use GitHub private vulnerability reporting when it is enabled, or contact the repository maintainer privately before disclosing an exploitable issue.

The Shizuku bridge exposes typed operations and no arbitrary shell endpoint. Macros run in a Java-class-denied interpreter with deadlines and a limited host API. Private information requires per-operation strong biometric authorization with Android Keystore AES-GCM. Biometric enrollment changes may invalidate keys; the application never silently resets unreadable private data.

Captured screenshots may include visible sensitive information even when password metadata is excluded. Review archives, macro files and logs before sharing. The app does not upload page content, notebook values or macro source.

请勿在公开报告中提交动态码密钥、隐私条目、机密截图、仓库令牌或签名密钥。存在可利用漏洞时优先通过私密渠道联系维护者。
