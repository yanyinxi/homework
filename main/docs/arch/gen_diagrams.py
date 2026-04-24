"""Generate architecture diagrams for README."""
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import matplotlib.patches as mpatches
from matplotlib.patches import FancyBboxPatch, FancyArrowPatch
import matplotlib.font_manager as fm

# Use a font that supports CJK characters
plt.rcParams["font.family"] = ["Arial Unicode MS", "STHeiti", "PingFang HK", "DejaVu Sans"]


def box(ax, x, y, w, h, label, sublabel="", color="#4A90D9", text_color="white",
        style="round,pad=0.1", fontsize=11, subfontsize=9):
    patch = FancyBboxPatch((x - w/2, y - h/2), w, h,
                           boxstyle=style, linewidth=1.5,
                           edgecolor="#2c3e50", facecolor=color, zorder=3)
    ax.add_patch(patch)
    if sublabel:
        ax.text(x, y + 0.12, label, ha="center", va="center",
                fontsize=fontsize, color=text_color, fontweight="bold", zorder=4)
        ax.text(x, y - 0.18, sublabel, ha="center", va="center",
                fontsize=subfontsize, color=text_color, zorder=4, alpha=0.9)
    else:
        ax.text(x, y, label, ha="center", va="center",
                fontsize=fontsize, color=text_color, fontweight="bold", zorder=4)


def arrow(ax, x1, y1, x2, y2, label="", color="#2c3e50"):
    ax.annotate("", xy=(x2, y2), xytext=(x1, y1),
                arrowprops=dict(arrowstyle="-|>", color=color, lw=1.5),
                zorder=2)
    if label:
        mx, my = (x1+x2)/2, (y1+y2)/2
        ax.text(mx, my + 0.08, label, ha="center", va="bottom",
                fontsize=8, color="#555", zorder=5,
                bbox=dict(boxstyle="round,pad=0.2", facecolor="white", edgecolor="none", alpha=0.8))


def rect_border(ax, x, y, w, h, label, color="#ecf0f1", edgecolor="#bdc3c7"):
    patch = FancyBboxPatch((x, y), w, h,
                           boxstyle="round,pad=0.05", linewidth=1.5,
                           edgecolor=edgecolor, facecolor=color,
                           linestyle="--", zorder=1)
    ax.add_patch(patch)
    ax.text(x + 0.15, y + h - 0.18, label, ha="left", va="top",
            fontsize=9, color="#7f8c8d", style="italic", zorder=2)


# ── Diagram 1: System Architecture ───────────────────────────────────────────
fig, ax = plt.subplots(figsize=(12, 7))
ax.set_xlim(0, 12)
ax.set_ylim(0, 7)
ax.axis("off")
ax.set_facecolor("#f8f9fa")
fig.patch.set_facecolor("#f8f9fa")

ax.text(6, 6.65, "系统架构", ha="center", va="center",
        fontsize=16, fontweight="bold", color="#2c3e50")

# Docker Compose border
rect_border(ax, 2.8, 0.8, 6.4, 5.2, "Docker Compose", "#eaf4fb", "#5dade2")

# Services
box(ax, 6.0, 5.3, 2.4, 0.9, "Frontend", "Vue 3 · Vite · Pinia\nport 80 / 5173",
    color="#27ae60", fontsize=10, subfontsize=8)
box(ax, 6.0, 3.5, 2.4, 0.9, "Backend", "Spring Boot 3.3 · MyBatis-Plus\nport 8080",
    color="#2980b9", fontsize=10, subfontsize=8)
box(ax, 6.0, 1.7, 2.4, 0.9, "PostgreSQL 15", "assets · ingest_run · ingest_reject\nport 5432",
    color="#8e44ad", fontsize=10, subfontsize=8)

# Browser
box(ax, 1.4, 5.3, 1.6, 0.7, "Browser", "", color="#e67e22", fontsize=10)

# XLS
box(ax, 1.4, 2.5, 1.6, 1.4, "XLS × 3", "数据集1\n数据集2\n数据集3",
    color="#c0392b", fontsize=9, subfontsize=8)

# Arrows
arrow(ax, 2.2, 5.3, 4.8, 5.3, "HTTP")
arrow(ax, 6.0, 4.85, 6.0, 4.0, "Axios REST")
arrow(ax, 6.0, 3.05, 6.0, 2.15, "JDBC / MyBatis-Plus")
arrow(ax, 2.2, 2.8, 4.8, 3.3, "ETL on startup\nAdapter → Normalizer\nON CONFLICT DO UPDATE")

# Legend
legend_items = [
    mpatches.Patch(color="#27ae60", label="Frontend"),
    mpatches.Patch(color="#2980b9", label="Backend"),
    mpatches.Patch(color="#8e44ad", label="Database"),
    mpatches.Patch(color="#c0392b", label="Data Sources"),
]
ax.legend(handles=legend_items, loc="lower right", fontsize=8,
          framealpha=0.8, bbox_to_anchor=(0.98, 0.02))

plt.tight_layout(pad=0.5)
plt.savefig("/Users/yanyinxi/工作/code/github/homework/main/docs/arch/system-arch.png",
            dpi=150, bbox_inches="tight", facecolor="#f8f9fa")
plt.close()
print("system-arch.png saved")


# ── Diagram 2: Request Chain ──────────────────────────────────────────────────
fig2, ax2 = plt.subplots(figsize=(13, 4))
ax2.set_xlim(0, 13)
ax2.set_ylim(0, 4)
ax2.axis("off")
ax2.set_facecolor("#f8f9fa")
fig2.patch.set_facecolor("#f8f9fa")

