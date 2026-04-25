# 动态数据集适配指南

## 概述

通过配置驱动的方式，支持任意数量的异构数据集导入，无需修改代码。

## 快速开始

### 1. 添加新数据集

编辑 `src/main/resources/dataset-mappings.json`，添加新的数据集定义：

```json
{
  "datasets": [
    {
      "id": "dataset_004",
      "name": "新业务线素材库",
      "description": "第四方系统的素材数据",
      "filePattern": "new_business_*.xls",
      "fieldMappings": {
        "素材ID": {"target": "source_id", "type": "string"},
        "名称": {"target": "title", "type": "string"},
        "创建时间": {"target": "uploaded_at", "type": "unix_timestamp"},
        "大小": {"target": "file_size_bytes", "type": "bytes"},
        "状态": {"target": "status", "type": "status"},
        "标签列表": {"target": "tags", "type": "tags", "separator": "|"}
      }
    }
  ]
}
```

### 2. 支持的字段类型

| 类型 | 说明 | 示例 |
|------|------|------|
| `string` | 直接字符串映射 | `"标题"` → `title` |
| `integer` | 整数类型 | `120` → `duration_sec` |
| `bytes` | 字节大小（数字） | `123456789` → `file_size_bytes` |
| `size_with_unit` | 带单位的大小字符串 | `"63.76MB"` → `66857206` bytes |
| `excel_date` | Excel 日期序列号 | `45540` → `2024-09-01` |
| `unix_timestamp` | Unix 时间戳（秒） | `1715336373` → `2024-05-10` |
| `status` | 状态归一化 | `"已通过"/"approved"` → `"approved"` |
| `tags` | 分隔符分隔的标签 | `"节日;促销"` → `["节日", "促销"]` |
| `tags_python_list` | Python list 格式 | `"['品牌', '测评']"` → `["品牌", "测评"]` |
| `platform` | 平台归一化 | `"千川"/"qianchuan"` → `"qianchuan"` |

### 3. 目标字段列表

| target | 数据库字段 | 说明 |
|--------|-----------|------|
| `source_id` | source_id | 原始数据集ID |
| `title` | title | 标题 |
| `uploader` | uploader | 上传人 |
| `uploaded_at` | uploaded_at | 上传时间 |
| `file_size_bytes` | file_size_bytes | 文件大小（字节） |
| `status` | status | 状态（pending/approved/rejected） |
| `tags` | tags | 标签数组 |
| `city` | city | 城市 |
| `platform` | platform | 平台 |
| `reviewer` | reviewer | 审核人 |
| `remark` | remark | 备注 |
| `resolution` | resolution | 分辨率 |
| `duration_sec` | duration_sec | 时长（秒） |

### 4. 使用动态适配器

```java
@Autowired
private DatasetMappingLoader mappingLoader;

public void importDataset(String fileName, InputStream fileContent) {
    // 根据文件名自动匹配数据集定义
    DatasetMappingConfig.DatasetDefinition definition = mappingLoader.getByFileName(fileName);
    
    if (definition == null) {
        throw new IllegalArgumentException("Unknown dataset: " + fileName);
    }
    
    // 创建动态适配器
    DynamicDatasetAdapter adapter = new DynamicDatasetAdapter(definition);
    
    // 解析Excel并转换
    List<Map<String, Object>> rows = ExcelReader.read(fileContent);
    for (Map<String, Object> row : rows) {
        Asset asset = adapter.convert(row);
        // 保存到数据库
        assetMapper.upsert(asset);
    }
}
```

## 扩展指南

### 添加新的字段类型

1. 在 `DynamicDatasetAdapter` 中添加新的 case 分支
2. 在 `EtlNormalizers` 中添加对应的归一化方法

### 添加新的目标字段

1. 在数据库表中添加新列
2. 在 `Asset` 实体中添加新字段
3. 在配置文件中使用新字段

## 示例：适配新的数据源

假设有一个新的营销系统，数据格式如下：

| 字段名 | 类型 | 示例值 |
|--------|------|--------|
| material_code | string | MAT001 |
| material_name | string | 产品介绍视频 |
| creator | string | 王五 |
| create_ts | number | 1704067200 |
| size_kb | number | 10240 |
| audit_result | string | PASS |
| keywords | string | 产品\|宣传\|新品 |

配置文件：

```json
{
  "id": "marketing_001",
  "name": "营销系统素材",
  "filePattern": "marketing_*.xlsx",
  "fieldMappings": {
    "material_code": {"target": "source_id", "type": "string"},
    "material_name": {"target": "title", "type": "string"},
    "creator": {"target": "uploader", "type": "string"},
    "create_ts": {"target": "uploaded_at", "type": "unix_timestamp"},
    "size_kb": {"target": "file_size_bytes", "type": "bytes"},
    "audit_result": {"target": "status", "type": "status"},
    "keywords": {"target": "tags", "type": "tags", "separator": "|"}
  }
}
```

**注意**：`size_kb` 需要转换为 bytes。如果原始数据是 KB，需要在归一化时乘以 1024。
