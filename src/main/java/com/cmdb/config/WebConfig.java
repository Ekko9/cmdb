package com.cmdb.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final AuthInterceptor authInterceptor;
    public WebConfig(AuthInterceptor authInterceptor) { this.authInterceptor = authInterceptor; }
    @Override public void addInterceptors(InterceptorRegistry registry) { registry.addInterceptor(authInterceptor); }
    @Override public void addCorsMappings(CorsRegistry registry) { registry.addMapping("/api/**").allowedOrigins("*").allowedMethods("*").allowedHeaders("*"); }
    @Override public void addResourceHandlers(ResourceHandlerRegistry registry) { registry.addResourceHandler("/**").addResourceLocations("classpath:/static/"); }
}
