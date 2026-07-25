package com.zx.consultant.user.dto;

import lombok.Data;

@Data
public class LoginRequest {

    private String username;

    private String password;
}

/**因为登录请求和 User 实体不是一回事 */

