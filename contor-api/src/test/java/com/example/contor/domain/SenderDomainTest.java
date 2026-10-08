package com.example.contor.domain;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class SenderDomainTest {

    @Test
    public void can_create_valid_domain_in_lower_case() {
        assertThat(new SenderDomain("Mail.Siemens.COM").value()).isEqualTo("mail.siemens.com");
        assertThat(new SenderDomain("bank-austria.at").value()).isEqualTo("bank-austria.at");
    }

    @Test
    public void null_and_empty_domain_throws_error() {
        assertThatThrownBy(() -> new SenderDomain(null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SenderDomain("")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void hosts_that_are_not_public_domain_names_are_rejected() {
        assertThatThrownBy(() -> new SenderDomain("localhost")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SenderDomain("127.0.0.1")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SenderDomain("example.com:8080")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SenderDomain("evil.com/path")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SenderDomain("-bad.com")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SenderDomain("a".repeat(250) + ".com")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void domain_of_an_address_is_the_part_after_the_at_sign() {
        assertThat(SenderDomain.ofAddress("Talent.Acquisition@Siemens.com")).contains(new SenderDomain("siemens.com"));
        assertThat(SenderDomain.ofAddress("not-an-address")).isEmpty();
        assertThat(SenderDomain.ofAddress(null)).isEmpty();
    }

    @Test
    public void parent_is_the_last_two_labels() {
        assertThat(new SenderDomain("news.mail.microsoft.com").parent()).contains(new SenderDomain("microsoft.com"));
        assertThat(new SenderDomain("microsoft.com").parent()).isEmpty();
    }
}
