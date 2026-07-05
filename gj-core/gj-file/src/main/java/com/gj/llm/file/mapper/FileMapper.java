package com.gj.llm.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gj.llm.file.entity.FileEntity;

/**
 * 文件记录 Mapper —— 由 {@code MyBatisGlobalConfig @MapperScan("com.gj.llm")} 统一扫描注册。
 *
 * @author gj-llm
 */
public interface FileMapper extends BaseMapper<FileEntity> {
}
