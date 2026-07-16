# Elasticsearch 安装指南（Docker）

## 环境要求

- CentOS 7+ / Ubuntu 18+
- Docker 已安装
- 可用内存 ≥ 4G（ES 建议 2G 堆内存）

## 一键安装

```bash
# 1. 内核参数（ES 硬性要求）
sudo sysctl -w vm.max_map_count=262144
echo "vm.max_map_count=262144" | sudo tee -a /etc/sysctl.conf

# 2. 数据目录（ES 容器内 UID=1000）
sudo mkdir -p /data/elasticsearch
sudo chown -R 1000:1000 /data/elasticsearch
sudo chmod 750 /data/elasticsearch

# 3. 启动 ES（单节点，关闭安全认证）
docker run -d \
  --name elasticsearch \
  --net host \
  -e "discovery.type=single-node" \
  -e "xpack.security.enabled=false" \
  -e "ES_JAVA_OPTS=-Xms2g -Xmx2g" \
  -v /data/elasticsearch:/usr/share/elasticsearch/data \
  docker.elastic.co/elasticsearch/elasticsearch:8.15.3

# 4. 安装 IK 中文分词器
docker exec -it elasticsearch ./bin/elasticsearch-plugin install \
  https://get.infini.cloud/elasticsearch/analysis-ik/8.15.3

# 5. 重启 ES
docker restart elasticsearch

# 6. 验证（等待 10s 后执行）
curl http://localhost:9200
```

## 安装后配置

### CentOS 7 防火墙

```bash
# 开放 9200 端口（外部服务需要访问）
sudo firewall-cmd --zone=public --add-port=9200/tcp --permanent
sudo firewall-cmd --reload

# 如果 SELinux 拦截
sudo setenforce 0
```

### 验证安装

```bash
# 1. ES 是否正常运行（返回 JSON 即正常）
curl http://localhost:9200

# 2. IK 分词器是否安装成功
curl http://localhost:9200/_cat/plugins
# 应返回: localhost.localdomain analysis-ik 8.15.3

# 3. 测试 IK 分词效果
curl -X POST "http://localhost:9200/_analyze" \
  -H "Content-Type: application/json" \
  -d '{"analyzer": "ik_max_word", "text": "向量检索召回率优化"}'

# 4. 外部机器验证（替换 IP 为虚拟机实际地址）
curl http://<虚拟机IP>:9200/_cat/plugins
```

> **注意**：外部访问必须用虚拟机 IP（如 `192.168.40.130`），不能用 `localhost`。<br>
> ES 返回的 `name: "localhost.localdomain"` 只是节点名，与访问地址无关，可以忽略。

---

## 常用命令

```bash
# 查看日志
docker logs elasticsearch --tail 100

# 查看索引列表
curl http://localhost:9200/_cat/indices?v

# 删除指定索引
curl -X DELETE "http://localhost:9200/rag_<collection_name>"

# 查看 IK 分词效果
curl -X POST "http://localhost:9200/_analyze" \
  -H "Content-Type: application/json" \
  -d '{"analyzer": "ik_max_word", "text": "中华人民共和国"}'

# 重启 ES
docker restart elasticsearch

# 停止并删除 ES（数据卷保留）
docker stop elasticsearch && docker rm elasticsearch
```

## 自定义 IK 词典（可选）

如需要添加专业术语/自定义词库：

```bash
# 进入容器
docker exec -it elasticsearch bash

# 编辑自定义词典
vi /usr/share/elasticsearch/config/analysis-ik/custom.dic
# 一行一个词，例如：
# 向量检索
# 大语言模型
# 召回率

# 退出并重启
exit
docker restart elasticsearch
```

## 应用配置

在 `application-ai.yml` 中：

```yaml
gj:
  llm:
    es:
      host: <ES服务器IP>
      port: 9200
      index-prefix: rag_
      embedding-dimension: 1024
      shards: 1
      replicas: 0
```

## 应用对接步骤

### 1. 确认配置指向虚拟机 IP

```yaml
gj:
  llm:
    es:
      host: 192.168.40.130    # 替换为虚拟机实际 IP
      port: 9200
```

### 2. 重启 Spring Boot 应用

### 3. 重建知识库

ES 索引由 `EsSearchService.ensureIndexExists()` 自动创建，无需手动建索引。

- 删除旧知识库（如有）
- 创建新知识库 → 上传 PDF 文件

### 4. 验证数据写入

```bash
# 查看 ES 索引列表
curl http://<虚拟机IP>:9200/_cat/indices?v

# 应看到 rag_<collection_name> 索引，查看文档数
curl http://<虚拟机IP>:9200/rag_<collection_name>/_count
```

### 5. 测试检索

调用 `POST /api/v1/datasets/{datasetId}/test` 或通过聊天接口验证 RAG 效果。

### 6. 检索数据流

```
用户查询
  → Spring Boot 应用
  → Ollama BGE-M3 嵌入（加指令前缀）
  → ES hybrid retriever:
      ├── BM25 match（ik_max_word 分词）
      ├── KNN dense_vector（cosine 相似度）
      └── RRF 融合排序
  → 返回 top-K → LLM 生成回答
```

## 常见问题

| 问题 | 原因 | 解决 |
|------|------|------|
| Exited (1) / node.lock | 目录权限不对 | `chown -R 1000:1000 /data/elasticsearch` |
| max_map_count 不足 | 内核参数太低 | `sysctl -w vm.max_map_count=262144` |
| Exited (137) | 内存不足被 OOM Killer 杀掉 | 降低 `-Xmx` 或增加宿主机内存 |
