```bash
mkdir -p /home/tools/reranker/models/bge-reranker-base

docker run --rm \
  -v /home/tools/reranker/models:/models \
  alpine:latest \
  sh -c "
    apk add --no-cache wget &&
    cd /models/bge-reranker-base &&
    wget -c https://hf-mirror.com/BAAI/bge-reranker-base/resolve/main/config.json &&
    wget -c https://hf-mirror.com/BAAI/bge-reranker-base/resolve/main/tokenizer.json &&
    wget -c https://hf-mirror.com/BAAI/bge-reranker-base/resolve/main/tokenizer_config.json &&
    wget -c https://hf-mirror.com/BAAI/bge-reranker-base/resolve/main/model.safetensors
  "
```
# 下载后检查：
```bash
ls -lh /home/tools/reranker/models/bge-reranker-base/
```
如果出现以下内容（这个是简化版，是1.1G的）：
```bash
总计 1.1G
-rw-r--r--. 1 root root  799  7月18日 14:50 config.json
-rw-r--r--. 1 root root 1.1G  7月18日 14:52 model.safetensors
-rw-r--r--. 1 root root  443  7月18日 14:50 tokenizer_config.json
-rw-r--r--. 1 root root  17M  7月18日 14:50 tokenizer.json
```
则说明下载成功。
应看到 model.safetensors（约 1GB）。

# 启动服务（修正端口映射）
```bash
podman run -d \
  --name reranker \
  --restart unless-stopped \
  -p 3000:80 \
  --memory 6g \
  --memory-swap 8g \
  --security-opt label:disable \
  -v /home/tools/reranker/models:/models:ro \
  ghcr.io/huggingface/text-embeddings-inference:cpu-latest \
  --model-id /models/bge-reranker-base \
  --dtype float32 \
  --max-batch-tokens 4096
```
# 等待服务就绪
```bash
docker logs -f reranker
```
看到类似 Starting HTTP server: 0.0.0.0:80 和 Ready 即启动完成。

# 测试（字段名必须用 texts）
```bash
curl -X POST http://localhost:3000/rerank \
  -H "Content-Type: application/json" \
  -d '{
    "query": "Reranker 是做什么的",
    "texts": [
      "Reranker 是一种对检索结果进行重排序的技术",
      "Ollama 是一个本地运行大模型的工具",
      "今天天气很好，适合出去散步"
    ]
  }'
```
预期返回（带分数的排序结果）：
```bash
[
  {"index":0, "score":2.418, ...},
  {"index":1, "score":-0.524, ...},
  {"index":2, "score":-3.112, ...}
]
```
# 检查日志是否启动成功
```bash
docker logs -f reranker
```