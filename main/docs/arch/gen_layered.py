"""架构分层图 — 单列纵向分层流式布局。
每层一条横幅，左侧为层标识（色块+名称），中部为组件列表，右侧为技术决策卡片。
"""
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.patches import FancyBboxPatch

plt.rcParams["font.family"] = ["Arial Unicode MS", "STHeiti", "PingFang HK", "DejaVu Sans"]
plt.rcParams["axes.unicode_minus"] = False

# ══════════════════════════════════════════════════════════════════════════════
# CANVAS
# ══════════════════════════════════════════════════════════════════════════════
W, H = 16, 20
fig, ax = plt.subplots(figsize=(W, H))
ax.set_xlim(0, W)
ax.set_ylim(0, H)
ax.axis("off")
fig.patch.set_facecolor("#ffffff")
ax.set_facecolor("#ffffff")


def rbox(ax, x, y, w, h, fc, ec="none", lw=0, alpha=1.0, r=0.15, z=2):
    ax.add_patch(FancyBboxPatch(
        (x, y), w, h, boxstyle=f"round,pad={r}",
        linewidth=lw, edgecolor=ec, facecolor=fc, alpha=alpha, zorder=z))


# ══════════════════════════════════════════════════════════════════════════════
# HEADER
# ══════════════════════════════════════════════════════════════════════════════
rbox(ax, 0.5, 18.4, W - 1.0, 1.3, "#1c2833", r=0.25, z=3)
ax.text(W/2, 19.2, "视频素材查询服务  ·  架构分层与技术决策",
        ha="center", va="center", fontsize=18, fontweight="bold", color="white", zorder=4)
ax.text(W/2, 18.75, "Spring Boot 3.3    ·    MyBatis-Plus    ·    PostgreSQL 15    ·    Vue 3    ·    Docker Compose",
        ha="center", va="center", fontsize=10, color="#aab7b8", zorder=4)


# ══════════════════════════════════════════════════════════════════════════════
# LAYERS — 从上到下：前端 → API → 服务 → 数据访问 → ETL → 存储
# ══════════════════════════════════════════════════════════════════════════════
# 每层高度 2.7，之间间隔 0.45（留给箭头）
LAYER_H = 2.6
GAP = 0.35
MARGIN_L = 0.55
MARGIN_R = 0.55
LAYER_W = W - MARGIN_L - MARGIN_R

# 分区宽度（在层内）
BADGE_W = 2.4      # 左侧色块
COMP_W  = 5.5      # 中部组件列表
CARD_PAD = 0.25    # 色块与组件间距
DEC_X_START = MARGIN_L + BADGE_W + 0.2 + COMP_W + 0.3


