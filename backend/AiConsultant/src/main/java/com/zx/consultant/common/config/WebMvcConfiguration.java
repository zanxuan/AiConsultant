package com.zx.consultant.common.config;


import com.zx.consultant.common.interceptor.JwtTokenUserInterceptor;
import com.zx.consultant.common.utils.JacksonObjectMapper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;


import java.util.List;

/**
 * 配置类，注册web层相关组件
 */
@Configuration
@Slf4j
public class WebMvcConfiguration extends WebMvcConfigurationSupport {

  

    @Autowired
    private JwtTokenUserInterceptor jwtTokenUserInterceptor;

    /**
     * 注册自定义拦截器
     *
     * @param registry
     */
    //InterceptorRegistry（拦截器注册器）
    protected void addInterceptors(InterceptorRegistry registry) {
        log.info("开始注册自定义拦截器...");

        registry.addInterceptor(jwtTokenUserInterceptor)
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns("/api/v1/auth/login")
                .excludePathPatterns("/api/v1/auth/logout");//排除路径
    }

  

    /**
     * 设置静态资源映射
     * @param registry
     */
    /* 
    protected void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/doc.html")
                .addResourceLocations("classpath:/META-INF/resources/");
        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/");


        registry.addResourceHandler("/files/**")
                .addResourceLocations("classpath:/static/files/");
    }*/

    /**
     * 拓展mvc框架的消息转换器
     * @param converters
     */

    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        log.info("开启拓展消息转换器......");
        //创建一个消息转化器对象
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        //设置对象转换器，可以将java对象转化为json字符串
        converter.setObjectMapper(new JacksonObjectMapper());
        //将我们自己的转换器放入springmvc框架的容器里
        converters.add(0,converter);


    }
}
