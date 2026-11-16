package com.kousen.cert.controller;

import com.kousen.cert.analytics.service.AnalyticsService;
import com.kousen.cert.model.CertificateRequest;
import com.kousen.cert.service.QrCodeGenerator;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.aMapWithSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * One test per @ExceptionHandler, driven through a tiny test-only controller so each
 * exception type can be raised deterministically.
 */
@WebMvcTest(controllers = GlobalExceptionHandlerTest.ThrowingController.class)
@Import({GlobalExceptionHandlerTest.ThrowingController.class, GlobalExceptionHandler.class})
@ActiveProfiles("test")
class GlobalExceptionHandlerTest {

    private static final String VALIDATION_TYPE = "https://api.certificate-service.com/errors/validation";

    @Autowired
    private MockMvc mockMvc;

    // Required by beans the MVC slice loads (ApiTrackingInterceptor, CertificateConfig)
    @MockitoBean
    private AnalyticsService analyticsService;
    @MockitoBean
    private QrCodeGenerator qrCodeGenerator;

    record Payload(@NotBlank(message = "name is required") String name,
                   @Email(message = "bad email") String email) {}

    @RestController
    static class ThrowingController {
        private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

        @PostMapping("/test/body")
        String body(@Valid @RequestBody Payload payload) {
            return "ok";
        }

        @GetMapping("/test/constraint")
        String constraint() {
            throw new ConstraintViolationException(validator.validate(new Payload("", "not-an-email")));
        }

        @GetMapping("/test/validation")
        String validation() {
            throw new CertificateRequest.ValidationException("bookTitle", "Unknown book");
        }

        @GetMapping("/test/illegal")
        String illegal() {
            throw new IllegalArgumentException("bad input");
        }
    }

    @Test
    void shouldMapInvalidRequestBodyToFieldErrors() throws Exception {
        mockMvc.perform(post("/test/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\", \"email\": \"nope\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validation Error"))
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.type").value(VALIDATION_TYPE))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.errors", aMapWithSize(2)))
                .andExpect(jsonPath("$.errors.name").value("name is required"))
                .andExpect(jsonPath("$.errors.email").value("bad email"));
    }

    @Test
    void shouldMapConstraintViolationsToLeafFieldNames() throws Exception {
        mockMvc.perform(get("/test/constraint"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Error"))
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.type").value(VALIDATION_TYPE))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.errors", aMapWithSize(2)))
                .andExpect(jsonPath("$.errors.name").value("name is required"))
                .andExpect(jsonPath("$.errors.email").value("bad email"));
    }

    @Test
    void shouldMapCustomValidationExceptionToItsField() throws Exception {
        mockMvc.perform(get("/test/validation"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Error"))
                .andExpect(jsonPath("$.detail").value("Unknown book"))
                .andExpect(jsonPath("$.type").value(VALIDATION_TYPE))
                .andExpect(jsonPath("$.errors.bookTitle").value("Unknown book"));
    }

    @Test
    void shouldMapIllegalArgumentToBadRequest() throws Exception {
        mockMvc.perform(get("/test/illegal"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").value("bad input"))
                .andExpect(jsonPath("$.type").value("https://api.certificate-service.com/errors/bad-request"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.errors").doesNotExist());
    }
}
