package com.customer.repository;

import com.customer.entity.Agent;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AgentMapper extends BaseMapper<Agent> {
}