ax2.text(6.5, 3.65, "请求处理链路", ha="center", va="center",
         fontsize=15, fontweight="bold", color="#2c3e50")

nodes = [
    (1.1,  2.0, 1.8, 1.2, "HTTP 请求",      "?status=approved\n&tags[has]=节日\n&sort=uploaded_at:desc", "#e67e22"),
    (3.3,  2.0, 1.8, 1.2, "QueryDslParser", "bracket-style DSL\n字段/操作符\n枚举白名单 → 400", "#2980b9"),
    (5.5,  2.0, 1.8, 1.2, "AssetMapper.xml","动态 SQL\n#{} 参数化\n<foreach> 硬编码列名", "#16a085"),
    (7.7,  2.0, 1.8, 1.2, "PostgreSQL 15",  "GIN idx @>\nB-tree range\nidx_assets_tags_gin", "#8e44ad"),
    (9.9,  2.0, 1.8, 1.2, "ApiEnvelope",    "{code, message, data}\nPagedResponse\n{items, total, page}", "#27ae60"),
]

for (x, y, w, h, label, sub, color) in nodes:
    box(ax2, x, y, w, h, label, sub, color=color, fontsize=9, subfontsize=7.5)

for i in range(len(nodes) - 1):
    x1 = nodes[i][0] + nodes[i][2]/2
    x2 = nodes[i+1][0] - nodes[i+1][2]/2
    arrow(ax2, x1, nodes[i][1], x2, nodes[i+1][1])

# Rejection path
ax2.annotate("", xy=(3.3, 0.8), xytext=(3.3, 1.4),
             arrowprops=dict(arrowstyle="-|>", color="#e74c3c", lw=1.5))
ax2.text(3.3, 0.55, "未知字段/操作符 → 400 Bad Request",
         ha="center", va="center", fontsize=8, color="#e74c3c",
         bbox=dict(boxstyle="round,pad=0.25", facecolor="#fde8e8", edgecolor="#e74c3c", alpha=0.9))

plt.tight_layout(pad=0.5)
plt.savefig("/Users/yanyinxi/工作/code/github/homework/main/docs/arch/request-chain.png",
            dpi=150, bbox_inches="tight", facecolor="#f8f9fa")
plt.close()
print("request-chain.png saved")


# ── Diagram 3: ETL Pipeline ───────────────────────────────────────────────────
fig3, ax3 = plt.subplots(figsize=(14, 4.5))
ax3.set_xlim(0, 14)
ax3.set_ylim(0, 4.5)
ax3.axis("off")
ax3.set_facecolor("#f8f9fa")
fig3.patch.set_facecolor("#f8f9fa")

ax3.text(7, 4.15, "ETL 数据导入流水线", ha="center", va="center",
         fontsize=15, fontweight="bold", color="#2c3e50")

etl_nodes = [
    (1.0,  2.5, 1.5, 1.1, "XLS 源文件",      "数据集1/2/3\n格式各异", "#c0392b"),
    (2.9,  2.5, 1.6, 1.1, "ExcelReader",     "Apache POI\n逐行读取", "#d35400"),
    (4.9,  2.5, 1.7, 1.1, "DatasetAdapter",  "字段映射\n三个实现类", "#e67e22"),
    (7.0,  2.5, 1.7, 1.4, "EtlNormalizers",  "DateNorm·SizeNorm\nTagNorm·StatusNorm\nPlatformNorm", "#2980b9"),
    (9.2,  2.5, 1.6, 1.1, "行级校验",         "title/uploader\nstatus 非空", "#16a085"),
    (11.1, 2.5, 1.6, 1.1, "BatchService",    "ON CONFLICT\nDO UPDATE", "#27ae60"),
    (13.0, 2.5, 1.4, 1.1, "assets 表",        "69 条有效\n记录入库", "#8e44ad"),
]

for (x, y, w, h, label, sub, color) in etl_nodes:
    box(ax3, x, y, w, h, label, sub, color=color, fontsize=9, subfontsize=7.5)

for i in range(len(etl_nodes) - 1):
    x1 = etl_nodes[i][0] + etl_nodes[i][2]/2
    x2 = etl_nodes[i+1][0] - etl_nodes[i+1][2]/2
    arrow(ax3, x1, etl_nodes[i][1], x2, etl_nodes[i+1][1])

# Reject path from 行级校验
ax3.annotate("", xy=(9.2, 0.9), xytext=(9.2, 1.95),
             arrowprops=dict(arrowstyle="-|>", color="#e74c3c", lw=1.5))
ax3.text(9.2, 0.6, "拒绝：ingest_reject_log\nvid0008/vid0015/vid0017",
         ha="center", va="center", fontsize=7.5, color="#e74c3c",
         bbox=dict(boxstyle="round,pad=0.25", facecolor="#fde8e8", edgecolor="#e74c3c", alpha=0.9))

# Stats annotation
ax3.text(7.0, 0.25, "74 源行  →  3条空标题拒绝  →  2条重复幂等合并  →  69条入库",
         ha="center", va="center", fontsize=9, color="#555",
         bbox=dict(boxstyle="round,pad=0.3", facecolor="#fffff0", edgecolor="#bbb", alpha=0.9))

plt.tight_layout(pad=0.5)
plt.savefig("/Users/yanyinxi/工作/code/github/homework/main/docs/arch/etl-pipeline.png",
            dpi=150, bbox_inches="tight", facecolor="#f8f9fa")
plt.close()
print("etl-pipeline.png saved")
