package com.zx.consultant.common.interceptor;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import com.zx.consultant.common.utils.JwtProperties;
import com.zx.consultant.common.utils.JwtUtil;
import com.zx.consultant.common.utils.BaseContext;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


@Component
@Slf4j
public class JwtTokenUserInterceptor implements HandlerInterceptor{

    @Autowired
    private JwtProperties jwtProperties;

    /**
     * 校验jwt
     *请求到达 Controller 之前
     * @param request
     * @param response
     * @param handler
     * @return
     * @throws Exception
     */
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //判断当前拦截到的是Controller的方法还是其他资源
        System.out.println("======> 拦截器生效了！当前请求路径: " + request.getRequestURI());
        
        if (!(handler instanceof HandlerMethod)) {//检查 “左边的对象” 是不是 “右边的类”（或其家族）创造出来的
            //HandlerMethod 是 Spring MVC 框架中的一个核心类，用于封装 “Controller 中的具体方法”（包含方法所属的类、方法对象、参数等信息）。
            //当前拦截到的不是动态方法，直接放行
            return true;
        }

        //1、从请求头中获取令牌
        String token = request.getHeader(jwtProperties.getUserTokenName());
        if (token != null && token.startsWith(JwtUtil.BEARER_PREFIX)) {
            token = token.substring(JwtUtil.BEARER_PREFIX.length());
        }

        //2、校验令牌
        try {
            log.info("jwt校验:{}", token);
            //jwtProperties.getAdminSecretKey() 获取的密钥，验证令牌的签名是否被篡改;
            // 如果令牌被修改，签名会不匹配，直接抛出异常
            Claims claims = JwtUtil.parseJWT(jwtProperties.getUserSecretKey(), token);
            Long userId = Long.valueOf(claims.get("userId").toString());
            log.info("当前用户id：{}", userId);
            //将员工的id封装进入thread
            BaseContext.setCurrentId(userId);
            //3、通过，放行
            return true;
        } catch (Exception ex) {
            //4、不通过，响应401状态码
            response.setStatus(401);
            return false;
        }
    }
}
