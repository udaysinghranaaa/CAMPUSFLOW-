package com.example.campusflow_backend.support;
import com.example.campusflow_backend.security.*;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.lang.annotation.*;
@Target(ElementType.TYPE) @Retention(RetentionPolicy.RUNTIME)
@Import({SecurityConfiguration.class,SecurityErrors.class})
@MockitoBean(types={JwtDecoder.class,AccountJwtConverter.class})
public @interface SecurityWebSupport { }
