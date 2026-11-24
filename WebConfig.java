package com.homestay.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoginInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/login", "/register", "/api/user/login", "/api/user/register", "/api/upload",
                        "/api/house/list", "/api/house/detail/**", "/api/house/comments/**",
                        "/api/house/disabled-dates/**",
                        "/", "/house/list", "/house/detail/**",
                        "/css/**", "/js/**", "/images/**", "/webjars/**",
                        "/doc.html", "/v2/api-docs/**", "/swagger-resources/**"
                );
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/upload/**")
                .addResourceLocations("file:D:/upload/homestay/");
    }
}
