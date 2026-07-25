package com.zx.consultant.user.service;

import com.zx.consultant.user.entity.User;
import com.zx.consultant.user.dto.LoginRequest;

public interface UserService {
    User login(LoginRequest request);
  
}
