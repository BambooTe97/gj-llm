package com.gj.llm.rag.service;

import com.gj.llm.rag.config.RagProperties;
import com.gj.llm.rag.service.impl.RetrievalServiceImpl;
import com.gj.llm.reranker.service.RerankerService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;


/**
 * {@link RetrievalServiceImpl} 门面终检(数据可见域)测试 —— 红线的最后一道防线。
 *
 * <p>fail-closed 语义:userId 为 null 或可见集为空 ⇒ 检索直接返回 empty,
 * {@code HybridSearcher} 零调用(绝不无过滤放行);正常用户目标库与可见集求交集。</p>
 */
class RetrievalServiceImplVisibilityTest {

    private final HybridSearcher hybridSearcher = mock(HybridSearcher.class);
    private final RerankerService rerankerService = mock(RerankerService.class);
    private final QueryRewriter queryRewriter = mock(QueryRewriter.class);
    private final DatasetService datasetService = mock(DatasetService.class);
    private final DatasetVisibleService datasetVisibleService = mock(DatasetVisibleService.class);

    private final RetrievalServiceImpl service = new RetrievalServiceImpl(
            hybridSearcher, rerankerService, queryRewriter, datasetService,
            new RagProperties(), datasetVisibleService);

    @Test
    void retrieve_nullUser_failClosed_noSearcherCall() {
        // visibleDatasetIds(null) 契约即空集(实现保证),此处直接验证门面终检行为
        when(datasetVisibleService.visibleDatasetIds(null)).thenReturn(Set.of());
        RetrievalResult r = service.retrieve("问题", List.of(1L, 2L), null);
        assertThat(r.context()).isEmpty();
        assertThat(r.references()).isEmpty();
        verifyNoInteractions(hybridSearcher, queryRewriter);
    }

    @Test
    void retrieve_emptyVisibleSet_failClosed_noSearcherCall() {
        when(datasetVisibleService.visibleDatasetIds(7L)).thenReturn(Set.of());
        RetrievalResult r = service.retrieve("问题", List.of(1L, 2L), 7L);
        assertThat(r.context()).isEmpty();
        assertThat(r.references()).isEmpty();
        verifyNoInteractions(hybridSearcher);
        verify(datasetService, never()).getById(anyLong());
    }

    @Test
    void retrieve_visibleSetIntersects_targetLibraries() {
        // 目标 [1,2,2],可见仅 {2}:1 被剔除、2 去重,只查 2
        when(datasetVisibleService.visibleDatasetIds(7L)).thenReturn(Set.of(2L));
        when(datasetService.getById(2L)).thenReturn(null); // 库不存在也要安全跳过
        RetrievalResult r = service.retrieve("问题", List.of(1L, 2L, 2L), 7L);
        assertThat(r.context()).isEmpty();
        verify(datasetService).getById(2L);
        verify(datasetService, never()).getById(1L);
        verifyNoInteractions(hybridSearcher);
    }

    @Test
    void retrievePerSubQueries_nullUser_failClosed() {
        when(datasetVisibleService.visibleDatasetIds(null)).thenReturn(Set.of());
        RetrievalResult r = service.retrievePerSubQueries("原问题", List.of("子问题"), List.of(1L), null);
        assertThat(r.context()).isEmpty();
        verifyNoInteractions(hybridSearcher, queryRewriter);
    }
}
