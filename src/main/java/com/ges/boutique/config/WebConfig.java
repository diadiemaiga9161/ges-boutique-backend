package com.ges.boutique.config;

import com.ges.boutique.role.PermissionInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final PermissionInterceptor permissionInterceptor;

    // Rôles personnalisés (Caissier...) : contrôle central des droits sur toute l'API.
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(permissionInterceptor).addPathPatterns("/api/**");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {

        // ================================
        // ANGULAR WEB
        // ================================

        registry.addViewController("/")
                .setViewName("forward:/index.html");

        // Routes Angular niveau 1
        // Exclut : mobile, api, ws, swagger, ET tous les dossiers de fichiers statiques
        registry.addViewController("/{path:(?!mobile|api|ws|swagger-ui|v3|api-docs|assets|media|svg|fonts|icons|images|favicon)[^\\.]*}")
                .setViewName("forward:/index.html");

        // Routes Angular imbriquées
        registry.addViewController("/{path:(?!mobile|api|ws|swagger-ui|v3|api-docs|assets|media|svg|fonts|icons|images|favicon)[^\\.]*}/**")
                .setViewName("forward:/index.html");


        // ================================
        // IONIC MOBILE
        // ================================

        registry.addViewController("/mobile")
                .setViewName("forward:/mobile/index.html");

        registry.addViewController("/mobile/")
                .setViewName("forward:/mobile/index.html");

        // Routes Ionic sans extension (ex: /mobile/tabs/caisse)
        registry.addViewController("/mobile/{path:[^\\.]*}")
                .setViewName("forward:/mobile/index.html");

        registry.addViewController("/mobile/**/{path:[^\\.]*}")
                .setViewName("forward:/mobile/index.html");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {

        // ================================
        // ANGULAR STATIC FILES
        // ================================

        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCachePeriod(3600);

        registry.addResourceHandler("/media/**")
                .addResourceLocations("classpath:/static/media/")
                .setCachePeriod(3600);

        registry.addResourceHandler("/images/**")
                .addResourceLocations(
                        "classpath:/static/images/",
                        "classpath:/static/assets/images/"
                )
                .setCachePeriod(3600);

        registry.addResourceHandler("/icons/**")
                .addResourceLocations(
                        "classpath:/static/icons/",
                        "classpath:/static/assets/icons/"
                )
                .setCachePeriod(3600);

        registry.addResourceHandler("/svg/**")
                .addResourceLocations(
                        "classpath:/static/svg/",
                        "classpath:/static/assets/svg/"
                )
                .setCachePeriod(3600);

        registry.addResourceHandler("/fonts/**")
                .addResourceLocations(
                        "classpath:/static/fonts/",
                        "classpath:/static/assets/fonts/"
                )
                .setCachePeriod(3600);


        // ================================
        // IONIC MOBILE STATIC FILES
        // ================================

        registry.addResourceHandler("/mobile/**")
                .addResourceLocations("classpath:/static/mobile/")
                .setCachePeriod(3600);
    }
}
