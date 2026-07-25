package com.zx.consultant.user.service.impl;


import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import com.zx.consultant.user.mapper.UserMapper;
import com.zx.consultant.user.entity.User;
import com.zx.consultant.common.exception.AccountNotFoundException;
import com.zx.consultant.common.exception.PasswordErrorException;
import com.zx.consultant.user.dto.LoginRequest;
import org.springframework.util.DigestUtils;
import com.zx.consultant.common.constant.MessageConstant;
import com.zx.consultant.user.service.UserService;
import lombok.extern.slf4j.Slf4j;


/**
 * UserServiceImpl
 */
@Service
@Slf4j
public class UserServiceImpl implements UserService {

    @Autowired
    private UserMapper userMapper;
   

    /**
     * 用户登录
     * @param request
     * @return
     */
    
    @Transactional(rollbackFor = Exception.class)
    public User login(LoginRequest request) {
        String username = request.getUsername();
        String password = request.getPassword();

        //1、根据用户名查询数据库中的数据
        User user = userMapper.getByUsername(username);

        //2、处理各种异常情况（用户名不存在、密码不对、账号被锁定）
        if (user == null) {
            //账号不存在
            throw new AccountNotFoundException(MessageConstant.ACCOUNT_NOT_FOUND);
        }

        //密码比对
        //进行md5加密，然后再进行比对
        password = DigestUtils.md5DigestAsHex(password.getBytes());
        log.info("进行密码比对：password:{},user.getPassword():{}", password,user.getPassword());

        if (!password.equals(user.getPassword())) {
            //密码错误
            throw new PasswordErrorException(MessageConstant.PASSWORD_ERROR);
        }

        //3、返回实体对象
        return user;
    }


    public User getCurrentUser(Long currentId) {
        return userMapper.getById(currentId);
    }

  
}