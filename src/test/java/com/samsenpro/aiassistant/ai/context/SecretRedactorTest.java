package com.samsenpro.aiassistant.ai.context;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class SecretRedactorTest {

    private final SecretRedactor redactor = new SecretRedactor();

    @ParameterizedTest
    @ValueSource(strings = {
            "String key = \"sk-proj-abcdefghijklmnopqrstuvwxyz012345\";",
            "token = eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U",
            "aws = AKIAIOSFODNN7EXAMPLE",
            "gh = ghp_abcdefghijklmnopqrstuvwxyz0123456789AB",
            "url = \"jdbc:postgresql://admin:SuperSecret123@db:5432/app\"",
            "db.password = \"hunter2hunter2\"",
            "API_KEY: 'abcd1234efgh'"
    })
    void masksCommonSecrets(String line) {
        SecretRedactor.Redaction redaction = redactor.redact(line);

        assertThat(redaction.count()).isGreaterThanOrEqualTo(1);
        assertThat(redaction.text()).contains(SecretRedactor.MASK)
                .doesNotContain("abcdefghijklmnopqrstuvwxyz012345")
                .doesNotContain("SuperSecret123")
                .doesNotContain("hunter2hunter2")
                .doesNotContain("AKIAIOSFODNN7EXAMPLE");
    }

    @Test
    void keepsTheUserOfConnectionStringsAndTheNameOfAssignments() {
        assertThat(redactor.redact("postgres://admin:s3cret@host/db").text())
                .isEqualTo("postgres://admin:[REDACTED]@host/db");
        assertThat(redactor.redact("String password = \"hunter22\";").text())
                .isEqualTo("String password = \"[REDACTED]\";");
    }

    @Test
    void doesNotTouchCodeThatOnlyMentionsSecrets() {
        String code = """
                String password = request.getPassword();
                if (passwordEncoder.matches(password, user.getPasswordHash())) {
                    return tokenService.issue(user);
                }
                """;

        SecretRedactor.Redaction redaction = redactor.redact(code);

        assertThat(redaction.count()).isZero();
        assertThat(redaction.text()).isEqualTo(code);
    }

    @Test
    void privateKeyBlocksKeepTheNumberOfLines() {
        String code = """
                line 1
                -----BEGIN RSA PRIVATE KEY-----
                MIIEowIBAAKCAQEA
                abcdef
                -----END RSA PRIVATE KEY-----
                line 6
                """;

        SecretRedactor.Redaction redaction = redactor.redact(code);

        assertThat(redaction.count()).isEqualTo(1);
        assertThat(redaction.text()).doesNotContain("MIIEowIBAAKCAQEA");
        assertThat(redaction.text().lines().count()).isEqualTo(code.lines().count());
        assertThat(redaction.text().lines().toList().get(5)).isEqualTo("line 6");
    }
}
