package com.kousen.cert.controller;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HomeControllerTest {

    @Test
    void shouldRedirectRootToStaticIndexPage() {
        assertThat(new HomeController().home().getUrl()).isEqualTo("/index.html");
    }
}
