#!/bin/bash
# API 性能压测脚本
# 使用 ab (Apache Bench) 或 curl 进行简单压测

set -e

BASE_URL="${BASE_URL:-http://localhost:8080}"
API_KEY="${API_KEY:-dev-api-key-001}"
REQUESTS="${REQUESTS:-100}"
CONCURRENCY="${CONCURRENCY:-10}"

echo "=========================================="
echo "API Performance Benchmark"
echo "=========================================="
echo "Base URL: $BASE_URL"
echo "API Key: $API_KEY"
echo "Requests: $REQUESTS"
echo "Concurrency: $CONCURRENCY"
echo ""

# 检查服务是否运行
echo "Checking service health..."
HEALTH=$(curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/actuator/health" 2>/dev/null || echo "000")
if [ "$HEALTH" != "200" ]; then
    echo "ERROR: Service not available (HTTP $HEALTH)"
    echo "Please start the service first: bash start-docker.sh"
    exit 1
fi
echo "Service is healthy ✓"
echo ""

# 测试 1: 列表 API (OFFSET 分页)
echo "--- Test 1: List API (OFFSET Pagination) ---"
if command -v ab &> /dev/null; then
    ab -n $REQUESTS -c $CONCURRENCY -H "X-API-Key: $API_KEY" \
        "$BASE_URL/api/v1/assets?page=1&page_size=20" 2>&1 | grep -E "(Requests per second|Time per request|Transfer rate)"
else
    echo "ab (Apache Bench) not installed, using curl..."
    START=$(date +%s%N)
    for i in $(seq 1 $REQUESTS); do
        curl -s -H "X-API-Key: $API_KEY" "$BASE_URL/api/v1/assets?page=1&page_size=20" > /dev/null
    done
    END=$(date +%s%N)
    DURATION=$(( ($END - $START) / 1000000 ))
    QPS=$(echo "scale=2; $REQUESTS * 1000 / $DURATION" | bc)
    echo "Total: ${DURATION}ms, QPS: $QPS"
fi
echo ""

# 测试 2: Cursor 分页
echo "--- Test 2: Cursor Pagination ---"
if command -v ab &> /dev/null; then
    ab -n $REQUESTS -c $CONCURRENCY -H "X-API-Key: $API_KEY" \
        "$BASE_URL/api/v1/assets/cursor?page_size=20" 2>&1 | grep -E "(Requests per second|Time per request|Transfer rate)"
else
    START=$(date +%s%N)
    for i in $(seq 1 $REQUESTS); do
        curl -s -H "X-API-Key: $API_KEY" "$BASE_URL/api/v1/assets/cursor?page_size=20" > /dev/null
    done
    END=$(date +%s%N)
    DURATION=$(( ($END - $START) / 1000000 ))
    QPS=$(echo "scale=2; $REQUESTS * 1000 / $DURATION" | bc)
    echo "Total: ${DURATION}ms, QPS: $QPS"
fi
echo ""

# 测试 3: 详情 API
echo "--- Test 3: Detail API ---"
# 获取一个 ID
FIRST_ID=$(curl -s -H "X-API-Key: $API_KEY" "$BASE_URL/api/v1/assets?page=1&page_size=1" | jq -r '.data.items[0].id' 2>/dev/null || echo "")
if [ -z "$FIRST_ID" ] || [ "$FIRST_ID" == "null" ]; then
    echo "No data found, skipping detail test"
else
    if command -v ab &> /dev/null; then
        ab -n $REQUESTS -c $CONCURRENCY -H "X-API-Key: $API_KEY" \
            "$BASE_URL/api/v1/assets/$FIRST_ID" 2>&1 | grep -E "(Requests per second|Time per request|Transfer rate)"
    else
        START=$(date +%s%N)
        for i in $(seq 1 $REQUESTS); do
            curl -s -H "X-API-Key: $API_KEY" "$BASE_URL/api/v1/assets/$FIRST_ID" > /dev/null
        done
        END=$(date +%s%N)
        DURATION=$(( ($END - $START) / 1000000 ))
        QPS=$(echo "scale=2; $REQUESTS * 1000 / $DURATION" | bc)
        echo "Total: ${DURATION}ms, QPS: $QPS"
    fi
fi
echo ""

# 测试 4: 统计 API
echo "--- Test 4: Stats API ---"
if command -v ab &> /dev/null; then
    ab -n $REQUESTS -c $CONCURRENCY -H "X-API-Key: $API_KEY" \
        "$BASE_URL/api/v1/stats/uploader-avg-size" 2>&1 | grep -E "(Requests per second|Time per request|Transfer rate)"
else
    START=$(date +%s%N)
    for i in $(seq 1 $REQUESTS); do
        curl -s -H "X-API-Key: $API_KEY" "$BASE_URL/api/v1/stats/uploader-avg-size" > /dev/null
    done
    END=$(date +%s%N)
    DURATION=$(( ($END - $START) / 1000000 ))
    QPS=$(echo "scale=2; $REQUESTS * 1000 / $DURATION" | bc)
    echo "Total: ${DURATION}ms, QPS: $QPS"
fi
echo ""

echo "=========================================="
echo "Benchmark completed!"
echo ""
echo "For more accurate results, use Apache Bench:"
echo "  brew install httpd  # macOS"
echo "  ab -n 1000 -c 50 -H 'X-API-Key: $API_KEY' $BASE_URL/api/v1/assets"
echo ""
echo "Or use wrk for more advanced testing:"
echo "  brew install wrk"
echo "  wrk -t4 -c100 -d30s -H 'X-API-Key: $API_KEY' $BASE_URL/api/v1/assets"
echo "=========================================="
