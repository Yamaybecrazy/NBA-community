package com.nba.community.dao;


import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

@Repository
@Primary
public class AlphaDaoMyBatislmpl implements AlphaDao{

    @Override
    public String select() {
        return "MyBatis";
    }
}
