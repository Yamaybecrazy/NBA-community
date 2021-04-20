package com.nba.community.service;

import com.nba.community.dao.UserMapper;
import com.nba.community.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    @Autowired
    private UserMapper userMapper;

    public User fidUserById(int id){
        return  userMapper.selectById(id);
    }
}