def draw_layer(top_y, color, tier_label, tier_name, components, decision_title, decision_points):
    """
    top_y: 层的顶部 Y 坐标
    tier_label: 英文缩写（如 API, DAL）
    tier_name: 中文名（如 API 层）
    components: [str, ...]  最多 5 项
    decision_title: 技术决策标题
    decision_points: [str, ...]  最多 3 项
    """
    y = top_y - LAYER_H

    # —— 外层阴影 + 主容器 ——————————————————————————————————————————
    rbox(ax, MARGIN_L + 0.08, y - 0.08, LAYER_W, LAYER_H,
         "#d5dbdf", alpha=0.55, r=0.18, z=2)
    rbox(ax, MARGIN_L, y, LAYER_W, LAYER_H,
         "white", ec="#e3e7ea", lw=1.0, r=0.18, z=3)

    # —— 左侧色块（层标识）————————————————————————————————————————
    bx = MARGIN_L + 0.15
    bw = BADGE_W
    rbox(ax, bx, y + 0.15, bw, LAYER_H - 0.3, color, r=0.14, z=4)
    # tier 英文缩写（大字）
    ax.text(bx + bw/2, y + LAYER_H/2 + 0.3, tier_label,
            ha="center", va="center", fontsize=22, fontweight="bold",
            color="white", zorder=5, alpha=0.95)
    # 中文层名
    ax.text(bx + bw/2, y + LAYER_H/2 - 0.4, tier_name,
            ha="center", va="center", fontsize=10.5, fontweight="bold",
            color="white", zorder=5, alpha=0.92)

    # —— 中部：组件列表 ——————————————————————————————————————————
    cx = bx + bw + CARD_PAD + 0.15
    cy_top = y + LAYER_H - 0.3

    # "组件" 小标题
    ax.text(cx, cy_top, "组件构成",
            ha="left", va="top", fontsize=8.5, color="#7f8c8d",
            fontweight="bold", style="italic", zorder=5)

    n = len(components)
    comp_start_y = cy_top - 0.45
    comp_gap = min(0.40, (LAYER_H - 0.8) / n)
    for i, comp in enumerate(components):
        cy = comp_start_y - i * comp_gap
        # 小方块图标
        rbox(ax, cx, cy - 0.08, 0.14, 0.16, color, alpha=0.85, r=0.02, z=5)
        ax.text(cx + 0.25, cy, comp,
                ha="left", va="center", fontsize=9.3,
                color="#2c3e50", zorder=5)

    # —— 垂直分隔竖线 ——————————————————————————————————————————
    sep_x = DEC_X_START - 0.25
    ax.plot([sep_x, sep_x], [y + 0.25, y + LAYER_H - 0.25],
            color="#e3e7ea", lw=1.2, zorder=4)

    # —— 右侧：技术决策 ——————————————————————————————————————————
    dx = DEC_X_START
    dy_top = y + LAYER_H - 0.3

    # 决策标签
    ax.text(dx, dy_top, "技术决策",
            ha="left", va="top", fontsize=8.5, color=color,
            fontweight="bold", style="italic", zorder=5)

    # 决策标题
    ax.text(dx, dy_top - 0.4, decision_title,
            ha="left", va="top", fontsize=10.5, fontweight="bold",
            color="#1c2833", zorder=5)

    # 决策点
    dn = len(decision_points)
    d_start_y = dy_top - 0.85
    d_gap = min(0.52, (LAYER_H - 1.2) / dn)
    for i, pt in enumerate(decision_points):
        py = d_start_y - i * d_gap
        # 彩色圆点
        ax.plot(dx + 0.12, py, "o", color=color, markersize=6,
                alpha=0.85, zorder=5)
        ax.text(dx + 0.35, py, pt,
                ha="left", va="center", fontsize=8.8,
                color="#34495e", zorder=5, wrap=True)


# ── 6 个层次 ──────────────────────────────────────────────────────────────────
COLORS = {
    "fe":  "#16a085",
    "api": "#2471a3",
    "svc": "#b7950b",
    "dal": "#6c3483",
    "etl": "#a04000",
    "db":  "#922b21",
}

