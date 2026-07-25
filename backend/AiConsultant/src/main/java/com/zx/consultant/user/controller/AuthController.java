package com.zx.consultant.user.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import com.zx.consultant.user.dto.LoginRequest;
import com.zx.consultant.user.entity.User;
import com.zx.consultant.user.service.impl.UserServiceImpl;
import com.zx.consultant.user.vo.LoginResponse;
import com.zx.consultant.user.vo.UserInfoVO;
import com.zx.consultant.common.utils.JwtUtil;
import com.zx.consultant.common.utils.JwtProperties;
import java.util.Map;
import java.util.HashMap;
import com.zx.consultant.common.constant.JwtClaimsConstant;
import com.zx.consultant.common.constant.MessageConstant;
import com.zx.consultant.common.result.Result;

import org.springframework.beans.factory.annotation.Autowired;



@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
/**
 * 用户认证
 */
public class AuthController {

    @Autowired
    private UserServiceImpl userService;
    
    @Autowired
    private JwtProperties jwtProperties;

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest request ) {
      //@RequestBody将请求体中的参数全部传递进来【JSON形式】.@RequestBody 接收数据时，其实是反序列化
      log.info("用户登录：{}", request);
      User user = userService.login(request);

      //这里不成功会在service层抛出异常
      //登录成功后，生成jwt令牌
      Map<String, Object> claims = new HashMap<>();
      claims.put(JwtClaimsConstant.USER_ID, user.getId());
      String token = JwtUtil.createJWT(
              jwtProperties.getUserSecretKey(),
              jwtProperties.getUserTtl(),
              claims);

      //封装为视图对象
      LoginResponse loginResponse = LoginResponse.builder()
              .userInfo(UserInfoVO.builder()
                      .userId(user.getId())
                      .nickname(user.getNickname())
                      .build())
              .token(token)
              .build();
      //返回Result<LoginResponse>
      log.info("登录成功：{}", loginResponse.toString());
      return Result.success(loginResponse);
    }

    /**
     * 退出登录
     * @return
     */
    @PostMapping("/logout")
    public Result<Void> logout() {
        return Result.success(MessageConstant.SUCCESS);
    }
}