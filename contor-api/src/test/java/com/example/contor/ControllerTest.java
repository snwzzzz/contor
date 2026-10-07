package com.example.contor;

import com.example.contor.config.SecurityConfig;
import com.example.contor.security.SetupState;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@code @WebMvcTest} slice with the real {@link SecurityConfig} (CSRF, 401/403 JSON handlers, authorization rules).
 * Use {@link TestAuth} for authenticated requests and {@code csrf()} for mutating ones.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@WebMvcTest
@Import({SecurityConfig.class, ControllerTestConfiguration.class})
@MockitoBean(types = SetupState.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public @interface ControllerTest {

    @AliasFor(annotation = WebMvcTest.class, attribute = "controllers")
    Class<?>[] value() default {};
}
