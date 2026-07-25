package com.zx.consultant.user.entity;

import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User implements Serializable{
    private static final long serialVersionUID = 1L;

    private Long id;

    //姓名
    private String  username;

    //手机号
    private String password;

    //身份证号
    private String nickname;

    //邮箱
    private String email;

    //状态
    private Integer status;

    //头像
    private String avatar;

    //注册时间
    private LocalDateTime createTime;

    //更新时间
    private LocalDateTime updateTime;
}
