package com.gj.llm.agent.doc;

import com.gj.llm.agent.vector.DynamicVectorStoreManager;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;

import java.util.List;

public class AgentPdfIngestionService {
    private final DynamicVectorStoreManager storeManager;
    private final TokenTextSplitter textSplitter;

    public AgentPdfIngestionService(DynamicVectorStoreManager storeManager, TokenTextSplitter textSplitter) {
        this.storeManager = storeManager;
        this.textSplitter = textSplitter;
    }

    public void ingestPdf(String type, Resource resource) {
        // 1. 根据类型获取专属的 VectorStore (如果不存在会自动创建集合)
        VectorStore vectorStore = storeManager.getVectorStore(type);

        // 2. 读取 PDF
        DocumentReader reader = new PagePdfDocumentReader(resource);
        List<Document> documents = reader.get();

        // 3. 元数据增强 (重要！)
        // 我们可以给每个文档块打上标签，方便以后做更精细的过滤
        documents.forEach(doc -> {
            doc.getMetadata().put("type", type); // 标记类型
            doc.getMetadata().put("source", resource.getFilename());
        });

        // 4. 切分
        List<Document> splits = textSplitter.apply(documents);

        // 5. 存入对应的集合
        vectorStore.add(splits);
        System.out.println("成功将文件存入集合: collection_" + type);
    }
}
