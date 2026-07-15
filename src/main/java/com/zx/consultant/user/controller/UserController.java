package com.zx.consultant.user.controller;

import com.zx.consultant.common.result.Result;
import com.zx.consultant.common.utils.BaseContext;
import com.zx.consultant.user.dto.UserUpdateRequest;
import com.zx.consultant.user.entity.User;
import com.zx.consultant.user.service.UserService;
import com.zx.consultant.user.vo.UserInfoVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 个人信息接口
 */
@RestController
@Slf4j
@RequestMapping("/api/v1/users")
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * 获取当前用户信息
     */
    @GetMapping("/me")
    public Result<UserInfoVO> getCurrentUser() {

        log.info("获取当前用户信息:{}", BaseContext.getCurrentId());
        User user = userService.getCurrentUser(BaseContext.getCurrentId());
        UserInfoVO userInfoVO = UserInfoVO.builder()
                                .userId(user.getId())
                                .nickname(user.getNickname())
                                .build();
        return Result.success(userInfoVO);
    }

    /**
     * 修改个人信息
     */
    @PutMapping("/me")
    public Result<UserInfoVO> updateCurrentUser(@RequestBody UserUpdateRequest request) {
        // TODO: 实现修改个人信息逻辑
        return Result.success();
    }
}
