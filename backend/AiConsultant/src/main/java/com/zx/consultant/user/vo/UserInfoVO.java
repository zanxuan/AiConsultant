package com.zx.consultant.user.vo;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class UserInfoVO {

    private Long userId;

    private String nickname;
}

