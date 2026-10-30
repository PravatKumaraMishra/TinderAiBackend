package me.pravat.tinder_ai_backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("images/**")
                .addResourceLocations(java.nio.file.Path.of("src/main/resources/static/images")
                        .toAbsolutePath().toUri().toString() + "/", "classpath:/static/images/");
    }
}