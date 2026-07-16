package com.gj.llm.rag.doc;

import com.gj.llm.rag.vector.DynamicVectorStoreManager;
import com.gj.llm.rag.vector.splitter.RecursiveCharacterTextSplitter;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentReader;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;

import java.util.List;

public class AgentPdfIngestionService {

    private static final int DEFAULT_CHUNK_SIZE = 800;
    private static final int DEFAULT_CHUNK_OVERLAP = 100;
    private static final int DEFAULT_MIN_CHUNK_LENGTH = 20;

    private final DynamicVectorStoreManager storeManager;

    public AgentPdfIngestionService(DynamicVectorStoreManager storeManager) {
        this.storeManager = storeManager;
    }

    public void ingestPdf(String type, Resource resource) {
        VectorStore vectorStore = storeManager.getVectorStore(type);

        DocumentReader reader = new PagePdfDocumentReader(resource);
        List<Document> documents = reader.get();

        documents.forEach(doc -> {
            doc.getMetadata().put("type", type);
            doc.getMetadata().put("source", resource.getFilename());
        });

        RecursiveCharacterTextSplitter splitter = new RecursiveCharacterTextSplitter(
                DEFAULT_CHUNK_SIZE, DEFAULT_CHUNK_OVERLAP, DEFAULT_MIN_CHUNK_LENGTH);
        List<Document> splits = splitter.split(documents);

        vectorStore.add(splits);
        System.out.println("成功将文件存入集合: collection_" + type);
    }
}