LAYERS = [
    # (color, tier_label, tier_name, components, decision_title, decision_points)
    (COLORS["fe"], "FE", "前端层",
     ["Vue 3 + Vite + Element Plus",
      "Pinia — 状态 ↔ URL 双向绑定",
      "ECharts — 数据可视化",
      "queryBuilder — 序列化契约"],
     "前后端契约唯一来源",
     ["queryBuilder 统一生成后端 query 字符串",
      "避免前后端 camelCase/snake_case 漂移",
      "类型定义落在 TS interface，契约可见"]),

    (COLORS["api"], "API", "API 层",
     ["AssetController  /api/v1/assets",
      "StatsController  /api/v1/stats/*",
      "QueryDslParser  DSL 核心",
      "GlobalExceptionHandler",
      "ApiEnvelope · PagedResponse"],
     "自研 bracket-style DSL + 4 层防注入",
     ["作业语法 field[lte]=v，RSQL 格式不同，自研更可控",
      "四层：字段白名单 → 操作符白名单 → #{} 参数化 → XML hardcode",
      "${orderBy} 绝对禁用；稳定分页追加 id DESC tie-breaker"]),

    (COLORS["svc"], "SVC", "业务服务层",
     ["AssetQueryService — 列表 / 详情 / 稀疏字段",
      "AssetStatsService — Q1 / Q2 / Q3 聚合"],
     "稀疏字段双保险，扩展数据集 O(1)",
     ["稀疏字段：DB SELECT 投影 + DTO 剥字段，防敏感泄漏",
      "新增数据集：只需实现 DatasetAdapter 接口",
      "Schema / API / 其他 Normalizer 均不变"]),

    (COLORS["dal"], "DAL", "数据访问层",
     ["AssetMapper.xml — 动态 SQL",
      "PgStringArrayTypeHandler — text[] 适配",
      "MyBatis-Plus — ORM + 分页插件",
      "Flyway — 版本化 DDL 迁移"],
     "解决 PostgreSQL text[] JDBC 序列化陷阱",
     ["JacksonTypeHandler 输出 JSON 字符串，PG 无法识别",
      "自定义 TypeHandler 调用 createArrayOf(\"text\", ...)",
      "在 Mapper XML 通过 typeHandler 属性声明透明适配"]),

    (COLORS["etl"], "ETL", "ETL 导入层",
     ["IngestRunner — ApplicationRunner 入口",
      "DatasetAdapter — 接口 + 三实现",
      "5 个纯函数 Normalizer — 各自单测",
      "IngestBatchService — 幂等 upsert"],
     "应用层归一化，行级预校验防整批回滚",
     ["不放 DB 触发器：脏数据清洗属业务语义，需独立单测",
      "五大难点：Excel 1900闰年Bug · BigDecimal · Python单引号list",
      "行级预校验：null title/uploader 在归一化阶段即剔除"]),

    (COLORS["db"], "DB", "存储层",
     ["assets — UUID · TEXT[] · JSONB · CHECK",
      "ingest_run / ingest_reject_log — 血缘审计",
      "8 个索引：GIN + B-tree，按查询场景建立",
      "ON CONFLICT DO UPDATE — 幂等写入"],
     "PostgreSQL 单表 + JSONB，拒绝 ES / EAV / 多表",
     ["访问模式驱动：结构化过滤+GROUP BY+ACID = PG 主战场",
      "ES 优势（全文检索、亿级 facet）本题不存在",
      "TEXT[]+GIN 胜 JSONB 数组；TEXT CHECK 胜 PG ENUM"]),
]

# 从顶部开始绘制，每层向下移动
start_y = 18.15
for i, (color, tier_label, tier_name, comps, dec_title, dec_points) in enumerate(LAYERS):
    top = start_y - i * (LAYER_H + GAP)
    draw_layer(top, color, tier_label, tier_name, comps, dec_title, dec_points)

    # 层间箭头
    if i < len(LAYERS) - 1:
        arrow_y_top = top - LAYER_H - 0.02
        arrow_y_bot = top - LAYER_H - GAP + 0.02
        ax.annotate("", xy=(W/2, arrow_y_bot), xytext=(W/2, arrow_y_top),
                    arrowprops=dict(arrowstyle="-|>", color="#95a5a6", lw=2.0),
                    zorder=6)


# ══════════════════════════════════════════════════════════════════════════════
# FOOTER
# ══════════════════════════════════════════════════════════════════════════════
footer_y = 0.3
rbox(ax, 0.5, footer_y, W - 1.0, 0.52, "#ecf0f1", r=0.12, z=2)
ax.text(W/2, footer_y + 0.27,
        "生产演进路径  ·  OFFSET 分页 → Keyset  ·  ILIKE → pg_trgm / ES  "
        "·  ETL 全量 → Debezium CDC  ·  无认证 → API Key + Bucket4j",
        ha="center", va="center", fontsize=8.5, color="#5d6d7e", fontweight="bold")

plt.subplots_adjust(left=0.01, right=0.99, top=0.99, bottom=0.01)
plt.savefig(
    "/Users/yanyinxi/工作/code/github/homework/main/docs/arch/architecture-detailed.png",
    dpi=160, bbox_inches="tight", facecolor="white")
plt.close()
print("done")
