package com.customer.repository;

import com.customer.entity.CsUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CsUserMapper extends BaseMapper<CsUser> {
}
