package com.gj.llm.rag.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gj.llm.rag.entity.DatasetEntity;
import org.apache.ibatis.annotations.Param;

public interface DatasetMapper extends BaseMapper<DatasetEntity> {

    /**
     * 原子调整知识库计数（算术在数据库端完成，行锁保证并发安全，避免"读-改-写"丢失更新）。
     *
     * @param id           知识库 ID
     * @param docDelta     文档数增量（0=不变，负数=递减）
     * @param segmentDelta 切片数增量（0=不变，负数=递减）
     * @return 影响行数（0=知识库不存在）
     */
    int adjustCounters(@Param("id") Long id,
                       @Param("docDelta") int docDelta,
                       @Param("segmentDelta") int segmentDelta);
}
