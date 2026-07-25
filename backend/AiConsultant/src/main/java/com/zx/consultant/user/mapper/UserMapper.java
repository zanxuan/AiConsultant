package com.zx.consultant.user.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import com.zx.consultant.user.entity.User;
@Mapper
public interface UserMapper {


     /**
     * 根据username获取信息
     * @param username
     * @return
     */
     @Select("select * from user where username = #{username}")
     User getByUsername(String username);

       /**
     * 根据ID获取信息
     * @param id
     * @return
     */
    @Select("select * from user where id = #{id}")
    User getById(Long id);
    
}
